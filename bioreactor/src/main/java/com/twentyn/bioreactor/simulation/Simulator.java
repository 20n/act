package com.twentyn.bioreactor.simulation;

import com.twentyn.bioreactor.pH.ControlSystem;
import com.twentyn.bioreactor.sensors.PHSensorData;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Simulator extends ControlSystem {

  public static final String OPTION_TARGET_PH = "p";
  public static final String OPTION_CONTROL_SOLUTION = "c";
  public static final String HELP_MESSAGE = "This class simulates the control system.";

  private static final Logger LOGGER = LogManager.getFormatterLogger(Simulator.class);

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

  // TODO: implement a speedup process

  private static final Double INIT_PH_DATA = 1.0;
  private static final Double INIT_VOLUME = 10000.0;
  private static final Integer SENSOR_READ_TIME = 1500;

  private static final Double FLOW_RATE = 1.0; // in mL/s
  private static final Double ACID_PH = 3.0;
  private static final Double BASE_PH = 11.0;
  private static final String DEVICE_NAME = "virtual_device";


  private PHSensorData currentSensorData;
  private Double bioreactorVolume; // in mL
  private Action lastAction;

  private class Action {
    private SOLUTION solution;
    private Integer duration;
    private DateTime timestamp;

    public Action(SOLUTION solution, Integer duration, DateTime timestamp) {
      this.solution = solution;
      this.duration = duration;
      this.timestamp = timestamp;
    }

    public Integer getDuration() {
      return duration;
    }

    public SOLUTION getSolution() {
      return solution;
    }
  }

  public Simulator(SOLUTION solution, Double targetPH) {
    super.solution = solution;
    super.targetPH = targetPH;
    bioreactorVolume = INIT_VOLUME;
    currentSensorData = new PHSensorData(INIT_PH_DATA, DEVICE_NAME, new DateTime());
  }

  @Override
  protected void takeAction() {
    lastAction = new Action(solution, PUMP_TIME_WAIT_IN_MILLI_SECONDS, new DateTime());
    LOGGER.debug("Last action was updated to: {solution: %s, duration: %d}", solution, PUMP_TIME_WAIT_IN_MILLI_SECONDS);
  }

  private void updateSystemStateWithLastAction() {
    if (lastAction == null) {
      return;
    }
    Double addedVolume = FLOW_RATE * lastAction.getDuration();
    Double previousVolume = bioreactorVolume;
    bioreactorVolume += addedVolume;
    Double controlSolutionPH = (lastAction.getSolution().equals(SOLUTION.ACID)) ? ACID_PH : BASE_PH;
    Double previousPH = currentSensorData.getpH();
    Double bioreactorPH = (previousVolume * previousPH + addedVolume * controlSolutionPH) /
        bioreactorVolume;
    LOGGER.debug("#######################");
    LOGGER.debug("%.2f of %s were added to the bioreactor.", addedVolume, lastAction.getSolution());
    LOGGER.debug("Initial state was {volume: %.2f, pH: %.2f.", previousVolume, previousPH);
    LOGGER.debug("Final state is {volume: %.2f, pH: %.2f}", bioreactorVolume, bioreactorPH);
    currentSensorData = new PHSensorData(bioreactorPH, DEVICE_NAME, new DateTime());
    lastAction = null;
  }

  @Override
  protected PHSensorData readPhSensorData(File f) {
    try {
      Thread.sleep(SENSOR_READ_TIME);
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted exception was caught while reading sensor data with the following error message: ", e);
    }
    updateSystemStateWithLastAction();
    return currentSensorData;
  }

  @Override
  protected void shutdownFermentation() {}

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
      HELP_FORMATTER.printHelp(Simulator.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(Simulator.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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

    Simulator simulator = new Simulator(solution, targetPH);
    try {
      simulator.run();
    } finally {
      System.out.println("Shutting down");
      simulator.shutdownFermentation();
    }
  }
}

