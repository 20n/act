package com.act.biointerpretation.l2expansion;

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
import java.util.Optional;
import java.util.List;

/**
 * Runs analysis on generated prediction corpus
 */
public class PredictionCorpusAnalyzer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionDriver.class);

  private static final String OPTION_CORPUS_PATH = "c";
  private static final String OPTION_DRAW_IMAGES = "d";
  private static final String OPTION_IMAGE_FORMAT = "f";
  private static final String OPTION_IMAGE_WIDTH = "w";
  private static final String OPTION_IMAGE_HEIGHT = "h";
  private static final String OPTION_HELP = "h";

  private static final String DEFAULT_IMAGE_FORMAT = "png";
  private static final String DEFAULT_IMAGE_WIDTH = "1000";
  private static final String DEFAULT_IMAGE_HEIGHT = "1000";

  public static final String HELP_MESSAGE =
          "This class is used to analyze an already-generated prediction corpus.  The corpus is read in from" +
                  "file, and basic statistics about it are generated.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CORPUS_PATH)
            .argName("corpus file path")
            .desc("The path to the prediction corpus file.")
            .hasArg()
            .longOpt("corpus-path")
            .required()
    );
    add(Option.builder(OPTION_DRAW_IMAGES)
            .argName("draw images")
            .desc("The directory in which to place the image files.")
            .hasArg()
            .longOpt("draw-images")
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
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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
    LOGGER.info("Predictions with reaction that matches RO: %d",
            predictionCorpus.countPredictions(prediction -> prediction.matchesRo()));

    // Draw images of predicted reactions.
    if (cl.hasOption(OPTION_DRAW_IMAGES)) {

      LOGGER.info("Drawing images for predictions not in the DB.");

      // Set image format options
      String format = cl.getOptionValue(OPTION_IMAGE_FORMAT, DEFAULT_IMAGE_FORMAT);
      Integer width = Integer.parseInt(cl.getOptionValue(OPTION_IMAGE_WIDTH, DEFAULT_IMAGE_WIDTH));
      Integer height = Integer.parseInt(cl.getOptionValue(OPTION_IMAGE_HEIGHT, DEFAULT_IMAGE_HEIGHT));

      String imageDirPath = cl.getOptionValue(OPTION_DRAW_IMAGES);

      // Get only those predictions which do not correspond to any reaction in the DB
      predictionCorpus.applyFilter(
              prediction -> prediction.getReactionCount() == 0 ? Optional.of(prediction) : Optional.empty()
      );

      // Render the corpus
      PredictionCorpusRenderer renderer = new PredictionCorpusRenderer(format, width, height);
      renderer.renderCorpus(predictionCorpus, imageDirPath);
    }

    LOGGER.info("L2ExpansionDriver complete!");
  }


}
