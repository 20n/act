package com.act.lcms;

import com.act.lcms.db.analysis.HitOrMissReplicateFilterAndTransformer;
import com.act.lcms.db.analysis.HitOrMissSingleSampleFilterAndTransformer;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class IonAnalysisInterchangeModelTest {

  @Test
  public void testFilterFunctionOnSingleModel() throws Exception {
    IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
    model.loadResultsFromFile(new File(getClass().getResource("sampleIonAnalysisInterchangeModel.json").toURI()));

    Set<String> ions = new HashSet<>();
    ions.add("M+H");
    ions.add("M+Na");
    ions.add("M+H-H2O");

    HitOrMissSingleSampleFilterAndTransformer hitOrMissSingleSampleTransformer =
        new HitOrMissSingleSampleFilterAndTransformer(10000.0, 1000.0, 15.0, ions);

    IonAnalysisInterchangeModel outputModel =
        IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromModel(model, hitOrMissSingleSampleTransformer);

    int numHits = 0;
    for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : outputModel.getResults()) {
      numHits += resultForMZ.getMolecules().size();
    }

    assertEquals("Expected and actual number of hits after thresholding should be the same", 13, numHits);
  }

  @Test
  public void testFilterFunctionOnMultipleReplicateModels() throws Exception {

    /**
     * In this test, we take in two models, which are almost identical except one molecule has intensity, snr and time
     * that do not pass the thresholds in one of the models (non-hit) while the other one does pass the threshold (hit).
     * During the min analysis, the non-hit molecule is selected since it's statistics are the minimum for that molecule
     * across all the replicates. So when we do the threshold analysis, we throw that molecule out since it will not
     * pass the thresholds.
     */

    IonAnalysisInterchangeModel model1 = new IonAnalysisInterchangeModel();
    model1.loadResultsFromFile(new File(getClass().getResource("sampleIonAnalysisInterchangeModel.json").toURI()));

    // The derivativeSampleIonAnalysisInterchangeModel model is mostly the same as the sampleIonAnalysisInterchangeModel
    // except one of the hits in sampleIonAnalysisInterchangeModel does not exist in derivativeSampleIonAnalysisInterchangeModel.
    IonAnalysisInterchangeModel model2 = new IonAnalysisInterchangeModel();
    model2.loadResultsFromFile(new File(getClass().getResource("derivativeSampleIonAnalysisInterchangeModel.json").toURI()));

    List<IonAnalysisInterchangeModel> models = new ArrayList<>();
    models.add(model1);
    models.add(model2);

    Set<String> ions = new HashSet<>();
    ions.add("M+H");
    ions.add("M+Na");
    ions.add("M+H-H2O");

    HitOrMissReplicateFilterAndTransformer transformer = new HitOrMissReplicateFilterAndTransformer();

    IonAnalysisInterchangeModel minAnalysisOutput =
        IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateModels(models, transformer);

    HitOrMissSingleSampleFilterAndTransformer hitOrMissSingleSampleTransformer =
        new HitOrMissSingleSampleFilterAndTransformer(10000.0, 1000.0, 15.0, ions);

    IonAnalysisInterchangeModel thresholdingOutputModel =
        IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromModel(minAnalysisOutput, hitOrMissSingleSampleTransformer);

    int numHits = 0;
    for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : thresholdingOutputModel.getResults()) {
      numHits += resultForMZ.getMolecules().size();
    }

    assertEquals("Expected and actual number of hits after min analysis and thresholding should be the same", 12, numHits);
  }
}
