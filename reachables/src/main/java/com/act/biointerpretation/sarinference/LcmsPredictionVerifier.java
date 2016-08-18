package com.act.biointerpretation.sarinference;

import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;

import java.util.List;
import java.util.function.Function;

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
      if (lcmsResults.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.HIT)) {
        return SarTreeNode.LCMS_RESULT.NO_DATA;
      }
      if (lcmsResults.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.HIT)) {
        return SarTreeNode.LCMS_RESULT.HIT;
      }
    }
    return SarTreeNode.LCMS_RESULT.MISS;
  }

}
