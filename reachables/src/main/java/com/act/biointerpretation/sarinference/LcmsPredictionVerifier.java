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

  @Override
  public SarTreeNode.LCMS_RESULT apply(Integer predictionId) {
    List<String> productInchis = predictionCorpus.getPredictionFromId(predictionId).getProductInchis();
    for (String product : productInchis) {
      // If any of the results have no data, return NO_DATA. Such results shouldn't happen for now, so the caller will
      // likely throw an exception if this happens.
      if (lcmsResults.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.HIT)) {
        return SarTreeNode.LCMS_RESULT.NO_DATA;
      }
      // Otherwise, if a hit is found among the prediction's products, return it as a hit
      if (lcmsResults.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.HIT)) {
        return SarTreeNode.LCMS_RESULT.HIT;
      }
    }
    // If every prediction is a MISS, return MISS.
    return SarTreeNode.LCMS_RESULT.MISS;
  }

}
