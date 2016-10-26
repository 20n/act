package com.act.lcms.v2;


import com.act.lcms.MS1;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LcmsIonCalculator implements IonCalculator<LcmsIon> {

  public List<LcmsIon> getIons(Isotope isotope) {
    List<LcmsIon> ions = Arrays.asList(MS1.ionDeltas)
        .stream()
        .map(m -> new LcmsIon(isotope.getIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }

  public List<LcmsIon> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter) {
    List<MS1.MetlinIonMass> metlinIonMass = Arrays.asList(MS1.ionDeltas)
        .stream()
        .filter(ionFilter)
        .collect(Collectors.toList());
    List<LcmsIon> ions = metlinIonMass
        .stream()
        .map(m -> new LcmsIon(isotope.getIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }

  public List<LcmsIon> getIons(Metabolite metabolite) {
    List<LcmsIon> ions = Arrays.asList(MS1.ionDeltas)
        .stream()
        .map(m -> new LcmsIon(metabolite.getMonoIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }

  public List<LcmsIon> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter) {
    List<MS1.MetlinIonMass> metlinIonMass = Arrays.asList(MS1.ionDeltas)
        .stream()
        .filter(ionFilter)
        .collect(Collectors.toList());
    List<LcmsIon> ions = metlinIonMass
        .stream()
        .map(m -> new LcmsIon(metabolite.getMonoIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }
}
