package com.act.biointerpretation.sarinference;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2FilteringDriver;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ProductScorer {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger(ProductScorer.class);

  private static final String OPTION_PREDICTION_CORPUS = "c";
  private static final String OPTION_POSITIVE_INCHIS = "p";
  private static final String OPTION_SCORED_SARS = "s";
  private static final String OPTION_OUTPUT_PATH = "o";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_PREDICTION_CORPUS)
          .argName("input corpus path")
          .desc("The absolute path to the input prediction corpus.")
          .hasArg()
          .longOpt("input-corpus-path")
          .required(true)
      );
      add(Option.builder(OPTION_POSITIVE_INCHIS)
          .argName("positive inchis file")
          .desc("The path to a file of positive inchis from LCMS analysis of the prediction corpus.")
          .hasArg()
          .longOpt("input-positive-inchis")
      );
      add(Option.builder(OPTION_SCORED_SARS)
          .argName("scored sars corpus")
          .hasArg()
          .longOpt("input-scored-sars")
      );
      add(Option.builder(OPTION_OUTPUT_PATH)
          .argName("output path")
          .desc("The path to which to write the output.")
          .hasArg()
          .longOpt("output-path")
          .required(true)
      );
    }
  };


  public static final String HELP_MESSAGE =
      "This class is used to rank the products of PredictionCorpus according to a set of SARs.";


  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final String INCHI_IMPORT_SETTINGS = "inchi";

  public static Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
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
      HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File inputCorpusFile = new File(cl.getOptionValue(OPTION_PREDICTION_CORPUS));
    File positiveInchisFile = new File(cl.getOptionValue(OPTION_POSITIVE_INCHIS));
    File scoredSarsFile = new File(cl.getOptionValue(OPTION_SCORED_SARS));
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));
    L2PredictionCorpus fullCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputCorpusFile);
    LOGGER.info("Number of predictions: %d", fullCorpus.getCorpus().size());

    L2InchiCorpus positiveInchis = new L2InchiCorpus();
    positiveInchis.loadCorpus(positiveInchisFile);
    List<String> inchiList = positiveInchis.getInchiList();

    L2PredictionCorpus positiveCorpus = fullCorpus.applyFilter(prediction -> inchiList.containsAll(prediction.getProductInchis()));
    LOGGER.info("Number of LCMS positives: %d", positiveCorpus.getCorpus().size());

    SarTreeNodeList scoredSars = new SarTreeNodeList();
    scoredSars.loadFromFile(scoredSarsFile);

    LOGGER.info("Number of sars: %d", scoredSars.getSarTreeNodes().size());

    Function<L2Prediction, SarTreeNode> confidenceCalculator = new PredictionConfidenceCalculator(scoredSars);

    Map<L2Prediction, SarTreeNode> predictionToSarMap = new HashMap<>();

    for (L2Prediction prediction : positiveCorpus.getCorpus()) {
      SarTreeNode bestSar = confidenceCalculator.apply(prediction);
      if (bestSar == null) {
        LOGGER.warn("No SAR found for this prediction.");
        continue;
      }
      predictionToSarMap.put(prediction, bestSar);
      prediction.setProjectorName(
          prediction.getProjectorName() + ":" +
              bestSar.getHierarchyId() + ":" +
              bestSar.getPercentageHits());
    }

    List<L2Prediction> predictions = new ArrayList<>(predictionToSarMap.keySet());
    predictions.sort((a, b) ->
        (-Double.compare(predictionToSarMap.get(a).getPercentageHits(), predictionToSarMap.get(b).getPercentageHits())));

    L2PredictionCorpus finalCorpus = new L2PredictionCorpus(predictions);
    finalCorpus.writePredictionsToJsonFile(outputFile);
  }
}
