package com.act.biointerpretation.sars.sartrees;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Created by gil on 8/1/16.
 */
public class ConfidenceCalculator implements Function<Sar, Double> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ConfidenceCalculator.class);
  L2PredictionCorpus predictionCorpus;

  public ConfidenceCalculator(L2PredictionCorpus predictionCorpus) {
    this.predictionCorpus = predictionCorpus;
  }

  @Override
  public Double apply(Sar sar) {
    int hits = 0;
    int misses = 0;

    for (String inchi : predictionCorpus.getUniqueSubstrateInchis()) {
      Molecule substrate;
      try {
        substrate = MolImporter.importMol(inchi, "inchi");
      } catch (MolFormatException e) {
        LOGGER.error("Couldn't import substrate %s from prediction corpus: %s", inchi, e.getMessage());
        continue;
      }
      if (sar.test(Arrays.asList(substrate))) {
        hits++;
      } else {
        misses++;
      }
    }

    return new Double(hits)/new Double(hits+misses);
  }

}
