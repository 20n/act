package com.act.lcms;

import com.act.lcms.db.analysis.HitOrMissReplicateFilterAndTransformer;
import com.act.lcms.db.analysis.HitOrMissSingleSampleFilterAndTransformer;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class IonAnalysisInterchangeModelTest {

  @Test
  public void testFilterAndOperateOnMoleculesFromMultipleReplicateResultFilesPerformsThresholdingAnalysisOnSingleModel() throws Exception {
    IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
    model.loadResultsFromFile(new File(getClass().getResource("sampleIonAnalysisInterchangeModel.json").toURI()));

    List<IonAnalysisInterchangeModel> models = new ArrayList<>();
    models.add(model);

    Set<String> ions = new HashSet<>();
    ions.add("M+H");
    ions.add("M+Na");
    ions.add("M+H-H2O");

    HitOrMissSingleSampleFilterAndTransformer hitOrMissSingleSampleTransformer =
        new HitOrMissSingleSampleFilterAndTransformer(10000.0, 1000.0, 15.0, ions);

    IonAnalysisInterchangeModel outputModel =
        IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(models, hitOrMissSingleSampleTransformer);

    int numHits = 0;
    for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : outputModel.getResults()) {
      numHits += resultForMZ.getMolecules().size();
    }

    assertEquals("Expected and actual number of hits after thresholding should be the same", 13, numHits);
  }

  @Test
  public void testFilterAndOperateOnMoleculesFromMultipleReplicateResultFilesPerformsMinAnalysisForMulitpleModels() throws Exception {

    /**
     * In this test, we take in two models, which are almost identical except one hit exists in one model while the other
     * does not have that. So, during the min analysis step, we will select the non-hit molecule since it will be the
     * min of the two statistics and when we threshold, we should be throwing that molecule out, hence the expected
     * number of hits will be one short.
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
        IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(models, transformer);

    HitOrMissSingleSampleFilterAndTransformer hitOrMissSingleSampleTransformer =
        new HitOrMissSingleSampleFilterAndTransformer(10000.0, 1000.0, 15.0, ions);

    List<IonAnalysisInterchangeModel> thresholdingModels = new ArrayList<>();
    thresholdingModels.add(minAnalysisOutput);

    IonAnalysisInterchangeModel thresholdingOutputModel =
        IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(thresholdingModels, hitOrMissSingleSampleTransformer);

    int numHits = 0;
    for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : thresholdingOutputModel.getResults()) {
      numHits += resultForMZ.getMolecules().size();
    }

    assertEquals("Expected and actual number of hits after min analysis and thresholding should be the same", 12, numHits);
  }
}
