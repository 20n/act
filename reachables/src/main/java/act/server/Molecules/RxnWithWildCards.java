package act.server.Molecules;

import java.util.HashMap;

public class RxnWithWildCards {
  String rxn;
  String rxn_with_concretes;
  private HashMap<Integer, Atom> constraintsSubs, constraintsProd;

  public RxnWithWildCards(String rxnSmiles,
      HashMap<Integer, Atom> cSubstrate, HashMap<Integer, Atom> cProducts) {
    this.rxn_with_concretes = rxnSmiles;
    this.rxn = removeConcrete(rxnSmiles);
    this.constraintsSubs = cSubstrate;
    this.constraintsProd = cProducts;
  }

  public RxnWithWildCards(String rxnSmiles) {
    this.rxn_with_concretes = rxnSmiles;
    this.rxn = removeConcrete(rxnSmiles);
    this.constraintsSubs = null;
    this.constraintsProd = null;
  }

  public RxnWithWildCards reverseCopy() {
    String[] parts = rxn.split(">>");
    String newRxn = parts[1] + ">>" + parts[0];
    return new RxnWithWildCards(newRxn, constraintsProd, constraintsSubs);
  }

  @Override
  public String toString() {
    return this.rxn;
  }

  public String removeConcrete(String r) {
    String[] all = r.split(">>");
    if (all.length < 2)
      return r;
    String[] substrates = all[0].split("[.]");
    String[] products = all[1].split("[.]");
    return removeConcrete(substrates) + ">>" + removeConcrete(products);
  }

  private String removeConcrete(String[] l) {
    String filtered = "";
    for (String s : l) {
      if (s.indexOf('*') == -1)
        continue; // do not include this string into consideration if it is missing *'s. Otherwise include
      filtered += filtered.equals("") ? s : "." + s;
    }
    return filtered;
  }

  public static String reverse(String r) {
    String[] s_p = r.split(">>");
    return s_p[1] + ">>" + s_p[0];
  }
}