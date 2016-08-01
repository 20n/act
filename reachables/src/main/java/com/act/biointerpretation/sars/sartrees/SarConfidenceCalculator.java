package com.act.biointerpretation.sars.sartrees;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Created by gil on 8/1/16.
 */
public class SarConfidenceCalculator implements Function<Sar, Double> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarConfidenceCalculator.class);

  private final L2PredictionCorpus positivePredictionCorpus;
  private final L2PredictionCorpus fullPredictionCorpus;

  public SarConfidenceCalculator(L2PredictionCorpus positivePredictions, L2PredictionCorpus fullPredictionCorpus) {
    this.positivePredictionCorpus = positivePredictions;
    this.fullPredictionCorpus = fullPredictionCorpus;
  }

  @Override
  public Double apply(Sar sar) {
    return new Double(getHits(sar, positivePredictionCorpus))/new Double(getHits(sar, fullPredictionCorpus));
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
