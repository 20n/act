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

  private static final Logger LOGGER = LogManager.getFormatterLogger(Sensor.class);

  private static final String HELP_MESSAGE =
      "This class allows clients to register and run different sensors from a Raspberry Pi";

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
        .desc("Name under which to register the sensor")
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

  // Response codes from a read event (encoded in the first byte)
  private static final byte REPONSE_CODE_SUCCESS = (byte) 1;
  private static final byte REPONSE_CODE_FAILED = (byte) 2;
  private static final byte REPONSE_CODE_PENDING = (byte) 254;
  private static final byte REPONSE_CODE_NO_DATA = (byte) 255;

  // In the event of a failed reading, we will retry N_RETRIES times to read, after waiting RETRY_DELAY seconds.
  private static final Integer N_RETRIES = 3;
  private static final Integer RETRY_DELAY = 500;

  // Default bus is #1
  private static final Integer I2CBUS = I2CBus.BUS_1;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  // Device object
  private I2CDevice sensor;
  // Device Type
  private SensorType sensorType;
  // Device name
  private String deviceName;
  // Sensor data (updated each read)
  private SensorData sensorData;
  // Sensor reading file location
  private Path sensorReadingFilePath;
  // Sensor reading log file location
  private Path sensorReadingLogFilePath;
  // Json generator to append to log file
  private JsonGenerator jsonGenerator;
  // Temp file where to write readings before atomically moving it
  private File sensorReadingTmp;

  // Sensor config parameters
  private Byte readCommand;
  private Integer readQueryTimeDelay;

  static {
    objectMapper.registerModule(new JodaModule());
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
  }

  public enum SensorType {
    PH, DO, TEMP
  }

  public Sensor(SensorType sensorType, String deviceName) {
    this.sensorType = sensorType;
    this.deviceName = deviceName;
    switch (sensorType) {
      case PH:
        sensorData = new PHSensorData();
        break;
      case DO:
        sensorData = new DOSensorData();
        break;
      case TEMP:
        sensorData = new TempSensorData();
        break;
    }
    sensorData.setDeviceName(deviceName);
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
    try {
      this.jsonGenerator = objectMapper.getFactory().createGenerator(
          new File(sensorReadingLogFilePath.toString()), JsonEncoding.UTF8);
      this.sensorReadingTmp = File.createTempFile(sensorReadingFilePath.toString(), ".tmp");
    } catch (IOException e) {
      LOGGER.error("Error during reading/log files creation: %s", e);
      System.exit(1);
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

  public void setReadCommand(byte readCommand) {
    this.readCommand = readCommand;
  }

  private byte[] readSensorResponse() {

    byte[] deviceResponse = new byte[N_BYTES];
    try {
      sensor.write(readCommand);
      Thread.sleep(readQueryTimeDelay);

      sensor.read(deviceResponse, 0, N_BYTES);
      byte responseCode = deviceResponse[0];
      if (responseCode == REPONSE_CODE_FAILED || responseCode == REPONSE_CODE_NO_DATA) {
        LOGGER.error("A read query failed or returned no data");
        throw new IOException("The device did not return any sensor reading.");
      } else if (responseCode == REPONSE_CODE_PENDING) {
        int retryCounter = 0;
        while (responseCode == REPONSE_CODE_PENDING && retryCounter <= N_RETRIES) {
          LOGGER.debug("Read query returned PENDING response code: %d remaining attempts to read",
              N_RETRIES - retryCounter);
          Thread.sleep(RETRY_DELAY);
          sensor.read(deviceResponse, 0, N_BYTES);
          responseCode = deviceResponse[0];
          retryCounter++;
        }
        if (responseCode != REPONSE_CODE_SUCCESS) {
          LOGGER.error("Did not manage to read sensor values after %d retries\n", N_RETRIES);
          throw new IOException("The device did not return any sensor reading.");
        }
        LOGGER.debug("Read succeeded after %d retries", retryCounter);
      }
    } catch (IOException e) {
      LOGGER.error("Error reading sensor value: %s", e);
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted Exception: %s", e);
    }
    return deviceResponse;
  }

  public DateTime now() {
    return Time.now();
  }

  private void atomicWrite(SensorData sensorData) throws IOException {
    // Write a sensor reading to a temporary file
    objectMapper.writeValue(sensorReadingTmp, sensorData);
    // Atomically move the temporary file once written to the location
    Files.move(sensorReadingTmp.toPath(), sensorReadingFilePath,
        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    // Append sensor reading to log file
    objectMapper.writeValue(jsonGenerator, sensorData);
  }

  public void run() {
    // We start an infinite reading loop, which we exit only by interrupting the process.
    while (INFINITE_LOOP_READING) {
      byte[] sensorResponse = readSensorResponse();
      sensorData.setTimeOfReading(now());
      sensorData.parseSensorDataFromResponse(sensorResponse);
      try {
        atomicWrite(sensorData);
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
      HELP_FORMATTER.printHelp(Sensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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
