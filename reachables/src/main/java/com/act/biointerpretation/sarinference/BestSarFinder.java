package com.act.biointerpretation.sarinference;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * Test a given prediction against all Sars in a corpus, and return the highest-scored matching Sar.
 */
public class BestSarFinder implements Function<L2Prediction, Optional<SarTreeNode>> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BestSarFinder.class);

  SarTreeNodeList sarCorpus;

  public BestSarFinder(SarTreeNodeList sarCorpus) {
    this.sarCorpus = sarCorpus;
  }

  public Optional<SarTreeNode> apply(L2Prediction prediction) {
    Molecule substrate;
    try {
      substrate = MolImporter.importMol(prediction.getSubstrateInchis().get(0), "inchi");
    } catch (MolFormatException e) {
      LOGGER.error("Couldn't import molecule %s: %s", prediction.getSubstrateInchis().get(0), e.getMessage());
      return Optional.empty();
    }

    Double bestScore = 0D;
    Optional<SarTreeNode> bestSarTreeNode = Optional.empty();
    for (SarTreeNode scoredSar : sarCorpus.getSarTreeNodes()) {
      Sar sar = scoredSar.getSar();

      if (sar.test(Arrays.asList(substrate))) {
        Double sarScore = scoredSar.getPercentageHits();
        if (sarScore > bestScore) {
          bestScore = sarScore;
          bestSarTreeNode = Optional.of(scoredSar);
        }
      }
    }

    return bestSarTreeNode;
  }
}
