package com.act.lcms.v2;


import com.act.lcms.MS1;

import java.util.List;
import java.util.function.Predicate;

public interface IonCalculator {

  List<Ion> getIons(Isotope isotope);

  List<Ion> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter);

  List<Ion> getIons(Metabolite metabolite);

  List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter);

}
