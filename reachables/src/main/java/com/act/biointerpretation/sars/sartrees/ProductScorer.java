package com.act.biointerpretation.sars.sartrees;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.act.biointerpretation.sars.CharacterizedGroup;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SarCorpus;
import com.act.biointerpretation.sars.SerializableReactor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    }};


  public static final String HELP_MESSAGE =
      "This class is used to build sars from an L2Prediction run and LCMS analysis results.  The inputs are an " +
          "L2PredictionCorpus and a file with all the product inchis that came up as positive in the LCMS analysis. " +
          "The output is a list of Sars with confidence scores based on how predictive they are of the reactions in " +
          "the corpus.";


  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final Boolean ALL_NODES = false;
  private static final String INCHI_IMPORT_SETTINGS = "inchi";
  private static final Double THRESHOLD_CONFIDENCE = 0.2;
  private static final Integer THRESHOLD_TREE_SIZE = 4;

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
      System.exit(1);
    }

    File inputCorpusFile = new File(cl.getOptionValue(OPTION_PREDICTION_CORPUS));
    File positiveInchisFile = new File(cl.getOptionValue(OPTION_POSITIVE_INCHIS));
    File scoredSarsFile = new File(cl.getOptionValue(OPTION_SCORED_SARS));
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));

    L2PredictionCorpus fullCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputCorpusFile);

    L2InchiCorpus positiveInchis = new L2InchiCorpus();
    positiveInchis.loadCorpus(positiveInchisFile);
    List<String> inchiList = positiveInchis.getInchiList();

    ScoredSarCorpus scoredSars = new ScoredSarCorpus();
    scoredSars.loadFromFile(scoredSarsFile);

    L2PredictionCorpus positiveCorpus = fullCorpus.applyFilter(prediction -> inchiList.contains(prediction.getSubstrateInchis().get(0)));

    Function<L2Prediction, Double> confidenceCalculator = new PredictionConfidenceCalculator(scoredSars);

    Map<L2Prediction, Double> predictionScoreMap = new HashMap<>();

    for (L2Prediction prediction : positiveCorpus.getCorpus()) {
      predictionScoreMap.put(prediction, confidenceCalculator.apply(prediction));
    }

    ScoredPredictionCorpus predictions = new ScoredPredictionCorpus(predictionScoreMap);
    predictions.writeToFile(outputFile);
  }
}
