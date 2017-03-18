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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LcmsIonCalculator implements IonCalculator {


  List<Ion> getIonsFromMass(Double mass, Predicate<MS1.MetlinIonMass> ionFilter) {
    List<Ion> ions = Arrays.asList(MS1.ionDeltas)
        .stream()
        .filter(ionFilter)
        .map(ionDelta -> new LcmsIon(MS1.computeIonMz(mass, ionDelta), ionDelta))
        .collect(Collectors.toList());
    return ions;
  }

  public List<Ion> getIons(Isotope isotope) {
    return getIonsFromMass(isotope.getIsotopicMass(), x -> true);
  }

  public List<Ion> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter) {
    return getIonsFromMass(isotope.getIsotopicMass(), ionFilter);
  }

  public List<Ion> getSelectedIons(Isotope isotope, Set<String> ionNames, MS1.IonMode mode) {
    return getIonsFromMass(isotope.getIsotopicMass(), x -> ionNames.contains(x.getName()) && x.getMode() == mode);
  }

  public List<Ion> getIons(Metabolite metabolite) {
    return getIonsFromMass(metabolite.getMonoIsotopicMass(), x -> true);
  }

  public List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter) {
    return getIonsFromMass(metabolite.getMonoIsotopicMass(), ionFilter);
  }

  public List<Ion> getSelectedIons(Metabolite metabolite, Set<String> ionNames, MS1.IonMode mode) {
    return getIonsFromMass(metabolite.getMonoIsotopicMass(), x -> ionNames.contains(x.getName()) && x.getMode() == mode);
  }
}
