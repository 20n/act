package com.act.biointerpretation.sarinference;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2FilteringDriver;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
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
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class LibMcsClustering {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsClustering.class);

  private static final String OPTION_PREDICTION_CORPUS = "c";
  private static final String OPTION_POSITIVE_INCHIS = "p";
  private static final String OPTION_OUTPUT_PATH = "o";
  private static final String OPTION_CLUSTER_FIRST = "f";
  private static final String OPTION_TREE_SCORING = "t";
  private static final String OPTION_HELP = "h";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_PREDICTION_CORPUS)
          .argName("input corpus path")
          .desc("The absolute path to the input prediction corpus.")
          .hasArg()
          .longOpt("input-corpus-path")
          .required()
      );
      add(Option.builder(OPTION_POSITIVE_INCHIS)
          .argName("positive inchis file")
          .desc("The path to a file of positive inchis from LCMS analysis of the prediction corpus.")
          .hasArg()
          .longOpt("input-positive-inchis")
          .required()
      );
      add(Option.builder(OPTION_OUTPUT_PATH)
          .argName("output path")
          .desc("The path to which to write the output.")
          .hasArg()
          .longOpt("output-path")
          .required()
      );
      add(Option.builder(OPTION_CLUSTER_FIRST)
          .argName("cluster first")
          .desc("Use LibMCS to cluster substrates on full corpus before applying the LCMS results.")
          .longOpt("cluster-first")
      );
      add(Option.builder(OPTION_TREE_SCORING)
          .argName("tree scoring")
          .desc("Score SARs based on hits and misses found in subtree of SAR; don't apply SAR elsewhere. This should " +
              "only be run in conjunction with the <cluster first> option.")
          .longOpt("tree-scoring")
      );
      add(Option.builder(OPTION_HELP)
          .argName("help")
          .desc("Prints this help message.")
          .longOpt("help")
      );
    }
  };

  public static final String HELP_MESSAGE =
      "This class is used to build sars from an L2Prediction run and LCMS analysis results.  The inputs are an " +
          "L2PredictionCorpus and a file with all the product inchis that came up as positive in the LCMS analysis. " +
          "The output is a list of Sars with percentageHits scores based on how predictive they are of the " +
          "reactions in the corpus.";

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final String INCHI_IMPORT_SETTINGS = "inchi";

  // This value should stay between 0 and 1 as it indicates the minimum hit percentage for a SAR worth keeping around.
  private static final Double THRESHOLD_CONFIDENCE = 0D;  // no threshold is currently applied

  private static final Integer THRESHOLD_TREE_SIZE = 2; // any SAR that is not simply one specific substrate is allowed

  private static final Random RANDOM_GENERATOR = new Random();

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
      exitWithHelp(opts);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File inputCorpusFile = new File(cl.getOptionValue(OPTION_PREDICTION_CORPUS));
    File positiveInchisFile = new File(cl.getOptionValue(OPTION_POSITIVE_INCHIS));
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));

    L2PredictionCorpus fullCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputCorpusFile);
    LOGGER.info("Number of predictions: %d", fullCorpus.getCorpus().size());
    LOGGER.info("Number of unique substrates: %d", fullCorpus.getUniqueSubstrateInchis().size());
    Collection<String> allSubstrateInchis = fullCorpus.getUniqueSubstrateInchis();

    L2InchiCorpus positiveProducts = new L2InchiCorpus();
    positiveProducts.loadCorpus(positiveInchisFile);
    List<String> positiveProductList = positiveProducts.getInchiList();

    // This only filters based on the first product of the prediction.
    // TODO: generalize the pipeline to ROs that produce multiple products.
    L2PredictionCorpus positiveCorpus = fullCorpus.applyFilter(
        prediction -> positiveProductList.contains(prediction.getProductInchis().get(0)));
    LOGGER.info("Number of LCMS positive predictions: %d", positiveCorpus.getCorpus().size());
    Set<String> positiveSubstrateInchis = positiveCorpus.getUniqueSubstrateInchis();
    LOGGER.info("Number of substrates with positive LCMS products: %d", positiveCorpus.getCorpus().size());

    if (cl.hasOption(OPTION_TREE_SCORING) && !cl.hasOption(OPTION_CLUSTER_FIRST)) {
      LOGGER.error("Cannot use tree scoring if clustering only builds tree on positive substrates.");
      exitWithHelp(opts);
    }

    LOGGER.info("Importing molecules from inchi lists.");
    List<Molecule> molecules = null;
    if (cl.hasOption(OPTION_CLUSTER_FIRST)) {
      molecules = importInchisWithLcmsResults(allSubstrateInchis, inchi -> positiveSubstrateInchis.contains(inchi));
    } else {
      molecules = importInchisWithLcmsResults(positiveSubstrateInchis, inchi -> true);
    }
    LOGGER.info("Building SAR tree with LibraryMCS.");
    LibraryMCS libMcs = new LibraryMCS();
    SarTree sarTree = new SarTree();
    sarTree.buildByClustering(libMcs, molecules);

    Consumer<SarTreeNode> sarConfidenceCalculator = null;
    if (cl.hasOption(OPTION_TREE_SCORING)) {
      // TODO: put in Vijay's actual LCMS module here
      sarConfidenceCalculator = new SarTreeBasedCalculator(sarTree, getRandomScorer(.1));
      LOGGER.info("Only scoring SARs based on hits and misses within their subtrees.");
    } else {
      sarConfidenceCalculator = new SarHitPercentageCalculator(positiveCorpus, fullCorpus);
    }

    LOGGER.info("Scoring sars.");
    sarTree.applyToNodes(sarConfidenceCalculator, THRESHOLD_TREE_SIZE);

    LOGGER.info("Getting explanatory sars.");
    SarTreeNodeList explanatorySars = sarTree.getExplanatoryNodes(THRESHOLD_TREE_SIZE, THRESHOLD_CONFIDENCE);
    LOGGER.info("%d sars passed thresholds of subtree size %d, percentageHits %f.",
        explanatorySars.size(), THRESHOLD_TREE_SIZE, THRESHOLD_CONFIDENCE);
    LOGGER.info("Producing and writing output.");

    explanatorySars.sortByDecreasingConfidence();
    explanatorySars.writeToFile(outputFile);
    LOGGER.info("Complete!.");
  }

  /**
   * Imports inchis into molecules for use in clustering. Label all returned molecules with a property that says whether
   * they were an LCMS positive or negative, so this can be read from the clustering tree directly later.
   *
   * @param inchis The inchis to import.
   * @param lcmsTester The function used to classify inchis as LCMS positives or negatives.
   * @return The inchis as molecules.
   */
  private static List<Molecule> importInchisWithLcmsResults(Collection<String> inchis, Predicate<String> lcmsTester) {
    List<Molecule> molecules = new ArrayList<>();
    for (String inchi : inchis) {
      try {
        Molecule mol = MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
        molecules.add(mol);
      } catch (MolFormatException e) {
        LOGGER.warn("Error importing inchi %s:%s", inchi, e.getMessage());
      }
    }
    return molecules;
  }

  private static void exitWithHelp(Options opts) {
    HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
    System.exit(1);
  }

  /**
   * Returns a hit calculator that calculates which molecules are hits at random with the given positive rate
   *
   * @param positiveRate The positive rate.
   * @return The calculator.
   */
  public static Function<Molecule, SarTreeNode.LCMS_RESULT> getRandomScorer(Double positiveRate) {
    return (Molecule mol) -> RANDOM_GENERATOR.nextDouble() < positiveRate ?
        SarTreeNode.LCMS_RESULT.HIT :
        SarTreeNode.LCMS_RESULT.MISS;
  }

  /**
   * Reads in a prediction corpus, containing only one RO's predictions, and builds a clustering tree of the
   * substrates. Returns every SarTreeNode in the tree.
   *
   * @param predictionCorpusInput The prediction corpus input file.
   * @param sarTreeNodesOutput The file to which to write the SarTreeNodeList of every node in the clustering tree.
   * @return A JavaRunnable to run the appropriate clustering.
   */
  public static JavaRunnable getRunnableClusterer(File predictionCorpusInput, File sarTreeNodesOutput) {

    return new JavaRunnable() {
      @Override
      public void run() throws IOException {
        // Verify input and output files
        FileChecker.verifyInputFile(predictionCorpusInput);
        FileChecker.verifyAndCreateOutputFile(sarTreeNodesOutput);

        // Build input corpus and substrate list
        L2PredictionCorpus inputCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(predictionCorpusInput);
        L2InchiCorpus substrateInchis = new L2InchiCorpus(inputCorpus.getUniqueProductInchis());

        // Run substrate clustering
        SarTree sarTree = new SarTree();
        try {
          sarTree.buildByClustering(new LibraryMCS(), substrateInchis.getMolecules());
        } catch (InterruptedException e) {
          LOGGER.error("Threw interrupted exception during buildByClustering: %s", e.getMessage());
          throw new RuntimeException(e);
        }

        // Write output to file
        SarTreeNodeList nodeList = new SarTreeNodeList(new ArrayList<>(sarTree.getNodes()));
        nodeList.writeToFile(sarTreeNodesOutput);
      }

      @Override
      public String toString() {
        return "SarClusterer:" + predictionCorpusInput.getName();
      }
    };
  }

  /**
   * Reads in an already-built SarTree from a SarTreeNodeList, and scores the SARs based on LCMS results.  Currently
   * LCMS results are a dummy function that randomly classifies molecules as hits and misses.
   * TODO: hook this up with Vijays actual LCMS module to load the LCMS data in and use it to calculate hit
   *
   * @param sarTreeInput An input file containing a SarTreeNodeList with all Sars from the clustering tree.
   * @param sarTreeNodeOutput The output file to which to write the relevant SARs from the corpus, sorted in decreasing
   * order of confidence.
   * @param positiveRate Percentage of LCMS hits, randomly assigned.
   * @return A JavaRunnable to run the SAR scoring.
   */
  public static JavaRunnable getRunnableRandomSarScorer(
      File sarTreeInput,
      File sarTreeNodeOutput,
      Double positiveRate,
      Double confidenceThreshold,
      Integer subtreeThreshold) {

    return new JavaRunnable() {
      @Override
      public void run() throws IOException {
        // Verify input and output files
        FileChecker.verifyInputFile(sarTreeInput);
        FileChecker.verifyAndCreateOutputFile(sarTreeNodeOutput);

        // Build SAR tree from input file
        SarTreeNodeList nodeList = new SarTreeNodeList();
        nodeList.loadFromFile(sarTreeInput);
        SarTree sarTree = new SarTree();
        nodeList.getSarTreeNodes().forEach(node -> sarTree.addNode(node));

        // Build sar scorer with appropriate positive rate
        Function<Molecule, SarTreeNode.LCMS_RESULT> hitCalculator = getRandomScorer(positiveRate);
        SarTreeBasedCalculator sarConfidenceCalculator = new SarTreeBasedCalculator(sarTree, hitCalculator);

        // Score SARs
        sarTree.applyToNodes(sarConfidenceCalculator, subtreeThreshold);
        SarTreeNodeList treeNodeList = sarTree.getExplanatoryNodes(subtreeThreshold, confidenceThreshold);
        treeNodeList.sortByDecreasingConfidence();

        // Write out output.
        treeNodeList.writeToFile(sarTreeNodeOutput);
      }

      @Override
      public String toString() {
        return "SarScorer:" + sarTreeInput.getName();
      }
    };
  }

  /**
   * Gets a Sar scorer with default confidence and tree size thresholds
   */
  public static JavaRunnable getRunnableRandomSarScorer(File sarTreeInput,
                                                        File sarTreeNodeOutput,
                                                        Double positiveRate) {
    return getRunnableRandomSarScorer(sarTreeInput, sarTreeNodeOutput, positiveRate,
        THRESHOLD_CONFIDENCE, THRESHOLD_TREE_SIZE);
  }
}
