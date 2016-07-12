package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.*;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.joda.time.DateTime;

public class ControlSystem {

  private static final Integer TOTAL_DURATION_OF_RUN_IN_MILLISECONDS = 60000;
  private static final String SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private static final Logger LOGGER = LogManager.getFormatterLogger(ControlSystem.class);
  private static final Double MARGIN_OF_ACCEPTANCE_IN_PH = 0.5;

  public static final String OPTION_TARGET_PH = "p";
  public static final String OPTION_CONTROL_SOLUTION = "c";
  public static final String HELP_MESSAGE = "This class runs the control system of one fermentation run";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_TARGET_PH)
        .argName("target ph")
        .desc("The target pH of the system")
        .hasArg().required()
        .longOpt("target-ph")
    );
    add(Option.builder(OPTION_CONTROL_SOLUTION)
        .argName("control solution")
        .desc("The solution from which we can control the pH")
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

  private GpioController gpioController;
  private GpioPinDigitalOutput pumpReversePin;
  private GpioPinDigitalOutput pumpForwardPin;
  private GpioPinDigitalOutput pumpEnablePin;
  private SOLUTION solution;
  private Double targetPH;
  private ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public ControlSystem(SOLUTION solution, Double targetPH) {
    OBJECT_MAPPER.registerModule(new JodaModule());
    initializeGPIOPins();
    this.solution = solution;
    this.targetPH = targetPH;
  }

  private void initializeGPIOPins() {
    gpioController = GpioFactory.getInstance();
    pumpReversePin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_00, "ReverseMode", PinState.LOW);
    pumpForwardPin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_01, "ForwardMode", PinState.LOW);
    pumpEnablePin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_07, "PumpEnable", PinState.LOW);

    // set shutdown state for pins
    pumpReversePin.setShutdownOptions(true, PinState.LOW);
    pumpForwardPin.setShutdownOptions(true, PinState.LOW);
    pumpEnablePin.setShutdownOptions(true, PinState.LOW);

    // Set mode to be forward
    pumpForwardPin.high();
  }

  private Double readPHValue() throws IOException {
    File file = new File(SENSOR_READING_FILE_LOCATION);
    PHSensorData sensorData = OBJECT_MAPPER.readValue(file, PHSensorData.class);
    return sensorData.getpH();
  }

  private void takeAction() throws InterruptedException {
    System.out.println(String.format("Pump more solution"));
    LOGGER.info("Pump more solution");
    pumpEnablePin.high();
    Thread.sleep(1000);

    System.out.println(String.format("Stop pumping"));
    LOGGER.info("Stop pumping");
    pumpEnablePin.low();
  }

  private void run() {
    DateTime startTime = new DateTime();
    DateTime currTime = new DateTime();

    while (currTime.getMillis() - startTime.getMillis() < TOTAL_DURATION_OF_RUN_IN_MILLISECONDS) {
      try {
        Double phValue = readPHValue();
        LOGGER.info("PH value is %d" + phValue);
        System.out.println(String.format("PH value is " + phValue.toString()));

        if (phValue < this.targetPH - MARGIN_OF_ACCEPTANCE_IN_PH &&
            this.solution.equals(SOLUTION.BASE)) {
          takeAction();
        }

        if (phValue > this.targetPH + MARGIN_OF_ACCEPTANCE_IN_PH &&
            this.solution.equals(SOLUTION.ACID)) {
          takeAction();
        }

        Thread.sleep(1000);

      } catch (IOException e) {
        LOGGER.error("Could not read pH value due to IOException. Error is %s:", e.getMessage());
      } catch (InterruptedException e) {
        LOGGER.error("Could not read pH value due to InterruptedException. Error is %s:", e.getMessage());
      }

      currTime = new DateTime();
    }

    gpioController.shutdown();
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

    ControlSystem controlSystem = new ControlSystem(solution, targetPH);
    controlSystem.run();
  }
}
