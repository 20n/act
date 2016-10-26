package com.act.biointerpretation.networkanalysis;


import com.act.lcms.MS1;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.IonCalculator;
import com.act.lcms.v2.IsotopeCalculator;
import com.act.lcms.v2.Metabolite;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.act.lcms.MS1.computeIonMz;
import static com.act.lcms.MS1.ionDeltas;

public class MetaboliteToMz {

  List<MS1.MetlinIonMass> ionMasses;
  IsotopeCalculator isotopeCalculator;
  IonCalculator ionCalculator;

  public MetaboliteToMz(Set<String> ions) {
    ionMasses = Arrays.asList(ionDeltas);
    ionMasses.removeIf(m -> !ions.contains(m.getName()));
  }

  List<Ion> getIons(Metabolite metabolite) {
    double mass = metabolite.getMonoIsotopicMass();
    return ionMasses.stream().map(ion -> new MzIon(computeIonMz(mass, ion), ion)).collect(Collectors.toList());
  }
}
