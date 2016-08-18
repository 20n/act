package com.act.biointerpretation.sarinference;

import com.act.biointerpretation.l2expansion.L2FilteringDriver;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProductScorer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ProductScorer.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  private static final String OPTION_PREDICTION_CORPUS = "c";
  private static final String OPTION_LCMS_RESULTS = "p";
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
      add(Option.builder(OPTION_LCMS_RESULTS)
          .argName("lcms results")
          .desc("The path to a file of lcms results.")
          .hasArg()
          .longOpt("input-lcms-results")
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
    File lcmsFile = new File(cl.getOptionValue(OPTION_LCMS_RESULTS));
    File scoredSarsFile = new File(cl.getOptionValue(OPTION_SCORED_SARS));
    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_PATH));

    JavaRunnable productScoreRunner = getRunnableProductScorer(
        inputCorpusFile,
        scoredSarsFile,
        lcmsFile,
        outputFile);

    LOGGER.info("Scoring products.");
    productScoreRunner.run();
    LOGGER.info("Complete!.");
  }

  /**
   * Reads in scored SARs, checks them against a prediction corpus and positive inchi list to get a product ranking.
   * TODO: improve the data structure used to store scored products- using an L2PredictionCorpus is pretty ugly
   *
   * @param predictionCorpus The prediction corpus to score.
   * @param scoredSars The scored SARs to use.
   * @param lcmsFile The set of positive LCMS inchis, to use in scoring.
   * @return A JavaRunnable to run the product scoring.
   */
  public static JavaRunnable getRunnableProductScorer(File predictionCorpus, File scoredSars, File lcmsFile,
                                                      File outputFile) {

    return new JavaRunnable() {
      @Override
      public void run() throws IOException {
        // Verify files
        FileChecker.verifyInputFile(predictionCorpus);
        FileChecker.verifyInputFile(scoredSars);
        FileChecker.verifyInputFile(lcmsFile);
        FileChecker.verifyAndCreateOutputFile(outputFile);

        // Build SAR node list and best sar finder
        SarTreeNodeList nodeList = new SarTreeNodeList();
        nodeList.loadFromFile(scoredSars);
        BestSarFinder sarFinder = new BestSarFinder(nodeList);

        // Build prediction corpus
        L2PredictionCorpus predictions = L2PredictionCorpus.readPredictionsFromJsonFile(predictionCorpus);

        // Build LCMS results
        IonAnalysisInterchangeModel lcmsResults = new IonAnalysisInterchangeModel();
        lcmsResults.loadResultsFromFile(lcmsFile);

        /**
         * Build map from predictions to their scores based on SAR
         * For each prediction, we add on auxiliary info about its SARs and score to its projector name.
         // TODO: build data structure to store a scored prediction, instead of hijacking the projector name.
         */
        Map<L2Prediction, Double> predictionToScoreMap = new HashMap<>();
        LOGGER.info("Scoring predictions.");
        for (L2Prediction prediction : predictions.getCorpus()) {
          String nameAppendage = lcmsResults.getLcmsDataForPrediction(prediction).toString(); // Always tack LCMS result onto name

          Optional<SarTreeNode> maybeBestSar = sarFinder.apply(prediction);

          if (!maybeBestSar.isPresent()) {
            // If a SAR was matched, add info about it to the projector name, and put its score into the map
            SarTreeNode bestSar = maybeBestSar.get();
            nameAppendage += ":" +
                bestSar.getHierarchyId() + ":" +
                bestSar.getRankingScore();
            predictionToScoreMap.put(prediction, bestSar.getRankingScore());
          } else {
            // If no SAR is found, append "NO_SAR" to the prediction, and give it a ranking score of 0
            nameAppendage += "NO_SAR";
            predictionToScoreMap.put(prediction, 0D);
          }

          prediction.setProjectorName(prediction.getProjectorName() + nameAppendage);
        }

        LOGGER.info("Sorting predictions in decreasing order of best associated SAR rank.");
        List<L2Prediction> predictionList = new ArrayList<>(predictionToScoreMap.keySet());
        predictionList.sort((a, b) -> -Double.compare(
            predictionToScoreMap.get(a),
            predictionToScoreMap.get(b)));

        // Wrap results in a corpus and write to file.
        L2PredictionCorpus finalCorpus = new L2PredictionCorpus(predictionList);
        finalCorpus.writePredictionsToJsonFile(outputFile);
        LOGGER.info("Complete!.");
      }

      @Override
      public String toString() {
        return "ProductScorer:" + scoredSars.getName();
      }
    };
  }
}
