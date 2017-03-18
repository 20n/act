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


import chemaxon.common.util.Pair;
import chemaxon.marvin.calculations.ElementalAnalyserPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LcmsIsotopeCalculator implements IsotopeCalculator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LcmsIsotopeCalculator.class);

  private ElementalAnalyserPlugin analyser;

  public LcmsIsotopeCalculator() {
    analyser = new ElementalAnalyserPlugin();
  }

  public List<Isotope> getIsotopes(Metabolite metabolite) {
    // If possible, extract a ChemicalFormula from the Metabolite
    ChemicalFormula formula;
    if (metabolite.getFormula().isPresent()) {
      formula = metabolite.getFormula().get();
    } else if (metabolite.getStructure().isPresent()) {
      MolecularStructure structure = metabolite.getStructure().get();
      formula = structure.getChemicalFormula();
    } else {
      LOGGER.error("No structure or formula was found for metabolite (mass: %f.3). Skipping",
          metabolite.getMonoIsotopicMass());
      return new ArrayList<>();
    }
    analyser.setMolecule(formula.toString());
    List<Pair<BigDecimal, BigDecimal>> isotopicDistribution = analyser.getIsotopeDistribution();
    return isotopicDistribution
        .stream()
        .map(isotope -> new LcmsIsotope(metabolite, isotope.left().doubleValue(), isotope.right().doubleValue()))
        .collect(Collectors.toList());
  }

  public List<Isotope> getIsotopes(Metabolite metabolite, Predicate<Isotope> isotopeFilter) {
    return getIsotopes(metabolite).stream().filter(isotopeFilter).collect(Collectors.toList());
  }

  public List<Isotope> getMainIsotopes(Metabolite metabolite, Double minAbundance) {
    return getIsotopes(metabolite, isotope -> isotope.getAbundance() > minAbundance);
  }
}
