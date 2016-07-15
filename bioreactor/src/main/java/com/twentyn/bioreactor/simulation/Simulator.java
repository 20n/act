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

<<<<<<< d1297359e7afa1e40e7cdba43169fbcd7e07a803
import java.io.File;
=======
import java.util.ArrayList;
import java.util.List;
>>>>>>> Added basic simulator and a couple control tiny changes

public class Simulator extends ControlSystem {

  public static final String OPTION_TARGET_PH = "p";
  public static final String OPTION_CONTROL_SOLUTION = "c";
  public static final String HELP_MESSAGE = "This class runs the control system of one fermentation run";

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

  private static final Double FLOW_RATE = 1.0; // in mL/s
  private static final Double ACID_PH = 3.0;
  private static final Double BASE_PH = 11.0;
  private static final String DEVICE_NAME = "virtual_device";
  private static final Double INIT_PH_DATA = 1.0;

  private PHSensorData currentSensorData;
  private Action lastAction;
  private Integer volume; // in mL

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
    currentSensorData = new PHSensorData(INIT_PH_DATA, DEVICE_NAME, new DateTime());
  }

  private void updateSensorDataWithAction(Action action) {
    Double solutionPH = (action.getSolution().equals(SOLUTION.ACID)) ? ACID_PH : BASE_PH;
    Double addedVolume = FLOW_RATE * action.getDuration();
    Double totalVolume = addedVolume + volume;
    Double bioreactorPH = (volume * currentSensorData.getpH() + addedVolume * solutionPH) / totalVolume;
    currentSensorData = new PHSensorData(bioreactorPH, DEVICE_NAME, new DateTime());
  }

  @Override
  protected void takeAction() {
<<<<<<< 3724a08ad5c39bcfd805fcfd8dddc7b89f29414d
    lastAction = new Action(solution, PUMP_TIME_WAIT_IN_MILLI_SECONDS, new DateTime());
  }

<<<<<<< d1297359e7afa1e40e7cdba43169fbcd7e07a803
  @Override
  protected PHSensorData readPhSensorData(File f) {
=======
  private PHSensorData readSensorData() {
>>>>>>> Added basic simulator and a couple control tiny changes
=======
    lastAction = new Action(solution, PUMP_ACTION_DURATION, new DateTime());
  }

  @Override
  protected PHSensorData readSensorData() {
>>>>>>> Fixing over-ridding of methods
    updateSensorDataWithAction(lastAction);
    return currentSensorData;
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

