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

package com.act.lcms.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LcmsPeakSpectrum implements PeakSpectrum {

  List<DetectedPeak> peaks;

  public LcmsPeakSpectrum(List<DetectedPeak> peaks) {
    this.peaks = peaks;
  }

  public LcmsPeakSpectrum(PeakSpectrum spectrum) {
    this(spectrum.getAllPeaks());
  }

  public LcmsPeakSpectrum() {
    this(new ArrayList<>());
  }

  public void addPeak(DetectedPeak peak) {
    peaks.add(peak);
  }

  @Override
  public List<DetectedPeak> getAllPeaks() {
    return peaks;
  }

  @Override
  public List<DetectedPeak> getPeaks(Predicate<DetectedPeak> filter) {
    return peaks.stream().filter(filter).collect(Collectors.toList());
  }

  @Override
  public List<DetectedPeak> getPeaksByMZ(Double mz, Double confidenceLevel) {
    return getPeaks(peak -> peak.matchesMz(mz, confidenceLevel));
  }

  @Override
  public List<DetectedPeak> getPeaksByTime(Double time, Double timeTolerance) {
    return getPeaks(peak -> Math.abs(peak.getRetentionTime() - time) <= timeTolerance);
  }

  @Override
  public List<DetectedPeak> getPeaksByMzTime(Double time, Double mz, Double confidenceLevel) {
    return getPeaks(peak -> peak.matchesMzTime(mz, time, confidenceLevel));
  }

  @Override
  public List<DetectedPeak> getNeighborhoodPeaks(DetectedPeak targetPeak, Double massTolerance, Double timeTolerance) {
    return getNeighborhoodPeaks(targetPeak.getMz(), massTolerance, targetPeak.getRetentionTime(), timeTolerance);
  }

  @Override
  public List<DetectedPeak> getNeighborhoodPeaks(Double mass, Double massTolerance, Double time, Double timeTolerance) {
    return getPeaks(peak -> Math.abs(peak.getRetentionTime() - time) < timeTolerance &&
        Math.abs(peak.getMz() - mass) < massTolerance);
  }

  @Override
  public Map<String, List<DetectedPeak>> getPeaksByScanFile() {
    return peaks.stream().collect(Collectors.groupingBy(DetectedPeak::getSourceScanFileId));
  }

  @Override
  public List<DetectedPeak> getPeaks(String scanFileId) {
    return getPeaks(peak -> peak.getSourceScanFileId().equals(scanFileId));
  }
}
