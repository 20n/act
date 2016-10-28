package com.act.biointerpretation.networkanalysis;


import com.act.lcms.MS1;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.IonCalculator;
import com.act.lcms.v2.Isotope;
import com.act.lcms.v2.Metabolite;
import com.jacob.com.NotImplementedException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.act.lcms.MS1.computeIonMz;
import static com.act.lcms.MS1.ionDeltas;

public class MetaboliteToMz implements IonCalculator<Ion> {

  List<MS1.MetlinIonMass> ionMasses;

  public MetaboliteToMz(Set<String> ions) {
    ionMasses = Arrays.stream(ionDeltas).filter(m -> ions.contains(m.getName())).collect(Collectors.toList());
  }

  @Override
  public List<Ion> getIons(Isotope isotope) {
    throw new NotImplementedException("Not yet implemented!");
  }

  @Override
  public List<Ion> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter) {
    throw new NotImplementedException("Not yet implemented!");
  }

  @Override
  public List<Ion> getIons(Metabolite metabolite) {
    return getSelectedIons(metabolite, ion -> true);
  }

  @Override
  public List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter) {
    double mass = metabolite.getMonoIsotopicMass();
    return ionMasses.stream().filter(ionFilter).map(ion -> new MzIon(computeIonMz(mass, ion), ion)).collect(Collectors.toList());

  }
}
