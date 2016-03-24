package com.act.biointerpretation.step2_desalting;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.server.Logger;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class RxnTx {

  /*
   * input: substrates in smiles DotNotation, RO object (with DotNotation.rxn() from DB)
   * output: products in smiles DotNotation
   */
  public static List<List<String>> expandChemical2AllProducts(List<String> substrates, RO ro, Indigo indigo, IndigoInchi indigoInchi) {
    return expandChemical2AllProducts(substrates, ro.rxn(), indigo, indigoInchi);
  }

  /*
   * input: substrates in smiles DotNotation, ro SMARTS string (with DotNotation)
   * output: products in smiles DotNotation
   */
  public static List<List<String>> expandChemical2AllProducts(List<String> substrates, String roStr, Indigo indigo, IndigoInchi indigoInchi) {
    boolean debug = false;
    boolean smiles = true;

    // tutorial through example is here:
    // https://groups.google.com/d/msg/indigo-general/QTzP50ARHNw/7Y2U5ZOnh3QJ

    if (debug)
      System.out.format("\nAttempt tfm: \n%s\n%s\n\n", substrates, roStr);

    // Setting table of monomers (each reactant can have different monomers)
    IndigoObject monomers_table = indigo.createArray();
    IndigoObject output_reactions;

    try {
      int idx = 1;
      for (String s : substrates) {
        IndigoObject monomer_array = indigo.createArray();
        IndigoObject monomer;
        if (!smiles) {
          monomer = indigoInchi.loadMolecule(s);
          System.err.println(
              "You provided the substrate as InChI, but inchi does not "
                  +"play very well with DOT notation."
                  +"\n\tStill attempting conversion, but it will most likely fail."
                  +"\n\tReason is, InChI does semantic transformations that end up "
                  +"\n\tbreaking apart the heavy atom covalent bond we use as a DOT,"
                  +"\n\tand putting it as a metal ion on the side. Not good.");
        } else {
          monomer = indigo.loadMolecule(s);
        }
        monomer.setName("" + (idx++)); // for correct working: need names (!)
        monomer_array.arrayAdd(monomer);
        monomers_table.arrayAdd(monomer_array);
      }

      // Enumerating reaction products. Fn returns array of output reactions.
      IndigoObject q_rxn = getReactionObject(indigo, roStr);
      output_reactions = indigo.reactionProductEnumerate(q_rxn, monomers_table);

      // String all_inchi = "";
      List<List<String>> output = new ArrayList<List<String>>();

      // After this you will get array of output reactions. Each one of them
      // consists of products and monomers used to build these products.
      for (int i = 0; i < output_reactions.count(); i++) {
        IndigoObject out_rxn = output_reactions.at(i);

        // Saving each product from each output reaction.
        // In this example each reaction has only one product

        List<String> products_1rxn = new ArrayList<String>();
        for (IndigoObject products : out_rxn.iterateProducts()) {
          if (debug) System.out.println("----- Product: " + products.smiles());

          for (IndigoObject comp : products.iterateComponents())
          {
            IndigoObject mol = comp.clone();
            if (smiles)
              products_1rxn.add(mol.smiles());
            else
              products_1rxn.add(indigoInchi.getInchi(mol));
          }
					/*
					 * Other additional manipulations; see email thread:
					 * https://groups.google.com/d/msg/indigo-general/QTzP50ARHNw/7Y2U5ZOnh3QJ
					 * -- Applying layout to molecule
					 *  if (!mol.hasCoord())
					 *     mol.layout();
					 *
					 * -- Marking undefined cis-trans bonds
					 * mol.markEitherCisTrans();
					 */
        }
        output.add(products_1rxn);
      }

      return output.size() == 0 ? null : output;
    } catch(Exception e) {
      if (e.getMessage().equals("core: Too small monomers array")) {
        System.err.println("#args in operator > #args supplied");
      } else {
        e.printStackTrace();
      }
      return null;
    }
  }

  private static IndigoObject getReactionObject(Indigo indigo, String transformSmiles) {
    if (transformSmiles.contains("|")) {
      Logger.print(0, "WOA! Need to fix this. The operators DB "
          + "was not correctly populated. It still has |f or |$ entries...\n");
      transformSmiles = SMILES.HACKY_fixQueryAtomsMapping(transformSmiles);
    }

    IndigoObject reaction = indigo.loadQueryReaction(transformSmiles);
    return reaction;
  }
}
