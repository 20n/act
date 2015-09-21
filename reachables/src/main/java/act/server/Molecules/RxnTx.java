package act.server.Molecules;

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
   * input: substrates in inchi (NormalMol), RO object (with DotNotation.rxn() from DB)
   * output: products in inchi (NormalMol)
   */
  public static List<List<String>> expandChemical2AllProductsNormalMol(List<String> substratesNorm, String roStr) {
    Indigo indigo = new Indigo();
    IndigoInchi inchi = new IndigoInchi(indigo);
    List<String> substratesDot = new ArrayList<String>();
    for (String s: substratesNorm) {
      substratesDot.add(DotNotation.ToDotNotationMol(inchi.loadMolecule(s)).smiles());
    }

    // do actual transform from subs_smiles -> prod_smiles; both in dotNotation
    List<List<String>> productsDot = expandChemical2AllProducts(substratesDot, roStr, indigo, inchi);

    // if failed transformation report as such with null
    if (productsDot == null) return null;

    // else convert the smiles back to normal mol and return those
    List<List<String>> productsNorm = new ArrayList<List<String>>();
    for (List<String> ps : productsDot) {
      List<String> psNorm = new ArrayList<String>();
      for (String p: ps) {
        String smiles = DotNotation.ToNormalMol(indigo.loadMolecule(p), indigo);
        psNorm.add(inchi.getInchi(indigo.loadMolecule(smiles)));
      }
      productsNorm.add(psNorm);
    }
    return productsNorm;
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

  public static void testEnumeration() {
    Indigo indigo = new Indigo();
    IndigoInchi ic = new IndigoInchi(indigo);

    // String reactant = "C(=O)CCC(=O)C";
    String reactant = "C(O)CCCC";
    // String reactant_inchi = "InChI=1S/C5H8O2/c1-5(7)3-2-4-6/h4H,2-3H2,1H3";
    String reaction = "[H,*:1]C(=O)[H,*:2]>>[H,*:1]C(O)[H,*:2]";

    //Setting table of monomers (each reactant can have different monomers)
    //But in this example we have only one reactant.
    IndigoObject monomers_table = indigo.createArray();

    IndigoObject monomer_array_0 = indigo.createArray();
    IndigoObject monomer_0_0 = indigo.loadMolecule(reactant);
    // monomer_0_0 = ic.loadMolecule(reactant_inchi); // lets see if we can transform inchi's too...

    // We have found a little bug, which will be fixed in next indigo
    // version: for correct working molecules should have names.
    monomer_0_0.setName("1");
    monomer_array_0.arrayAdd(monomer_0_0);
    monomers_table.arrayAdd(monomer_array_0);

    //Enumerating reaction products. This function returns array of output reactions.
    IndigoObject react = indigo.loadQueryReaction(reaction);
    IndigoObject output_reactions = indigo.reactionProductEnumerate(react, monomers_table);

    for (int i = 0; i < output_reactions.count(); i++)
    {
      IndigoObject out_rxn = output_reactions.at(i);

      //Saving each product from each output reaction.
      for (IndigoObject iter : out_rxn.iterateProducts())
      {
        String product_smiles = iter.smiles();
        System.out.println("Product: " + product_smiles);
      }
      try { System.in.read(); } catch (IOException e) { }
    }
  }





  /*
   * Naive transforms.
   * These are NOT chemical operator applications,
   * instead just used for canonicalization when we have to check
   * what one product through direct matching will yield.
   */

  /*
   * Use expandChemical2AllProducts if you really want to apply an RO
   * This is just for rxn smarts canonicalization when you have to
   * identically substitute without enumerating all products
   * (Used in SMILES.putCanonicalSubstrateProductTogether)
   */
  public static String naiveApplyOpOneProduct(String smiles, String roStr, Indigo indigo) {

    RO ro = new RO(new RxnWithWildCards(roStr, null, null));
    List<String> products;
    products = naiveApplyOpOneProduct(smiles, ro); // uses indigo.transform
    /* deprecated code...
    {
      Set<String> splitSmiles = new HashSet<String>();
      String[] monomers = smiles.split("\\.");
      for (String monomer: monomers) splitSmiles.add(monomer);
      List<List<String>> output = expandChemicalUsingOperatorSMILES_AllProducts(splitSmiles, ro, indigo); // uses indigo.reactionProductEnumerate
      if (products.size() != 1) {
        System.err.format("More than one reaction availability!?: %s, operator: %s, substrate: %s\n", products, roStr, smiles);
        System.exit(-1);
      }
      products = output.get(0);
    }
    */
    String out = "";
    for (String prod : products)
      out += (out.equals("") ? "" : ".") + prod;
    return out;
  }

  /*
   * Use expandChemical2AllProducts if you really want to apply an RO
   * This is just for rxn smarts canonicalization when you have to
   * identically substitute without enumerating all products
   * (Used in SMILES.putCanonicalSubstrateProductTogether)
   */
  public static List<String> naiveApplyOpOneProduct(String inputSMILES, RO ro) {
    Indigo indigo = new Indigo();
    IndigoObject molecule = indigo.loadMolecule(inputSMILES);
    boolean debug = false;

    String transformSmiles = ro.rxn();

    if (transformSmiles.contains("|")) {
      Logger.print(0, "WOA! Need to fix this. The operators DB was not correctly populated. It still has |f or |$ entries...\n");
      transformSmiles = SMILES.HACKY_fixQueryAtomsMapping(transformSmiles);
    }

    IndigoObject reaction = indigo.loadQueryReaction(transformSmiles);
    // IndigoObject reaction = indigo.loadReactionSmarts(transformSmiles);
    // See email thread https://groups.google.com/forum/?fromgroups#!searchin/indigo-general/applying$20reaction$20smarts/indigo-general/QTzP50ARHNw/tVEsxeFuCekJ
    // There is some mention of using loadReactionSmarts as opposed to loadQueryReaction...
    indigo.transform(reaction, molecule);
    String smiles = molecule.canonicalSmiles();

    if (debug)
      if (smiles.equals(inputSMILES))
        System.out.format("No new chemical. Chemical %s and transform %s\n", inputSMILES, transformSmiles);
      else
        System.out.format("New chemicals generated : { %s>>%s } by applying { %s } \n", inputSMILES, smiles, transformSmiles);

    String[] chems = smiles.split("[.]");
    return Arrays.asList(chems);

  }


  // @Deprecated // Use expandChemical2AllProducts
  // public static List<String> expandChemicalUsingOperatorInchi(String inputInChi, RO ro, Indigo indigo, IndigoInchi indigoInchi) {
  //   IndigoObject molecule = indigoInchi.loadMolecule(inputInChi);
  //
  //   // IndigoObject reaction = indigo.loadReactionSmarts(transformSmiles);
  //   // See email thread https://groups.google.com/forum/?fromgroups#!searchin/indigo-general/applying$20reaction$20smarts/indigo-general/QTzP50ARHNw/tVEsxeFuCekJ
  //   // There is some mention of using loadReactionSmarts as opposed to loadQueryReaction...
  //   String roStr = ro.rxn();
  //   IndigoObject q_rxn = getReactionObject(indigo, roStr);
  //   indigo.transform(q_rxn, molecule);
  //   String inchi = indigoInchi.getInchi(molecule);
  //
  //   if (inchi.equals(inputInChi)) {
  //   //  System.out.format("No new chemical. Chemical %s and transform %s\n", inputInChi, transformSmiles);
  //     return null;
  //   } else {
  //     String[] chems = { inchi };//inchi.split("[.]");
  //     //System.err.format("New chemicals generated : { %s>>%s } by applying { %s } \n", inputInChi, inchi, transformSmiles);
  //     return Arrays.asList(chems);
  //   }
  // }

  // @Deprecated // Definitely deprecated: Use expandChemical2AllProducts
  // public static List<List<String>> expandChemicalUsingOperatorSMILES_AllProducts(Set<String> inputSMILES, RO ro, Indigo indigo) {
  //
  //   // See https://groups.google.com/d/msg/indigo-general/QTzP50ARHNw/7Y2U5ZOnh3QJ
  //   boolean debug = true;
  //
  //   if (debug) {
  //     System.out.format("\nAttempting to transform: \n%s\n%s\n\n", inputSMILES, ro.rxn());

  //     // if (inputInChi.split("[.]").length != 1) {
  //     //   System.err.println("Input Inchi contains a dot: " + inputInChi);
  //     // }
  //   }
  //
  //   // Setting table of monomers (each reactant can have different monomers)
  //   IndigoObject monomers_table = indigo.createArray();
  //   IndigoObject output_reactions;

  //   try {
  //     for (String smile : inputSMILES) {
  //       IndigoObject monomer_array = indigo.createArray();
  //       IndigoObject monomer = indigo.loadMolecule(smile);
  //       monomer.setName("1"); // for correct working molecules should have names.
  //       monomer_array.arrayAdd(monomer);
  //       monomers_table.arrayAdd(monomer_array);
  //     }
  //     // Enumerating reaction products. This function returns array of output reactions.
  //     String roStr = ro.rxn();
  //     IndigoObject q_rxn = getReactionObject(indigo, roStr);
  //     output_reactions = indigo.reactionProductEnumerate(q_rxn, monomers_table);

  //     List<List<String>> output = new ArrayList<List<String>>();
  //
  //     // After this you will get array of output reactions. Each one of them
  //     // consists of products and monomers used to build these products.
  //     for (int i = 0; i < output_reactions.count(); i++) {
  //       IndigoObject out_rxn = output_reactions.at(i);
  //
  //       // Saving each product from each output reaction.
  //       // In this example each reaction has only one product
  //
  //       List<String> products_1rxn = new ArrayList<String>();
  //       for (IndigoObject products : out_rxn.iterateProducts()) {
  //         if (debug)
  //           System.out.println("------- Product: " + products.smiles());
  //
  //         for (IndigoObject comp : products.iterateComponents())
  //         {
  //           IndigoObject mol = comp.clone();
  //
  //           if (false)
  //             System.out.println("------- Product Component: " + comp.clone().smiles());
  //
  //           String product_smile = mol.canonicalSmiles();
  //           products_1rxn.add(product_smile);
  //         }
  //       }
  //       output.add(products_1rxn);
  //     }
  //
  //     return output.size() == 0 ? null : output;
  //   } catch(Exception e) {

  //     if (e.getMessage().startsWith("core: Too small monomers array"))
  //       System.out.println("There are still some EROs are that opposite to the CRO they are contained within. Which is why out assumption of filtering on numReactants in CRO may not translate to ERO numReactants.");
  //     e.printStackTrace();
  //     return null;
  //   }
  // }


}
