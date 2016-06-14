package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.translate.NumericEntityUnescaper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

      // Build image directory
      String imageDirPath = cl.getOptionValue(OPTION_DRAW_IMAGES);
      File imageDirectory = new File(imageDirPath);
      if (imageDirectory.exists() && !imageDirectory.isDirectory()) {
        LOGGER.error("Supplied image directory is an existing non-directory file.");
        return;
      }
      imageDirectory.mkdir();

      // Get only those predictions which do not correspond to any reaction in the DB
      predictionCorpus.applyFilter(
              prediction -> prediction.getReactionCount() == 0 ? Optional.of(prediction) : Optional.empty()
      );

      // Build files for images and corpus
      Map<Integer, File> predictionFileMap = getPredictionFileMap(predictionCorpus, imageDirPath, format);

      List<Ero> roSet = predictionCorpus.getRoSet();
      Map<Integer, File> roFileMap = getRoFileMap(roSet, imageDirPath, format);

      File outCorpusFile = new File(StringUtils.join(imageDirPath, "/predictionCorpus.json"));

      // Print reaction images to file.
      for (L2Prediction prediction : predictionCorpus.getCorpus()) {

        try {
          drawAndSaveMolecule(predictionFileMap.get(prediction.getId()), prediction.getChemicalsRxnMolecule(),
                  format, width, height);
        } catch (IOException e) {
          LOGGER.error("Couldn't render prediction %d. %s", prediction.getId(), e.getMessage());
        }
      }

      // Print RO images to file.
      for (Ero ro: roSet) {

        try {
          Molecule roMolecule = MolImporter.importMol(ro.getRo(), "smiles");
          drawAndSaveMolecule(roFileMap.get(ro.getId()), roMolecule, format, width, height);
        } catch (IOException e) {
          LOGGER.error("Couldn't render RO %d. %s", ro.getId(), e.getMessage());
        }
      }

      // Print prediction corpus to file.
      predictionCorpus.writePredictionsToJsonFile(outCorpusFile);
    }

    LOGGER.info("L2ExpansionDriver complete!");
  }

  private static Map<Integer, File> getRoFileMap(List<Ero> roSet, String imageDirectory, String format) {
    Map<Integer, File> fileMap = new HashMap<Integer, File>();

    for (Ero ro: roSet) {
      String filePath = StringUtils.join(imageDirectory, "/RO_", ro.getId(), ".", format);
      fileMap.put(ro.getId(), new File(filePath));
    }

    return fileMap;
  }

  private static Map<Integer, File> getPredictionFileMap(L2PredictionCorpus predictionCorpus, String imageDir, String format) {
    Map<Integer, File> fileMap = new HashMap<Integer, File>();

    for (L2Prediction prediction : predictionCorpus.getCorpus()) {

      String filePath = StringUtils.join(imageDir, "/PREDICTION_", prediction.getId(),
              "_RO_", prediction.getRO().getId(), ".", format);
      fileMap.put(prediction.getId(), new File(filePath));
    }

    return fileMap;
  }

  private static void drawAndSaveMolecule(File imageFile, Molecule molecule, String format, Integer width, Integer height)
          throws IOException {

    byte[] graphics = MolExporter.exportToBinFormat(molecule, getFormatAndSize(format, width, height));

    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
      fos.write(graphics);
    }
  }

  private static void drawAndSaveMolecule(File imageFile, RxnMolecule molecule, String format, Integer width, Integer height)
          throws IOException {

    byte[] graphics = MolExporter.exportToBinFormat(molecule, getFormatAndSize(format, width, height));

    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
      fos.write(graphics);
    }
  }

  private static String getFormatAndSize(String format, Integer width, Integer height) {
    return DEFAULT_IMAGE_FORMAT + StringUtils.join(
            new String[]{":w", DEFAULT_IMAGE_WIDTH.toString(), ",", "h", DEFAULT_IMAGE_HEIGHT.toString()});
  }
}
