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

/**
 * A lightweight class to hold metabolites parsed from an enumerated file.
 * Keeping metabolites as strings allows to parse a corpus of size ~10M in a matter of seconds in a NavigableMap.
 * Conversions to Metabolite after filtering by mass, on a subset of interest.
 * TODO: evaluate performance of a large corpus with direct conversion to Metabolites
 */

public class RawMetabolite {

  private final Double monoIsotopicMass;
  private final String molecule;
  private final String name;

  public RawMetabolite(Double monoIsotopicMass, String molecule, String name) {
    this.monoIsotopicMass = monoIsotopicMass;
    this.molecule = molecule;
    this.name = name;
  }

  public Double getMonoIsotopicMass() {
    return monoIsotopicMass;
  }

  public String getMolecule() {
    return molecule;
  }

  public Optional<String> getName() {
    return Optional.ofNullable(this.name);
  }
}
