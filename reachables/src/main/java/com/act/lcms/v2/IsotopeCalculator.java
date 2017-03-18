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
