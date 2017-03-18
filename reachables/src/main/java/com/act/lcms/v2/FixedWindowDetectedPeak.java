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

/**
 * Implementation of a fixed window peak.
 * Such a peak matches a m/z value if it falls within the m/z window around the peak.
 */
public class FixedWindowDetectedPeak implements DetectedPeak {

  private String sourceScanFileId;

  // Estimated m/z value for the peak
  private Double mz;

  // Full size of the m/z window for the peak.
  // The true m/z value lies between `mz - mzWindow / 2` and `mz + mzWindow / 2`
  private Double mzWindow;

  // Estimated retention time for the peak
  private Double retentionTime;

  // Full size of the retention time window
  // The true m/z value lies between `rt - retentionTimeWindow / 2` and `rt + retentionTimeWindow / 2`
  private Double retentionTimeWindow;

  // Peak intensity
  private Double intensity;

  // Probability (hence in [0,1]) that the peak is a true peak
  private Double confidence;

  public FixedWindowDetectedPeak(String sourceScanFileId, Double mz, Double mzWindow, Double retentionTime,
                                 Double retentionTimeWindow, Double intensity, Double confidence) {
    this.sourceScanFileId = sourceScanFileId;
    this.mz = mz;
    this.mzWindow = mzWindow;
    this.retentionTime = retentionTime;
    this.retentionTimeWindow = retentionTimeWindow;
    this.intensity = intensity;
    this.confidence = confidence;
  }

  @Override
  public Double getMz() {
    return mz;
  }

  @Override
  public Double getRetentionTime() {
    return retentionTime;
  }

  @Override
  public Double getIntensity() {
    return intensity;
  }

  @Override
  public String getSourceScanFileId() {
    return sourceScanFileId;
  }

  @Override
  public Double getConfidence() {
    return confidence;
  }

  @Override
  public Boolean matchesMz(Double mz, Double confidenceLevel) {
    // Matches if the m/z value is in the m/z window, regardless of the confidenceLevel
    return (this.mz - this.mzWindow / 2 <= mz) && (this.mz + this.mzWindow / 2 >= mz);
  }

  @Override
  public Boolean matchesMzTime(Double mz, Double retentionTime, Double confidenceLevel) {
    // Matches if the m/z value is in the m/z window, regardless of the confidenceLevel
    Boolean matchesMz = (this.mz - this.mzWindow / 2 <= mz) && (this.mz + this.mzWindow / 2 >= mz);
    Boolean matchesTime = (this.retentionTime - this.retentionTimeWindow / 2 <= retentionTime) &&
        (this.retentionTime + this.retentionTimeWindow / 2 >= retentionTime);
    return matchesMz && matchesTime;
  }
}
