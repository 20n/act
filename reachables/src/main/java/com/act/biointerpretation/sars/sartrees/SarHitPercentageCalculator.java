package com.act.biointerpretation.sars.sartrees;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.function.Consumer;

public class SarHitPercentageCalculator implements Consumer<SarTreeNode> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarHitPercentageCalculator.class);

  private final L2PredictionCorpus positivePredictionCorpus;
  private final L2PredictionCorpus fullPredictionCorpus;

  public SarHitPercentageCalculator(L2PredictionCorpus positivePredictions, L2PredictionCorpus fullPredictionCorpus) {
    this.positivePredictionCorpus = positivePredictions;
    this.fullPredictionCorpus = fullPredictionCorpus;
  }

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
