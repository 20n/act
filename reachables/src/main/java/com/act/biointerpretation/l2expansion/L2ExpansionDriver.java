package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.act.biointerpretation.sars.SarCorpus;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs L2 Expansion
 */
public class L2ExpansionDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionDriver.class);

  private static final String OPTION_METABOLITES = "m";
  private static final String OPTION_RO_CORPUS = "c";
  private static final String OPTION_RO_IDS = "r";
  private static final String OPTION_SAR_CORPUS = "s";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_DB = "db";
  private static final String OPTION_EXPANSION_TYPE = "t";
  private static final String OPTION_ADDITIONAL_CHEMICALS = "p";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class is used to carry out L2 expansion. It first applies every RO from the input RO list to " +
          "every metabolite in the input metabolite list.  Example input lists can be found on the NAS at " +
          "shared-data/Gil/resources. This creates a list of predicted reactions, which are augmented " +
          "with chemical ids and names, as well as reaction ids from the database. At the end of the run, " +
          "the predictions are printed to a json file.";

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
        .desc("The path to the file containing the eros corpus, if not the validation corpus. Ignored if " +
            "running a SAR expansion.")
        .hasArg()
        .longOpt("ro-corpus")
    );
    add(Option.builder(OPTION_RO_IDS)
        .argName("ro ids path name")
        .desc("The path to a file containing the RO ids to use. If this option is omitted, " +
            "all ROs in the corpus are used. Ignored if running a SAR expansion.")
        .hasArg()
        .longOpt("ro-file")
    );
    add(Option.builder(OPTION_SAR_CORPUS)
        .argName("sar corpus")
        .desc("The path to a file containing the sar corpus to use. Ignored if running an RO-only expansion.")
        .hasArg()
        .longOpt("sar-corpus")
    );
    add(Option.builder(OPTION_RO_IDS)
        .argName("ro ids")
        .desc("The absolute path to the file containing the RO ids to use. If this option is omitted, " +
            "all ROs in the corpus are used.")
        .hasArg()
        .longOpt("ro-ids")
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
    add(Option.builder(OPTION_EXPANSION_TYPE)
        .argName("type of expansion")
        .desc("Type can take values: {ONE_SUB, TWO_SUB, SAR}.  ONE_SUB and TWO_SUB operate with only ROs, on one " +
            "and two substrates, respectively, using only ROs. SAR runs an expansion from a SarCorpus, which " +
            "still applies ROs but additionally constrains the substrates of each RO based on the supplied SARs.")
        .hasArg()
        .longOpt("expansion-type")
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

  public static final String LOCAL_HOST = "localhost";
  public static final Integer PORT_NUMBER = 27017;

  public enum ExpansionType {
    ONE_SUB,
    TWO_SUB,
    SAR,
  }

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

    // Get output files.
    String outputPath = cl.getOptionValue(OPTION_OUTPUT_PATH);
    File outputFile = new File(outputPath);
    if (outputFile.isDirectory() || outputFile.exists()) {
      LOGGER.error("Supplied output file is a directory or already exists.");
      System.exit(1);
    }
    outputFile.createNewFile();

    // Get metabolite list
    List<String> metaboliteList = getInchiList(cl, OPTION_METABOLITES);

    PredictionGenerator generator = new AllPredictionsGenerator(new ReactionProjector());
    L2Expander expander = buildExpander(cl, metaboliteList, generator);
    L2PredictionCorpus predictionCorpus = expander.getPredictions();

    LOGGER.info("Done with L2 expansion. Produced %d predictions.", predictionCorpus.getCorpus().size());

    LOGGER.info("Writing corpus to file.");
    predictionCorpus.writePredictionsToJsonFile(outputFile);

    LOGGER.info("L2ExpansionDriver complete!");
  }

  private static L2Expander buildExpander(CommandLine cl,
                                          List<String> metaboliteList,
                                          PredictionGenerator generator) throws IOException {

    ExpansionType expansionType = ExpansionType.valueOf(cl.getOptionValue(OPTION_EXPANSION_TYPE));

    switch (expansionType) {
      case ONE_SUB:
        LOGGER.info("Running one substrate expansion");
        return new SingleSubstrateRoExpander(getRoList(cl), metaboliteList, generator);

      case TWO_SUB:
        LOGGER.info("Running two substrate expansion.");
        LOGGER.warn("This functionality is still experimental as it is not currently tested.");
        if (!cl.hasOption(OPTION_ADDITIONAL_CHEMICALS)) {
          LOGGER.error("Must supply additional chemicals file for two substrate expansion.");
          System.exit(1);
        }
        MongoDB mongoDB = new MongoDB(LOCAL_HOST, PORT_NUMBER, cl.getOptionValue(OPTION_DB)); // Start mongo instance.
        List<String> additionalChemicals = getInchiList(cl, OPTION_ADDITIONAL_CHEMICALS);
        List<Chemical> chemicalsOfInterest =
            L2ExpansionDriver.convertListOfInchisToMolecules(additionalChemicals, mongoDB);
        List<Chemical> metaboliteChemicals =
            L2ExpansionDriver.convertListOfInchisToMolecules(metaboliteList, mongoDB);
        return new TwoSubstrateRoExpander(chemicalsOfInterest, metaboliteChemicals, getRoList(cl), generator);

      case SAR:
        LOGGER.info("Running sar-based expansion.");
        File sarCorpusFile = new File(cl.getOptionValue(OPTION_SAR_CORPUS));
        if (!sarCorpusFile.exists() || sarCorpusFile.isDirectory()) {
          LOGGER.error("Sar corpus is not a valid file.");
          System.exit(1);
        }
        SarCorpus sarCorpus = SarCorpus.readCorpusFromJsonFile(sarCorpusFile);
        return new SingleSubstrateSarExpander(sarCorpus, metaboliteList, getRoCorpus(cl), generator);

      default:
        throw new IllegalArgumentException("Invalid expansion type.");
    }
  }

  private static List<Ero> getRoList(CommandLine cl) throws IOException {
    ErosCorpus eroCorpus = getRoCorpus(cl);

    List<Ero> roList;
    if (cl.hasOption(OPTION_RO_IDS)) {
      LOGGER.info("Getting ro list from rosFile.");
      File roIdsFile = new File(cl.getOptionValue(OPTION_RO_IDS));

      if (!roIdsFile.exists()) {
        LOGGER.error("Ro ids file does not exist.");
        System.exit(1);
      }

      List<Integer> roIdList = eroCorpus.getRoIdListFromFile(roIdsFile);
      roList = eroCorpus.getRos(roIdList);
    } else {
      LOGGER.info("Getting all ROs.");
      roList = eroCorpus.getRos();
    }
    LOGGER.info("Ro list contains %d ros", roList.size());
    return roList;
  }

  private static ErosCorpus getRoCorpus(CommandLine cl) throws IOException {
    ErosCorpus eroCorpus = new ErosCorpus();
    if (cl.hasOption(OPTION_RO_CORPUS)) {
      File roCorpusFile = new File(cl.getOptionValue(OPTION_RO_CORPUS));

      if (!roCorpusFile.exists()) {
        LOGGER.error("Ro corpus file does not exist.");
        System.exit(1);
      }
      FileInputStream roInputStream = new FileInputStream(roCorpusFile);
      eroCorpus.loadCorpus(roInputStream);
    } else {
      eroCorpus.loadValidationCorpus();
    }
    return eroCorpus;
  }


  /**
   * Gets a list of inchis for a command line option that points to a file with one inchi per line.
   *
   * @param cl Command line parser.
   * @param optionForFileName Option for a file with one inchi per line. Either the metabolite list or addition
   * chemical list.
   * @return The list of inchis contained in the file.
   * @throws IOException
   */
  private static List<String> getInchiList(CommandLine cl, String optionForFileName) throws IOException {
    File inchisFile = new File(cl.getOptionValue(optionForFileName));
    LOGGER.info("Getting inchi list from %s", inchisFile);
    L2InchiCorpus inchiCorpus = new L2InchiCorpus();
    inchiCorpus.loadCorpus(inchisFile);

    List<String> inchiList = inchiCorpus.getInchiList();
    LOGGER.info("Inchi list contains %d inchis", inchiList.size());
    return inchiList;
  }

  /**
   * This function constructs a mapping between inchi and its chemical representation.
   *
   * @param inchis A list of inchis
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
}
