package com.act.lcms.v2;


import com.act.lcms.MS1;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LcmsIonCalculator implements IonCalculator {


  private List<Ion> getIonsFromMass(Double mass, Predicate<MS1.MetlinIonMass> ionFilter) {
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

  public List<Ion> getIons(Metabolite metabolite) {
    return getIonsFromMass(metabolite.getMonoIsotopicMass(), x -> true);
  }

  public List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter) {
    return getIonsFromMass(metabolite.getMonoIsotopicMass(), ionFilter);
  }
}
