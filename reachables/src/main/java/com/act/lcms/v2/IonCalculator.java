package com.act.lcms.v2;


import com.act.lcms.MS1;

import java.util.List;
import java.util.function.Predicate;

public interface IonCalculator<T extends Ion> {

  List<T> getIons(Isotope isotope);

  List<T> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter);

  List<T> getIons(Metabolite metabolite);

  List<T> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter);

}
