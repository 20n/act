package com.act.biointerpretation.networkanalysis;


import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.lcms.MassCalculator2;
import com.act.lcms.v2.Ion;
import org.apache.commons.lang.NotImplementedException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.act.lcms.MS1.computeIonMz;
import static com.act.lcms.MS1.getIonMasses;
import static com.act.lcms.MS1.ionDeltas;

public class MetaboliteToMz {

  Set<String> ions;
  List<MS1.MetlinIonMass> ionMasses;

  public MetaboliteToMz(Set<String> ions) {
    this.ions = ions;
    ionMasses = Arrays.asList(ionDeltas);
    ionMasses.removeIf(m -> !ions.contains(m.getName()));
  }

  List<Ion> getIons(Metabolite metabolite) {
    double mass = MassCalculator.calculateMass(metabolite.getInchi());
    return ionMasses.stream().map(ion -> new MzIon(computeIonMz(mass, ion), ion)).collect(Collectors.toList());
  }
}
