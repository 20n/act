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

package com.act.biointerpretation.sarinference;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Calculates a SARs hit percentage score by testing the SAR against all substrates in a prediction corpus, and
 * counting LCMS positives and negatives among the substrates that match the SAR. This is the most complete scoring
 * possible, as it does not rely on the clustering that generated the SarTree to be perfect (unlike
 * SarTreeBasedCalculator). However, it is computationally expensive.
 */
public class SarHitPercentageCalculator implements Consumer<SarTreeNode> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarHitPercentageCalculator.class);

  private final L2PredictionCorpus positivePredictionCorpus;
  private final L2PredictionCorpus fullPredictionCorpus;

  public SarHitPercentageCalculator(L2PredictionCorpus positivePredictions, L2PredictionCorpus fullPredictionCorpus) {
    this.positivePredictionCorpus = positivePredictions;
    this.fullPredictionCorpus = fullPredictionCorpus;
  }

  /**
   * Score the SAR against all substrates in the positive corpus, and against all substrates in the entire
   * corpus, to get a ratio of LCMS hits to misses for this SAR.
   *
   * @param node The SarTreeNode to score.
   */
  @Override
  public void accept(SarTreeNode node) {
    Sar sar = node.getSar();
    node.setNumberHits(getHits(sar, positivePredictionCorpus));
    node.setNumberMisses(getHits(sar, fullPredictionCorpus) - node.getNumberHits());
  }

  private Integer getHits(Sar sar, L2PredictionCorpus corpus) {
    int hits = 0;

    for (String inchi : corpus.getUniqueSubstrateInchis()) {
      Molecule substrate;
      try {
        substrate = MolImporter.importMol(inchi, "inchi");
      } catch (MolFormatException e) {
        LOGGER.error("Couldn't import substrate %s from prediction corpus: %s", inchi, e.getMessage());
        continue;
      }
      if (sar.test(Arrays.asList(substrate))) {
        hits++;
      }
    }

    return hits;
  }
}
