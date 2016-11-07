package com.act.lcms.v2;

import java.util.List;
import java.util.function.Predicate;

/**
 * Interface representing a Metabolite corpus.
 */
public interface MetaboliteCorpus {

  /**
   * Fetches metabolites within a certain monoisotopic mass window
   * @param minMass minimum mono-isotopic mass (in Da)
   * @param maxMass maximum mono-isotopic mass (in Da)
   * @return a list of metabolites satisfying the constraint
   */
  List<Metabolite> fetchMetabolitesInMassWindow(Double minMass, Double maxMass);

  /**
   * More general API to fetch metabolites
   * @param filter a predicate for metabolites
   * @return a list of filtered metabolites
   */
  List<Metabolite> getMetabolites(Predicate<Metabolite> filter);

  /**
   * Get all metabolites
   * @return a list of all metabolites in the corpus
   */
  List<Metabolite> getAllMetabolites();
}
