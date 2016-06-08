package com.act.biointerpretation.l2expansion;

import com.act.biointerpretation.analytics.ReactionCountProvenance;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Runs L2 Expansion
 */
public class L2ExpansionDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  private static final String OPTION_METABOLITES = "m";
  private static final String OPTION_ROS = "r";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE = StringUtils.join(
          "This class is used to apply every RO from an input list to every metabolite in another input list. ",
          "It creates a list of predicted reactions, containing the substrates, reactor, and products for each " +
                  "(RO, metabolite) pair for which a reaction is predicted to occur. ",
          "This list is written to a file in json format."
          );

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_METABOLITES)
            .argName("metabolites file name")
            .desc("The name of the metabolites file.  File should be in " +
                    "resources.com.act.biointerpretation.l2expansion and contain one inchi per line.")
            .hasArg()
            .longOpt("metabolite_file")
    );
    add(Option.builder(OPTION_ROS)
            .argName("Ros file name")
            .desc("The name of the ros file.  File should be in " +
                    "resources.com.act.biointerpretation.mechanisminspection and contain one integer RO ID per line.")
            .hasArgs()
            .longOpt("ro_file")
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
            .argName("file path for output")
            .desc("A path to which to write the predicted reactions.")
            .hasArgs()
            .longOpt("output_path")
    );
    add(Option.builder("h")
            .argName("help")
            .desc("Prints this help message")
            .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  public static void main(String[] args) throws Exception {

    // Build command line parser
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
      HELP_FORMATTER.printHelp(ReactionCountProvenance.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(ReactionCountProvenance.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Set metabolites file
    String METABOLITES_FILE = "PABA_metabolites.txt";
    if (!cl.hasOption(OPTION_METABOLITES)) {
      LOGGER.warn("No metabolites file given; using PABA_metabolites.txt as default.\n" +
              "Use -m metabolites_file to specify a different file.");
    }
    else{
      METABOLITES_FILE = cl.getOptionValue(OPTION_METABOLITES);
    }

    // Set ros file
    String ROS_FILE = "PABA_ros.txt";
    if (!cl.hasOption(OPTION_ROS)) {
      LOGGER.warn("No ROs file given; using PABA_ros.txt as default.\n" +
              "Use -r ros_file to specify a different file.");
    }
    else{
      ROS_FILE = cl.getOptionValue(OPTION_ROS);
    }

    // Set output file
    String OUTPUT_FILE_PATH = "";
    if (!cl.hasOption(OPTION_OUTPUT_PATH)) {
      LOGGER.error("No output path given; usinInput -r ro_file");
      return;
    }
    else{
      OUTPUT_FILE_PATH = cl.getOptionValue(OPTION_OUTPUT_PATH);
    }

    // Initialize input corpuses and expander
    LOGGER.info("Getting metabolite list from ", METABOLITES_FILE);
    L2MetaboliteCorpus metaboliteCorpus = new L2MetaboliteCorpus(METABOLITES_FILE);
    metaboliteCorpus.buildCorpus();
    List<String> metaboliteList = metaboliteCorpus.getMetaboliteList();
    LOGGER.info("Metabolite list contains %d metabolites", metaboliteList.size());

    LOGGER.info("Getting ro list from ", ROS_FILE);
    ErosCorpus eroCorpus = new ErosCorpus();
    eroCorpus.loadCorpus();
    List<Ero> roList = eroCorpus.getRoListFromFile(ROS_FILE);
    LOGGER.info("Ro list contains %d ros",roList.size());

    L2Expander expander = new L2Expander(roList, metaboliteList);

    // Carry out L2 expansion
    LOGGER.info("Beginning L2 expansion.");
    L2PredictionCorpus predictionCorpus = expander.getPredictionCorpus();
    LOGGER.info("Done with L2 expansion.  Produced %d predictions.", predictionCorpus.getCorpus().size());

    // Print prediction corpus as json file
    LOGGER.info("Printing corpus to file ", OUTPUT_FILE_PATH);
    predictionCorpus.writePredictionsToJsonFile(OUTPUT_FILE_PATH);
  }
}
