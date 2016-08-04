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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sensor {

  private static final Logger LOGGER = LogManager.getFormatterLogger(Sensor.class);

  private static final String HELP_MESSAGE = "This class allows to register sensors and get value readings";

  private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  private static final String OPTION_TYPE = "t";
  private static final String OPTION_READING_PATH = "p";
  private static final String OPTION_ADDRESS = "a";
  private static final String OPTION_NAME = "n";


  private static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
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

  // The following constants are used to serialize data in JSON format
  private static final String DO_NAME = "dissolved_oxygen";
  private static final String SP_NAME = "saturation_percentage";
  private static final String PH_NAME = "pH";
  private static final String TEMP_NAME = "temperature";

  // Reading and log file default locations
  private static final String DEFAULT_READING_PATH = "/tmp/sensors/";
  private static final String LOG_EXTENSION = ".log";

  // READ command for sensor
  // This command is the same across Sensor types (pH, dissolved oxygen, temperature).
  // If that changes, make it Sensor type specific.
  private static final byte READ_COMMAND = (byte) 'R';

  // When reading EZO circuits responses, we ask for a specific number of bytes.
  // The following constant defines how many bytes to read from the circuit response to a read query
  // Max number of bytes back from each sensor: {pH: 7, Temp: 9, DO: 14}
  // Therefore, this constant is set to 14 to be sure to read everything. Any extra byte will be null ('\0')
  private static final Integer N_BYTES = 14;

  // Sensor have nominal read delays (aka processing time), that we store in the following Map.
  // These can be found in the datasheets
  private static final Map<SensorType, Integer> NOMINAL_READ_DELAY = new HashMap<>();
  static {
    NOMINAL_READ_DELAY.put(SensorType.PH, 1000);
    NOMINAL_READ_DELAY.put(SensorType.DO, 1000);
    NOMINAL_READ_DELAY.put(SensorType.TEMP, 600);
  }
  // As a safeguard against missed readings, we add an extra 200 ms before we try to read the device response
  private static final Integer ADD_READ_DELAY = 200; // 200 ms

  // In the event of a failed reading, we will retry N_RETRIES times to read, after waiting RETRY_DELAY seconds.
  private static final Integer N_RETRIES = 3;
  private static final Integer RETRY_DELAY = 500;

  // Default bus is #1
  private static final Integer I2CBUS = I2CBus.BUS_1;

  // Device object
  private I2CDevice sensor;
  // Device Type
  private SensorType sensorType;
  // Device name
  private String deviceName;
  // Sensor reading file location
  private Path sensorReadingFilePath;
  // Sensor reading log file location
  private Path sensorReadingLogFilePath;

  // Sensor config parameters
  private Byte readCommand;
  private Integer readQueryTimeDelay;


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

  private void setupFiles(String sensorReadingPath) {
    String logFilename = deviceName.concat(LOG_EXTENSION);
    Path sensorReadingDirectory = Paths.get(sensorReadingPath, sensorType.name());
    this.sensorReadingFilePath = Paths.get(
        sensorReadingDirectory.toString(), deviceName);
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
        Thread.sleep(RETRY_DELAY);
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
        break;
      case TEMP:
        valueMap.put(TEMP_NAME, Double.parseDouble(response));
        break;
      case DO:
        String[] responseArray = response.split(",");
        if (responseArray.length < 2) {
          LOGGER.error("Error while parsing sensor values: found array of size %d and expected 2.\n" +
              "Device response was %s", responseArray.length, Arrays.toString(responseArray));
        }
        valueMap.put(DO_NAME, Double.parseDouble(responseArray[0]));
        valueMap.put(SP_NAME, Double.parseDouble(responseArray[1]));
        break;
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
        break;
      case DO:
        Double dissolvedOxygen = valueMap.get(DO_NAME);
        Double saturationPercentage = valueMap.get(SP_NAME);
        sensorData = new DOSensorData(dissolvedOxygen, saturationPercentage, deviceName, currTime);
        break;
      case TEMP:
        Double temperature = valueMap.get(TEMP_NAME);
        sensorData = new TempSensorData(temperature, deviceName, currTime);
        break;
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
      System.exit(1);
    }
    // We start an infinite reading loop, which we exit only by interrupting the process.
    while (INFINITE_LOOP_READING) {
      byte[] sensorResponse = readSensorResponse();
      Map<String, Double> valueMap = parseSensorValueFromResponse(sensorResponse);
      SensorData sensorData = getSensorDataFromValueMap(valueMap);
      try {
        atomicWrite(sensorReadingTmp, generator, sensorData);
      } catch (IOException e) {
        LOGGER.error("Exception when trying to write the sensor data to file: %s", e);
        System.exit(1);
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
    SensorType sensorType = null;
    try {
      sensorType  = SensorType.valueOf(cl.getOptionValue(OPTION_TYPE));
      LOGGER.debug("Sensor Type %s was choosen", sensorType);
    } catch (IllegalArgumentException e) {
      LOGGER.error("Illegal value for Sensor Type. Note: it is case-sensitive.");
      System.exit(1);
    }

    Integer deviceAddress = Integer.parseInt(cl.getOptionValue(OPTION_ADDRESS));
    String deviceName = cl.getOptionValue(OPTION_NAME);
    String sensorReadingPath = cl.getOptionValue(OPTION_READING_PATH, DEFAULT_READING_PATH);

    Sensor sensor = new Sensor(sensorType, deviceName);
    sensor.setup(deviceAddress, sensorReadingPath);
    sensor.run();
  }
}
