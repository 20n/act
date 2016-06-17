package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs L2 Expansion
 */
public class L2ExpansionDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionDriver.class);

  private static final String OUTPUT_FILE_NAME_PREFIX = "predictions";
  private static final String UNFILTERED_SUFFIX = ".raw";
  private static final String CHEMICALS_SUFFIX = ".product_filtered";
  private static final String REACTIONS_SUFFIX = ".reaction_filtered";

  private static final String OPTION_METABOLITES = "m";
  private static final String OPTION_ROS = "r";
  private static final String OPTION_OUTPUT_PREFIX = "o";
  private static final String OPTION_DB = "db";
  private static final String OPTION_HELP = "h";
  private static final String OPTION_ALL_ROS = "A";
  private static final String OPTION_ADDITIONAL_CHEMICALS = "p";

  public static final String HELP_MESSAGE =
          "This class is used to carry out L2 expansion. It first applies every RO from the input RO list to " +
                  "every metabolite in the input metabolite list.  Example input lists can be found on the NAS at " +
                  "shared-data/Gil/resources. This creates a list of predicted reactions, which are vetted " +
                  "based on whether their substrates and products are in the chemicals database. Finally, " +
                  "the remaining predictions are tested against the reaction database, and any reaction which " +
                  "matches all substrates and products of a prediction is noted in that prediction. The raw " +
                  "predictions, chemical-filtered predictions, and reaction-filtered predictions are each written " +
                  "to a file in json format.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_METABOLITES)
            .argName("metabolites path name")
            .desc("The absolute path to the metabolites file.")
            .hasArg()
            .longOpt("metabolite-file")
            .required(true)
    );
    add(Option.builder(OPTION_ADDITIONAL_CHEMICALS)
        .argName("additional chemicals path name")
        .desc("The absolute path to the additional chemicals file.")
        .hasArg()
        .longOpt("additional-chemicals-file")
    );
    add(Option.builder(OPTION_ROS)
            .argName("ros path name")
            .desc("The absolute path to the ros file.")
            .hasArg()
            .longOpt("ro-file")
    );
    add(Option.builder(OPTION_ALL_ROS)
            .argName("all ros flag")
            .desc("If this option is chosen, the expansion will be run on every RO in eros.json. " +
                    "This overrides any file provided with -r.")
            .longOpt("all-ros")
    );
    add(Option.builder(OPTION_OUTPUT_PREFIX)
            .argName("output file directory")
            .desc("The path to the directory in which to write the json files of predicted reactions.")
            .hasArg()
            .longOpt("output-dir")
            .required(true)
    );
    add(Option.builder(OPTION_DB)
            .argName("db name")
            .desc("The name of the mongo DB to use.")
            .hasArg()
            .longOpt("db-name")
            .required(true)
    );
    add(Option.builder(OPTION_HELP)
            .argName("help")
            .desc("Prints this help message.")
            .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final String DB_NAME = "marvin";

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
      LOGGER.error("Argument parsing failed: %s", e.getMessage());
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    List<String> additionalChemicals = new ArrayList<>();
    // Get additional chemicals file
    if (cl.hasOption(OPTION_ADDITIONAL_CHEMICALS)) {
      File additionalChemicalsFile = new File(cl.getOptionValue(OPTION_ADDITIONAL_CHEMICALS));
      BufferedReader br = new BufferedReader(new FileReader(additionalChemicalsFile));

      String line = null;
      while ((line = br.readLine()) != null) {
        additionalChemicals.add(line);
      }

      br.close();
    }

    // Get input files
    File metabolitesFile = new File(cl.getOptionValue(OPTION_METABOLITES));

    // Get output files
    String outputDirectory = cl.getOptionValue(OPTION_OUTPUT_PREFIX);
    File dirFile = new File(outputDirectory);
    if (dirFile.exists() && !dirFile.isDirectory()) {
      LOGGER.info("Specified output directory is a non-directory file.");
      return;
    }
    dirFile.mkdir();

    File unfilteredFile = new File(outputDirectory, OUTPUT_FILE_NAME_PREFIX + UNFILTERED_SUFFIX);
    File chemicalsFilteredFile = new File(outputDirectory, OUTPUT_FILE_NAME_PREFIX + CHEMICALS_SUFFIX);
    File reactionsFilteredFile = new File(outputDirectory, OUTPUT_FILE_NAME_PREFIX + REACTIONS_SUFFIX);

    // Start up mongo instance
    MongoDB mongoDB = new MongoDB("localhost", 27017, cl.getOptionValue(OPTION_DB));

    // Build metabolite list
    LOGGER.info("Getting metabolite list from %s", metabolitesFile);
    L2MetaboliteCorpus metaboliteCorpus = new L2MetaboliteCorpus();
    metaboliteCorpus.loadCorpus(metabolitesFile);
    List<String> metaboliteList = metaboliteCorpus.getMetaboliteList();
    LOGGER.info("Metabolite list contains %d metabolites", metaboliteList.size());

    //Remove metabolites that are not in reaction DB
    int initialSize = metaboliteList.size();
    metaboliteList.removeIf(inchi -> mongoDB.getChemicalFromInChI(inchi) == null);
    LOGGER.info("Removed %d metabolites not in DB.", initialSize - metaboliteList.size());

    // Build ro list
    List<Ero> roList;
    ErosCorpus eroCorpus = new ErosCorpus();
    eroCorpus.loadCorpus();
    if (!cl.hasOption(OPTION_ALL_ROS)) {
      File rosFile = new File(cl.getOptionValue(OPTION_ROS));
      LOGGER.info("Getting ro list from %s", rosFile);
      roList = eroCorpus.getRoListFromFile(rosFile);
    } else {
      LOGGER.info("Getting all ROs.");
      roList = eroCorpus.getRos();
    }
    LOGGER.info("Ro list contains %d ros", roList.size());

    // Build L2Expander
    L2Expander expander = new L2Expander(roList, metaboliteList);
    // Carry out L2 expansion
    LOGGER.info("Beginning L2 expansion.");
    L2PredictionCorpus predictionCorpus = expander.getMultipleSubstratePredictionCorpus(additionalChemicals, 2, mongoDB);
    LOGGER.info("Done with L2 expansion. Produced %d predictions.", predictionCorpus.getCorpus().size());
    predictionCorpus.writePredictionsToJsonFile(unfilteredFile);

    // Test chemicals in DB
    LOGGER.info("Filtering by  chemicals in DB.");
    predictionCorpus.applyFilter(new ChemicalsFilter(mongoDB));
    LOGGER.info("Filtered by chemicals in DB. %d predictions remain.", predictionCorpus.getCorpus().size());
    predictionCorpus.writePredictionsToJsonFile(chemicalsFilteredFile);

    // Test against reactions DB
    LOGGER.info("Filtering by reactions in DB.");
    predictionCorpus.applyFilter(new ReactionsFilter(mongoDB));
    LOGGER.info("Filtered by reactions in DB. %d predictions remain.", predictionCorpus.getCorpus().size());
    predictionCorpus.writePredictionsToJsonFile(reactionsFilteredFile);

    LOGGER.info("L2ExpansionDriver complete!");
  }
}
