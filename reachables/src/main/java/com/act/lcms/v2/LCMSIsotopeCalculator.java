package com.act.lcms.v2;


import chemaxon.common.util.Pair;
import chemaxon.marvin.calculations.ElementalAnalyserPlugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class LcmsIsotopeCalculator implements IsotopeCalculator {

  private ElementalAnalyserPlugin analyser;

  public LcmsIsotopeCalculator() {
    analyser = new ElementalAnalyserPlugin();
  }

  public List<Isotope> getIsotopes(Metabolite metabolite) {
    ChemicalFormula formula;
    if (metabolite.getStructure().isPresent()) {
      MolecularStructure structure = metabolite.getStructure().get();
      formula = structure.getChemicalFormula();
    } else if (metabolite.getFormula().isPresent()) {
      formula = metabolite.getFormula().get();
    } else {
      throw new RuntimeException("No structure or formula present");
    }
    analyser.setMolecule(formula.toString());
    List<Pair<BigDecimal, BigDecimal>> isotopicDistribution = analyser.getIsotopeDistribution();
    return isotopicDistribution
        .stream()
        .map(isotope -> new LcmsIsotope(metabolite, isotope.left().doubleValue(), isotope.right().doubleValue()))
        .collect(Collectors.toList());
  }

  public List<Isotope> getMainIsotopes(Metabolite metabolite, Predicate<Isotope> isotopeFilter) {
    return getIsotopes(metabolite).stream().filter(isotopeFilter).collect(Collectors.toList());
  }
}
