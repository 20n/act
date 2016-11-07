package com.act.biointerpretation.networkanalysis;


import com.act.lcms.MS1;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.IonCalculator;
import com.act.lcms.v2.Isotope;
import com.act.lcms.v2.Metabolite;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.act.lcms.MS1.computeIonMz;
import static com.act.lcms.MS1.ionDeltas;

public class MetaboliteToMz implements IonCalculator {

  @Override
  public List<Ion> getIons(Isotope isotope) {
    return getSelectedIons(isotope, ion -> true);
  }

  @Override
  public List<Ion> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter) {
    return getSelectedIons(isotope.getIsotopicMass(), ionFilter);
  }

  @Override
  public List<Ion> getSelectedIons(Isotope isotope, Set<String> ions, MS1.IonMode mode) {
    return getSelectedIons(isotope, ion -> ions.contains(ion));
  }

  @Override
  public List<Ion> getIons(Metabolite metabolite) {
    return getSelectedIons(metabolite, ion -> true);
  }

  @Override
  public List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter) {
    return getSelectedIons(metabolite.getMonoIsotopicMass(), ionFilter);
  }

  @Override
  public List<Ion> getSelectedIons(Metabolite metabolite, Set<String> ions, MS1.IonMode mode) {
    return getSelectedIons(metabolite, ion -> ions.contains(ion.getName()));
  }

  private List<Ion> getSelectedIons(Double mass, Predicate<MS1.MetlinIonMass> ionFilter) {
    return Arrays.stream(ionDeltas).filter(ionFilter).map(ion -> new MzIon(computeIonMz(mass, ion), ion)).collect(Collectors.toList());
  }
}
