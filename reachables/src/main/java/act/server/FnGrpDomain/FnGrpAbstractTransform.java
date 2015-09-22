package act.server.FnGrpDomain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;

import act.server.Molecules.RO;

public class FnGrpAbstractTransform {
  List<FnGrpAbstractChem> substrates;
  List<FnGrpAbstractChem> products;
  String[] basis_vector;
  RO original_ro;

  public FnGrpAbstractTransform(RO ro, String[] fngrp_basis) {
    this.original_ro = ro;
    this.basis_vector = fngrp_basis;
    this.substrates = new ArrayList<FnGrpAbstractChem>();
    this.products = new ArrayList<FnGrpAbstractChem>();

    String transformStr = ro.rxn();
    Indigo indigo = new Indigo();
    IndigoObject rxn = indigo.loadReaction(replaceWildCards(transformStr));

    for (IndigoObject s : rxn.iterateReactants()) {
      this.substrates.add(new FnGrpAbstractChem(s.smiles(), this.basis_vector));
    }
    for (IndigoObject p : rxn.iterateProducts()) {
      this.products.add(new FnGrpAbstractChem(p.smiles(), this.basis_vector));
    }
  }

  private String replaceWildCards(String rxn_smile) {
    String atom = "[Pu]"; // plutonium :), we are (pretty sure) never going to see this in natural molecules, so replacing the *'s with Pu will definitely make the molecule "non-query"
    String replaced = rxn_smile.replaceAll("\\[H,\\*:[0-9]+\\]", atom);
    // System.out.format("Replaced %s from %s\n", replaced, rxn_smile);
    return replaced;
  }

  @Override
  public String toString() {
    String subs = "", prod = "";
    for (FnGrpAbstractChem s : substrates) {
      String abs = Arrays.toString(s.abstraction());
      subs += subs.equals("") ? abs : ("." + abs);
    }
    for (FnGrpAbstractChem p : products) {
      String abs = Arrays.toString(p.abstraction());
      prod += prod.equals("") ? abs : ("." + abs);
    }
    return subs + "->" + prod
        + " with basis: " + Arrays.asList(this.basis_vector).toString()
        + " where original was " + this.original_ro.rxn();
  }

}
