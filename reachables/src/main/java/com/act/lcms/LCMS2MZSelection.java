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

package com.act.lcms;

public class LCMS2MZSelection {
  private Integer index;
  private Double timeVal;
  private String timeUnit;
  private Integer scan;
  private Double isolationWindowTargetMZ;
  private Double selectedIoMZ;
  private Double collisionEnergy; // Assumed to be in electronvolts.

  // The m/z and intensity values for each MS2 spectrum don't line up (the counts differ), so we ignore them.


  public LCMS2MZSelection(Integer index, Double timeVal, String timeUnit, Integer scan, Double isolationWindowTargetMZ,
                          Double selectedIoMZ, Double collisionEnergy) {
    this.index = index;
    this.timeVal = timeVal;
    this.timeUnit = timeUnit;
    this.scan = scan;
    this.isolationWindowTargetMZ = isolationWindowTargetMZ;
    this.selectedIoMZ = selectedIoMZ;
    this.collisionEnergy = collisionEnergy;
  }

  /**
   * Gets the index of this time point in its input file.
   *
   * Note: This is not always guaranteed to be the index of this time point in the scan, as the input data file may
   * have several kinds of spectra in the same array.
   * @return The index of this spectrum in the input file (not the same as the index in the scan!).
   */
  public Integer getIndex() {
    return index;
  }

  /**
   * Get the time value when this scan was done.  It is uncertain whether this represents the start or end of the scan,
   * but that is expected to be consistent for all spectra sourced from the same file.
   * @return A numeric representation of the time of this scan, expressed in units available from
   * {@link #getTimeUnit()}.
   */
  public Double getTimeVal() {
    return timeVal;
  }

  /**
   * Gets the units in which the time value is expressed.  This is assumed to be consistent for all spectra from a
   * particular data file.
   * @return A string representing the units in which the time value is expressed.
   */
  public String getTimeUnit() {
    return timeUnit;
  }

  /**
   * An integer representing the index/ordinal of this time point in the scan of which it was part.  This may be the
   * same as {@link #getIndex()} if the input file contains only standard mass/charge+intensity data; if there are
   * other kinds of scan data avilable, this will represent the offset of this time point in its respective scan type.
   *
   * Note that mzXML files will usually have this defined and distinct from {@link #getIndex()}, whereas those values
   * will be the same in NetCDF files.
   * @return An integer representing this time points offset within its particular scan.
   */
  public Integer getScan() {
    return scan;
  }

  /**
   * Gets the "isolation window" selected for the MS2 scan.  This is based on the {@link #getSelectedIonMZ()} value,
   * but tends to have lower precision.
   * @return The isolation window target mass/charge for this MS2 scan.
   */
  public Double getIsolationWindowTargetMZ() {
    return isolationWindowTargetMZ;
  }

  /**
   * Gets the selected ion m/z value for this MS2 scan.  The selection process is carried out by the instrument based on
   * a variety of (potentially hidden) selection criteria, which may be time, context, and input sensitive.  For
   * example, we know that the LCMS instrument can be tuned to ignore a previously selected m/z value in MS2 selection
   * for a certain number of scans to ensure that a consistently high m/z value does not monopolize the MS2 scans for
   * an entire run.
   *
   * Note that a given spectrum may have multiple selection ions (based on the selectedIonList element's count
   * attribute), but we've only ever seen one in practice.  As such, we assume that only one will exist.
   *
   * @return The selected ion mass/charge value for this MS2 scan.
   */
  public Double getSelectedIonMZ() {
    return selectedIoMZ;
  }

  /**
   * The collision energy used for fragmentation in this MS2, assumed to be expressed in electronvolts.
   * @return The collision energy for this scan.
   */
  public Double getCollisionEnergy() {
    return collisionEnergy;
  }
}
