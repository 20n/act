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


import com.act.lcms.MS1;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public interface IonCalculator {

  /**
   * Return all possible ions for a given isotope
   * @param isotope input Isotope
   * @return a list of Ion objects
   */
  List<Ion> getIons(Isotope isotope);

  /**
   * Return ions meeting the ionFilter criterion
   * @param isotope input Isotope
   * @param ionFilter Predicate on possible ions
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter);

  /**
   * Return ions for an isotope given input ion names and mode
   * @param isotope input Isotope
   * @param ions set of ion names
   * @param mode input ion mode
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Isotope isotope, Set<String> ions, MS1.IonMode mode);

  /**
   * Return all possible ions for a given metabolite
   * @param metabolite input Metabolite
   * @return a list of Ion objects
   */
  List<Ion> getIons(Metabolite metabolite);

  /**
   * Return ions meeting the ionFilter criterion
   * @param metabolite input Metabolite
   * @param ionFilter Predicate on possible ions
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter);

  /**
   * Return ions for an metabolite given input ion names and mode
   * @param metabolite input Metabolite
   * @param ions set of ion names
   * @param mode input ion mode
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Metabolite metabolite, Set<String> ions, MS1.IonMode mode);
}
