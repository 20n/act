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
