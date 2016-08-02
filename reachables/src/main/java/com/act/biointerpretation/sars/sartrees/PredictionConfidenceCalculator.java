package com.act.biointerpretation.sars.sartrees;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.function.Function;

public class PredictionConfidenceCalculator implements Function<L2Prediction, Double> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PredictionConfidenceCalculator.class);

  ScoredSarCorpus sarCorpus;

  public PredictionConfidenceCalculator(ScoredSarCorpus sarCorpus) {
    this.sarCorpus = sarCorpus;
  }

  public Double apply(L2Prediction prediction) {
    Molecule substrate;
    try {
      substrate = MolImporter.importMol(prediction.getSubstrateInchis().get(0), "inchi");
    } catch (MolFormatException e) {
      LOGGER.error("Couldn't import molecule %s: %s", prediction.getSubstrateInchis().get(0), e.getMessage());
      return 0D;
    }

    Double score = 0D;
    for (SarTreeNode scoredSar : sarCorpus.getSarTreeNodes()) {
      Sar sar = scoredSar.getSar();

      if (sar.test(Arrays.asList(substrate))) {
        Double sarScore = scoredSar.getPercentageHits();
        if (sarScore > score) {
          score = sarScore;
        }
      }
    }

    return score;
  }
}
