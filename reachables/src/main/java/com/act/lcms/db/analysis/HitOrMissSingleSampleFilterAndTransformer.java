/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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

    // If the intensity, snr and time pass the thresholds set AND the ion of the peak molecule is within the set of
    // ions we want extracted, we keep the molecule. Else, we throw it away.
    if (intensity > minIntensityThreshold && snr > minSnrThreshold && time > minTimeThreshold &&
        (ions.size() == 0 || ions.contains(ion))) {
      return Pair.of(molecule, DO_NOT_THROW_OUT_MOLECULE);
    } else {
      return Pair.of(molecule, THROW_OUT_MOLECULE);
    }
  }
}
