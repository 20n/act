package com.act.biointerpretation;

import act.shared.Chemical;
import act.shared.Reaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import act.api.NoSQLAPI;

public class SimpleReaction {
  public List<String> substrates;  //all inchis of reactants
  public List<String> products;  //all inchis of products
  public List<String> substrateNames;  //all names of reactants
  public List<String> productNames;  //all names of products

  public static NoSQLAPI api = new NoSQLAPI();

  public static SimpleReaction factory(Reaction rxn) {
    SimpleReaction out = new SimpleReaction();
    out.substrates = new ArrayList<>();
    out.products = new ArrayList<>();
    out.substrateNames = new ArrayList<>();
    out.productNames = new ArrayList<>();

    try {
      Long[] chemids = rxn.getSubstrates();
      Arrays.sort(chemids);
      for (Long along : chemids) {
        Chemical achem = api.readChemicalFromInKnowledgeGraph(along);
        out.substrates.add(achem.getInChI());
        out.substrateNames.add(achem.getFirstName());
      }
      chemids = rxn.getProducts();
      Arrays.sort(chemids);
      for (Long along : chemids) {
        Chemical achem = api.readChemicalFromInKnowledgeGraph(along);
        out.products.add(achem.getInChI());
        out.productNames.add(achem.getFirstName());
      }
      return out;
    } catch(Exception err) {
      return null;
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();

    for (int i=0; i<substrateNames.size(); i++) {
      String name = substrateNames.get(i);
      if (i > 0) {
        sb.append(" + ");
      }
      sb.append(name);
    }
    sb.append(" >> ");
    for (int i=0; i<productNames.size(); i++) {
      String name = productNames.get(i);
      if (i > 0) {
        sb.append(" + ");
      }
      sb.append(name);
    }

    sb.append("\n");

    for(String inchi : substrates) {
      sb.append(inchi);
      sb.append("\n");
    }

    sb.append("  v\n");

    for(String inchi : products) {
      sb.append(inchi);
      sb.append("\n");
    }

    sb.append("\n");

    return sb.toString();
  }
}
