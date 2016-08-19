package com.act.biointerpretation.sarinference;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2FilteringDriver;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LibMcsClustering {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsClustering.class);

  private static final String OPTION_PREDICTION_CORPUS = "c";
  private static final String OPTION_POSITIVE_INCHIS = "p";
  private static final String OPTION_SARTREE_PATH = "o";
  private static final String OPTION_SCORED_SAR_PATH = "o";
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
      add(Option.builder(OPTION_SARTREE_PATH)
          .argName("sartree path")
          .desc("The path to which to write the intermediate file produced by structure clustering.")
          .hasArg()
          .longOpt("sartree-path")
          .required()
      );
      add(Option.builder(OPTION_SCORED_SAR_PATH)
          .argName("scored sar path")
          .desc("The path to which to write the final output file of scored sars.")
          .hasArg()
          .longOpt("scored-sar-path")
          .required()
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

  // This value should stay between 0 and 1 as it indicates the minimum hit percentage for a SAR worth keeping around.
  private static final Double MISS_PENALTY = 1.0;  // no threshold is currently applied

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
    File sartreeFile = new File(cl.getOptionValue(OPTION_SARTREE_PATH));
    File scoredSarFile = new File(cl.getOptionValue(OPTION_SCORED_SAR_PATH));

    JavaRunnable clusterRunner = getRunnableClusterer(inputCorpusFile, sartreeFile);
    JavaRunnable scoringRunner = getRunnableSarScorer(
        inputCorpusFile,
        sartreeFile,
        positiveInchisFile,
        scoredSarFile,
        MISS_PENALTY,
        THRESHOLD_TREE_SIZE);

    LOGGER.info("Running clustering.");
    clusterRunner.run();

    LOGGER.info("Running sar scoring.");
    scoringRunner.run();

    LOGGER.info("Complete!.");
  }

  private static void exitWithHelp(Options opts) {
    HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
    System.exit(1);
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
        Collection<Molecule> molecules = importMoleculesWithPredictionIds(inputCorpus);

        // Run substrate clustering
        SarTree sarTree = new SarTree();
        try {
          sarTree.buildByClustering(new LibraryMCS(), molecules);
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

      public Collection<Molecule> importMoleculesWithPredictionIds(L2PredictionCorpus inputCorpus) throws MolFormatException {
        Map<String, Molecule> inchiToMoleculeMap = new HashMap<>();
        for (L2Prediction prediction : inputCorpus.getCorpus()) {
          for (String substrateInchi : prediction.getSubstrateInchis()) { // For now this should only be one substrate
            if (!inchiToMoleculeMap.containsKey(substrateInchi)) {
              Molecule mol = importMoleculeWithPredictionId(substrateInchi, prediction.getId());
              inchiToMoleculeMap.put(substrateInchi, mol);
            } else {
              List<Integer> predictionIds =
                  (ArrayList<Integer>) inchiToMoleculeMap
                      .get(substrateInchi)
                      .getPropertyObject(SarTreeNode.PREDICTION_ID_KEY);
              predictionIds.add(prediction.getId());
            }
          }
        }
        return inchiToMoleculeMap.values();
      }

      public Molecule importMoleculeWithPredictionId(String inchi, Integer id) throws MolFormatException {
        Molecule mol = MolImporter.importMol(inchi, "inchi");
        List<Integer> predictionIds = new ArrayList<>();
        predictionIds.add(id);
        mol.setPropertyObject(SarTreeNode.PREDICTION_ID_KEY, predictionIds);
        return mol;
      }
    };
  }

  /**
   * Reads in an already-built SarTree from a SarTreeNodeList, and scores the SARs based on LCMS results.  Currently
   * LCMS results are a dummy function that randomly classifies molecules as hits and misses.
   *
   * @param sarTreeInput An input file containing a SarTreeNodeList with all Sars from the clustering tree.
   * @param lcmsInput File with LCMS hits.
   * @param sarTreeNodeOutput The output file to which to write the relevant SARs from the corpus, sorted in decreasing
   * order of confidence.
   * @param missPenalty The penalty to assign an LCMS miss for a SAR, when scoring SARs.
   * @param subtreeThreshold The minimum number of leaves a sAR should match to be returned.
   * @return A JavaRunnable to run the SAR scoring.
   */
  public static JavaRunnable getRunnableSarScorer(
      File predictionsFile,
      File sarTreeInput,
      File lcmsInput,
      File sarTreeNodeOutput,
      Double missPenalty,
      Integer subtreeThreshold) {

    return new JavaRunnable() {
      @Override
      public void run() throws IOException {
        // Verify input and output files
        FileChecker.verifyInputFile(predictionsFile);
        FileChecker.verifyInputFile(lcmsInput);
        FileChecker.verifyInputFile(sarTreeInput);
        FileChecker.verifyAndCreateOutputFile(sarTreeNodeOutput);

        L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(predictionsFile);

        // Build SAR tree from input file
        SarTreeNodeList nodeList = new SarTreeNodeList();
        nodeList.loadFromFile(sarTreeInput);
        SarTree sarTree = new SarTree();
        nodeList.getSarTreeNodes().forEach(node -> sarTree.addNode(node));

        // Build LCMS results
        IonAnalysisInterchangeModel lcmsResults = new IonAnalysisInterchangeModel();
        lcmsResults.loadResultsFromFile(lcmsInput);

        // Build sar scorer from LCMS and prediction corpus
        SarTreeBasedCalculator sarScorer = new SarTreeBasedCalculator(sarTree, predictionCorpus, lcmsResults);

        // Score SARs
        // Calculate hits and misses
        sarTree.applyToNodes(sarScorer, subtreeThreshold);
        // Calculate ranking scores
        sarTree.getNodes().forEach(node ->
            node.setRankingScore(node.getNumberHits() - (missPenalty * node.getNumberMisses())));
        // Retain nodes that are not repeats or leaves, and have more than 0 LCMS hits
        SarTreeNodeList treeNodeList = sarTree.getExplanatoryNodes(subtreeThreshold, 0.0);
        treeNodeList.sortByDecreasingScores();

        // Write out output.
        treeNodeList.writeToFile(sarTreeNodeOutput);
      }

      @Override
      public String toString() {
        return "SarScorer:" + sarTreeInput.getName();
      }
    };
  }
}
