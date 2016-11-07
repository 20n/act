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
