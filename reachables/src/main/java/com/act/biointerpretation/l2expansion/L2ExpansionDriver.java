package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import com.act.biointerpretation.Utils.ReactionProjector;
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
import java.util.function.Predicate;

/**
 * Runs L2 Expansion
 */
public class L2ExpansionDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionDriver.class);

  private static final Integer ONE_SUBSTRATE = 1;
  private static final Integer TWO_SUBSTRATE = 2;

  private static final String OPTION_METABOLITES = "m";
  private static final String OPTION_RO_CORPUS = "c";
  private static final String OPTION_RO_IDS = "r";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_DB = "db";
  private static final String OPTION_NUM_SUBSTRATES = "s";
  private static final String OPTION_ADDITIONAL_CHEMICALS = "p";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class is used to carry out L2 expansion. It first applies every RO from the input RO list to " +
          "every metabolite in the input metabolite list.  Example input lists can be found on the NAS at " +
          "shared-data/Gil/resources. This creates a list of predicted reactions, which are augmented " +
          "with chemical ids and names, as well as reaction ids from the database. The " +
          "predictions are then printed to a json file. Two auxiliary json files are also printed, " +
          "the first of which contains only those predictions whose chemicals all matched the DB, and " +
          "the second of which contains only those which did not match any existing reaction in the DB.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_METABOLITES)
        .argName("metabolites path name")
        .desc("The absolute path to the metabolites file.")
        .hasArg()
        .longOpt("metabolite-file")
        .required(true)
    );
    add(Option.builder(OPTION_RO_CORPUS)
        .argName("ro corpus")
        .desc("The path to the file containing the eros corpus, if not the validation corpus.")
        .hasArg()
        .longOpt("ro-corpus")
    );
    add(Option.builder(OPTION_RO_IDS)
        .argName("ro ids path name")
        .desc("The path to a file containing the RO ids to use. If this option is omitted, " +
            "all ROs in the corpus are used.")
        .hasArg()
        .longOpt("ro-file")
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("output file path")
        .desc("The path to the file to which to write the json file of predicted reactions.")
        .hasArg()
        .longOpt("output-file-path")
        .required(true)
    );
    add(Option.builder(OPTION_DB)
        .argName("db name")
        .desc("The name of the mongo DB to use.")
        .hasArg()
        .longOpt("db-name")
        .required(true)
    );
    add(Option.builder(OPTION_NUM_SUBSTRATES)
        .argName("number of substrates")
        .desc("The number of substrates to use for the reaction.")
        .hasArg()
        .longOpt("num-substrates")
        .required(true)
    );
    add(Option.builder(OPTION_ADDITIONAL_CHEMICALS)
        .argName("additional chemicals path name")
        .desc("The absolute path to the additional chemicals file.")
        .hasArg()
        .longOpt("additional-chemicals-file")
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

  /**
   * This function constructs a mapping between inchi and it's chemical representation.
   *
   * @param inchis  A list of inchis
   * @param mongoDB The db from which to get the chemical entry
   * @return A map of inchi to chemical
   */
  private static List<Chemical> convertListOfInchisToMolecules(List<String> inchis, MongoDB mongoDB) {
    List<Chemical> result = new ArrayList<>();
    for (String inchi : inchis) {
      result.add(mongoDB.getChemicalFromInChI(inchi));
    }
    return result;
  }


  private static final Predicate<L2Prediction> ALL_CHEMICALS_IN_DB = prediction ->
      prediction.getProductIds().size() == prediction.getProducts().size() &&
          prediction.getSubstrateIds().size() == prediction.getSubstrates().size();

  private static final Predicate<L2Prediction> NO_REACTIONS_IN_DB = prediction -> prediction.getReactionCount() == 0;

  public static void main(String[] args) throws Exception {

    // Build command line parser.
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

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Build ro list.
    ErosCorpus eroCorpus = new ErosCorpus();
    if (cl.hasOption(OPTION_RO_CORPUS)) {
      File roCorpusFile = new File(cl.getOptionValue(OPTION_RO_CORPUS));

      if (!roCorpusFile.exists()) {
        LOGGER.error("Ro corpus file does not exist.");
        return;
      }

      eroCorpus.loadCorpus(roCorpusFile);
    } else {
      eroCorpus.loadValidationCorpus();
    }
    List<Ero> roList;
    if (cl.hasOption(OPTION_RO_IDS)) {
      LOGGER.info("Getting ro list from rosFile.");
      File roIdsFile = new File(cl.getOptionValue(OPTION_RO_IDS));

      if (!roIdsFile.exists()) {
        LOGGER.error("Ro ids file does not exist.");
        return;
      }

      List<Integer> roIdList = eroCorpus.getRoIdListFromFile(roIdsFile);
      roList = eroCorpus.getRos(roIdList);
    } else {
      LOGGER.info("Getting all ROs.");
      roList = eroCorpus.getRos();
    }
    LOGGER.info("Ro list contains %d ros", roList.size());

    // Build metabolite list.
    File metabolitesFile = new File(cl.getOptionValue(OPTION_METABOLITES));
    LOGGER.info("Getting metabolite list from %s", metabolitesFile);
    L2MetaboliteCorpus metaboliteCorpus = new L2MetaboliteCorpus();
    metaboliteCorpus.loadCorpus(metabolitesFile);
    List<String> metaboliteList = metaboliteCorpus.getMetaboliteList();
    LOGGER.info("Metabolite list contains %d metabolites", metaboliteList.size());

    List<String> additionalChemicals = new ArrayList<>();
    // Get additional chemicals file
    if (cl.hasOption(OPTION_ADDITIONAL_CHEMICALS)) {
      File additionalChemicalsFile = new File(cl.getOptionValue(OPTION_ADDITIONAL_CHEMICALS));
      if (!additionalChemicalsFile.exists()) {
        LOGGER.error("The additional chemicals file does not exist.");
        System.exit(1);
      }

      BufferedReader br = new BufferedReader(new FileReader(additionalChemicalsFile));
      String line = null;
      while ((line = br.readLine()) != null) {
        additionalChemicals.add(line);
      }

      br.close();
    }

    // Get output files.
    String outputPath = cl.getOptionValue(OPTION_OUTPUT_PATH);
    File outputFile = new File(outputPath);

    // Start up mongo instance.
    MongoDB mongoDB = new MongoDB("localhost", 27017, cl.getOptionValue(OPTION_DB));

    //Remove metabolites that are not in reaction DB.
    int initialSize = metaboliteList.size();
    metaboliteList.removeIf(inchi -> mongoDB.getChemicalFromInChI(inchi) == null);
    LOGGER.info("Removed %d metabolites not in DB.", initialSize - metaboliteList.size());

    // Build L2Expander.
    L2Expander expander = new L2Expander(roList, metaboliteList, new ReactionProjector());

    LOGGER.info("Beginning L2 expansion.");

    L2PredictionCorpus predictionCorpus = null;
    if (cl.getOptionValue(OPTION_NUM_SUBSTRATES).equals(ONE_SUBSTRATE.toString())) {
      LOGGER.info("Doing one substrate expansion");
      predictionCorpus = expander.getSingleSubstratePredictionCorpus();
    } else if (cl.getOptionValue(OPTION_NUM_SUBSTRATES).equals(TWO_SUBSTRATE.toString())) {
      LOGGER.info("Doing two substrate expansion");

      List<Chemical> chemicalsOfInterest = L2ExpansionDriver.convertListOfInchisToMolecules(additionalChemicals, mongoDB);
      List<Chemical> metaboliteChemicals = L2ExpansionDriver.convertListOfInchisToMolecules(metaboliteList, mongoDB);

      predictionCorpus = expander.getTwoSubstratePredictionCorpus(chemicalsOfInterest, metaboliteChemicals);
    } else {
      LOGGER.error("We currently do not handle > 2 substrate L2 expansion");
      System.exit(1);
    }

    LOGGER.info("Done with L2 expansion. Produced %d predictions.", predictionCorpus.getCorpus().size());

    LOGGER.info("Looking up chemicals in DB.");
    predictionCorpus = predictionCorpus.applyTransformation(new ChemicalsTransformer(mongoDB));
    LOGGER.info("Looking up reactions in DB.");
    predictionCorpus = predictionCorpus.applyTransformation(new ReactionsTransformer(mongoDB));

    LOGGER.info("Writing corpus to file.");
    predictionCorpus.writePredictionsToJsonFile(outputFile);

    LOGGER.info("L2ExpansionDriver complete!");
  }
}
