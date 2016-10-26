package com.act.lcms.v2;

import java.util.List;

import java.util.function.Predicate;

/**
 * Interface for computing isotope distributions
 */
public interface IsotopeCalculator<T extends Isotope, M extends Metabolite> {

  List<T> getIsotopes(M metabolite);

  List<T> getMainIsotopes(M metabolite, Predicate<T> IsotopeFilter);

}
