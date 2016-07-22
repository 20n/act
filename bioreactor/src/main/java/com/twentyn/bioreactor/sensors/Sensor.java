package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.twentyn.bioreactor.util.Time;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sensor {

  protected static final Logger LOGGER = LogManager.getFormatterLogger(Sensor.class);

  public static final String HELP_MESSAGE = "This class allows to register sensors and get value readings";

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  private static final String OPTION_TYPE = "t";
  private static final String OPTION_READING_PATH = "p";
  private static final String OPTION_ADDRESS = "a";
  private static final String OPTION_NAME = "n";


  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_TYPE)
        .argName("sensor type")
        .desc("Type of sensor: can take values of the enum SensorType: {PH, DO, TEMP}")
        .hasArg().required()
        .longOpt("sensor_type")
    );
    add(Option.builder(OPTION_ADDRESS)
        .argName("sensor address")
        .desc("Address of the sensor to read from")
        .hasArg().required()
        .longOpt("sensor_address")
    );
    add(Option.builder(OPTION_NAME)
        .argName("sensor name")
        .desc("Name under which register the sensor")
        .hasArg().required()
        .longOpt("sensor_name")
    );
    add(Option.builder(OPTION_READING_PATH)
        .argName("reading log path")
        .desc("Directory in which to store the sensor readings/logs")
        .hasArg()
        .longOpt("reading_log_path")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  private static final Boolean INFINITE_LOOP_READING = true;

  private static final String DO_NAME = "dissolved_oxygen";
  private static final String SP_NAME = "saturation_percentage";
  private static final String PH_NAME = "pH";
  private static final String TEMP_NAME = "temperature";


  // Device address
  private static final String DEFAULT_ADDRESS = "99";
  // Device name
  private static final String DEFAULT_SENSOR_NAME = "PH_sensor_0";
  // Reading and log file default locations
  private static final String DEFAULT_SENSOR_READING_PATH = "/tmp/sensors/";
  private static final String LOG_EXTENSION = ".log";

  // READ command for sensor
  private static final byte READ_COMMAND = (byte) 'R';
  // Number of bytes to read from the response
  // DO sensor: the response format is [1,{DO},null]
  //            total number of bytes to read is 14 (response should never be more according to datasheet)
  //            http://www.atlas-scientific.com/_files/_datasheets/_circuit/pH_EZO_datasheet.pdf
  //  TODO: update this class so we only read until
  private static final int N_BYTES = 14;

  private static final int RETRY_TIMEOUT = 500;
  private static final int N_RETRIES = 3;


  private static final Map<SensorType, Integer> NOMINAL_READ_DELAY = new HashMap<>();
  private static final Integer ADD_READ_DELAY = 200; // 200 ms
  static {
    NOMINAL_READ_DELAY.put(SensorType.PH, 1000);
    NOMINAL_READ_DELAY.put(SensorType.DO, 1000);
    NOMINAL_READ_DELAY.put(SensorType.TEMP, 600);
  }

  private static final Integer I2CBUS = I2CBus.BUS_1;

  // Device object
  protected I2CDevice sensor;
  // Device Type
  private SensorType sensorType;
  // Device name
  protected String deviceName;
  // Sensor reading file location
  private Path sensorReadingFilePath;
  // Sensor reading log file location
  private Path sensorReadingLogFilePath;

  // Sensor config parameters
  protected byte readCommand;
  protected int readQueryTimeDelay;


  private static ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JodaModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
  }

  public enum SensorType {
    PH, DO, TEMP
  }

  public Sensor(SensorType sensorType, String deviceName) {
    this.sensorType = sensorType;
    this.deviceName = deviceName;
  }

  public void setup(Integer deviceAddress, String sensorReadingPath) {
    // connects the device, create the right logging directories
    connectToDevice(I2CBUS, deviceAddress);
    setupFiles(sensorReadingPath);
    this.readCommand = READ_COMMAND;
    this.readQueryTimeDelay = NOMINAL_READ_DELAY.get(sensorType) + ADD_READ_DELAY;
  }

  public void setupFiles(String sensorReadingPath) {
    String readingFilename = String.join(deviceName);
    String logFilename = String.join(deviceName, LOG_EXTENSION);
    Path sensorReadingDirectory = Paths.get(sensorReadingPath, sensorType.name());
    this.sensorReadingFilePath = Paths.get(
        sensorReadingDirectory.toString(), readingFilename);
    this.sensorReadingLogFilePath = Paths.get(
        sensorReadingDirectory.toString(), logFilename);
    if (!Files.exists(sensorReadingDirectory)) {
      Boolean madeDir = sensorReadingDirectory.toFile().mkdirs();
      if (!madeDir) {
        LOGGER.error("The following directory could not be accessed or created: %s", sensorReadingDirectory);
      }
    }
  }

  private void connectToDevice(Integer i2CBusNumber, Integer deviceAddress) {

    try {
      I2CBus bus = I2CFactory.getInstance(i2CBusNumber);
      LOGGER.info("Connected to bus #%d\n", i2CBusNumber);
      sensor = bus.getDevice(deviceAddress);
      LOGGER.info("Connected to device at address %d\n", deviceAddress);

    } catch (I2CFactory.UnsupportedBusNumberException e) {
      LOGGER.error("Connection to bus #%d failed: %s", i2CBusNumber, e);
      System.exit(1);
    }
    catch (IOException e) {
      LOGGER.error("Connection to device at address %d failed: %s\n", deviceAddress, e);
      System.exit(1);
    }
  }

  private Boolean readSuccess(byte[] deviceResponse) {
    return deviceResponse[0] == (byte) 1;
  }

  private byte[] readSensorResponse() {

    byte[] deviceResponse = new byte[N_BYTES];
    try {
      sensor.write(readCommand);
      Thread.sleep(readQueryTimeDelay);

      sensor.read(deviceResponse, 0, N_BYTES);
      int retryCounter = 0;
      while (!readSuccess(deviceResponse) && retryCounter < N_RETRIES) {
        LOGGER.debug("Read failed: will try %d times more", N_RETRIES - retryCounter);
        Thread.sleep(RETRY_TIMEOUT);
        retryCounter++;
        sensor.read(deviceResponse, 0, N_BYTES);
      }
      if (!readSuccess(deviceResponse)) {
        LOGGER.error("Did not manage to read sensor values after %d retries\n", N_RETRIES);
      }
      LOGGER.debug("Read succeeded after %d retries", retryCounter);

    } catch (IOException e) {
      LOGGER.error("Error reading sensor value: " + e.getMessage());
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted Exception: " + e.getMessage());
    }
    return deviceResponse;
  }

  public DateTime now() {
    return Time.now();
  }

  private Map<String, Double> parseSensorValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    Map<String, Double> valueMap = new HashMap<>();
    switch (sensorType) {
      case PH:
        valueMap.put(PH_NAME, Double.parseDouble(response));
      case TEMP:
        valueMap.put(TEMP_NAME, Double.parseDouble(response));
      case DO:
        String[] responseArray = response.split(",");
        if (responseArray.length < 2) {
          LOGGER.error("Error while parsing sensor values: found array of size %d and expected 2.\n" +
              "Device response was %s", responseArray.length, responseArray.toString());
        }
        valueMap.put(DO_NAME, Double.parseDouble(responseArray[0]));
        valueMap.put(SP_NAME, Double.parseDouble(responseArray[1]));
    }
    return valueMap;
  }

  private SensorData getSensorDataFromValueMap(Map<String, Double> valueMap) {
    DateTime currTime = now();
    SensorData sensorData = null;
    switch (sensorType) {
      case PH:
        Double pH = valueMap.get(PH_NAME);
        sensorData = new PHSensorData(pH, deviceName, currTime);
      case DO:
        Double dissolvedOxygen = valueMap.get(DO_NAME);
        Double saturationPercentage = valueMap.get(SP_NAME);
        sensorData = new DOSensorData(dissolvedOxygen, saturationPercentage, deviceName, currTime);
      case TEMP:
        Double temperature = valueMap.get(TEMP_NAME);
        sensorData = new TempSensorData(temperature, deviceName, currTime);
    }
    return sensorData;
  }

  private void atomicWrite(File sensorReadingTmp, JsonGenerator generator, SensorData sensorData) throws IOException {
    // Writing single value for control module to use
    objectMapper.writeValue(sensorReadingTmp, sensorData);
    // Copy a single reading from its tmp location to its final location
    // We do this to make sure a file will always have a valid reading to process
    Files.copy(sensorReadingTmp.toPath(), sensorReadingFilePath, StandardCopyOption.REPLACE_EXISTING);
    // Appending value to log file
    objectMapper.writeValue(generator, sensorData);
  }

  public void run() {
    JsonGenerator generator = null;
    File sensorReadingTmp = null;
    try {
      generator = objectMapper.getFactory().createGenerator(
          new File(sensorReadingLogFilePath.toString()), JsonEncoding.UTF8);
      sensorReadingTmp = File.createTempFile(sensorReadingFilePath.toString(), ".tmp");
    } catch (IOException e) {
      LOGGER.error("Error during reading/log files creation: %s", e);
    }
    while (INFINITE_LOOP_READING) {
      byte[] sensorResponse = readSensorResponse();
      Map<String, Double> valueMap = parseSensorValueFromResponse(sensorResponse);
      SensorData sensorData = getSensorDataFromValueMap(valueMap);
      try {
        atomicWrite(sensorReadingTmp, generator, sensorData);
      } catch (IOException e) {
        LOGGER.error("Exception when trying to write the sensor data to file: %s", e);
      }
    }
  }


  public static void main(String[] args) {

    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      LOGGER.error(String.format("Argument parsing failed: %s\n", e.getMessage()));
      HELP_FORMATTER.printHelp(Sensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(Sensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    SensorType sensorType = SensorType.valueOf(cl.getOptionValue(OPTION_TYPE));
    Integer deviceAddress = Integer.parseInt(cl.getOptionValue(OPTION_ADDRESS));
    String deviceName = cl.getOptionValue(OPTION_NAME);
    String sensorReadingPath = cl.getOptionValue(OPTION_READING_PATH);

    Sensor sensor = new Sensor(sensorType, deviceName);
    sensor.setup(deviceAddress, sensorReadingPath);
    sensor.run();
  }
}
