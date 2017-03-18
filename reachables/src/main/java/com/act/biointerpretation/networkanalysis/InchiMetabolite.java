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

import com.act.lcms.v2.ChemicalFormula;
import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.MolecularStructure;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * Represents a metabolite in the metabolite network.
 * For now this class only stores an inchi, but in the future it can represent multiple levels of abstraction: a
 * structure in any format, a chemical formula, or only a mass.
 * TODO: Implement full serialization on LcmsMetabolite, and replace every use of InchiMetabolite with LcmsMetabolite.
 */
public class InchiMetabolite implements Metabolite {

  @JsonProperty("inchi")
  private String inchi;

  private MolecularStructure structure;

  @JsonCreator
  public InchiMetabolite(@JsonProperty("inchi") String inchi) {
    this.inchi = inchi;
    this.structure = new InchiStructure(inchi);
  }

  @JsonIgnore
  public String getInchi() {
    return inchi;
  }

  @JsonIgnore
  @Override
  public Optional<MolecularStructure> getStructure() {
    return Optional.of(structure);
  }

  @JsonIgnore
  @Override
  public Optional<ChemicalFormula> getFormula() {
    return Optional.empty();
  }

  @JsonIgnore
  @Override
  public Double getMonoIsotopicMass() {
    return structure.getMonoIsotopicMass();
  }
}
