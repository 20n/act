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

import java.util.List;

/**
 * Interface representing supporting evidence for the presence of a Metabolite.
 */
public interface MoleculeSupport {

  /**
   * Get the supported Metabolite
   */
  Metabolite getMetabolite();

  /**
   * Get the list of Ions supporting the presence of the Metabolite
   */
  List<Ion> getSupportedIons();

  /**
   * Return detected peaks supporting the presence of a particular ion
   * @param ion query Ion
   * @return a list of detected peaks, supporting the presence of that ion
   */
  List<DetectedPeak> getDetectedPeaks(Ion ion);
}
