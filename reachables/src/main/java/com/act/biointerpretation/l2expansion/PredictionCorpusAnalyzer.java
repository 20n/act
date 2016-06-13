package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs analysis on generated prediction corpus
 */
public class PredictionCorpusAnalyzer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionDriver.class);

  private static final String OPTION_CORPUS_PATH = "c";
  private static final String OPTION_DRAW_IMAGES = "d";
  private static final String OPTION_HELP = "h";

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

    // Build command line parser
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

    // Print help
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2ExpansionDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Set filenames
    String corpusFile = cl.getOptionValue(OPTION_CORPUS_PATH);

    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(corpusFile);

    LOGGER.info("Total predictions: %d", predictionCorpus.countPredictions(prediction -> true));
    LOGGER.info("Predictions with no matching reaction: %d",
            predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() == 0));
    LOGGER.info("Predictions with some matching reaction: %d",
            predictionCorpus.countPredictions(prediction -> prediction.getReactionCount() > 0));
    LOGGER.info("Predictions with reaction that matches RO: %d",
            predictionCorpus.countPredictions(prediction -> prediction.matchesRo()));

    if (cl.hasOption(OPTION_DRAW_IMAGES)) {
      LOGGER.info("Drawing images for predictions not in the DB.");

      predictionCorpus.applyFilter(
              prediction -> prediction.getReactionCount() == 0 ? Arrays.asList(prediction) : Arrays.asList()
      );

      String imageDir = cl.getOptionValue(OPTION_DRAW_IMAGES);
      Set<String> roAlreadyPrinted = new HashSet<>();

      for (L2Prediction prediction : predictionCorpus.getCorpus()) {

        String filePath = StringUtils.join(imageDir, "PREDICTION_", prediction.getId(),
                "_RO_", prediction.getRO().getId());

        try {
          drawAndSaveMolecule(filePath, prediction.getChemicalsRxnMolecule(), "png", 1000, 1000);
        } catch (IOException e) {
          LOGGER.error("Couldn't render molecule %s. %s", filePath, e.getMessage());
        }

        if (!roAlreadyPrinted.contains(prediction.getRO().getRo())) {
          filePath = StringUtils.join(imageDir, "RO_", prediction.getRO().getId());
          Molecule roMolecule = MolImporter.importMol(prediction.getRO().getRo(), "smiles");
          drawAndSaveMolecule(filePath, roMolecule, "png", 1000, 1000);
          roAlreadyPrinted.add(prediction.getRO().getRo());
        }
      }

      predictionCorpus.writePredictionsToJsonFile(StringUtils.join(imageDir, "predictionCorpus.json"));
    }

    LOGGER.info("L2ExpansionDriver complete!");
  }

  public static void drawAndSaveMolecule(String filePath, Molecule molecule, String format, Integer height, Integer width)
          throws IOException {

    String formatAndSize = format + StringUtils.join(new String[]{":w", width.toString(), ",", "h", height.toString()});
    byte[] graphics = MolExporter.exportToBinFormat(molecule, formatAndSize);

    String fullPath = StringUtils.join(new String[]{filePath, ".", format});
    try (FileOutputStream fos = new FileOutputStream(new File(fullPath))) {
      fos.write(graphics);
    }
  }

  public static void drawAndSaveMolecule(String filePath, RxnMolecule molecule, String format, Integer height, Integer width)
          throws IOException {

    String formatAndSize = format + StringUtils.join(new String[]{":w", width.toString(), ",", "h", height.toString()});
    byte[] graphics = MolExporter.exportToBinFormat(molecule, formatAndSize);

    String fullPath = StringUtils.join(new String[]{filePath, ".", format});
    try (FileOutputStream fos = new FileOutputStream(new File(fullPath))) {
      fos.write(graphics);
    }
  }
}
