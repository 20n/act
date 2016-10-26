package com.act.lcms.v2;


import java.util.List;

public interface IonCalculator {

  List<Ion> getIons(Isotope isotope);

  List<Ion> getIons(Metabolite metabolite);

}
