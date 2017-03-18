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
 * An interface representing a chemical element such as C, H or O
 */
public interface Element {

  /**
   * Get the symbol of the element (for example, "C" for carbon)
   */
  String getSymbol();

  /**
   * Get the atomic number (# protons) of the element (for example, 12 for carbon)
   */
  Integer getAtomicNumber();

  /**
   * Get the valency of the element (for example, 4 for carbon)
   * The valency represents the combining power of the element
   */
  Integer getValency();

  /**
   * Get the mass of the most frequent natural isotope
   */
  Double getMass();

  /**
   * Get the isotopes of the element
   */
  List<ElementIsotope> getElementIsotopes();
}
