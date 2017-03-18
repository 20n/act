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


import chemaxon.struc.PeriodicSystem;

import java.util.ArrayList;
import java.util.List;

public class LcmsElement implements Element {

  // Chemaxon's API for PeriodicSystem returns isotopes that have abundance 0.
  // We need to filter these out by setting a minimum abundance level.
  // Abundance ranges from 0-100 with the most abundant isotope having an abundance of 100
  private static final Double MIN_ABUNDANCE = 0.1;

  private String symbol;
  private Integer atomicNumber;
  private Double mass;
  private Integer valency;

  public LcmsElement(String symbol) {
    this.symbol = symbol;
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
    Integer massNumber = PeriodicSystem.getMostFrequentNaturalIsotope(this.atomicNumber);
    this.mass = PeriodicSystem.getMass(this.atomicNumber, massNumber);
  }

  public LcmsElement(Integer atomicNumber) {
    this.atomicNumber = atomicNumber;
    Integer massNumber = PeriodicSystem.getMostFrequentNaturalIsotope(this.atomicNumber);
    this.mass = PeriodicSystem.getMass(this.atomicNumber, massNumber);
  }

  public LcmsElement(String symbol, Integer valency) {
    this.symbol = symbol;
    this.atomicNumber = PeriodicSystem.findAtomicNumber(this.symbol);
    Integer massNumber = PeriodicSystem.getMostFrequentNaturalIsotope(this.atomicNumber);
    this.mass = PeriodicSystem.getMass(this.atomicNumber, massNumber);
    this.valency = valency;
  }

  @Override
  public String getSymbol() {
    return this.symbol;
  }

  @Override
  public Integer getAtomicNumber() {
    return this.atomicNumber;
  }

  @Override
  public Double getMass() {
    return this.mass;
  }

  @Override
  public List<ElementIsotope> getElementIsotopes() {
    Integer isotopeCount = PeriodicSystem.getIsotopeCount(atomicNumber);
    List<ElementIsotope> elementIsotopes = new ArrayList<>(isotopeCount);

    for (int i = 0; i < isotopeCount; i++) {
      Integer massNumber = PeriodicSystem.getIsotope(atomicNumber, i);
      Double abundance = PeriodicSystem.getAbundance(atomicNumber, massNumber);
      // Chemaxon returns isotopes having ~0 abundance, so we filter these out to focus on the ones that actually occur
      // in nature. MIN_ABUNDANCE should definitely be < 1 so we don't filter out C-13 which has ~1% natural abundance.
      if (abundance > MIN_ABUNDANCE) {
        elementIsotopes.add(new LcmsElementIsotope(this, massNumber));
      }
    }

    return elementIsotopes;
  }

  @Override
  public Integer getValency() {
    return this.valency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Element that = (Element) o;

    if (!symbol.equals(that.getSymbol())) return false;
    return atomicNumber.equals(that.getAtomicNumber());
  }

  @Override
  public int hashCode() {
    int result = symbol.hashCode();
    result = 31 * result + atomicNumber.hashCode();
    return result;
  }
}
