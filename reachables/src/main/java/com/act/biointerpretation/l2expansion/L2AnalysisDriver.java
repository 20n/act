package com.act.biointerpretation.l2expansion;

import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
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
import java.util.List;

/**
 * Used to render an already-genderated prediction corpus for manual curation.
 */
public class L2AnalysisDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2AnalysisDriver.class);

  private static final String OPTION_CORPUS_PATH = "c";
  private static final String OPTION_RENDER_CORPUS = "r";
  private static final String OPTION_IMAGE_FORMAT = "f";
  private static final String OPTION_IMAGE_WIDTH = "w";
  private static final String OPTION_IMAGE_HEIGHT = "h";
  private static final String OPTION_HELP = "h";

  private static final String DEFAULT_IMAGE_FORMAT = "png";
  private static final String DEFAULT_IMAGE_WIDTH = "1000";
  private static final String DEFAULT_IMAGE_HEIGHT = "1000";

  public static final String HELP_MESSAGE =
      "This class is used to render an already-generated prediction corpus.  The corpus is read in from " +
          "file, and basic statistics about it are generated. Without -r, nothing more is done.  With " +
          "-r, the corpus is also rendered into a specified directory. This entails printing one image of " +
          "each prediction, and one image of each ro that occurs in any prediction. The corpus itself is " +
          "also printed to the same directory, as well as a file containing the product inchis from the corpus, " +
          "which contains one inchi per line, and can be used as input to the BingSearchRanker.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CORPUS_PATH)
        .argName("corpus file path")
        .desc("The path to the prediction corpus file.")
        .hasArg()
        .longOpt("corpus-path")
        .required()
    );
    add(Option.builder(OPTION_RENDER_CORPUS)
        .argName("render corpus")
        .desc("Render the corpus. Parameter specifies directory in which to place the rendered images")
        .hasArg()
        .longOpt("render-corpus")
    );
    add(Option.builder(OPTION_IMAGE_FORMAT)
        .argName("image format")
        .desc("The format in which to print the images, i.e 'png'")
        .hasArg()
        .longOpt("image-format")
    );
    add(Option.builder(OPTION_IMAGE_WIDTH)
        .argName("image width")
        .desc("The width of the images to print")
        .hasArg()
        .longOpt("image-width")
    );
    add(Option.builder(OPTION_IMAGE_HEIGHT)
        .argName("image height")
        .desc("The height of the images to print")
        .hasArg()
        .longOpt("image-height")
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
      HELP_FORMATTER.printHelp(L2AnalysisDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2AnalysisDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Get corpus file.
    File corpusFile = new File(cl.getOptionValue(OPTION_CORPUS_PATH));

    // Read prediction corpus from file.
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);

    // Print summary statistics on corpus.
    LOGGER.info("Total predictions: %d", predictionCorpus.countPredictions(prediction -> true));
    LOGGER.info("Predictions with no matching reaction: %d",
        predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() == 0));
    LOGGER.info("Predictions with some matching reaction: %d",
        predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() > 0));
    LOGGER.info("Predictions with only reactions that match RO: %d",
        predictionCorpus.countPredictions(prediction -> prediction.getReactionsNoRoMatch().isEmpty()));

    // Draw images of predicted reactions.
    if (cl.hasOption(OPTION_RENDER_CORPUS)) {

      LOGGER.info("Drawing images for predictions.");

      // Set image format options
      String format = cl.getOptionValue(OPTION_IMAGE_FORMAT, DEFAULT_IMAGE_FORMAT);
      Integer width = Integer.parseInt(cl.getOptionValue(OPTION_IMAGE_WIDTH, DEFAULT_IMAGE_WIDTH));
      Integer height = Integer.parseInt(cl.getOptionValue(OPTION_IMAGE_HEIGHT, DEFAULT_IMAGE_HEIGHT));

      String imageDirPath = cl.getOptionValue(OPTION_RENDER_CORPUS);

      // Render the corpus
      ReactionRenderer reactionRenderer = new ReactionRenderer(format, width, height);
      PredictionCorpusRenderer renderer = new PredictionCorpusRenderer(reactionRenderer);
      renderer.renderCorpus(predictionCorpus, imageDirPath);
    }

    LOGGER.info("L2AnalysisDriver complete!");
  }


}
