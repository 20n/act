package com.act.biointerpretation.sarinference;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Test a given prediction against all Sars in a corpus, and returns the highest-scored matching Sar.
 */
public class PredictionConfidenceCalculator implements Function<L2Prediction, SarTreeNode> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PredictionConfidenceCalculator.class);

  SarTreeNodeList sarCorpus;

  public PredictionConfidenceCalculator(SarTreeNodeList sarCorpus) {
    this.sarCorpus = sarCorpus;
  }

  public SarTreeNode apply(L2Prediction prediction) {
    Molecule substrate;
    try {
      substrate = MolImporter.importMol(prediction.getSubstrateInchis().get(0), "inchi");
    } catch (MolFormatException e) {
      LOGGER.error("Couldn't import molecule %s: %s", prediction.getSubstrateInchis().get(0), e.getMessage());
      return null;
    }

    Double score = 0D;
    SarTreeNode bestSarTreeNode = null;
    for (SarTreeNode scoredSar : sarCorpus.getSarTreeNodes()) {
      Sar sar = scoredSar.getSar();

      if (sar.test(Arrays.asList(substrate))) {
        Double sarScore = scoredSar.getPercentageHits();
        if (sarScore > score) {
          score = sarScore;
          bestSarTreeNode = scoredSar;
        }
      }
    }

    return bestSarTreeNode;
  }
}
