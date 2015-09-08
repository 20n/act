package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Reaction;

import java.io.File;
import java.io.FileWriter;
import java.util.*;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class ReactionCleaner {
//  public static enum RxnError {
//    SUBSTRATE_OR_PRODUCT_FELL_OUT,
//    BREAKS_CLEANUP,
//    UNBALANCED
//  }
//
//    static final File logDir = new File("");
//    Indigo indigo;
//    IndigoInchi iinchi;
//    ChemicalCleaner chemclean = new ChemicalCleaner();
//    Map<String, Long> existingReactions;
//    Map<Long, Long> oldIdToNew;
//    int reactionsEncountered = 0;
//    int reactionsLogged = 0;
//
//
//  public ReactionCleaner() {
//    this.indigo = new Indigo();
//    this.iinchi = new IndigoInchi(this.indigo);
//    existingReactions = new HashMap<>();
//    oldIdToNew = new HashMap<>();
//  }
//
//  /**
//   * Interprets a Reaction and either puts it into an error bin, or puts data into Dr. Know
//   */
//  public void clean(Reaction rxn) throws Exception {
//    boolean invalid = false;
//
//    //Check the chemicals for correctness, or fix them
//    Long[] substrates = rxn.getSubstrates();
//    Long[] products = rxn.getProducts();
//
//    // check if all substrate chemss are ok
//    for (Long id : substrates) {
//        Long newid =  chemclean.clean(id);
//        if(newid < 0) {
//            log(rxn, RxnError.SUBSTRATE_OR_PRODUCT_FELL_OUT, id);
//            return;
//        }
//    }
//
//    // check if all product chems are ok
//      for (Long id : products) {
//          Long newid =  chemclean.clean(id);
//          if(newid < 0) {
//              log(rxn, RxnError.SUBSTRATE_OR_PRODUCT_FELL_OUT, id);
//              return;
//          }
//      }
//
//    Long[] ss = rxn.getSubstrates();
//    Long[] mappedSubstrates = new Long[ss.length];
//    for (int i = 0; i < ss.length; i++)
//      mappedSubstrates[i] = this.validChemsMapOld2New.get(ss[i]);
//
//    Long[] ps = rxn.getProducts();
//    Long[] mappedProducts = new Long[ps.length];
//    for (int i = 0; i < ps.length; i++)
//      mappedProducts[i] = this.validChemsMapOld2New.get(ps[i]);
//
//    rxn.setSubstrates(mappedSubstrates);
//    rxn.setProducts(mappedProducts);
//
//    // return rxn if it passed the test, otherwise null
//    if (invalid) {
//      log(rxn, RxnError.SUBSTRATE_OR_PRODUCT_FELL_OUT, rxn.getReactionName());
//      return null;
//    } else {
//      return rxn;
//    }
//  }
//
//  private boolean checkBalanced(SimpleReaction rxn) throws Exception {
//    // this checks balancing based on mono isotopic mass of the
//    // substrates and products.
//    //
//    // See indigo documentation: https://github.com/ggasoftware/indigo/blob/master/doc/source/indigo/concepts/mass.rst
//    // and e.g., chemical http://www.chemspider.com/Chemical-Structure.370269.html
//    // that has a mass of 827.118347 Da
//    // So by summing up the masses on either side, we get to
//    // compare if the reaction is balanced.
//
//    double subsrateBal = 0.0;
//    for (String inchi : rxn.substrates) {
//      IndigoObject mol = iinchi.loadMolecule(inchi);
//      subsrateBal += mol.monoisotopicMass();
//    }
//
//    double productBal = 0.0;
//    for (String inchi : rxn.products) {
//      IndigoObject mol = iinchi.loadMolecule(inchi);
//      productBal += mol.monoisotopicMass();
//    }
//
//    // we should not be doing equality comparisons
//    // over doubles (or floats). See links below
//    //
//    // https://randomascii.wordpress.com/2012/06/26/doubles-are-not-floats-so-dont-compare-them/
//    // https://randomascii.wordpress.com/2012/02/25/comparing-floating-point-numbers-2012-edition/
//    //
//    // So instead of (subsrateBal != productBal)
//    // we do difference and threshold to account for
//    // number binary representation errors
//
//    // if the masses are equal upto 15 decimal places, we're good
//    double SMALL_EPSILON = 1e-15;
//    if (Math.abs(subsrateBal - productBal) > SMALL_EPSILON) {
//      System.err.println("Not Balanced: " + rxn.toString());
//      return false;
//    }
//
//    return true;
//  }
//
//  private void log(Reaction rxn, RxnError errcode, String error) {
//      reactionsLogged++;
//    System.err.println(errcode.toString() + ":\n\tIncoming KG ID: " + rxn.getUUID() + "\n\tIncoming KG rxn:" + error);
//    //TODO:  append a list somewhere
//  }
}

