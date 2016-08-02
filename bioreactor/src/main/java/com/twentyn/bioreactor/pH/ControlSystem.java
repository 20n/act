package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyn.bioreactor.util.Time;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.joda.time.DateTime;

import com.twentyn.bioreactor.sensors.PHSensorData;

public class ControlSystem {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ControlSystem.class);
  private static final String SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private static final Double MARGIN_OF_ACCEPTANCE_IN_PH = 0.5;
  private static final Integer WAIT_TIME = 20000;
  private static final Integer PUMP_TIME_WAIT_IN_MILLI_SECONDS = 1000;
  private static final Integer WAIT_TIME_BETWEEN_ACTION_IN_MILLI_SECONDS = 100;

  private static final String OPTION_TARGET_PH = "p";
  private static final String OPTION_SENSOR_READING_FILE_LOCATION = "s";
  private static final String OPTION_CONTROL_SOLUTION = "c";
  private static final String HELP_MESSAGE = "This class runs the control system of one fermentation run";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_SENSOR_READING_FILE_LOCATION)
        .argName("sensor reading file location")
        .desc("The file location of sensor reading data")
        .hasArg()
        .longOpt("sensor-reading-file-location")
    );
    add(Option.builder(OPTION_TARGET_PH)
        .argName("target ph")
        .desc("The target pH of the system")
        .hasArg().required()
        .longOpt("target-ph")
    );
    add(Option.builder(OPTION_CONTROL_SOLUTION)
        .argName("control solution")
        .desc("The solution from which we can control the pH. The control solution can have two values: ACID or BASE.")
        .hasArg().required()
        .longOpt("control-solution")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public enum SOLUTION {
    ACID,
    BASE
  }

  private MotorPinConfiguration motorPinConfiguration;
  private SOLUTION solution;
  private Double targetPH;
  private ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private File sensorDataFile;

  public ControlSystem(MotorPinConfiguration.PinNumberingScheme configurationScheme,
                       SOLUTION solution,
                       Double targetPH,
                       File sensorDataFile) {
    OBJECT_MAPPER.registerModule(new JodaModule());
    this.motorPinConfiguration = new MotorPinConfiguration(configurationScheme);
    this.motorPinConfiguration.initializeGPIOPinsAndSetConfigToStartState();
    this.solution = solution;
    this.targetPH = targetPH;
    this.sensorDataFile = sensorDataFile;
  }

  // TODO: Move this functionality to the sensor module in the future since the control system is not responsible
  // for where the data is in a file or not.
  private PHSensorData readSensorData(File sensorDataFile) throws IOException {
    PHSensorData sensorData = OBJECT_MAPPER.readValue(sensorDataFile, PHSensorData.class);
    return sensorData;
  }

  private void takeAction() throws InterruptedException {
    LOGGER.info("Pump more solution");
    this.motorPinConfiguration.getPumpEnablePin().high();
    Thread.sleep(PUMP_TIME_WAIT_IN_MILLI_SECONDS);
    LOGGER.info("Stop pumping");
    this.motorPinConfiguration.getPumpEnablePin().low();
  }

  private Long timeDifference(DateTime longerTime, DateTime shorterTime) {
    return longerTime.getMillis() - shorterTime.getMillis();
  }

  private void shutdownFermentation() {
    this.motorPinConfiguration.shutdownFermentation();
  }

  private Boolean pHOutOfRange(Double phValue) {
    return (phValue < this.targetPH - MARGIN_OF_ACCEPTANCE_IN_PH && this.solution.equals(SOLUTION.BASE)) ||
        (phValue > this.targetPH + MARGIN_OF_ACCEPTANCE_IN_PH && this.solution.equals(SOLUTION.ACID));
  }

  private void run() throws InterruptedException {
    DateTime lastTimeSinceDoseAdministered = Time.now();
    DateTime currTime;

    while (true) {
      try {
        currTime = Time.now();
        Long timeDiff = timeDifference(currTime, lastTimeSinceDoseAdministered);

        PHSensorData phSensorData = readSensorData(this.sensorDataFile);
        Double phValue = phSensorData.getpH();
        LOGGER.info("PH value is %d", phValue);

        if (timeDiff <= WAIT_TIME || !pHOutOfRange(phValue)) {
          continue;
        }

        takeAction();
        LOGGER.info("Took action when pH was %d", phValue);
        lastTimeSinceDoseAdministered = new DateTime();

      } catch (IOException e) {
        LOGGER.error("Could not read pH value due to IOException. Error is %s:", e.getMessage());
      } catch (InterruptedException e) {
        LOGGER.error("Could not read pH value due to InterruptedException. Error is %s:", e.getMessage());
      }

      Thread.sleep(WAIT_TIME_BETWEEN_ACTION_IN_MILLI_SECONDS);
    }
  }

  public static void main(String[] args) throws Exception {

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

    SOLUTION solution = null;
    String acidOrBase = cl.getOptionValue(OPTION_CONTROL_SOLUTION);
    if (acidOrBase.equals(SOLUTION.ACID.name())) {
      solution = SOLUTION.ACID;
    }

    if (acidOrBase.equals(SOLUTION.BASE.name())) {
      solution = SOLUTION.BASE;
    }

    if (solution == null) {
      LOGGER.error("Input solution is neither %s or %s", SOLUTION.ACID.name(), SOLUTION.BASE.name());
      return;
    }

    Double targetPH = Double.parseDouble(cl.getOptionValue(OPTION_TARGET_PH));

    File sensorReadingDataFile = new File(cl.getOptionValue(OPTION_SENSOR_READING_FILE_LOCATION, SENSOR_READING_FILE_LOCATION));

    ControlSystem controlSystem =
        new ControlSystem(MotorPinConfiguration.PinNumberingScheme.BOARD, solution, targetPH, sensorReadingDataFile);
    try {
      controlSystem.run();
    } finally {
      LOGGER.info("Shutting down");
      controlSystem.shutdownFermentation();
    }
  }
}
