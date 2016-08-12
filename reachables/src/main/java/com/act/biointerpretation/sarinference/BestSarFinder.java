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
 * Test a given prediction against all Sars in a corpus, and return the highest-scored matching Sar. This assumes that
 * the SarTreeNodes used have all already been scored in some way.  This class then checks, for any given
 * prediction, which Sars in the SarTreeNodeList match that prediction, and returns the one that has the highest
 * confidence, so that we can associate that confident SAR with the prediction.  i.e., a prediction that matches a
 * SAR which has an 80% hit rate should be considered a much more likely prediction than one which only matches
 * SARs with 10% hit rates- roughly, we'd expect the first one to be correct with probability .8, and the second to
 * be correct with only probability .1.
 */
public class BestSarFinder implements Function<L2Prediction, Optional<SarTreeNode>> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BestSarFinder.class);

  SarTreeNodeList sarTreeNodes;

  public BestSarFinder(SarTreeNodeList sarTreeNodes) {
    this.sarTreeNodes = sarTreeNodes;
  }

  /**
   * Find the highest scored Sar in the SarTreeNodeList that matches the given prediction
   *
   * @param prediction The prediction to score.
   * @return The highest scored matching Sar, or empty if no Sar matches.
   */
  public Optional<SarTreeNode> apply(L2Prediction prediction) {
    if (prediction.getSubstrates().size() != 1) {
      LOGGER.error("BestSarFinder only works on single substrate predictions.");
      return Optional.empty();
    }

    // Import the substrate into chemaxon
    Molecule substrate;
    try {
      substrate = MolImporter.importMol(prediction.getSubstrateInchis().get(0), "inchi");
    } catch (MolFormatException e) {
      LOGGER.error("Couldn't import molecule %s: %s", prediction.getSubstrateInchis().get(0), e.getMessage());
      return Optional.empty();
    }

    // Iterate over the SarTreeNodes.  For each one that matches the substrate, get its score.  Return the highest
    // scored matching Sar as the "best sar" for this prediction.
    Double bestScore = 0D;
    Optional<SarTreeNode> bestSarTreeNode = Optional.empty();
    for (SarTreeNode scoredSar : sarTreeNodes.getSarTreeNodes()) {
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
