package com.act.biointerpretation.sarinference;

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * Calculates a SARs hit percentage score by seeing which of the leaves of its subtree are LCMS hits,
 * and which are LCMS misses. This is not a perfect scoring across an entire prediction corpus; it is essentially
 * a much faster, heuristic approach to simulating what SarHitPercentageCalculator does.  The key assumption here is
 * that we don't have to look outside a given SAR's subtree when we're trying to assign it a score, since there is
 * probably almost nothing outside its subtree (in the clustering hierarchy) which will match it anyway.  However,
 * as LibMCS clustering is not always perfect, there may in fact be molecules that match a SAR, which do not get
 * clustered under that SAR's subtree.  In such cases, this heuristic score diverges from the full score calculated
 * across the entire corpus.
 */
public class SarTreeBasedCalculator implements Consumer<SarTreeNode> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarTreeBasedCalculator.class);

  private final SarTree sarTree;
  private final L2PredictionCorpus predictionCorpus;
  private final IonAnalysisInterchangeModel lcmsResults;


  public SarTreeBasedCalculator(SarTree sarTree, L2PredictionCorpus corpus, IonAnalysisInterchangeModel lcmsResults) {
    this.sarTree = sarTree;
    this.predictionCorpus = corpus;
    this.lcmsResults = lcmsResults;
  }

  /**
   * Set the hits and missess on the given sarTreeNode based on which of the leaves in its subtree are hits,
   * and which are misses.
   *
   * @param sarTreeNode The node to score.
   */
  @Override
  public void accept(SarTreeNode sarTreeNode) {
    int hits = 0;
    int misses = 0;

    for (SarTreeNode node : sarTree.traverseSubtree(sarTreeNode)) {
      // Only calculate on leaves
      if (sarTree.getChildren(node).isEmpty()) {
        switch (getLcmsDataForNode(node)) {
          case HIT:
            hits++;
            break;
          case MISS:
            misses++;
            break;
        }
      }
    }

    sarTreeNode.setNumberHits(hits);
    sarTreeNode.setNumberMisses(misses);
  }

  /**
   * Calculates whether a given SarTreeNode is a hit or not. If any of that node's predictions have positive
   * products, we consider it a hit.
   *
   * @param node The SarTreeNode.
   * @return True if at least one prediction Id of the node is an LCMS hit.
   */
  public IonAnalysisInterchangeModel.LCMS_RESULT getLcmsDataForNode(SarTreeNode node) {
    for (Integer predictionId : node.getPredictionIds()) {
      L2Prediction prediction = predictionCorpus.getPredictionFromId(predictionId);
      IonAnalysisInterchangeModel.LCMS_RESULT lcmsData = lcmsResults.getLcmsDataForPrediction(prediction);

      if (lcmsData == IonAnalysisInterchangeModel.LCMS_RESULT.HIT) {
        return IonAnalysisInterchangeModel.LCMS_RESULT.HIT;
      }
      if (lcmsData == IonAnalysisInterchangeModel.LCMS_RESULT.NO_DATA) {
        // Log this error but don't throw exception- some molecules aren't properly processed by LCMS and have no data
        LOGGER.error("No LCMS data found for prediction ID " + predictionId +
            " on inchi " + prediction.getProductInchis().toString());
      }
    }
    return IonAnalysisInterchangeModel.LCMS_RESULT.MISS;
  }
}
