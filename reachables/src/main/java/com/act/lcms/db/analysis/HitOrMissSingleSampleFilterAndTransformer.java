package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

public class HitOrMissSingleSampleFilterAndTransformer extends HitOrMissFilterAndTransformer<IonAnalysisInterchangeModel.HitOrMiss> {

  private Double minIntensityThreshold;
  private Double minSnrThreshold;
  private Double minTimeThreshold;
  private Set<String> ions;

  public HitOrMissSingleSampleFilterAndTransformer(Double minIntensityThreshold, Double minSnrThreshold, Double minTimeThreshold,
                                                   Set<String> ions) {
    this.minIntensityThreshold = minIntensityThreshold;
    this.minSnrThreshold = minSnrThreshold;
    this.minTimeThreshold = minTimeThreshold;
    this.ions = ions;
  }

  /**
   * This function takes in a HitOrMiss molecule and filters it based on metric thresholds.
   * @param replicate The molecule whose metric stats are being compared to the preset thresholds.
   * @return A pair of transformed HitOrMiss molecule and whether to save the result in the final model.
   */
  public Pair<IonAnalysisInterchangeModel.HitOrMiss, Boolean> apply(IonAnalysisInterchangeModel.HitOrMiss replicate) {
    Double intensity = replicate.getIntensity();
    Double snr = replicate.getSnr();
    Double time = replicate.getTime();
    String ion = replicate.getIon();

    IonAnalysisInterchangeModel.HitOrMiss molecule = new IonAnalysisInterchangeModel.HitOrMiss(
        replicate.getInchi(), ion, snr, time, intensity, replicate.getPlot());

    molecule.setAverageIntensity(replicate.getAverageIntensity());
    molecule.setMaxIntensity(replicate.getMaxIntensity());
    molecule.setMinIntensity(replicate.getMinIntensity());
    molecule.setStdIntensity(replicate.getStdIntensity());
    molecule.setMaxCrossSample(replicate.getMaxCrossSample());
    molecule.setMinCrossSample(replicate.getMinCrossSample());

    if (minSnrThreshold < 1) {
      // If the intensity, snr and time pass the thresholds set AND the ion of the peak molecule is within the set of
      // ions we want extracted, we keep the molecule. Else, we throw it away.
      if (intensity > minIntensityThreshold && replicate.getMaxCrossSample() < minSnrThreshold && time > minTimeThreshold &&
          (ions.size() == 0 || ions.contains(ion))) {
        return Pair.of(molecule, DO_NOT_THROW_OUT_MOLECULE);
      } else {
        return Pair.of(molecule, THROW_OUT_MOLECULE);
      }
    } else {
      // If the intensity, snr and time pass the thresholds set AND the ion of the peak molecule is within the set of
      // ions we want extracted, we keep the molecule. Else, we throw it away.
      if (intensity > minIntensityThreshold && replicate.getMinCrossSample() > minSnrThreshold && time > minTimeThreshold &&
          (ions.size() == 0 || ions.contains(ion))) {
        return Pair.of(molecule, DO_NOT_THROW_OUT_MOLECULE);
      } else {
        return Pair.of(molecule, THROW_OUT_MOLECULE);
      }
    }
  }
}
