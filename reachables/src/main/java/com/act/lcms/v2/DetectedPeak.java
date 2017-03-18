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
 * An interface for representing detected LCMS peaks
 */
public interface DetectedPeak {
  /**
   * Return the estimated peak m/z value
   */
  Double getMz();

  /**
   * Return the estimated peak retention time (in sec)
   */
  Double getRetentionTime();

  /**
   * Return the estimated peak intensity
   */
  Double getIntensity();

  /**
   * Return the id of the source scan file
   */
  String getSourceScanFileId();

  /**
   * Return the peak confidence (likelihood that the detected peak is actually a real peak)
   * The value would typically reflect the confidence of the peak detection algorithm that it found a peak at
   * that location
   */
  Double getConfidence();

  /**
   * Test whether an input m/z value matches with a detected peak at a certain confidence level.
   * @param mz input m/z value
   * @param confidenceLevel input confidence level, unrelated to the above peak confidence
   *                        given the peak true value distribution, this value indicates how conservative
   *                        the matching will be
   * @return result of the test, true if the m/z value matches with the peak
   */
  Boolean matchesMz(Double mz, Double confidenceLevel);

  /**
   * Test whether an input m/z value and retention time matches with a detected peak at a certain confidence level.
   * @param mz input m/z value
   * @param retentionTime input retention time
   * @param confidenceLevel input confidence level, unrelated to the above peak confidence
   *                        given the peak true value distribution, this value indicates how conservative
   *                        the matching will be
   * @return result of the test, true if the m/z value matches with the peak
   */
  Boolean matchesMzTime(Double mz, Double retentionTime, Double confidenceLevel);
}
