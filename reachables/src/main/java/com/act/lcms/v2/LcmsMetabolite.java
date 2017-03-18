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

import java.util.Optional;

public class LcmsMetabolite implements Metabolite {

  private MolecularStructure structure;
  private ChemicalFormula formula;
  private Double monoisotopicMass;

  public LcmsMetabolite(Double monoisotopicMass) {
    this(null, null, monoisotopicMass);
  }

  public LcmsMetabolite(ChemicalFormula formula) {
    this(null, formula, formula.getMonoIsotopicMass());
  }

  public LcmsMetabolite(MolecularStructure structure) {
    this(structure, structure.getChemicalFormula(), structure.getMonoIsotopicMass());
  }

  private LcmsMetabolite(MolecularStructure structure, ChemicalFormula formula, Double monoisotopicMass) {
    this.structure = structure;
    this.formula = formula;
    this.monoisotopicMass = monoisotopicMass;
  }

  public Optional<MolecularStructure> getStructure() {
    return Optional.ofNullable(this.structure);
  }

  public Optional<ChemicalFormula> getFormula() {
    return Optional.ofNullable(this.formula);
  }

  public Double getMonoIsotopicMass() {
    return this.monoisotopicMass;
  }

}
