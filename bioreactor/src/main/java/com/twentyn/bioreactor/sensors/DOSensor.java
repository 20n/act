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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DOSensor extends Sensor {

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
  private static final String DEFAULT_ADDRESS = "97";
  // Device name
  private static final String DEFAULT_SENSOR_NAME = "DO_sensor_0";
  // Reading and log file default locations
  private static final String DEFAULT_SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/DO/reading.json";
  private static final String DEFAULT_SENSOR_READING_LOG_FILE_LOCATION = "/tmp/sensors/v1/DO/reading_log.json";

  // READ command for sensor
  private static final byte READ_COMMAND = (byte) 'R';
  // Number of bytes to read from the response
  // DO sensor: the response format is [1,{DO},null]
  //            total number of bytes to read is 14 (response should never be more according to datasheet)
  //            http://www.atlas-scientific.com/_files/_datasheets/_circuit/pH_EZO_datasheet.pdf
  //  TODO: update this class so we only read until
  private static final int N_BYTES = 14;
  // Time delay to read response from the chip
  // According to the datasheet, 1sec. Adding 500ms for safety.
  private static final int READ_QUERY_TIME_DELAY = 1500;
  private static final int RETRY_TIMEOUT = 500;
  private static final int N_RETRIES = 3;

  private static final String DO_NAME = "dissolved_oxygen";
  private static final String SP_NAME = "saturation_percentage";

  private static final Boolean INFINITE_LOOP_READING = true;

  public void parseCommandLineOptions(CommandLine cl) {
    deviceAddress = Integer.parseInt(cl.getOptionValue(OPTION_SENSOR_ADDRESS, DEFAULT_ADDRESS));
    deviceName = cl.getOptionValue(OPTION_SENSOR_NAME, DEFAULT_SENSOR_NAME);
    sensorReadingLogFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_LOG_FILE_LOCATION,
        DEFAULT_SENSOR_READING_LOG_FILE_LOCATION);
    sensorReadingFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_FILE_LOCATION,
        DEFAULT_SENSOR_READING_FILE_LOCATION);
  }

  // TODO: we assume so far that the sensor will output the data in the right order
  // In the future, we should check that it is the case by querying the parameters order
  public Map<String, Double> parseSensorValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    String[] responseArray = response.split(",");
    if (responseArray.length < 2) {
      LOGGER.error("Error while parsing sensor values: found array of size %d", responseArray.length);
    }
    return new HashMap<String, Double>() {{
      put(DO_NAME, Double.parseDouble(responseArray[0]));
      put(SP_NAME, Double.parseDouble(responseArray[1]));
    }};
  }

  public void atomicWrite(File sensorReadingTmp, JsonGenerator generator,
                          DOSensorData dOSensorData) throws IOException {
    // Writing single value for control module to use
    objectMapper.writeValue(sensorReadingTmp, dOSensorData);
    // Copy a single reading from its tmp location to its final location
    // We do this to make sure a file will always have a valid reading to process
    Path sensorReadingPath = Paths.get(sensorReadingFileLocation);
    Files.copy(sensorReadingTmp.toPath(), sensorReadingPath, StandardCopyOption.REPLACE_EXISTING);
    // Appending value to log file
    objectMapper.writeValue(generator, dOSensorData);
  }

  public void run() {

    JsonGenerator generator = null;
    File sensorReadingTmp = null;
    try {
      generator = objectMapper.getFactory().createGenerator(
          new File(sensorReadingLogFileLocation), JsonEncoding.UTF8);
      sensorReadingTmp = File.createTempFile(sensorReadingFileLocation, ".tmp");
    } catch (IOException e) {
      LOGGER.error("Error during reading log file creation: %s", e);
    }
    while (INFINITE_LOOP_READING) {
      byte[] sensorResponse = readSensorResponse();
      Map<String, Double> valueMap = parseSensorValueFromResponse(sensorResponse);
      Double dissolvedOxygen = valueMap.get(DO_NAME);
      Double saturationPercentage = valueMap.get(SP_NAME);
      DateTime currTime = now();
      DOSensorData dOSensorData = new DOSensorData(dissolvedOxygen, saturationPercentage, deviceName, currTime);
      try {
        atomicWrite(sensorReadingTmp, generator, dOSensorData);
      } catch (IOException e) {
        LOGGER.error("Exception when trying to write dOSensorData: %s", e);
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
      HELP_FORMATTER.printHelp(DOSensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(DOSensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }
    
    DOSensor sensor = new DOSensor();
    sensor.setSensorConfig(READ_COMMAND, READ_QUERY_TIME_DELAY, RETRY_TIMEOUT, N_RETRIES, N_BYTES);
    sensor.parseCommandLineOptions(cl);
    sensor.connect();
    sensor.run();
  }
}
