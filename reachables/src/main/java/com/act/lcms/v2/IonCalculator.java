package com.act.lcms.v2;


import com.act.lcms.MS1;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public interface IonCalculator {

  /**
   * Return all possible ions for a given isotope
   * @param isotope input Isotope
   * @return a list of Ion objects
   */
  List<Ion> getIons(Isotope isotope);

  /**
   * Return ions meeting the ionFilter criterion
   * @param isotope input Isotope
   * @param ionFilter Predicate on possible ions
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Isotope isotope, Predicate<MS1.MetlinIonMass> ionFilter);

  /**
   * Return ions for an isotope given input ion names and mode
   * @param isotope input Isotope
   * @param ions set of ion names
   * @param mode input ion mode
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Isotope isotope, Set<String> ions, MS1.IonMode mode);

  /**
   * Return all possible ions for a given metabolite
   * @param metabolite input Metabolite
   * @return a list of Ion objects
   */
  List<Ion> getIons(Metabolite metabolite);

  /**
   * Return ions meeting the ionFilter criterion
   * @param metabolite input Metabolite
   * @param ionFilter Predicate on possible ions
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Metabolite metabolite, Predicate<MS1.MetlinIonMass> ionFilter);

  /**
   * Return ions for an metabolite given input ion names and mode
   * @param metabolite input Metabolite
   * @param ions set of ion names
   * @param mode input ion mode
   * @return a list of Ion objects
   */
  List<Ion> getSelectedIons(Metabolite metabolite, Set<String> ions, MS1.IonMode mode);
}
