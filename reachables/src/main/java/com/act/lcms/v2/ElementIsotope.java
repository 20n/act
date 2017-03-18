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
 * Representation of an element isotope, such as C-13
 */
public interface ElementIsotope {

  /**
   * Get the mass number (# protons + # neutrons)
   */
  Integer getMassNumber();

  /**
   * Get the isotopic mass
   */
  Double getIsotopicMass();

  /**
   * Get the base element for this atom.
   * In the example of C-13, the base element is Carbon
   */
  Element getElement();

  /**
   * Get the abundance of the isotope (probability in [0, 1])
   */
  Double getAbundance();
}
