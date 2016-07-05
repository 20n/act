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
public class L2RenderingDriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2RenderingDriver.class);

  private static final String OPTION_CORPUS_PATH = "c";
  private static final String OPTION_OUTPUT_DIRECTORY = "r";
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
    add(Option.builder(OPTION_OUTPUT_DIRECTORY)
        .argName("output directory")
        .desc("The path to the directory in which to render the corpus.")
        .hasArg()
        .longOpt("output-directory")
        .required()
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
      HELP_FORMATTER.printHelp(L2RenderingDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2RenderingDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Set up input corpus file.
    File corpusFile = new File(cl.getOptionValue(OPTION_CORPUS_PATH));
    if (!corpusFile.exists()) {
      LOGGER.error("Input corpus file does not exist");
      return;
    }
    //Set up output directory.
    File outputDirectory = new File(cl.getOptionValue(OPTION_OUTPUT_DIRECTORY));
    if (outputDirectory.exists() && !outputDirectory.isDirectory()) {
      LOGGER.error("Can't render corpus: supplied image directory is an existing non-directory file.");
      return;
    }
    outputDirectory.mkdir();

    LOGGER.info("Reading in corpus from file.");
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);
    LOGGER.info("Finished reading in corpus with %d predictions", predictionCorpus.getCorpus().size());

    // Set image format options
    String format = cl.getOptionValue(OPTION_IMAGE_FORMAT, DEFAULT_IMAGE_FORMAT);
    Integer width = Integer.parseInt(cl.getOptionValue(OPTION_IMAGE_WIDTH, DEFAULT_IMAGE_WIDTH));
    Integer height = Integer.parseInt(cl.getOptionValue(OPTION_IMAGE_HEIGHT, DEFAULT_IMAGE_HEIGHT));

    // Render the corpus.
    LOGGER.info("Drawing images for each prediction in corpus.");
    ReactionRenderer reactionRenderer = new ReactionRenderer(format, width, height);
    PredictionCorpusRenderer renderer = new PredictionCorpusRenderer(reactionRenderer);
    renderer.renderCorpus(predictionCorpus, outputDirectory);

    LOGGER.info("L2RenderingDriver complete!");
  }


}
