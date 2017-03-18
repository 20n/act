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

package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.v2.ChemicalFormula;
import com.act.lcms.v2.MolecularStructure;
import org.apache.commons.lang.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InchiStructure implements MolecularStructure {

  private static final Logger LOGGER = LogManager.getFormatterLogger(InchiStructure.class);

  private final String inchi;

  public InchiStructure(String inchi) {
    this.inchi = inchi;
  }

  @Override
  public String getInchi() {
    return inchi;
  }

  @Override
  public Double getMonoIsotopicMass() {
    try {
      return MassCalculator.calculateMass(inchi);
    } catch (Exception e) {
      return -1.0;
    }
  }

  @Override
  public ChemicalFormula getChemicalFormula() {
    throw new NotImplementedException();
  }
}

