package com.act.biointerpretation.l2expansion;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used to create drawings of a PredictionCorpus's predictions, for manual curation.
 */
public class PredictionCorpusRenderer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PredictionCorpusRenderer.class);

  // The filename to which to print the corpus as a json file.
  private static final String PREDICTION_CORPUS_FILE_NAME = "predictions.json";

  // The filename to which to print the raw list of inchis for bing search analysis
  private static final String INCHI_LIST_FILE_NAME = "prediction_inchis";

  // Settings for printing images
  ReactionRenderer reactionRenderer;

  public PredictionCorpusRenderer(ReactionRenderer reactionRenderer) {
    this.reactionRenderer = reactionRenderer;
  }

  /**
   * Renders the prediction corpus into the specified directory.  Prints an image of each predicted reaction, prints
   * an image of each RO used in the corpus, and prints the corpus itself to a json file.
   *
   * @param predictionCorpus The corpus to render.
   * @param imageDirectory The directory in which to put the files.
   */
  public void renderCorpus(L2PredictionCorpus predictionCorpus, File imageDirectory) {
    // Get relevant ros from ro corpus
    List<L2PredictionRo> roSet = predictionCorpus.getAllRos();

    // Build files for images and corpus
    Map<Integer, File> predictionFileMap = getPredictionFileMap(predictionCorpus, imageDirectory);
    Map<Integer, File> roFileMap = buildRoFileMap(roSet, imageDirectory);

    File outCorpusFile = new File(imageDirectory, PREDICTION_CORPUS_FILE_NAME);
    File inchiListFile = new File(imageDirectory, INCHI_LIST_FILE_NAME);

    // Print reaction images to file.
    for (L2Prediction prediction : predictionCorpus.getCorpus()) {
      try {
        reactionRenderer.drawRxnMolecule(getRxnMolecule(prediction), predictionFileMap.get(prediction.getId()));
      } catch (IOException e) {
        LOGGER.error("Couldn't render prediction %d. %s", prediction.getId(), e.getMessage());
      }
    }

    // Print RO images to file.
    for (L2PredictionRo ro : roSet) {
      try {
        Molecule roMolecule = MolImporter.importMol(ro.getReactionRule(), "smiles");
        reactionRenderer.drawMolecule(roMolecule, roFileMap.get(ro.getId()));
      } catch (IOException e) {
        LOGGER.error("Couldn't render RO %d. %s", ro.getId(), e.getMessage());
      }
    }

    // Print prediction corpus to file.
    try {
      predictionCorpus.writePredictionsToJsonFile(outCorpusFile);
    } catch (IOException e) {
      LOGGER.error("Couldn't print prediction corpus to file.");
    }

    try {
      L2InchiCorpus inchiCorpus = new L2InchiCorpus(predictionCorpus.getUniqueProductInchis());
      inchiCorpus.writeToFile(inchiListFile);
    } catch (IOException e) {
      LOGGER.error("Couldn't print product inchis to file.");
    }
  }

  /**
   * Create a file for each ro drawing, and return a map from ro ids to those files.
   *
   * @param roSet The list of ros used in the corpus.
   * @param imageDir The directory in which the files should be located.
   * @return A map from ro id to the corresponding ro's file.
   */
  private Map<Integer, File> buildRoFileMap(List<L2PredictionRo> roSet, File imageDir) {
    Map<Integer, File> fileMap = new HashMap<Integer, File>();

    for (L2PredictionRo ro : roSet) {
      String fileName = getRoFileName(ro) + "." + reactionRenderer.getFormat();
      fileMap.put(ro.getId(), new File(imageDir, fileName));
    }

    return fileMap;
  }

  /**
   * Create a file for each prediction drawing,and return a map from prediction ids to those files.
   *
   * @param predictionCorpus The prediction corpus.
   * @param imageDir The directory in which the files should be located.
   * @return A map from prediction id to the corresponding prediction's file.
   */
  private Map<Integer, File> getPredictionFileMap(L2PredictionCorpus predictionCorpus, File imageDir) {
    Map<Integer, File> fileMap = new HashMap<Integer, File>();

    for (L2Prediction prediction : predictionCorpus.getCorpus()) {
      String fileName = getPredictionFileName(prediction) + "." + reactionRenderer.getFormat();
      fileMap.put(prediction.getId(), new File(imageDir, fileName));
    }

    return fileMap;
  }

  private RxnMolecule getRxnMolecule(L2Prediction prediction)
      throws MolFormatException {

    RxnMolecule renderedReactionMolecule = new RxnMolecule();

    // Add substrates and products to molecule.
    for (String substrate : prediction.getSubstrateInchis()) {
      renderedReactionMolecule.addComponent(MolImporter.importMol(substrate), RxnMolecule.REACTANTS);
    }
    for (String product : prediction.getProductInchis()) {
      renderedReactionMolecule.addComponent(MolImporter.importMol(product), RxnMolecule.PRODUCTS);
    }

    // Calculate coordinates with a 2D coordinate system.
    Cleaner.clean(renderedReactionMolecule, 2, null);

    // Change the reaction arrow type.
    renderedReactionMolecule.setReactionArrowType(RxnMolecule.REGULAR_SINGLE);

    return renderedReactionMolecule;
  }

  private String getRoFileName(L2PredictionRo ro) {
    return StringUtils.join("RO_", ro.getId());
  }

  private String getPredictionFileName(L2Prediction prediction) {
    return StringUtils.join("PREDICTION_", prediction.getId());
  }
}
