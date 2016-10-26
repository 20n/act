package com.act.lcms.v2;


import com.act.lcms.MS1;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LCMSIonCalculator implements IonCalculator<LCMSIon> {

  public List<LCMSIon> getIons(Isotope isotope) {
    List<LCMSIon> ions = Arrays.asList(MS1.ionDeltas)
        .stream()
        .map(m -> new LCMSIon(isotope.getIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }

  public List<LCMSIon> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter) {
    List<MS1.MetlinIonMass> metlinIonMass = Arrays.asList(MS1.ionDeltas)
        .stream()
        .filter(ionFilter)
        .collect(Collectors.toList());
    List<LCMSIon> ions = metlinIonMass
        .stream()
        .map(m -> new LCMSIon(isotope.getIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }

  public List<LCMSIon> getIons(Metabolite metabolite) {
    List<LCMSIon> ions = Arrays.asList(MS1.ionDeltas)
        .stream()
        .map(m -> new LCMSIon(metabolite.getMonoIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }

  public List<LCMSIon> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter) {
    List<MS1.MetlinIonMass> metlinIonMass = Arrays.asList(MS1.ionDeltas)
        .stream()
        .filter(ionFilter)
        .collect(Collectors.toList());
    List<LCMSIon> ions = metlinIonMass
        .stream()
        .map(m -> new LCMSIon(metabolite.getMonoIsotopicMass(), m))
        .collect(Collectors.toList());
    return ions;
  }
}
