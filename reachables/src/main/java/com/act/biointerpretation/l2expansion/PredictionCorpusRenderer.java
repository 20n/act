/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.l2expansion;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
   * an image of each reactor used in the corpus, and prints the corpus itself to a json file.
   *
   * @param predictionCorpus The corpus to render.
   * @param imageDirectory The directory in which to put the files.
   */
  public void renderCorpus(L2PredictionCorpus predictionCorpus, File imageDirectory) throws IOException {

    // Build files for images and corpus
    Map<Integer, File> predictionFileMap = getPredictionFileMap(predictionCorpus, imageDirectory);

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

  private String getPredictionFileName(L2Prediction prediction) {
    return StringUtils.join("PREDICTION_", prediction.getId());
  }
}
