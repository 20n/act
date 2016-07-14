package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.joda.time.DateTime;

public class Sensor {

  private static final Logger LOGGER = LogManager.getFormatterLogger(Sensor.class);

  private static final String OPTION_SENSOR_READING_FILE_LOCATION = "r";
  private static final String OPTION_SENSOR_READING_LOG_FILE_LOCATION = "l";
  private static final String OPTION_SENSOR_ADDRESS = "a";
  private static final String OPTION_SENSOR_NAME = "n";

  public static final String HELP_MESSAGE = "This class allows to register sensors and get value readings";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_SENSOR_READING_FILE_LOCATION)
        .argName("reading file location")
        .desc("Location of the file where to store the sensor reading")
        .hasArg()
        .longOpt("reading_file_location")
    );
    add(Option.builder(OPTION_SENSOR_READING_LOG_FILE_LOCATION)
        .argName("reading log location")
        .desc("Location of the file where to log the sensor reading")
        .hasArg()
        .longOpt("reading_log_location")
    );
    add(Option.builder(OPTION_SENSOR_ADDRESS)
        .argName("sensor address")
        .desc("Address of the sensor to interact with")
        .hasArg()
        .longOpt("sensor_address")
    );
    add(Option.builder(OPTION_SENSOR_NAME)
        .argName("sensor name")
        .desc("Name of the sensor to interact with")
        .hasArg()
        .longOpt("sensor_name")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  // Device address
  private static final String DEFAULT_ADDRESS = "99";
  // READ command for sensor
  private static final byte DEFAULT_READ_COMMAND = (byte) 0x52; // R in hex
  // Device name
  private static final String DEFAULT_SENSOR_NAME = "pH_sensor_0";
  // Reading and log file default locations
  private static final String DEFAULT_SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private static final String DEFAULT_SENSOR_READING_LOG_FILE_LOCATION = "/tmp/sensors/v1/pH/reading_log.json";

  // Number of bytes to read from the response
  // pH sensor: the response format is [1,{pH},null] where pH is encoded over 5 bytes
  //            hence, total number of bytes to read is 7 (response should never be more according to datasheet)
  //            http://www.atlas-scientific.com/_files/_datasheets/_circuit/pH_EZO_datasheet.pdf
  private static final int N_BYTES = 7;
  // Time delay to read response from the chip
  // According to the datasheet, 1sec. Adding 500ms for safety.
  private static final int READ_QUERY_TIMEOUT = 1500;
  private static final int RETRY_TIMEOUT = 500;
  private static final int N_RETRIES = 3;


  //I2C bus
  private I2CBus bus;
  // Device object
  private I2CDevice sensor;
  // Device address
  private int deviceAddress;
  // Device name
  private String deviceName;

  private ObjectMapper objectMapper = new ObjectMapper();

  public Sensor(int deviceAddress, String deviceName) {
    objectMapper.registerModule(new JodaModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);

    this.deviceAddress = deviceAddress;
    this.deviceName = deviceName;

    // Connect to bus
    int i2CBus = I2CBus.BUS_1;
    try {
      bus = I2CFactory.getInstance(i2CBus);
      LOGGER.info("Connected to bus");
    } catch (Exception e) {
      LOGGER.error("Connection to bus %d failed: %s\n", i2CBus, e);
    }

    // Connect to device
    try {
      sensor = bus.getDevice(deviceAddress);
      LOGGER.info("Connected to device at address %d\n", deviceAddress);

    } catch (Exception e) {
      LOGGER.error("Connection to device at address %d failed: %s\n", deviceAddress, e);
    }
  }

  public Boolean readSuccess(byte[] deviceResponse) {
    if (deviceResponse[0] == (byte) 1) {
      return true;
    }
    return false;
  }

  public Double readSensorValue() {

    byte[] deviceResponse = new byte[N_BYTES];
    try {
      sensor.write(DEFAULT_READ_COMMAND);
      Thread.sleep(READ_QUERY_TIMEOUT);

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
    return parseSensorValueFromResponse(deviceResponse);
  }

  public Double parseSensorValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    return Double.parseDouble(response);
  }

  public void run(String sensorReadingLogFileLocation, String sensorReadingFileLocation) {
    try {
      JsonGenerator g = objectMapper.getFactory().createGenerator(
          new File(sensorReadingLogFileLocation), JsonEncoding.UTF8);
      File sensorReading = new File(sensorReadingFileLocation);

      while(true) {
        Double phValueFromResponse = readSensorValue();
        DateTime currTime = new DateTime();
        PHSensorData phSensorData = new PHSensorData(phValueFromResponse, deviceName, currTime);
        try {
          // Writing single value for control module to use
          objectMapper.writeValue(sensorReading, phSensorData);
          // Appending value to log file
          objectMapper.writeValue(g, phSensorData);
        } catch (IOException e) {
          LOGGER.error("Exception when trying to write phSensorData: %s", e);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Exception when trying to log phSensorData: %s", e);
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
      HELP_FORMATTER.printHelp(ControlSystem.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ControlSystem.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    int deviceAddress = Integer.parseInt(cl.getOptionValue(OPTION_SENSOR_ADDRESS, DEFAULT_ADDRESS));
    String deviceName = cl.getOptionValue(OPTION_SENSOR_NAME, DEFAULT_SENSOR_NAME);
    String sensorReadingLogFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_LOG_FILE_LOCATION,
        DEFAULT_SENSOR_READING_LOG_FILE_LOCATION);
    String sensorReadingFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_FILE_LOCATION,
        DEFAULT_SENSOR_READING_FILE_LOCATION);
    Sensor sensor = new Sensor(deviceAddress, deviceName);
    sensor.run(sensorReadingLogFileLocation, sensorReadingFileLocation);
  }
}
