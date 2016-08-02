package com.act.biointerpretation.sars.sartrees;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2FilteringDriver;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.act.biointerpretation.sars.CharacterizedGroup;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SarCorpus;
import com.act.biointerpretation.sars.SerializableReactor;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
          .desc("Use LibMCS to cluster substrates on full corpus, and only use pos/neg lcms results to score thereafter.")
          .longOpt("cluster-first")
      );
      add(Option.builder(OPTION_TREE_SCORING)
          .argName("tree scoring")
          .desc("Score based on hits and misses found in subtree of SAR; don't apply SAR elsewhere. This should only be " +
              "run if clustering is first.")
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
          "The output is a list of Sars with percentageHits scores based on how predictive they are of the reactions in " +
          "the corpus.";


  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final Boolean ALL_NODES = false;
  private static final String INCHI_IMPORT_SETTINGS = "inchi";
  private static final Double THRESHOLD_CONFIDENCE = 0D;
  private static final Integer THRESHOLD_TREE_SIZE = 2;

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
      HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
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

    L2InchiCorpus positiveInchis = new L2InchiCorpus();
    positiveInchis.loadCorpus(positiveInchisFile);
    List<String> inchiList = positiveInchis.getInchiList();

    L2PredictionCorpus positiveCorpus = fullCorpus.applyFilter(prediction -> inchiList.contains(prediction.getProductInchis().get(0)));
    LOGGER.info("Number of LCMS positives: %d", positiveCorpus.getCorpus().size());

    LibraryMCS libMcs = new LibraryMCS();

      LOGGER.info("Building sar tree.");
    L2PredictionCorpus corpusToCluster = positiveCorpus;

    SarTree sarTree;
    if (cl.hasOption(OPTION_CLUSTER_FIRST)) {
      sarTree = buildSarTree(libMcs, positiveCorpus.getUniqueSubstrateInchis(), fullCorpus.getUniqueSubstrateInchis());
    } else {
      sarTree = buildSarTree(libMcs, fullCorpus.getUniqueSubstrateInchis());
    }

    Consumer<SarTreeNode> sarConfidenceCalculator = new SarConfidenceCalculator(positiveCorpus, fullCorpus);
    if (cl.hasOption(OPTION_TREE_SCORING)) {
      Set<String> positiveInchiSet = new HashSet<>();
      positiveInchiSet.addAll(inchiList);
      sarConfidenceCalculator = new TreeBasedHitCalculator(positiveInchiSet, sarTree);
    }

    LOGGER.info("Scoring sars.");
    sarTree.scoreSars(sarConfidenceCalculator, THRESHOLD_TREE_SIZE);

    LOGGER.info("Getting explanatory nodes.");
    List<SarTreeNode> explanatorySars = sarTree.getExplanatoryNodes(THRESHOLD_TREE_SIZE, THRESHOLD_CONFIDENCE);

    LOGGER.info("%d sars passed thresholds of subtree size %d, percentageHits %f.",
        explanatorySars.size(), THRESHOLD_TREE_SIZE, THRESHOLD_CONFIDENCE);
    LOGGER.info("Producing and writing output.");

    explanatorySars.sort((a, b) -> a.getPercentageHits() > b.getPercentageHits() ? -1 : 1);

    ScoredSarCorpus scoredSarCorpus = new ScoredSarCorpus(explanatorySars);
    scoredSarCorpus.writeToFile(outputFile);
    LOGGER.info("Complete!.");
  }

  public static SarTree buildSarTree(LibraryMCS libMcs, Collection<String> positiveInchis, Collection<String> allInchis)
      throws InterruptedException, IOException {
    List<Molecule> molecules = new ArrayList<>();

    for (String inchi : allInchis) {
      try {
        Molecule mol = importMolecule(inchi);
        if (positiveInchis.contains(inchi)) {
          mol.setProperty("in_lcms","true");
        } else {
          mol.setProperty("in_lcms","false");
        }
        molecules.add(mol);
      } catch (MolFormatException e) {
        LOGGER.warn("Error importing inchi %s:%s", inchi, e.getMessage());
      }
    }

    return buildSarTree(libMcs, molecules);
  }

  public static SarTree buildSarTree(LibraryMCS libMcs, Collection<String> positiveInchis) throws InterruptedException, IOException {

    List<Molecule> molecules = new ArrayList<>();
    for (String inchi : positiveInchis) {
      try {
        Molecule mol = importMolecule(inchi);
        mol.setProperty("in_lcms","true");
        molecules.add(mol);
      } catch (MolFormatException e) {
        LOGGER.warn("Error importing inchi %s:%s", inchi, e.getMessage());
      }
    }

    return buildSarTree(libMcs, molecules);
  }


  public static SarTree buildSarTree(LibraryMCS libMcs, List<Molecule> molecules) throws InterruptedException {
    for (Molecule mol: molecules) {
      libMcs.addMolecule(mol);
    }

    libMcs.search();
    LibraryMCS.ClusterEnumerator enumerator = libMcs.getClusterEnumerator(ALL_NODES);

    SarTree sarTree = new SarTree();
    while (enumerator.hasNext()) {
      Molecule molecule = enumerator.next();
      String hierId = molecule.getPropertyObject("HierarchyID").toString();
      SarTreeNode thisNode = new SarTreeNode(molecule, hierId);
      sarTree.addNode(thisNode);
    }

    return sarTree;
  }

  public static Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
  }

  public static SarCorpus getSarCorpus(String projectorName, L2PredictionCorpus predictionCorpus, ErosCorpus roCorpus) throws ReactionException, IOException, InterruptedException {
    Integer roId = Integer.parseInt(projectorName);
    Reactor reactor = roCorpus.getEro(roId).getReactor();
    SerializableReactor serReactor = new SerializableReactor(reactor, roId);

    return getSarCorpus(serReactor, predictionCorpus.getUniqueSubstrateInchis());
  }

  public static SarCorpus getSarCorpus(SerializableReactor reactor, Collection<String> substrateInchis) throws IOException, InterruptedException {
    SarTree sarTree = buildSarTree(new LibraryMCS(), substrateInchis);

    SarCorpus sarCorpus = new SarCorpus();
    for (SarTreeNode node : sarTree.getSubtreeNodes()) {
      Molecule substructure = node.getSubstructure();
      List<Sar> sarContainer = Arrays.asList(new OneSubstrateSubstructureSar(substructure));
      String name = node.getHierarchyId();
      CharacterizedGroup group = new CharacterizedGroup(name, sarContainer, reactor);
      sarCorpus.addCharacterizedGroup(group);
    }

    return sarCorpus;
  }
}
