package com.act.lcms.v2;

import java.util.List;

import java.util.function.Predicate;

/**
 * Interface for computing isotope distributions
 */
public interface IsotopeCalculator {

  /**
   * Get all isotopes for a given metabolite
   * @param metabolite input Metabolite
   * @return a list of Isotope objects
   */
  List<Isotope> getIsotopes(Metabolite metabolite);

  /**
   * Get all isotopes for a given metabolite, meeting a specific criterion
   * @param metabolite input Metabolite
   * @param IsotopeFilter filter on Isotopes (for example specifying the min abundance)
   * @return a list of Isotope objects
   */
  List<Isotope> getIsotopes(Metabolite metabolite, Predicate<Isotope> IsotopeFilter);

  /**
   * Get isotopes with a minimum abundance
   * @param metabolite input Metabolite
   * @param minAbundance abundance threshold. Isotopes being less abundant than the threshold will be ignored
   * @return a lsit of Isotope objects
   */
  List<Isotope> getMainIsotopes(Metabolite metabolite, Double minAbundance);
}
