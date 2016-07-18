package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.joda.time.DateTime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PHSensor extends Sensor {

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
  // Device name
  private static final String DEFAULT_SENSOR_NAME = "pH_sensor_0";
  // Reading and log file default locations
  private static final String DEFAULT_SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private static final String DEFAULT_SENSOR_READING_LOG_FILE_LOCATION = "/tmp/sensors/v1/pH/reading_log.json";

  // READ command for sensor
  private static final byte READ_COMMAND = (byte) 0x52; // R in hex
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

  public void parseCommandLineOptions(CommandLine cl) {
    deviceAddress = Integer.parseInt(cl.getOptionValue(OPTION_SENSOR_ADDRESS, DEFAULT_ADDRESS));
    deviceName = cl.getOptionValue(OPTION_SENSOR_NAME, DEFAULT_SENSOR_NAME);
    sensorReadingLogFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_LOG_FILE_LOCATION,
        DEFAULT_SENSOR_READING_LOG_FILE_LOCATION);
    sensorReadingFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_FILE_LOCATION,
        DEFAULT_SENSOR_READING_FILE_LOCATION);
  }

  public Double parseSensorValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    return Double.parseDouble(response);
  }

  public void run() {
    try {
      JsonGenerator g = objectMapper.getFactory().createGenerator(
          new File(sensorReadingLogFileLocation), JsonEncoding.UTF8);
      String sensorReadingTmpFileLocation = String.format("%s.tmp", sensorReadingFileLocation);
      File sensorReadingTmp = new File(sensorReadingTmpFileLocation);

      while (true) {
        byte[] sensorResponse = readSensorResponse();
        Double phValueFromResponse = parseSensorValueFromResponse(sensorResponse);
        DateTime currTime = new DateTime();
        PHSensorData phSensorData = new PHSensorData(phValueFromResponse, deviceName, currTime);
        try {
          // Writing single reading to a tmp location
          objectMapper.writeValue(sensorReadingTmp, phSensorData);
          // Copy a single reading from its tmp location to its final location
          // We do this to make sure a file will always have a valid reading to process
          Path sensorReadingTmpPath = Paths.get(sensorReadingTmpFileLocation);
          Path sensorReadingPath = Paths.get(sensorReadingFileLocation);
          Files.copy(sensorReadingTmpPath, sensorReadingPath);
          // Appending value to log file
          objectMapper.writeValue(g, phSensorData);
        } catch (IOException e) {
          super.LOGGER.error("Exception when trying to write phSensorData: %s", e);
        }
      }
    } catch (IOException e) {
      super.LOGGER.error("Exception when trying to log phSensorData: %s", e);
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
      HELP_FORMATTER.printHelp(PHSensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(PHSensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }
    
    PHSensor sensor = new PHSensor();
    sensor.setSensorConfig(READ_COMMAND, READ_QUERY_TIMEOUT, RETRY_TIMEOUT, N_RETRIES, N_BYTES);
    sensor.parseCommandLineOptions(cl);
    sensor.connect();
    sensor.run();
  }
}
