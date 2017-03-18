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

import java.util.List;
import java.util.stream.Collectors;

public class HitOrMissReplicateFilterAndTransformer extends HitOrMissFilterAndTransformer<List<IonAnalysisInterchangeModel.HitOrMiss>> {

  public static final Integer TIME_TOLERANCE_IN_SECONDS = 5;
  // The peak statistic could be intensity, SNR or time.
  public static final Double LOWEST_POSSIBLE_VALUE_FOR_PEAK_STATISTIC = 0.0;
  public static String NIL_PLOT = "NIL_PLOT";
  public static final Integer REPRESENTATIVE_INDEX = 0;

  /**
   * This function takes in a list of molecules from multiple replicates over the same time and alignes the peaks across
   * these replicates. If the peaks can be aligned, the function reports the min statistic across those peaks, else it
   * defaults to a low statistic.
   * @param oneOrMoreReplicates
   * @return A pair of transformed HitOrMiss molecule and whether to save the result in the final model.
   */
  public Pair<IonAnalysisInterchangeModel.HitOrMiss, Boolean> apply(List<IonAnalysisInterchangeModel.HitOrMiss> oneOrMoreReplicates) {

    List<Double> intensityValues = oneOrMoreReplicates.stream().map(molecule -> molecule.getIntensity()).collect(Collectors.toList());
    List<Double> snrValues = oneOrMoreReplicates.stream().map(molecule -> molecule.getSnr()).collect(Collectors.toList());
    List<Double> timeValues = oneOrMoreReplicates.stream().map(molecule -> molecule.getTime()).collect(Collectors.toList());

    IonAnalysisInterchangeModel.HitOrMiss result = new IonAnalysisInterchangeModel.HitOrMiss();
    result.setInchi(oneOrMoreReplicates.get(REPRESENTATIVE_INDEX).getInchi());
    result.setIon(oneOrMoreReplicates.get(REPRESENTATIVE_INDEX).getIon());
    result.setPlot(NIL_PLOT);

    // We get the min and max time to calculate how much do the replicates deviate in time for the same signal. If
    // the deviation in the time axis is greater than our tolerance, we know the signal is bad.
    Double minTime = timeValues.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal));
    Double maxTime = timeValues.stream().reduce(Double.MIN_VALUE, (accum, newVal) -> Math.max(accum, newVal));

    if (maxTime - minTime < TIME_TOLERANCE_IN_SECONDS) {
      Double minIntensity = intensityValues.stream().reduce(Double.MAX_VALUE, (accum, newVal) -> Math.min(accum, newVal));

      Integer indexOfMinIntensityReplicate = intensityValues.indexOf(minIntensity);

      // The SNR and Time values will be the copy of the replicate with the lowest intensity value.
      result.setSnr(snrValues.get(indexOfMinIntensityReplicate));
      result.setIntensity(minIntensity);
      result.setTime(timeValues.get(indexOfMinIntensityReplicate));

      return Pair.of(result, DO_NOT_THROW_OUT_MOLECULE);
    } else {
      // TODO: We can just throw out such molecules.
      result.setSnr(LOWEST_POSSIBLE_VALUE_FOR_PEAK_STATISTIC);
      result.setIntensity(LOWEST_POSSIBLE_VALUE_FOR_PEAK_STATISTIC);
      result.setTime(LOWEST_POSSIBLE_VALUE_FOR_PEAK_STATISTIC);

      return Pair.of(result, DO_NOT_THROW_OUT_MOLECULE);
    }
  }
}
