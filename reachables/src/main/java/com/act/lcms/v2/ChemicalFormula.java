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


import java.util.Map;
import java.util.Optional;

/**
 * An interface representing a chemical formula.
 * A Chemical Formula is here represented by its elements counts.
 * For example, CH4 is represented as Map(C -> 1, H -> 4) where C and H are the carbon and hydrogen elements.
 * This API provides also mass retrieval, test of match with a structure and a optional name retrieval.
 */

public interface ChemicalFormula {
  /**
   * Get the formula's element counts
   */
  Map<Element, Integer> getElementCounts();

  /**
   * Get the number of a given element in the formula
   */
  Integer getElementCount(Element element);

  /**
   * Retrieve the mono-isotopic mass for the formula
   */
  Double getMonoIsotopicMass();

  /**
   * Retrieve an optional formula name
   */
  Optional<String> getName();

  /**
   * Check for equality with another Chemicalformula
   * @param chemicalFormula input formula
   * @return A boolean, indicating whether or not the formulae match
   */
  @Override
  boolean equals(Object chemicalFormula);

  /**
   * Converts a formula to its string representation using the Hill Order system
   * Hill Order: C's first, then H's, then others in alphabetical order.
   * @return string representation of the formula
   */
  @Override
  String toString();

  /**
   * Parses a formula from its string representation
   * @param formula string representation of the formula
   */
  void fromString(String formula);
}
