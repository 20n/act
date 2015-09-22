package act.server.Molecules;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;
import com.mongodb.MongoException;

import act.graph.Edge;
import act.graph.DiGraph;
import act.graph.GraphMatching;
import act.graph.Node;
import act.server.Logger;
import act.server.Molecules.SMILES.BalancingPattern;
import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDB.MappedCofactors;
import act.shared.AAMFailException;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.MalFormedReactionException;
import act.shared.NoSMILES4InChiException;
import act.shared.OperatorInferFailException;
import act.shared.Reaction;
import act.shared.SMARTSCanonicalizationException;
import act.shared.helpers.P;
import act.shared.helpers.T;

public class ReactionDiff {
  static BalancingPattern[] KnownBalancingPatterns;
  static {
    KnownBalancingPatterns = new BalancingPattern[] {
        /*
         * simply H
         */
        new BalancingPattern(0, // this is the empty fix! this allows any mismatch just on H's to be balanced
            getUnchargedAtoms(new Element[] { }),
            new Integer[]  { },
            0, // no **implicit** H's are encoded in this fix.
            null, null) // nothing to be added to either substrates/products, except H's which are automatic
        ,
        /*
         * water
         */
        new BalancingPattern(1, // 1 non-H atom is the delta
            getUnchargedAtoms(new Element[] { Element.O }),
            new Integer[]  { 1 },
            -2, // two implicit H's are encoded on the substrates side.. (SEE big explanation at end of BalancingPattern.matchesPattern for why this is +ve/-ve)
            new String[]   { "O" }, null) // add a water to the substrates; nothing to products
        ,
        new BalancingPattern(1,  // 1 non-H atom is the delta
            getUnchargedAtoms(new Element[] { Element.O }),
            new Integer[]  { -1 },
            2, // two implicit H's are encoded on the products side.. (SEE big explanation at end of BalancingPattern.matchesPattern for why this is +ve/-ve)
            null, new String[]   { "O" }) // add a water to the products; nothing to substrates
        ,
        /*
         * phosphate
         */
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.P, Element.O }),
            new Integer[]  { 1,          3 },
            2, // two implicity H's in this fix because of the water on the products
            new String[]   { "[O-]P([O-])([O-])=O" }, new String[]   { "O" }) // add a phosphate to substrates
        ,
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.P, Element.O }),
            new Integer[]  { -1,          -3 },
            -2, // two implicity H's in this fix because of the water on the substrates
            new String[]   { "O" }, new String[]   { "[O-]P([O-])([O-])=O" }) // add a phosphate to products
        ,
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.P, Element.O }),
            new Integer[]  { 2,          6 },
            2, // two implicity H's in this fix because of the water on the products
            new String[]   { "O=P([O-])([O-])OP([O-])([O-])=O" }, new String[]   { "O" }) // add a phosphate to substrates
        ,
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.P, Element.O }),
            new Integer[]  { -2,          -6 },
            -2, // two implicity H's in this fix because of the water on the substrates
            new String[]   { "O" }, new String[]   { "O=P([O-])([O-])OP([O-])([O-])=O" }) // add a phosphate to products
        ,
        /*
         * sulphate
         */
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.S, Element.O }),
            new Integer[]  { 1,          3 },
            1, // +2 water -1 suphate implicity H's
            new String[]   { "[O-]S([O-])([O-])=O" }, new String[]   { "O" })
        ,
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.S, Element.O }),
            new Integer[]  { -1,          -3 },
            -1, // -2 water +1 suphate implicity H's
            new String[]   { "O" }, new String[]   { "[O-]S([O-])([O-])=O" })
        ,
        /*
         * CO
         */
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.C, Element.O }),
            new Integer[]  { 1,          1 },
            2, // two implicity H's in this fix on the products side...
            new String[] { "C(=O)=O" }, new String[] { "O" }) // add a CO2 to substrates, water to products
        ,
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.C, Element.O }),
            new Integer[]  { -1,          -1 }, // CO is lost when going to the products
            -2, // two implicit H's are encoded on the substrates side..
            new String[] { "O" }, new String[] { "C(=O)=O" }) // add a CO2 to products, water to substrates
        ,
        /*
         * CO2
         */
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.C, Element.O }),
            new Integer[]  { 1,          2 },
            0, // no implicity H's in this fix on the products side...
            new String[] { "C(=O)=O" }, null) // add a CO2 to substrates, nothing to products
        ,
        new BalancingPattern(2,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.C, Element.O }),
            new Integer[]  { -1,          -2 }, // CO is lost when going to the products
            0, // no implicit H's are encoded on the substrates side..
            null, new String[] { "C(=O)=O" }) // add a CO2 to products, nothing to substrates
        ,
        /*
         * Just a C
         */
        new BalancingPattern(1,  // just a C imbalance
            getUnchargedAtoms(new Element[] { Element.C }),
            new Integer[]  { 1 }, // C is gained when going to the products
            4, // 4 implicity H's in this fix on the products side...
            new String[] { "C(=O)=O" }, new String[] { "O", "O" }) // add a CO2 to substrates, 2*water to products
        ,
        new BalancingPattern(1,  // two atom types different
            getUnchargedAtoms(new Element[] { Element.C }),
            new Integer[]  { -1 }, // C is lost when going to the products
            -4, // 4 implicit H's are encoded on the substrates side..
            new String[] { "O", "O" }, new String[] { "C(=O)=O" }) // add a CO2 to products, 2* water to substrates
        ,
        /*
         * O2
         */
        new BalancingPattern(1,  // just O different
            getUnchargedAtoms(new Element[] { Element.O }),
            new Integer[]  { 2 },
            0, // no implicity H's in this fix
            new String[] { "O=O" }, null) // add a O2 to substrates, nothing to products
        ,
        new BalancingPattern(1,  // just O different
            getUnchargedAtoms(new Element[] { Element.O }),
            new Integer[]  { -2 },
            0, // no implicity H's in this fix
            null, new String[] { "O=O" }) // add a O2 to products, nothing to substrates
        ,
    };
  }
  private static Atom[] getUnchargedAtoms(Element[] symbols) {
    Atom[] atoms = new Atom[symbols.length];
    for (int i = 0; i<symbols.length; i++)
      atoms[i] = new Atom(symbols[i]);
    return atoms;
  }

  MongoDB DB;

  public ReactionDiff(MongoDB db) {
    this.DB = db;
  }

  BufferedWriter TheoryROf, TheoryROHierf;
  KnownCofactors cofactors;
  boolean fixOMinus = false;

  public TheoryROClasses processAll(Long lowUUID, Long highUUID, List<Long> whitelist, boolean addToDB) {
    String rofile = Configuration.getInstance().theoryROOutfile;
    String hierfile = Configuration.getInstance().theoryROHierarchyFile;
    try {
      TheoryROf = new BufferedWriter(new FileWriter(rofile, true)); // open for append
      TheoryROHierf = new BufferedWriter(new FileWriter(hierfile, true)); // open for append
    } catch (IOException e) {
      System.err.println("Could not open TheoryRO logging file: " + rofile + " or hierarchy file: " + hierfile);
    }

    DBIterator iterator = this.DB.getIteratorOverReactions(this.DB.getRangeUUIDRestriction(lowUUID, highUUID), true /* notimeout=true */, null /* no keys: get full entry */);
    Reaction r;
    TheoryROs theoryRO;
    TheoryROClasses classes = new TheoryROClasses(this.DB);
    classes.SetHierarchyLogF(this.TheoryROHierf);

    System.out.format("Iterating over %d -> %d; addtoDB:" + addToDB, lowUUID, highUUID);

    // since we are iterating until the end, the getNextReaction call will close the DB cursor...
    while ((r = this.DB.getNextReaction(iterator)) != null) {
      Long luuid = (long) r.getUUID();
      if ( whitelist != null && !whitelist.contains(luuid) )
        continue;

      // when debug list contains "-1" then debug_dump_uuid == null; and it means that we dump everything
      if (Configuration.getInstance().debug_dump_uuid == null
          || Configuration.getInstance().debug_dump_uuid.contains(r.getUUID()))
        BadRxns.logReaction(r, this.DB); // not necessarily a bad reaction, but one the user wants rendered
      // some reactions we have seen before and they are either bad data; or too difficult to analyze
      if (BadRxns.isBad(r, this.DB))
        continue;

      long start = System.nanoTime();
      try {
        theoryRO = process(r);
        boolean knownGoodRxn = ( whitelist != null && whitelist.contains(luuid) );
        Logger.println(0, "Adding reaction : " + r.getUUID());
        classes.add(theoryRO, r, knownGoodRxn, addToDB);
      } catch (MalFormedReactionException e) {
        BadRxns.logUnprocessedReaction(r, e.getMessage(), this.DB); // unbalanced reactions are ignored, but logged
      } catch (AAMFailException e) {
        BadRxns.logAAMFails(r, e.getMessage(), this.DB); // unbalanced reactions are ignored, but logged
      } catch (OperatorInferFailException e) {
        BadRxns.logROInferFail(r, e.getMessage(), this.DB);
      } catch (SMARTSCanonicalizationException e) {
        BadRxns.logCouldNotCanonicalizeRO(r, e.getMessage(), this.DB);
      } catch (NoSMILES4InChiException e) {
        Logger.println(0, "Could not find SMILES: " + r.getUUID());
      }
      long elapsed = System.nanoTime() - start;
      logTiming(luuid, elapsed, DB.location());
    }

    try {
      TheoryROf.close();
      TheoryROHierf.close();
    } catch (IOException e) {
      System.err.println("Could not close TheoryRO logging file: " + rofile + " or hierarchy file: " + hierfile);
    }

    return classes;
  }

  public TheoryROs process(long id) throws MalFormedReactionException, AAMFailException, OperatorInferFailException, NoSMILES4InChiException, SMARTSCanonicalizationException {
    Reaction r = this.DB.getReactionFromUUID(id);
    return process(r);
  }

  private TheoryROs process(Reaction r) throws MalFormedReactionException, AAMFailException, OperatorInferFailException, NoSMILES4InChiException, SMARTSCanonicalizationException {
    Logger.println(0, "Reaction: \n" + r);
    int id = r.getUUID();
    P<List<String>, List<Double>> s = getSubstratesAndPromiscuity(r);
    P<List<String>, List<Double>> p = getProductsAndPromiscuity(r);

    FilteredReaction reduced = null;
    switch (Configuration.getInstance().rxnSimplifyUsing) {
      case HardCodedCoFactors:
        reduced = filterUsingCofactors(r);
        break;
      case SimpleRarity:
        reduced = removeRedundantMolecules(id, s.fst(), p.fst(), s.snd(), p.snd());
        break;
      case PairedRarity:
        reduced = removePairedRedundantMolecules(id, s.fst(), p.fst(), s.snd(), p.snd());
        break;
    }

    // BROs are trivial to compute; so we can compute them over the entire reaction, without having removed the cofactors...
    BRO broFull = SMILES.computeBondRO(s.fst(), p.fst());

    // get the substrate and product strings with cofactors removed...
    List<String> substratesReduced = reduced.getSubstratesFiltered(), productsReduced = reduced.getProductsFiltered();
    // because the cofactors were removed, the reaction might be unbalanced.. balance the partial reaction...
    P<List<String>,List<String>> reaction = balanceTheReducedReaction(id, substratesReduced, productsReduced);
    // compute the reaction transform under the reduced reaction...

    TheoryROs theoryRO = SMILES.ToReactionTransform(id, reaction, broFull);
    logCRO(theoryRO, r);
    return theoryRO;
  }

  private TheoryROs balanceFullRxnAndDoGraphMatchingDiff(Reaction r) throws MalFormedReactionException, NoSMILES4InChiException {
    //////////////////////////////////////////////////////////
    //   OLD deprecated code. Disabled.
    //////////////////////////////////////////////////////////

    P<List<String>, List<Double>> s = getSubstratesAndPromiscuity(r);
    P<List<String>, List<Double>> p = getProductsAndPromiscuity(r);

    MolGraph substrateG = SMILES.ToGraph(s.fst()); Logger.println(5, "Substrate graph: " + substrateG);
    MolGraph productG = SMILES.ToGraph(p.fst()); Logger.println(5, "Product graph: " + productG);

    HashMap<Atom, Integer> deltaRxn = checkBalancedReaction(substrateG, productG);
    // for the sake of completeness balance the full reaction (not needed for cro computation)
    P<List<String>, List<String>> balancedFull = balanceFullRxn(r.getUUID(), s.fst(), p.fst(), deltaRxn);

    // if we are showing any debugging information then wait for input
    // (0 is the least verbose, so at least >0);
    // otherwise just run through the entire data set
    if (Logger.getMaxImpToShow() > 0) {
      System.out.print("Press any key to process reaction.");
      try { System.in.read(); } catch (Exception e) { System.err.println("Could not read key."); System.exit(-1); }
    }

    if (Configuration.getInstance().graphMatchingDiff) { // very unlikely we use the OLD Graph Matching....
      if (!deltaRxn.isEmpty()) // !empty delta between substrates and products
        return null; // omit this reaction if it is not balanced.
      // this is dead code anyway, so we do not bother calculating the ERO (so null)....
      TheoryROs theoryRO = new TheoryROs(null, doGraphMatchingDiff(substrateG, productG), null);
      logCRO(theoryRO, r);
      return theoryRO;
    }

    System.err.println("In deprecated code. Aborting."); System.exit(-1);
    return null;
  }

  private P<List<String>, List<Double>> getSubstratesAndPromiscuity(Reaction r) throws NoSMILES4InChiException {
    HashMap<Long, Double> substrateProm = this.DB.getRarity((long)r.getUUID(), false /* not product */);

    List<String> substrates = new ArrayList<String>();
    List<Double> substratePromiscuity = new ArrayList<Double>();

    for (long s : r.getSubstrates()) {
      Chemical substr = this.DB.getChemicalFromChemicalUUID(s);
      String smiles = substr.getSmiles();
      if (smiles == null)
        throw new NoSMILES4InChiException("We dont have the smiles in the DB because the conversion from InChi not possible.");

      substratePromiscuity.add(substrateProm.get(s));
      if (fixOMinus) smiles = fixRedundantNegativeCharges(smiles);
      substrates.add(smiles);
    }
    Logger.println(5, "Substrates: " + Arrays.toString(substrates.toArray(new String[0])));
    return new P<List<String>, List<Double>>(substrates, substratePromiscuity);
  }

  private P<List<String>, List<Double>> getProductsAndPromiscuity(Reaction r) throws NoSMILES4InChiException {
    HashMap<Long, Double> productProm = this.DB.getRarity((long)r.getUUID(), true /* is product */);

    List<String> products = new ArrayList<String>();
    List<Double> productPromiscuity = new ArrayList<Double>();

    for (long p : r.getProducts()) {
      Chemical prod = this.DB.getChemicalFromChemicalUUID(p);
      String smiles = prod.getSmiles();
      if (smiles == null)
        throw new NoSMILES4InChiException("We dont have the smiles in the DB because the conversion from InChi not possible.");

      productPromiscuity.add(productProm.get(p));
      if (fixOMinus) smiles = fixRedundantNegativeCharges(smiles);
      products.add(smiles);
    }
    Logger.println(5, "Products: " + Arrays.toString(products.toArray(new String[0])));
    return new P<List<String>, List<Double>>(products, productPromiscuity);
  }

  private FilteredReaction filterUsingCofactors(Reaction r) {
    if (this.cofactors == null)
      this.cofactors = new KnownCofactors(this.DB);

    Long[] sIDs = r.getSubstrates();
    Long[] pIDs = r.getProducts();

    List<String> substratesReduced = new ArrayList<String>();
    List<String> productsReduced = new ArrayList<String>();

    // mappedcofactors just used to return [s_ids_remain],[p_ids_remain],  s_mapped,p_mapped
    // the function has the side effect of adding to substrateReduced and productsReduced, the cofactors that were already mapped.
    MappedCofactors mapped = this.cofactors.computeMaximalCofactorPairing(sIDs, pIDs, substratesReduced, productsReduced);

    String substrateStr = mapped.mapped_substrates, productStr = mapped.mapped_products;
    Logger.println(0, "Precomputed: " + substrateStr + ">>" + productStr);
    for (Chemical subchem : mapped.substrates) {
      String substrate = subchem.getSmiles();
      substrateStr += (substrateStr.isEmpty() ? "" : ".") + substrate;
      substratesReduced.add(substrate);
    }
    for (Chemical prodchem : mapped.products){
      String product = prodchem.getSmiles();
      productStr += (productStr.isEmpty() ? "" : ".") + product;
      productsReduced.add(product);
    }
    Logger.println(0, "Sending to algorithm: " + substrateStr + ">>" + productStr);

    return new FilteredReaction(substrateStr, productStr, substratesReduced, productsReduced);
  }

  private String fixRedundantNegativeCharges(String smiles) {
    if (smiles == null)
      return null;
    String replaced = smiles.replaceAll("O-", "OH");
    System.out.format("[fixRedundantNegativeCharges] %s --> %s\n", smiles, replaced);
    return replaced;
  }

  public void findCofactorPairs(Long lowUUID, Long highUUID) {
    DBIterator iterator = this.DB.getIteratorOverReactions(this.DB.getRangeUUIDRestriction(lowUUID, highUUID), false /* notimeout=false */, null /* no keys: get full entry */);
    Reaction r;

    System.out.format("Checking cofactors for rxn_id %d -> %d; \n", lowUUID, highUUID);

    // since we are iterating until the end, the getNextReaction call will close the DB cursor...
    HashMap<String, List<Integer>> cofactors = new HashMap<String, List<Integer>>();
    HashMap<String, Reaction> cofactor_sample = new HashMap<String, Reaction>();
    while ((r = this.DB.getNextReaction(iterator)) != null) {
      String cof = findCofactorPairs(r);
      cofactor_sample.put(cof, r);
      if (!cofactors.containsKey(cof))
        cofactors.put(cof, new ArrayList<Integer>());
      cofactors.get(cof).add(r.getUUID());
    }

    for (String cof : cofactors.keySet()) {
      Reaction rr = cofactor_sample.get(cof);
      Logger.println(0, "\t" + cof + "\t" + rr.getUUID() + "\t" + rr.getReactionName() + "\t" + cofactors.get(cof).toString());
    }
  }

  private String findCofactorPairs(Reaction r) {
    List<String> cofactorsS_smile = new ArrayList<String>(), cofactorsP_smile = new ArrayList<String>();
    List<Long> cofactorsS_id = new ArrayList<Long>(), cofactorsP_id = new ArrayList<Long>();
    List<String> cofactorsS_name = new ArrayList<String>(), cofactorsP_name = new ArrayList<String>();
    for (long s : r.getSubstrates()) {
      Chemical substr = this.DB.getChemicalFromChemicalUUID(s);
      String smiles = substr.getSmiles();
      if (smiles == null)
        continue;

      if (substr.isCofactor()) {
        cofactorsS_smile.add(smiles);
        cofactorsS_id.add(s);
        cofactorsS_name.add(substr.getBrendaNames().get(0));
      }
    }
    for (long p : r.getProducts()) {
      Chemical prod = this.DB.getChemicalFromChemicalUUID(p);
      String smiles = prod.getSmiles();
      if (smiles == null)
        continue;

      if (prod.isCofactor()) {
        cofactorsP_smile.add(smiles);
        cofactorsP_id.add(p);
        cofactorsP_name.add(prod.getBrendaNames().get(0));
      }
    }

    return cofactorsS_name.toString() + "\t-->\t" + cofactorsP_name.toString() +
        "\t\t\t" + cofactorsS_id.toString() + "\t-->\t" + cofactorsP_id.toString() +
        "\t\t\t" + cofactorsS_smile.toString() + "\t>>\t" + cofactorsP_smile.toString();

  }

  public void AAMCofactorPairs(List<Long[]> cofactors_left, List<Long[]> cofactors_right) {

    Indigo ind = new Indigo();
    for (int i =0; i < cofactors_left.size(); i++) {
      Long[] subs = cofactors_left.get(i);
      Long[] prod = cofactors_right.get(i);

      String substrates = "";
      for (Long s : subs) {
        Chemical substr = this.DB.getChemicalFromChemicalUUID(s);
        String smiles = substr.getSmiles();
        if (smiles == null)
          System.err.println("Smiles == NULL for chemical " + s + ". Abort.");

        substrates += substrates.equals("") ? smiles : ("." + smiles);
      }

      String products = "";
      for (Long p : prod) {
        Chemical product = this.DB.getChemicalFromChemicalUUID(p);
        String smiles = product.getSmiles();
        if (smiles == null)
          System.err.println("Smiles == NULL for chemical " + p + ". Abort.");

        products += products.equals("") ? smiles : ("." + smiles);
      }

      String rxn_str = substrates + ">>" + products;

      IndigoObject rxn = ind.loadReaction(rxn_str);
      rxn.automap("keep");
      System.out.println(i + "\t" + rxn.smiles() + "\t" + rxn_str);
      SMILES.renderReaction(rxn, "reactionTest-" + i + ".png", "comment", ind);
    }
  }

  private void logCRO(TheoryROs roSet, Reaction r) {
    try {
      String log = r.getUUID() + "\t" + r.getECNum() + "\t" + roSet.CRO() + "\t" + roSet.ERO() + "\n";
      this.TheoryROf.write(log); this.TheoryROf.flush();
    } catch (IOException e) {
      System.err.println("Could not write to TheoryRO logging file, the reaction: " + r);
    }
  }

  private P<List<String>, List<String>> balanceFullRxn(int id, List<String> substrates, List<String> products,
                                                       HashMap<Atom, Integer> deltaFullRxn) throws MalFormedReactionException {

    String substrateFullStr = "", productFullStr = "";
    for (int i = 0; i < substrates.size(); i++) {
      String substrate = substrates.get(i);
      substrateFullStr += (substrateFullStr.isEmpty() ? "" : ".") + substrate;
    }
    for (int i = 0; i < products.size(); i++){
      String product = products.get(i);
      productFullStr += (productFullStr.isEmpty() ? "" : ".") + product;
    }

    // balance the full reaction... (( This is only for debugging purposes ))
    // instead of the reaction being a string we keep the prodicts and substrates apart...
    P<List<String>,List<String>> reactionFullStr = new P<List<String>,List<String>>(substrates, products);
    if (!deltaFullRxn.isEmpty())
      reactionFullStr = balanceRxn(id, reactionFullStr, deltaFullRxn);

    return reactionFullStr;
  }

  public static P<List<String>,List<String>> balanceTheReducedReaction(int id, List<String> sReduced, List<String> pReduced) throws MalFormedReactionException {

    // compute the delta in the reduced reaction
    MolGraph substrateG = SMILES.ToGraph(sReduced);
    MolGraph productG = SMILES.ToGraph(pReduced);
    HashMap<Atom, Integer> deltaRxn = checkBalancedReaction(substrateG, productG);

    // balance the reduced reaction...
    P<List<String>, List<String>> reactionStr = new P<List<String>, List<String>>(sReduced, pReduced);
    if (!deltaRxn.isEmpty())
      reactionStr = balanceRxn(id, reactionStr, deltaRxn);

    return reactionStr;
  }

  protected FilteredReaction removePairedRedundantMolecules(
      int id, List<String> substrates, List<String> products,
      List<Double> substratePromiscuity, List<Double> productPromiscuity) {

    HashMap<P<String, String>, Double> pairedPromiscuity = SMILES.computePairingsSmiles(substrates, products, substratePromiscuity, productPromiscuity);

    // for paired removals; we have a single threshold on the pair
    Double threshold = computeRarityThreshold(pairedPromiscuity.values());

    List<String> substratesReduced = new ArrayList<String>();
    List<String> productsReduced = new ArrayList<String>();
    String substrateStr = "", productStr = "";
    for (P<String, String> pair : pairedPromiscuity.keySet()) {
      String substrate = pair.fst(), product = pair.snd();
      if (substrate == null) {
        // default keep product as it was not paired (product has to be not null)
        productStr += (productStr.isEmpty() ? "" : ".") + product;
        productsReduced.add(product);
        continue;
      }
      if (product == null) {
        // default keep substrate as it was not paired (substrate has to be not null)
        substrateStr += (substrateStr.isEmpty() ? "" : ".") + substrate;
        substratesReduced.add(substrate);
        continue;
      }
      Double combinedPromiscuity = pairedPromiscuity.get(pair);
      // we get a pair of (substrate, product) that is matched and has a combinedPromiscuity
      boolean keep = decideKeepPair(pair, combinedPromiscuity, threshold);
      if (keep) {
        substrateStr += (substrateStr.isEmpty() ? "" : ".") + substrate;
        productStr += (productStr.isEmpty() ? "" : ".") + product;
        substratesReduced.add(substrate);
        productsReduced.add(product);
      }
      Logger.printf(1, "[%s] %f <= %f (midway) => keep ... + size considerations: Pair (%s, %s)\n", keep?"kept":"dltd", combinedPromiscuity, threshold, substrate, product);
    }

    return new FilteredReaction(substrateStr, productStr, substratesReduced, productsReduced);
  }

  private FilteredReaction removeRedundantMolecules(int id,
                                                    List<String> substrates, List<String> products,
                                                    List<Double> substratePromiscuity, List<Double> productPromiscuity) {

    Double substrThreshold = computeRarityThreshold(substratePromiscuity), productThreshold = computeRarityThreshold(productPromiscuity); // by default keep everything, unless updated for rarity picking

    List<String> substratesReduced = new ArrayList<String>();
    List<String> productsReduced = new ArrayList<String>();
    String substrateStr = "", productStr = "";
    for (int i = 0; i < substrates.size(); i++) {
      String substrate = substrates.get(i);
      boolean keep = decideKeep(i, substratePromiscuity, substrThreshold, substrates);
      if (keep) {
        substrateStr += (substrateStr.isEmpty() ? "" : ".") + substrate;
        substratesReduced.add(substrate);
      }
      Logger.printf(1, "[%s] Substrat [%f vs midway %f]: %s\n", keep?"kept":"dltd", substratePromiscuity.get(i), substrThreshold, substrates.get(i));
    }
    for (int i = 0; i < products.size(); i++){
      String product = products.get(i);
      boolean keep = decideKeep(i, productPromiscuity, productThreshold, products);
      if (keep) {
        productStr += (productStr.isEmpty() ? "" : ".") + product;
        productsReduced.add(product);
      }
      Logger.printf(1, "[%s] Products [%f vs midway %f]: %s\n", keep?"kept":"dltd", productPromiscuity.get(i), productThreshold, products.get(i));
    }

    return new FilteredReaction(substrateStr, productStr, substratesReduced, productsReduced);
  }

  protected class FilteredReaction {
    public FilteredReaction(String s, String p, List<String> sReduced, List<String> pReduced) {
      this.substrateStr = s;
      this.productStr = p;
      this.setSubstratesFiltered(sReduced);
      this.setProductsFiltered(pReduced);
    }

    private void setSubstratesFiltered(List<String> substratesFiltered) {
      this.substratesFiltered = substratesFiltered;
    }

    public List<String> getSubstratesFiltered() {
      return substratesFiltered;
    }

    private void setProductsFiltered(List<String> productsFiltered) {
      this.productsFiltered = productsFiltered;
    }

    public List<String> getProductsFiltered() {
      return productsFiltered;
    }

    String substrateStr, productStr;
    private List<String> substratesFiltered;
    private List<String> productsFiltered;
  }

  private boolean decideKeepPair(P<String, String> pair, Double promiscuity, Double threshold) {
    return false
        || pair.fst().length() + pair.snd().length() <= 80
        || promiscuity <= threshold; // if they are (large molecules, and) below our threshold then throw them away
  }

  private boolean decideKeep(int i, List<Double> promiscuity, Double threshold, List<String> smiles) {
    return false
        || smiles.get(i).length() <= 50 // only consider throwing away very large molecules as they break our subgraph matching...
        || promiscuity.get(i) <= threshold; // if for a large molecule is below our threshold then throw it away.
  }

  private static Double computeRarityThreshold(Collection<Double> collection) {
    List<Double> copy = new ArrayList<Double>(); copy.addAll(collection);
    Collections.sort(copy);

    int atIndex = 0;
    double maxdistance = 0.0; // between two rarity scores

    for (int i = 0; i < copy.size() - 1; i++)
      // >= (as opposed to >) because if equal then we want to
      // be inclusive and keep as many chemicals as possible; so take the higher scores
      if (copy.get(i+1) - copy.get(i) >= maxdistance) {
        atIndex = i;
        maxdistance = copy.get(i+1) - copy.get(i);
      }
    // pick the point midway between the largest gap in the scores
    // that way we can fix (rare1, rare2, common) and (rare1, common1, common2), (rare1)
    // to (rare1, rare2), (rare1), (rare1) respectively.
    //
    // assuming that for singular elements, their score (i.e., probability distribution
    // amongst different chemicals) is 1.0.
    return copy.get(atIndex) + maxdistance/2;
  }

  private static P<List<String>,List<String>> balanceRxn(int id, P<List<String>,List<String>> rxnSmiles, HashMap<Atom, Integer> delta) throws MalFormedReactionException {

    // there are certain patterns of delta's that we recognize... and fix based on those
    P<String[], String[]> add = getBalancer(delta);

    if (add == null) {
      // we are about to abort exit; so it does not matter how many resources we use here...
      Indigo indigo = new Indigo();
      // create dir if it does not exists...
      new File("unprocessedrxns").mkdir();
      String fullRxn = SMILES.convertToSMILESRxn(rxnSmiles);
      SMILES.renderReaction(indigo.loadReaction(fullRxn), "unprocessedrxns/rxn-unbalanced-" + id + ".png", "Diff: " + delta, indigo);
      System.err.println("We do not know how to balance a reaction missing (+ is gained, - is lost): " + delta);
      throw new MalFormedReactionException("Unbalaned reaction. Missing (+ is gained, - is lost): " + delta);
    }

    List<String> newRxnS = new ArrayList<String>(rxnSmiles.fst());
    List<String> newRxnP = new ArrayList<String>(rxnSmiles.snd());
    if (add.fst() != null)
      for (String s : add.fst())
        newRxnS.add(s);

    if (add.snd() != null)
      for (String p : add.snd())
        newRxnP.add(p);

    Logger.printf(1,"[balanceRxn] Known how to balance reaction. \n[balanceRxn] %s\n[balanceRxn] ...converted to...\n[balanceRxn] %s\n", rxnSmiles, newRxnS+">>"+newRxnP);
    return new P<List<String>, List<String>>(newRxnS, newRxnP);
  }

  public static P<String[], String[]> getBalancer(HashMap<Atom, Integer> delta) {
    Integer extraHs;
    for (BalancingPattern patt : KnownBalancingPatterns) {
      if ((extraHs = patt.matchesPattern(delta)) != null) {
        String[] substrates, products;
        int H = Math.abs(extraHs);
        int substratesLen = patt.substrateAdd == null ? 0 : patt.substrateAdd.length;
        int productsLen = patt.productAdd == null ? 0 : patt.productAdd.length;
        // (SEE big explanation at end of BalancingPattern.matchesPattern for why the check is extraH<0 and not the other way around)
        if (extraHs < 0) { // - is lost; and therefore we need to add to the products side...
          // add the H's the products side
          substrates = new String[substratesLen]; products = new String[productsLen + H];
          for (int i = 0; i< substratesLen; i++) substrates[i] = patt.substrateAdd[i];
          for (int i = 0; i< productsLen; i++) products[i] = patt.productAdd[i];
          for (int i = productsLen; i< productsLen + H; i++) products[i] = "[H+]";
        } else {
          // add the H's the substrates side
          substrates = new String[substratesLen + H]; products = new String[productsLen];
          for (int i = 0; i< substratesLen; i++) substrates[i] = patt.substrateAdd[i];
          for (int i = 0; i< productsLen; i++) products[i] = patt.productAdd[i];
          for (int i = substratesLen; i<substratesLen + H; i++) substrates[i] = "[H+]";
        }
        return new P<String[], String[]>(substrates, products);
      }
    }

    return null; // could not find any pattern we recognize...
  }

  private CRO doGraphMatchingDiff(MolGraph substrateG, MolGraph productG) {
    SimpleNeighborAggregator aggrFns = SimpleNeighborAggregator.getInstance();

    MolGraphFactorer substrateFactors = new MolGraphFactorer(substrateG, aggrFns);
    MolGraphFactorer productFactors = new MolGraphFactorer(productG, aggrFns);

    // See comments in GraphAlignment.computeGraphDiff why products is the first argument and substrate is the second
    GraphAlignment align = new GraphAlignment(productFactors, substrateFactors);
    List<P<MolGraph, MolGraph>> diff = align.computeGraphDiff(1); // compute 1 optimal difference...
    Logger.println(1, "Reaction operator graph: " + diff);

    System.err.println("Not computing CRO for graph diff code right now. Use doIndigoAAMDiff."); System.exit(-1);
    return null;
  }

  public static HashMap<Atom, Integer> checkBalancedReaction(MolGraph substrateG, MolGraph productG) {
    int sz1 = substrateG.MaxNodeIDContained();
    int sz2 = productG.MaxNodeIDContained();
    HashMap<Atom, Integer> delta = new HashMap<Atom, Integer>();
    if (sz2 != sz1) {
      int deltaDir = 0; // is more atoms in products then +1, if more atoms in the substrates then -1
      Logger.println(1, "Imbalanced reaction? Atom set size not the same: "
          + "size(substrates)="
          + sz1
          + " vs size(products)="
          + sz2 + "\n" + substrateG + "\n vs \n" + productG);
      MolGraph smaller, larger;
      if (sz1 < sz2) {
        smaller = substrateG;
        larger = productG;
        deltaDir = +1; // gained some atoms in the products
      } else {
        smaller = productG;
        larger = substrateG;
        deltaDir = -1; // lost some atoms in the products
      }
      HashMap<Atom, Integer> atomCounts = new HashMap<Atom, Integer>();
      for (Integer n : larger.Nodes().keySet()) {
        Atom atom = larger.Nodes().get(n);
        if (!atomCounts.containsKey(atom))
          atomCounts.put(atom, 1);
        else
          atomCounts.put(atom, atomCounts.get(atom) + 1);
      }
      for (Integer n : smaller.Nodes().keySet()) {
        Atom atom = smaller.Nodes().get(n);
        if (!atomCounts.containsKey(atom))
          System.err.println("Woa!! New atom type in smaller!!! "
              + atom + "{" + n + "}");
        else
          atomCounts.put(atom, atomCounts.get(atom) - 1);
      }
      int count;
      for (Atom atom : atomCounts.keySet())
        if ((count = atomCounts.get(atom)) > 0) {
          Logger.println(1, "[checkBalancedReaction] Extra atoms: " + atom + " * " + count);
          delta.put(atom, deltaDir * count); // if more atoms in the products then positive delta; if more in the substrate then negative delta
        }
    }
    return delta;
  }

  private void logTiming(long id, long elapsed, String location) {
    try {
      // create file is it does not exist
      File logf = new File("RO-timings-" + location + ".txt");
      BufferedWriter out = new BufferedWriter(new FileWriter(logf, true /* append */));
      out.append(id + "," + elapsed + "\n");
      out.close();
    } catch (IOException e) {
      System.err.format("Could not log timing: (rxn=%s, time=%s)\n", id, elapsed);
    }
  }
}
