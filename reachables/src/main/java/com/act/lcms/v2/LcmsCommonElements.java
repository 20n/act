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
 * Enumerates some common elements, with their valency
 */

public enum LcmsCommonElements {
  CARBON("C", 4),
  HYDROGEN("H", 1),
  OXYGEN("O", 2),
  NITROGEN("N", 4),
  PHOSPHORUS("P", 2),
  SULFUR("S", 6),
  IODINE("I", 1),
  FLUORINE("F", 1),
  CHLORINE("Cl", 1),
  BROMINE("Br", 1),
  ;

  private LcmsElement element;

  LcmsCommonElements(String symbol, Integer valency) {
    element = new LcmsElement(symbol, valency);
  }

  public LcmsElement getElement() {
    return this.element;
  }
}
