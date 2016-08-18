package com.act.biointerpretation.sarinference;

import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;

import java.util.List;
import java.util.function.Function;

/**
 * Simple class that serves as an LCMS oracle. Uses a prediction corpus and LCMS results to tell whether a given
 * prediction ID corresponds to an LCMS positive or negative.
 */
public class LcmsPredictionVerifier implements Function<Integer, SarTreeNode.LCMS_RESULT> {

  private final L2PredictionCorpus predictionCorpus;
  private final IonAnalysisInterchangeModel lcmsResults;

  public LcmsPredictionVerifier(L2PredictionCorpus predictionCorpus, IonAnalysisInterchangeModel lcmsResults) {
    this.predictionCorpus = predictionCorpus;
    this.lcmsResults = lcmsResults;
  }

  /**
   *   TODO: think through our general approach to multiple substrate reactions when necessary.
   *   We'll need to balance the possibilities of false positives and false negatives- one idea would be to return
   *   a score based on the number of confirmed products of the reaction.
   */

  /**
   * Takes a prediction ID, gets the products from the prediction corpus, and checks them against the LCMS results to
   * produce the prediction's LCMS result.
   *
   * @param predictionId The prediction ID to test.
   * @return The LCMS result.
   */
  @Override
  public SarTreeNode.LCMS_RESULT apply(Integer predictionId) {
    List<String> productInchis = predictionCorpus.getPredictionFromId(predictionId).getProductInchis();
    for (String product : productInchis) {
      // If any of the results have no data, return NO_DATA. Such results shouldn't happen for now, so the caller will
      // likely throw an exception if this happens.
      if (lcmsResults.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.HIT)) {
        return SarTreeNode.LCMS_RESULT.NO_DATA;
      }
      // Otherwise, if a miss is found among the prediction's products, return it as a miss.  This implements an
      // AND among the products of the prediction- all must be present to register as a hit. This is motivated by the
      // fact that our only current multiple-product reaction produces one significant product, and one constant
      // cofactor. We verified that in both urine and saliva, the cofactor is present in our samples, so
      // an OR approach here would return a HIT for every prediction of that RO.
      if (lcmsResults.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.MISS)) {
        return SarTreeNode.LCMS_RESULT.MISS;
      }
    }
    // If every prediction is a HIT, return HIT.
    return SarTreeNode.LCMS_RESULT.HIT;
  }

}
