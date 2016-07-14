package com.twentyn.bioreactor.pH;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TempSensor extends Sensor {

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
  private static final String DEFAULT_ADDRESS = "102";
  // Device name
  private static final String DEFAULT_SENSOR_NAME = "temp_sensor_0";
  // Reading and log file default locations
  private static final String DEFAULT_SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/temp/reading.json";
  private static final String DEFAULT_SENSOR_READING_LOG_FILE_LOCATION = "/tmp/sensors/v1/temp/reading_log.json";

  // READ command for sensor
  private static final byte READ_COMMAND = (byte) 0x52; // R in hex
  // Number of bytes to read from the response
  // DO sensor: the response format is [1,{DO},null] where DO is encoded over 5 bytes
  //            hence, total number of bytes to read is 7 (response should never be more according to datasheet)
  //            http://www.atlas-scientific.com/_files/_datasheets/_circuit/pH_EZO_datasheet.pdf
  private static final int N_BYTES = 9;
  // Time delay to read response from the chip
  // According to the datasheet, 600ms. Adding 400ms for safety.
  private static final int READ_QUERY_TIMEOUT = 1000;
  private static final int RETRY_TIMEOUT = 300;
  private static final int N_RETRIES = 3;

  public void parseCommandLineOptions(CommandLine cl) {
    deviceAddress = Integer.parseInt(cl.getOptionValue(OPTION_SENSOR_ADDRESS, DEFAULT_ADDRESS));
    deviceName = cl.getOptionValue(OPTION_SENSOR_NAME, DEFAULT_SENSOR_NAME);
    sensorReadingLogFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_LOG_FILE_LOCATION,
        DEFAULT_SENSOR_READING_LOG_FILE_LOCATION);
    sensorReadingFileLocation = cl.getOptionValue(OPTION_SENSOR_READING_FILE_LOCATION,
        DEFAULT_SENSOR_READING_FILE_LOCATION);
  }

  // TODO: add initialization step to set the unit to be Celsius. Yes. Celsius :)
  public Double parseSensorValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    return Double.parseDouble(response);
  }

  public void run() {
    try {
      JsonGenerator g = objectMapper.getFactory().createGenerator(
          new File(sensorReadingLogFileLocation), JsonEncoding.UTF8);
      File sensorReading = new File(sensorReadingFileLocation);

      while(true) {
        byte[] sensorResponse = readSensorResponse();
        Double tempValueFromResponse = parseSensorValueFromResponse(sensorResponse);
        DateTime currTime = new DateTime();
        TempSensorData tempSensorData = new TempSensorData(tempValueFromResponse, deviceName, currTime);
        try {
          // Writing single value for control module to use
          objectMapper.writeValue(sensorReading, tempSensorData);
          // Appending value to log file
          objectMapper.writeValue(g, tempSensorData);
        } catch (IOException e) {
          super.LOGGER.error("Exception when trying to write dOSensorData: %s", e);
        }
      }
    } catch (IOException e) {
      super.LOGGER.error("Exception when trying to log dOSensorData: %s", e);
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
      HELP_FORMATTER.printHelp(TempSensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(TempSensor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }
    
    TempSensor sensor = new TempSensor();
    sensor.setSensorConfig(READ_COMMAND, READ_QUERY_TIMEOUT, RETRY_TIMEOUT, N_RETRIES, N_BYTES);
    sensor.parseCommandLineOptions(cl);
    sensor.connect();
    sensor.run();
  }
}
