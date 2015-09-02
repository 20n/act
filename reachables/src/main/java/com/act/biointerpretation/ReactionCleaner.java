package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Reaction;

import java.io.FileWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class ReactionCleaner {
  public static enum RxnError {
    SUBSTRATE_OR_PRODUCT_FELL_OUT,
    BREAKS_CLEANUP,
    UNBALANCED
  }

  Map<Long, Long> validChemsMapOld2New;
  Indigo indigo;
  IndigoInchi iinchi;


  public ReactionCleaner() {
    // the valid chems are not available at construction
    // they show up later, and we set them using the
    // setter below
    this.validChemsMapOld2New = null; 
    this.indigo = new Indigo();
    this.iinchi = new IndigoInchi(this.indigo);
  }
      
  public void setValidChems(Map<Long, Long> vchems) {
    this.validChemsMapOld2New = vchems;
  }

  /**
   * Returns null if the reaction fails cleanup. 
   * Bad rxn is logged based on its first error
   * Returns either the original rxn or a new one if the 
   * data passes through the gauntlet of validation/correction
   */
  public Reaction clean(Reaction rxn) {
    boolean invalid = false;

    //Check the chemicals for correctness, or fix them
    Long[] substrates = rxn.getSubstrates();
    Long[] products = rxn.getProducts();

    // check if all substrates are in the valid set
    for (Long substrate : substrates) {
      if (!this.validChemsMapOld2New.containsKey(substrate))
        invalid = true;
    }

    // check if all substrates are in the valid set
    for (Long product: products) {
      if (!this.validChemsMapOld2New.containsKey(product))
        invalid = true;
    }

    Long[] ss = rxn.getSubstrates();
    Long[] mappedSubstrates = new Long[ss.length];
    for (int i = 0; i < ss.length; i++)
      mappedSubstrates[i] = this.validChemsMapOld2New.get(ss[i]);

    Long[] ps = rxn.getProducts();
    Long[] mappedProducts = new Long[ps.length];
    for (int i = 0; i < ps.length; i++)
      mappedProducts[i] = this.validChemsMapOld2New.get(ps[i]);

    rxn.updateSubstrates(mappedSubstrates);
    rxn.updateProducts(mappedProducts);

    // return rxn if it passed the test, otherwise null
    if (invalid) {
      log(rxn, RxnError.SUBSTRATE_OR_PRODUCT_FELL_OUT, rxn.getReactionName());
      return null;
    } else {
      return rxn;
    }
  }

  private static void balanceOne(SimpleReaction rxn) throws Exception {
    System.out.println(rxn.toString());
    double subsrateBal = 0.0;
    for(String inchi : rxn.substrates) {
      Indigo indigo = new Indigo();
      IndigoInchi iinchi = new IndigoInchi(indigo);

      IndigoObject mol = iinchi.loadMolecule(inchi);
      subsrateBal += mol.monoisotopicMass();
    }
    System.out.println(subsrateBal);

    double productBal = 0.0;
    for(String inchi : rxn.products) {
      Indigo indigo = new Indigo();
      IndigoInchi iinchi = new IndigoInchi(indigo);

      IndigoObject mol = iinchi.loadMolecule(inchi);
      productBal += mol.monoisotopicMass();
    }
    System.out.println(productBal);

    if(subsrateBal == productBal) {
      System.err.println("balanced");
    } else {
      System.err.println("!!!!   Not Balanced");
    }

  }

  private static void log(Reaction rxn, RxnError errcode, String error) {
    System.err.println(errcode.toString() + ":\n\tIncoming KG ID: " + rxn.getUUID() + "\n\tIncoming KG rxn:" + error);
    //TODO:  append a list somewhere
  }

}

