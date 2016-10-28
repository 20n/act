package com.act.lcms.v2;

import java.util.List;

import java.util.function.Predicate;

/**
 * Interface for computing isotope distributions
 */
public interface IsotopeCalculator {

  List<Isotope> getIsotopes(Metabolite metabolite);

  List<Isotope> getMainIsotopes(Metabolite metabolite, Predicate<Isotope> IsotopeFilter);

}
