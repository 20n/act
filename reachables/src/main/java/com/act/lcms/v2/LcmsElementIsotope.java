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

public class LcmsElementIsotope implements ElementIsotope {
  private Integer massNumber;
  private Element element;
  private Double abundance;
  private Double isotopicMass;

  public LcmsElementIsotope(Element element, Integer massNumber) {
    this.element = element;
    this.massNumber = massNumber;
    this.abundance = PeriodicSystem.getAbundance(this.element.getAtomicNumber(), this.massNumber);
    this.isotopicMass = PeriodicSystem.getMass(this.element.getAtomicNumber(), this.massNumber);
  }

  @Override
  public Integer getMassNumber() {
    return massNumber;
  }

  @Override
  public Element getElement() {
    return element;
  }

  @Override
  public Double getAbundance() {
    return abundance;
  }

  @Override
  public Double getIsotopicMass() {
    return isotopicMass;
  }
}
