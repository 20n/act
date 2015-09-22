package act.server.Molecules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;


import act.graph.Edge;
import act.graph.EdmondsMatching;
import act.graph.Graph;
import act.graph.GraphMatching;
import act.graph.Node;
import act.server.Logger;
import act.server.EnumPath.Enumerator;
import act.server.StableMoiety.InvariantSubMolecules;
import act.shared.AAMFailException;
import act.shared.MalFormedReactionException;
import act.shared.OperatorInferFailException;
import act.shared.SMARTSCanonicalizationException;
import act.shared.helpers.P;
import act.shared.helpers.T;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoRenderer;

class RefInt {
  int i;
  public int Int() { return this.i; }
  RefInt(int i) { this.i = i; }
  public int preincr() { this.i++; return this.i; } // ++i
  public int postincr() { this.i++; return this.i - 1; } // i++
  public void incr() { this.i++; }
}

public class SMILES
{
  private static boolean _CanonicalizeQuerySMILES = true;
    /*
     *
     * Using the INDIGO library
     *
     */

  /*
   * Reaction: From Smiles to CRO, MCS
   */

  public static String convertToSMILESRxn(P<List<String>, List<String>> rxn) {
    String sub = "", prd = "";
    for (String s : rxn.fst())
      sub += sub.isEmpty() ? s : ("." + s);
    for (String p : rxn.snd())
      prd += prd.isEmpty() ? p : ("." + p);
    return sub + ">>" + prd;
  }

  // Computes a maximal preserved set, i.e., conceptually the opposite of CRO, but used for MCS computation
  public static MolGraph GetMaxPreserved(P<List<String>, List<String>> pseudoRxn) throws AAMFailException, MalFormedReactionException {
    Indigo indigo = new Indigo();
    System.out.println("Using getSanitizedReactionObjRadicals (in place of getSanitizedReactionObj) sanitize out =/# bonds, charges, into radicals");
    // IndigoObject reaction = getSanitizedReactionObj(rxn, indigo);
    IndigoObject reaction = DotNotation.ToDotNotationRxn(pseudoRxn, indigo);
    T<MolGraph, MolGraph, HashMap<Integer, Integer>> rxnGraphs = AnnotateReaction(reaction, -1, "mcs-pseudo-rxn.png", indigo);

    MolGraph G1 = rxnGraphs.fst();
    MolGraph G2 = rxnGraphs.snd();
    HashMap<Integer, Integer> map = rxnGraphs.third();

    MolGraph preserved = getGraphPreserved(G2, G1, map);
    return DotNotation.ToNormalMol(preserved, G2); // TODO: Is this G2, or G1, just guessing here...
  }

  // Compute transformation in terms of bond changes, i.e., a BRO
  public static BRO computeBondRO(List<String> substrates, List<String> products) {
    HashMap<Bond, Integer> delta = new HashMap<Bond, Integer>();
    // now go through each substrate and product...
    for (String s : substrates) {
      augmentBondSet(delta, s, -1); // for each bond seen in the substrates we say it was deleted/removed
    }
    for (String p : products) {
      augmentBondSet(delta, p, +1); // for each bond seed in the products we say it was added/made
    }
    // divide by two since each bond was counted twice, one from each of its endpoints.
    for (Bond b : delta.keySet()) {
      if (delta.get(b) % 2 != 0)
      {System.err.println("This cannot happen, each bond MUST have been counted twice..."); System.exit(-1);}
      delta.put(b, delta.get(b)/2);
    }

    // -c indicates that there were more bonds in the substrates than in the products
    // +c indicates that there were more bonds in the products than in the substrates
    // therefore -c indicates that those bonds were removed in the reaction
    // and ..... +c indicates that those bonds were made in the reaction
    return new BRO(delta);
  }

  private static void augmentBondSet(HashMap<Bond, Integer> delta, String cmpd, int negOrPos) {
    Indigo indigo = new Indigo();
    IndigoObject mol = indigo.loadMolecule(cmpd);
    MolGraph molG = ToGraph(mol);

    List<Integer> atomIds = molG.GetNodeIDs();
    for (Integer atomId : atomIds) {
      Atom atom = molG.GetNodeType(atomId);
      for (P<Integer, BondType> edge : molG.AdjList().get(atomId)) {
        Atom atom2 = molG.GetNodeType(edge.fst());
        BondType bondTyp = edge.snd();

        Bond bond = new Bond(atom, bondTyp, atom2);

        if (!delta.containsKey(bond))
          delta.put(bond, 0);
        delta.put(bond, delta.get(bond) + negOrPos);
      }
    }
  }

  // Computes a minimal transformation set, i.e., CRO
  public static TheoryROs ToReactionTransform(int id, P<List<String>,List<String>> rxn, BRO bro) throws AAMFailException, MalFormedReactionException, OperatorInferFailException, SMARTSCanonicalizationException {
    // BRO bro = computeBondRO(rxn.fst(), rxn.snd());

    MolGraph added, subtr; RxnWithWildCards ro;

    // String reactionStr = convertToSMILESRxn(rxn);
    Indigo indigo = new Indigo();
    // IndigoObject reaction = getSanitizedReactionObj(rxn, indigo);
    IndigoObject reaction = DotNotation.ToDotNotationRxn(rxn, indigo);
    T<MolGraph, MolGraph, HashMap<Integer, Integer>> rxnGraphs = AnnotateReaction(reaction, id, "before-diff-rxn.png", indigo);

    MolGraph substrateG = rxnGraphs.fst();
    MolGraph productG = rxnGraphs.snd();
    HashMap<Integer, Integer> mapProducts2Substates = rxnGraphs.third();

    // before permuting the product atoms, we extend the map to cover all
    // vertices in the product. Those vertices that are unmapped are
    // mapped to indices larger than any that already exist in the substrate

    mapProducts2Substates = extendMapToAllNodes(mapProducts2Substates, productG, substrateG);

    Logger.println(1, "Graph Reactant: " + substrateG);
    Logger.println(1, "Graph Products: " + productG);
    Logger.println(1, "Map: " + mapProducts2Substates);

    MolGraph constantGraph = InvariantSubMolecules.getConstantGraph(productG, substrateG, mapProducts2Substates);

    P<MolGraph, MolGraph> diff = getGraphDiff(productG, substrateG, mapProducts2Substates);

    added = diff.fst(); subtr = diff.snd();

    Logger.println(2, "Added: " + added + "\nDeleted: " + subtr);
    Logger.println(5, "ProductG: " + productG + "\n" + "SubstrateG: " + substrateG);

    ro = getROWithWildCards(id, productG, added, substrateG, subtr, mapProducts2Substates);
    CRO cro = new CRO(ro);

    // since "added" is in terms of permuted nodeids, the first call needs the mapping function so that we can
    // productG.permute(map) to relate back to added. The second call does not need a permutation (so null passed)
    added = expandCRO2ERO(productG, added, mapProducts2Substates);
    subtr = expandCRO2ERO(substrateG, subtr, null);
    ro = getROWithWildCards(id, productG, added, substrateG, subtr, mapProducts2Substates);
    ERO ero = new ERO(ro);

    Logger.printf(0, "[ToReactionTransform] Graph diff: %s\n[ToReactionTransform] BRO computed[%10d]: %s\n[ToReactionTransform] CRO computed[%10d]: %s\n[ToReactionTransform] ERO computed[%10d]: %s\n", diff, bro.ID(), bro, cro.ID(), cro, ero.ID(), ero);
    return new TheoryROs(bro, cro, ero);
  }

  @Deprecated
  private static IndigoObject radicalized(IndigoObject molecule) {
    // System.out.println("-- sanitize out =/# bonds, charges, into radicals");
    molecule.dearomatize(); // remove any aromatic representations
    for (IndigoObject bond : molecule.iterateBonds()) {
      switch (bond.bondOrder()) {
      case 1: // single bond, do nothing
        break;
      case 2: // double bond, put a radical on each atom on the side
        convertToIndigoRadical(bond.source(), bond.source().radical() + 1);
        convertToIndigoRadical(bond.destination(), bond.destination().radical() + 1);
        bond.setBondOrder(1);
        break;
      case 3: // triple bond, put two radical on each atom on the side
        convertToIndigoRadical(bond.source(), bond.source().radical() + 2);
        convertToIndigoRadical(bond.destination(), bond.destination().radical() + 2);
        bond.setBondOrder(1);
        break;
      case 4:
        System.err.println("Woa! We dearomatized. Why is an aromatic bond here."); System.exit(-1);
        break;
      }
    }

    // first handle all negative charges as they are just electrons
    for (IndigoObject atom : molecule.iterateAtoms())
      if (atom.charge() < 0) {
        convertToIndigoRadical(atom, atom.radical() + (-atom.charge())); // the negative charge actually adds to the radical count, so invert it
        atom.setCharge(0); // all negative charge has been moved as radicals on the atom
      }
    for (IndigoObject atom : molecule.iterateAtoms())
      if (atom.charge() > 0)
        if (atom.radical() >= atom.charge()) {
          convertToIndigoRadical(atom, atom.radical() - atom.charge()); // subtract the positive charge from the radical count
          atom.setCharge(0); // the charge has been neutralized
        } else {
          atom.setCharge(atom.charge() - atom.radical()); // substract the number of + charges from the number of radicals on it
          convertToIndigoRadical(atom, 0); // remove the radical count
        }

    return molecule;
  }

  @Deprecated
  private static void convertToIndigoRadical(IndigoObject atom, int radical) {
    switch (radical) {
    case 0: atom.resetRadical(); return;
    case 1: atom.setRadical(Indigo.SINGLET); return;
    case 2: atom.setRadical(Indigo.DOUBLET); return;
    case 3: atom.setRadical(Indigo.TRIPLET); return;
    default: System.err.println("Whops. Dont have a representation for these many radicals: " + radical); System.exit(-1);
    }
  }

  @Deprecated
  private static IndigoObject getSanitizedReactionObj(P<List<String>, List<String>> rxn, Indigo indigo) {
    IndigoObject r = indigo.createReaction();

    for (String s : rxn.fst())
      r.addReactant(indigo.loadMolecule(s));
    for (String p : rxn.snd())
      r.addProduct(indigo.loadMolecule(p));

    r.smiles(); // if this fails then we have a problematic element in our molecules (maybe an extended smiles?)

    return r;
  }

  private static IndigoObject getSanitizedReactionObjDeprecated(String reactionStr) {
    Indigo indigo = new Indigo();
    IndigoObject o = indigo.loadReaction(reactionStr);
    o.smiles(); // if this fails then we need to fix the reaction String...
    return o;
  }

  private static RxnWithWildCards getROWithWildCards(int id, MolGraph productG, MolGraph add, MolGraph substrateG, MolGraph sub, HashMap<Integer, Integer> map) throws OperatorInferFailException, SMARTSCanonicalizationException {
    WithWildCards added_w_wc = addWCAtoms(productG, add, map);
    WithWildCards subtr_w_wc = addWCAtoms(substrateG, sub, null);

    MolGraph substrates = subtr_w_wc.getComponents();
    MolGraph products = added_w_wc.getComponents();

    return new RxnWithWildCards(
        ReactionFromGraphs(id, substrates, products),
        subtr_w_wc.constraints, // subtracts are from the substrates (constraint_substrates)
        added_w_wc.constraints  // adds are to the products (constraint_products)
        );
  }

  private static WithWildCards addWCAtoms(MolGraph parentGraph, MolGraph subgraphThatWasModified, HashMap<Integer, Integer> map) {
    if (map != null) {
      parentGraph = parentGraph.permute(map); // instantiates new, so no need to worry about overwriting old.
    }
    subgraphThatWasModified = subgraphThatWasModified.duplicate();

    WithWildCards r = new WithWildCards(parentGraph);
    r.createWCAtoms(subgraphThatWasModified);
    return r;
  }

    private static String ReactionFromGraphs(int id, MolGraph substrates, MolGraph products) throws OperatorInferFailException, SMARTSCanonicalizationException {
    Indigo indigo = new Indigo();

        if (_CanonicalizeQuerySMILES) {
          return canonicalizeQuerySMILES(substrates, products, indigo);
        } else {
            IndigoObject rxn = indigo.createQueryReaction();
          rxn.addReactant(fromDisjointMolGraphToIndigoObject(indigo, substrates));
          rxn.addProduct(fromDisjointMolGraphToIndigoObject(indigo, products));
          try {
            return HACKY_fixQueryAtomsMapping(rxn.smiles());
          } catch (IndigoException e) {
            String msg = "Rxn " + id + "'s RO contains a not-well-formed molecule: " + e.getMessage();
            throw new OperatorInferFailException(msg);
          }
        }
    }

  private static String canonicalizeQuerySMILES(MolGraph substrates, MolGraph products, Indigo indigo) throws SMARTSCanonicalizationException {
    Logger.println(5, "[canonicalizeQuerySMILES] Substrates: " + substrates.toString() + "\n[canonicalizeQuerySMILES] Products: " + products.toString());

    String src, dst, srcNonCanon, dstNonCanon; boolean debug;
    try {
          src = fromDisjointMolGraphToCanonicalSMILES(indigo, substrates, debug=true);
          dst = fromDisjointMolGraphToCanonicalSMILES(indigo, products, debug=true);

          srcNonCanon = fromDisjointMolGraphToNonCanonicalButNumberedSMILES(indigo, substrates);
          dstNonCanon = fromDisjointMolGraphToNonCanonicalButNumberedSMILES(indigo, products);
          // these source and dst strings do not contain any mapping information, so we have to try all possibilities...

        } catch (IndigoException e) {
          String msg = e.getMessage();
          String reason = "unknown";
          if (msg.contains("core: Unknown radical type"))
            reason = "produced a molecule with radical set. how did this happen?";

          Logger.println(0, "[RO Canonicalization] Failed because: " + reason);
          throw new SMARTSCanonicalizationException(reason);
    }

        String molSrcConcrete = replaceStarsWithHeavyAtoms(src);
        int numStarsSrc = StringUtils.countMatches(src, "*");
        Integer[] order = new Integer[numStarsSrc]; for (int i=0;i<numStarsSrc; i++) order[i] = i+1;
        String srcNumbered = seqNumberStars(src, order); // sequentially number the *'s in src
        int numStars = StringUtils.countMatches(dst, "*");
        if (numStars == 0)
          return src + ">>" + dst;
        String spec = srcNonCanon + ">>" + dstNonCanon;
        if (spec.equals(">>"))
          return ">>";
        List<String> dstNumberedList = enumeratePermutationsOfStars(dst, numStars);

        try {

          Logger.println(5, "[canonicalizeQuerySMILES] testcase: " + molSrcConcrete);
          Logger.println(5, "[canonicalizeQuerySMILES] rxn to canonlicalize: " + spec);

          String correctOutMol = RxnTx.naiveApplyOpOneProduct(molSrcConcrete, spec, indigo);

          String correctRxn = null;
          for (String dstNumbered : dstNumberedList) {
            String candidateRxn = srcNumbered + ">>" + dstNumbered;
            String candidateOut = RxnTx.naiveApplyOpOneProduct(molSrcConcrete, candidateRxn, indigo);
            if (candidateOut.equals(correctOutMol))
            {
              correctRxn = candidateRxn;
              break;
            }
          }
          if (correctRxn == null) {
            Logger.println(0, "[canonicalizeQuerySMILES] Canonical rxn find failed. If we imposed artificial limit, ah well. Otherwise WTF!?");
            throw new SMARTSCanonicalizationException("No enumeration is correct. Either we imposed aritificial limit (ah well), or none found (wtf!)");
          } else {
            Logger.println(0, "[canonicalizeQuerySMILES] Canonical rxn: " + correctRxn);
          }
          return correctRxn;

        } catch (IndigoException e) {
          String msg = e.getMessage();
          String reason = "unknown";
          if (msg.contains("Reaction product enumerator state: Incorrect AAM"))
            reason = "Computed RO has mismatched wildcards.";

          Logger.println(0, "[RO Canonicalization] Failed because: " + reason);
          throw new SMARTSCanonicalizationException(reason);
        }
  }

  private static String replaceStarsWithHeavyAtoms(String srcQuerySMILES) throws SMARTSCanonicalizationException {
    // replace each [*,H] with [#num] where num is increasing from front to end starting at NodeType.allAtomicNumsAreUnder
    StringBuffer srcSeq = new StringBuffer();
    int atom = Element.expectedAtomicNumsAreUnder + 1;
    int i=0; while (i<srcQuerySMILES.length()) {
      int star = srcQuerySMILES.indexOf("[H,*]", i);
      if (star == -1) break;
      srcSeq.append(srcQuerySMILES.substring(i, star + 1)); // append everything preceeding the wildcard and including the [
      srcSeq.append(Element.getExtraHeavyAtoms(atom)); // actually put in the symbol for the heavy atom!
      atom++;
      i = star + 4; // exclude the "H,*" but then continue from ] onwards
    }
    srcSeq.append(srcQuerySMILES.substring(i, srcQuerySMILES.length()));
    return srcSeq.toString();
  }

  private static int _maxStarsEnumerate = 7; // will allow 7! = 5040, 8! is 40320
  private static int _maxTestCases = 6000; // incase #stars exceeds _maxStarsEnumerate (i.e., number of permutation needed to test > 7!), we will truncate to 6000 cases

  private static List<String> enumeratePermutationsOfStars(String dst, int N) {
    if (N > _maxStarsEnumerate) {
      Logger.println(0, "[Canonicalize by testing] Enumeration will be of size (" + N + "!)");
      Logger.println(0, "[Canonicalize by testing] Artificial limit of " + _maxTestCases + " testcases will kick in. So algorithm will be incomplete.");
    }
    List<String> permuted = new ArrayList<String>();
    if (N <= 0) return permuted;

    Integer[] perm = new Integer[N];
    for (int i=0; i<N; i++) perm[i] = i+1;
    int caseNum = 0;
    while (true) {
      caseNum++;
      if (caseNum > _maxTestCases)
        break;
      // System.out.println("Permutation: " + Arrays.asList(perm));
      permuted.add(seqNumberStars(dst, perm));

      int i = -1,j = -1, t, k;
      for (k=N-2; k>=0; k--)
        if (perm[k] < perm[k+1]) { i=k; break; }
      if (i == -1) { break; }
      for (k=N-1; k>i; k--)
        if (perm[i] < perm[k]) { j=k; break; }
      swap(perm, i, j);
      for (k=i+1, t=N-1; k<t; k++, t--)
        swap(perm, k, t);
    }
    return permuted;
  }

  private static void swap(Integer[] perm, int i, int j) {
    int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
  }

  private static String seqNumberStars(String src, Integer[] numbering) {
    StringBuffer srcSeq = new StringBuffer();
    int index = 0;
    int i=0; while (i<src.length()) {
      int star = src.indexOf('*', i);
      if (star == -1) break;
      srcSeq.append(src.substring(i, star + 1)); // include the star into the append
      srcSeq.append(":" + numbering[index]);
      index++;
      i = star + 1;
    }
    srcSeq.append(src.substring(i, src.length()));
    return srcSeq.toString();
  }

  public static String HACKY_fixQueryAtomsMapping(String smiles) {
    // Convert strings of the form
    // [*]C([*])=O>>[H]C([*])(O[H])[*] |$[*:3];;[*:8];;;;[*:8];;;[*:3]$|
    // or [H]C(=[*])[*].O([H])[H]>>[*]C(=[*])O |f:0.1,$;;_R2;_R1;;;;_R1;;_R2;$|
    // to real query strings:
    // [*:3]C([*:8])=O>>[H]C([*:3])(O[H])[*:8]
    //
    // Also, make sure that there are no 0-indexes. 1-indexed is what we want.

    int mid = smiles.indexOf("|");

    // sometimes, e.g., when computing EROs over very small molecules, the ERO is the entire concrete string
    // with no wild cards; and so no R groups appear there.. In that case we will not have have the " |$_R1;;_R2;;;;_R1;;_R2;$|"
    // portion of the string to lookup into. So return the input...
    if (mid == -1) {
      if (!smiles.contains("_R")) {
        Logger.printf(0, "HACKY_FIX: Does not contain _R Smiles %s\n", smiles);
        return smiles;
      } else {
        System.err.println("Query smiles with unexpected format encountered: " + smiles);
        System.exit(-1);
      }
    }

    String smiles_unindexed = smiles.substring(0, mid);
    String smiles_indexes = smiles.substring(mid);
    // System.out.format("{%s} {%s}\n", smiles_unindexed, smiles_indexes);

    int ptr = 0, ptr_ind = 0;
    while ((ptr = smiles_unindexed.indexOf("[*]", ptr)) != -1) {
      // extract the next index from smiles_indexes and insert it here...
      ptr_ind = smiles_indexes.indexOf("_R", ptr_ind);
      int end = ptr_ind+2;
      char c;
      while ((c = smiles_indexes.charAt(end)) >= '0' && c <= '9') end++;
      int index = Integer.parseInt(smiles_indexes.substring(ptr_ind + 2, end));

      // Look at email thread titled "[indigo-general] Re: applying reaction smarts to enumerate products"
      // for why we need to convert each [*] |$_R1 to [H,*:1]
      //
      // Quote:`` I my previous letter I wrote that there is bug that [*,H] and [H,*] has different meanings.
      //          I realized that it is not bug. [H,*] mean "any atom except H, or H", while [*,H] means
      //          "any atom except H, or any atom except H with 1 connected hydrogen". ''

      // now update the string.. and the pointers...
      smiles_unindexed =
          smiles_unindexed.substring(0, ptr+1) + // grab everything before and including the `['
          "H,*:" + index + // add the H,*:1
          smiles_unindexed.substring(ptr+2); // grab everything after the *], excluding the `*', but including the `]'
      ptr += 6; // need to jump at least six chars to be at the ending `]'...
      ptr_ind = end; // the indexes pointer needs to be moved past the end of the index [*:124]
    }
    Logger.printf(0, "HACKY_FIX: fixed %s to be %s\n", smiles, smiles_unindexed);

    return smiles_unindexed;
  }

  public static String replaceWithStars(String smiles, boolean keep_indices) {

    // System.out.println("Need to replace all maxAtomicNum+id atomic numbers with [*:id] ");
    // also all [H]'s

    // Convert strings of the form
    // [*]C([*])([*])O[*] |$[#91:0];;[#91:0];[H];;[H]$|
    // to real query strings:
    // [*]C([*])([H])O[H]

    int mid = smiles.indexOf("|");
    if (mid == -1) {
      if (!smiles.contains("#")) {
        Logger.printf(5, "[SMILES.replaceWithStars] Does not contain # replacements. Smiles %s\n", smiles);
        return smiles;
      } else {
        // the smiles contains a # but no |. The only valid option is that is a triple bond
        // the way to check for that is to ensure that the char after # is not a number
        char c = smiles.charAt(smiles.indexOf("#") + 1);
        if (c > '0' && c < '9') {
          // damn it is a number... what did we receive in here!?
          System.err.println("[SMILES.replaceWithStars] Query smiles with unexpected format encountered: " + smiles);
          System.exit(-1);
        } else {
          return smiles;
        }
      }
    }

    String smiles_unindexed = smiles.substring(0, mid - 1);
    String smiles_indexes = smiles.substring(mid);

    int ptr = 0, ptr_ind = 0;
    int possible;
    while ((ptr = smiles_unindexed.indexOf("[*]", ptr)) != -1) {
      // extract the next index from smiles_indexes and insert it here...
      possible = smiles_indexes.indexOf("[", ptr_ind); // either a [#91 or [H or [Lv
      char which = smiles_indexes.charAt(possible + 1);
      if (which == '#') {

        ptr_ind = smiles_indexes.indexOf("[#", ptr_ind); // only forms like [#91 here
        int end = ptr_ind+2;
        char c;
        while ((c = smiles_indexes.charAt(end)) >= '0' && c <= '9') end++;
        int atomindex = Integer.parseInt(smiles_indexes.substring(ptr_ind + 2, end))  - Element.expectedAtomicNumsAreUnder;
        assert(atomindex == 0); // See location **w** in the code above. Now we set unknowns to be #91:1 etc, so assert ==0
        assert(smiles_indexes.charAt(end) == ':');
        end++;
        int ptr_index_start = end;
        while ((c = smiles_indexes.charAt(end)) >= '0' && c <= '9') end++;
        int index = Integer.parseInt(smiles_indexes.substring(ptr_index_start, end));

        // now update the string.. and the pointers...
        smiles_unindexed =
            smiles_unindexed.substring(0, ptr+1) + // grab everything before and including the `['
            (keep_indices ?
                "H,*:" + index // add the H,*:1
                : "H,*" // just add the H,*
            ) +
            smiles_unindexed.substring(ptr+2); // grab everything after the *], excluding the `*', but including the `]'
        ptr += keep_indices ? 6 : 5; // need to jump at least len("[H,*:]")+len(index.toString()) or len("[H,*]") to be at the ending `]'...
        ptr_ind = end; // the indexes pointer needs to be moved past the end of the index [*:124]

      } else {
        int endAtom = smiles_indexes.indexOf("]", possible + 1);
        String singleAtom = smiles_indexes.substring(possible + 1, endAtom);

        Enum.valueOf(Element.class, singleAtom); // try this.. if this throws a value exception that means that the atom is unrecognized...

        // update the [*] to [H]/[Br]...
        // now update the string.. and the pointers...
        smiles_unindexed =
            smiles_unindexed.substring(0, ptr) + // grab everything before and EXcluding the `['
            "[" + singleAtom + "]" + // add the [H] or [Br] or whatever else...
            smiles_unindexed.substring(ptr+3); // grab everything after the *], excluding the `*', and EXcluding the `]'
        ptr += 3; // need to jump at least 3 chars to be at the ending `]'...
        ptr_ind = endAtom; // ptr_ind + 2 + singleAtom.length(); // the indexes pointer needs to be moved past the end of the index [H]
      }
    }
    // Logger.printf(7, "[SMILES.replaceWithStars] Indexed: %s; Original: %s\n", smiles_unindexed, smiles);

    return smiles_unindexed;
  }

  @Deprecated
  public static String replaceWithStars_old(String smiles) {

    // System.out.println("Need to replace all maxAtomicNum+id atomic numbers with [*:id] ");
    // also all [H]'s

    // Convert strings of the form
    // [*]C([*])([*])O[*] |$[#91:1];;[#91:2];[H];;[H]$|
    // to real query strings:
    // [*:1]C([*:2])([H])O[H]

    int mid = smiles.indexOf("|");
    if (mid == -1) {
      if (!smiles.contains("#")) {
        Logger.printf(5, "[SMILES.replaceWithStars] Does not contain # replacements. Smiles %s\n", smiles);
        return smiles;
      } else {
        System.err.println("[SMILES.replaceWithStars] Query smiles with unexpected format encountered: " + smiles);
        System.exit(-1);
      }
    }

    String smiles_unindexed = smiles.substring(0, mid - 1);
    String smiles_indexes = smiles.substring(mid);

    int ptr = 0, ptr_ind = 0;
    int possible;
    while ((ptr = smiles_unindexed.indexOf("[*]", ptr)) != -1) {
      // extract the next index from smiles_indexes and insert it here...
      possible = smiles_indexes.indexOf("[", ptr_ind); // either a [#91 or [H or [Lv
      char which = smiles_indexes.charAt(possible + 1);
      if (which == '#') {

        ptr_ind = smiles_indexes.indexOf("[#", ptr_ind); // only forms like [#91 here
        int end = ptr_ind+2;
        char c;
        while ((c = smiles_indexes.charAt(end)) >= '0' && c <= '9') end++;
        int atomindex = Integer.parseInt(smiles_indexes.substring(ptr_ind + 2, end))  - Element.expectedAtomicNumsAreUnder;
        assert(atomindex == 0); // See location **w** in the code above. Now we set unknowns to be #91:1 etc, so assert ==0
        assert(smiles_indexes.charAt(end) == ':');
        end++;
        int ptr_index_start = end;
        while ((c = smiles_indexes.charAt(end)) >= '0' && c <= '9') end++;
        int index = Integer.parseInt(smiles_indexes.substring(ptr_index_start, end));

        // now update the string.. and the pointers...
        smiles_unindexed =
            smiles_unindexed.substring(0, ptr+1) + // grab everything before and including the `['
            "H,*:" + index + // add the H,*:1
            smiles_unindexed.substring(ptr+2); // grab everything after the *], excluding the `*', but including the `]'
        ptr += 6; // need to jump at least six chars to be at the ending `]'...
        ptr_ind = end; // the indexes pointer needs to be moved past the end of the index [*:124]

      } else {
        int endAtom = smiles_indexes.indexOf("]", possible + 1);
        String singleAtom = smiles_indexes.substring(possible + 1, endAtom);

        Enum.valueOf(Element.class, singleAtom); // try this.. if this throws a value exception that means that the atom is unrecognized...

        // update the [*] to [H]/[Br]...
        // now update the string.. and the pointers...
        smiles_unindexed =
            smiles_unindexed.substring(0, ptr) + // grab everything before and EXcluding the `['
            "[" + singleAtom + "]" + // add the [H] or [Br] or whatever else...
            smiles_unindexed.substring(ptr+3); // grab everything after the *], excluding the `*', and EXcluding the `]'
        ptr += 3; // need to jump at least 3 chars to be at the ending `]'...
        ptr_ind = endAtom; // ptr_ind + 2 + singleAtom.length(); // the indexes pointer needs to be moved past the end of the index [H]
      }
    }
    Logger.printf(7, "[SMILES.replaceWithStars] Indexed: %s, Original: %s\n", smiles_unindexed, smiles);

    return smiles_unindexed;
  }

  private static T<MolGraph, MolGraph, HashMap<Integer, Integer>> AnnotateReaction(IndigoObject reaction, int id, String logfile, Indigo indigo) throws AAMFailException, MalFormedReactionException {

    // Add the mapping of molecules products <-> substrates
    // other options are "discard", "clear", "alter".
    // See http://ggasoftware.com/opensource/indigo/api#reaction-atom-to-atom-mapping for explanation.
    reaction.automap("keep");

    reaction.unfoldHydrogens(); // if we are going to postprocess to fix the permutation anyway; might as well unfold the H's
    fixUnmappedAtoms(reaction);

    // Two other API calls of interest:
    // http://ggasoftware.com/opensource/indigo/api#reaction-based-molecule-transformations
    // http://ggasoftware.com/opensource/indigo/api#reaction-substructure-matching

    // Save indigo internal representation of reaction
    // reaction.saveRxnfile("reaction.txt");

    // Render the reaction after filtering common big chemicals; unfolding H's; wavefront to fix pi
    tryRenderReaction(reaction, logfile, "id: " + id, indigo);

    T<MolGraph, MolGraph, HashMap<Integer, Integer>> rxnGraphs = ToGraphs(reaction);

    return rxnGraphs;
  }

  // This does its job, but is not really required because fixUnmappedAtoms is the real culprit
  // when it comes to dealing with H mappings. FixUnmappedAtoms does not treat H's as different.
  private static void removeMappingsOnH(IndigoObject reaction) {
    HashMap<IndigoObject, Integer> map = new HashMap<IndigoObject, Integer>();

    for (IndigoObject mol : reaction.iterateReactants()) {
      for (IndigoObject atom : mol.iterateAtoms()) {
        int mapsto =  reaction.atomMappingNumber(atom);
        if (!atom.symbol().equals("H"))
          map.put(atom, mapsto);
      }
    }
    for (IndigoObject mol : reaction.iterateProducts()) {
      for (IndigoObject atom : mol.iterateAtoms()) {
        int mapsto =  reaction.atomMappingNumber(atom);
        if (!atom.symbol().equals("H"))
          map.put(atom, mapsto);
      }
    }

    reaction.clearAAM();

    // re-put the mapping back on, modulo all H maps
    for (IndigoObject a : map.keySet())
      reaction.setAtomMappingNumber(a, map.get(a));
  }

  static void tryRenderReaction(IndigoObject reaction, String outfile, String comment, Indigo indigo) {
    // catch some cases that appear to be indigo faults..
    try {
      renderReaction(reaction, outfile, comment, indigo);
    } catch (IndigoException e) {
      String msg = e.getMessage();
      if (msg.equals("layout_graph: attaching cycle with only one vertex drawn")) {
        Logger.println(0, "Annotated: " + reaction.smiles());
        // wrote to the indigo dev: https://groups.google.com/d/topic/indigo-dev/zWzfGTqMKKw/discussion
      } else if (msg.equals("stereocenters: angle between bonds is too small")) {
        Logger.println(0, "Annotated: " + reaction.smiles());
      }
    }
  }

  public static void renderReaction(IndigoObject reaction, String outfile, String comment, Indigo indigo) {
    // Note that in the output, the atomMappingNumber(atom) are the black id's on the left of non-H atoms
    // blue colored indices are the debugging atom.index() if that is output (render-atom-ids-visible below)

    // rendering options are described here: http://ggasoftware.com/opensource/indigo/api/options#rendering
    IndigoRenderer renderer = new IndigoRenderer(indigo);
    indigo.setOption("render-output-format", "png");
    // indigo.setOption("render-atom-ids-visible", true); // this outputs the debugging atom.index() id's of the molecules; not really required
    // indigo.setOption("render-aam-color", "black"); // the value has to be of type 'color' and "blue"/"black" is not recognized; write to the googlegroups?
    reaction.layout();
    if (comment != null) indigo.setOption("render-comment", comment);
    renderer.renderToFile(reaction, outfile);
  }

  public static void renderMolecule(IndigoObject molecule, String outfile, String comment, Indigo indigo) {
    IndigoRenderer renderer = new IndigoRenderer(indigo);
    indigo.setOption("render-output-format", "png");
    molecule.layout();
    if (comment != null) indigo.setOption("render-comment", comment);
    renderer.renderToFile(molecule, outfile);
  }

  public static void fixUnmappedAtoms(IndigoObject reaction) throws MalFormedReactionException {
    // Even though Indigo's subgraph matching bins atoms pretty well; sometimes
    // do to bond changes in the neighborhood, it is not confident enough to
    // assign atom mapping numbers; and therefore defaults to 0
    //
    // we fix that by looking for neighbors who might indicate what my mapping bin is

    int maxBin = 0;
    int[][] map = new int[reaction.countMolecules()][];

    HashMap<String, Integer> atomCountS = new HashMap<String, Integer>();
    List<P<Integer, Integer>> unmappedS = new ArrayList<P<Integer, Integer>>();
    for (IndigoObject mol : reaction.iterateReactants()) {
      map[mol.index()] = new int[mol.countAtoms()];
      for (IndigoObject atom : mol.iterateAtoms()) {
        int mapsto =  reaction.atomMappingNumber(atom);
        if (mapsto > maxBin) maxBin = mapsto; // compute the open bin slots
        map[mol.index()][atom.index()] = mapsto;
        if (mapsto == 0) unmappedS.add(new P<Integer, Integer>(mol.index(), atom.index()));
        // next, increment the count of atoms
        String atomSym = atom.symbol();
        if (!atomCountS.containsKey(atomSym))
          atomCountS.put(atomSym, 0);
        atomCountS.put(atomSym, 1 + atomCountS.get(atomSym));
      }
    }
    HashMap<String, Integer> atomCountP = new HashMap<String, Integer>();
    List<P<Integer, Integer>> unmappedP = new ArrayList<P<Integer, Integer>>();
    for (IndigoObject mol : reaction.iterateProducts()) {
      map[mol.index()] = new int[mol.countAtoms()];
      for (IndigoObject atom : mol.iterateAtoms()) {
        int mapsto = reaction.atomMappingNumber(atom);
        if (mapsto > maxBin) maxBin = mapsto; // compute the open bin slots
        map[mol.index()][atom.index()] = mapsto;
        if (mapsto == 0) unmappedP.add(new P<Integer, Integer>(mol.index(), atom.index()));
        // next, increment the count of atoms
        String atomSym = atom.symbol();
        if (!atomCountP.containsKey(atomSym))
          atomCountP.put(atomSym, 0);
        atomCountP.put(atomSym, 1 + atomCountP.get(atomSym));
      }
    }

    printReactionMapping(4, reaction);

    // for each unmapped atom pair (a,b), ask its neighbors(a), neighbors(b) to weigh in
    // on the pairing. If a neighbor itself is unmapped it cannot have
    // an opinion. Then pick the pairing with the most number of votes; Iterate...
    while (unmappedP.size() > 0 || unmappedS.size() > 0) {

      // votes::double because we want to vote, but give very little priority to singleton atoms
      // so that they get matched last... so we vote 0.1 for them, but 1 for atoms with neighbors
      HashMap<P<Integer, Integer>, Double> votes = new HashMap<P<Integer, Integer>, Double>();

      for (int p = 0; p < unmappedP.size(); p++) {
        for (int s = 0; s < unmappedS.size(); s++) {
          // get votes for the (p,s) pairing

          P<Integer, Integer> pair = new P<Integer, Integer>(p, s);
          votes.put(pair, 0.0);

          P<Integer, Integer> pId = unmappedP.get(p);
          P<Integer, Integer> sId = unmappedS.get(s);
          IndigoObject pAtom = reaction.getMolecule(pId.fst()).getAtom(pId.snd());
          IndigoObject sAtom = reaction.getMolecule(sId.fst()).getAtom(sId.snd());
          // if atom types are different they cannot be paired; so continue
          if (!pAtom.symbol().equals(sAtom.symbol()))
            continue;
          // now we know they pAtom and sAtom's type are identical

          // first check if these atom (both of same type as we know) are singles on either side
          if (atomCountS.get(sAtom.symbol()) == 1) {
            if (atomCountP.get(pAtom.symbol()) != 1) {
              String msg = "For singleton atom " + sAtom.symbol() + ", the products side has " + atomCountP.get(pAtom.symbol()) + "!!";
              System.err.println(msg);
              // System.exit(-1);
              throw new MalFormedReactionException(msg);
            }
            // definitely match these two atoms as they have no other choice...
            votes.put(pair, 100.0);

          } else if // then check if any of them are singleton
            (pAtom.degree() + pAtom.countImplicitHydrogens() == 0 ||
              sAtom.degree() + sAtom.countImplicitHydrogens() == 0) {
            // if either are isolated (and we know they are the same atom type)
            // therefore they can automatically be assigned together; give
            // this pair an upvote (This really helps the isolated H+'s, other ions etc.)
            votes.put(pair, votes.get(pair) + 0.1);
          } else {
            // both of them have some neighbors.
            // Lets see if any of them vote for this pair...
            for (IndigoObject pNeigh : pAtom.iterateNeighbors()) {
              int pBin;
              // since the neighbor has to be in the same molecule; we just use pId.fst as getting from atom->mol difficult
              if ((pBin = map[pId.fst()][pNeigh.index()]) == 0)
                continue; // this has not been mapped to then it does not have the right to vote
              for (IndigoObject sNeigh : sAtom.iterateNeighbors()) {
                // check if this pNeigh, sNeigh pair votes for this atom
                // it will if pNeigh <-> sNeigh; i.e., they are in the same bin
                int sBin;
                // since the neighbor has to be in the same molecule; we just use pId.fst as getting from atom->mol difficult
                if ((sBin = map[sId.fst()][sNeigh.index()]) == 0)
                  continue; // this has not been mapped to then it does not have the right to vote
                if (pBin == sBin) // if the bins are equal then this (p,s) get a vote from (pNeigh[i], sNeigh[j])
                  votes.put(pair, votes.get(pair) + 1); // increment the votes
              }
            }
          }
        }
      }

      // check who has the most votes
      int pVoted = -1, sVoted = -1;
      double maxVotes = 0.0;
      for (int p = 0; p < unmappedP.size(); p++) {
        for (int s = 0; s < unmappedS.size(); s++) {
          // does this (p,s) pairing have the largest votes?
          P<Integer, Integer> pair = new P<Integer, Integer>(p, s);
          Logger.printf(15, "[Wavefront AAM fix] Votes for %s are %f\n", pair, votes.get(pair));
          if (votes.get(pair) > maxVotes) {
            maxVotes = votes.get(pair);
            pVoted = p; sVoted = s;
          }
        }
      }
      if (pVoted == -1) {
        Logger.println(5, "[Wavefront AAM fix] No atoms voted for; Finishing wavefront propagation.");
        break; // since there are no atoms to be voted on, we stop the wavefront procedure
      }

      // remove from unmapped
      P<Integer, Integer> pId = unmappedP.remove(pVoted);
      P<Integer, Integer> sId = unmappedS.remove(sVoted);

      // assign (pVoted, sVoted) a new bin
      // update the map[][] array to ++bin
      int bin = ++maxBin;
      map[pId.fst()][pId.snd()] = bin;
      map[sId.fst()][sId.snd()] = bin;

      // update the AAM in the reaction object
      IndigoObject pAtom = reaction.getMolecule(pId.fst()).getAtom(pId.snd());
      IndigoObject sAtom = reaction.getMolecule(sId.fst()).getAtom(sId.snd());
      reaction.setAtomMappingNumber(pAtom, bin);
      reaction.setAtomMappingNumber(sAtom, bin);
      Logger.printf(5, "[Wavefront AAM fix] Fixed mapping for [%d][%d] and [%d][%d] to %d\n", pId.fst(), pId.snd(), sId.fst(), sId.snd(), bin);
    }

    if (unmappedS.size() > 0) {
      Logger.println(4, "[Wavefront AAM fix] There are still some unmapped substrate atoms left: " + unmappedS);
    }
    if (unmappedP.size() > 0) {
      Logger.println(4, "[Wavefront AAM fix] There are still some unmapped product atoms left: " + unmappedP);
    }
    Logger.println(4, "Printing atom mappings after neighbor consensus growing...");
    printReactionMapping(4, reaction);
  }

  private static void printReactionMapping(int logLevel, IndigoObject reaction) {
    Logger.printf(logLevel, "Reaction smiles: %s\n", reaction.smiles()); // we cannot call canonicalSmiles on reactions...
    for (IndigoObject mol : reaction.iterateReactants()) {
      Logger.println(logLevel, "Next substrate molecule " + mol.index());
      for (IndigoObject atom : mol.iterateAtoms()) {
        int mapsto =  reaction.atomMappingNumber(atom);
        Logger.printf(logLevel, "Atom[%d][%d] -> %s\n", mol.index(), atom.index(), (mapsto == 0 ? "need to fix": mapsto + ""));
      }
    }
    for (IndigoObject mol : reaction.iterateProducts()) {
      Logger.printf(logLevel, "Next product molecule %d\n", mol.index());
      for (IndigoObject atom : mol.iterateAtoms()) {
        int mapsto = reaction.atomMappingNumber(atom);
        Logger.printf(logLevel, "Atom[%d][%d] -> %s\n", mol.index(), atom.index(), (mapsto == 0 ? "need to fix": mapsto + ""));
      }
    }
  }

  static class BalancingPattern {
    int num_atoms; Atom[] atoms; Integer[] counts;
    String[] substrateAdd; String[] productAdd;
    int implicitHinFix;
    BalancingPattern(int num, Atom[] nodes, Integer[] counts, int implicitHinFix, String[] sAdd, String[] pAdd) {
      this.num_atoms = num; this.atoms = nodes; this.counts = counts;
      this.substrateAdd = sAdd; this.productAdd = pAdd;
      this.implicitHinFix = implicitHinFix; // +ve indicates that there are implicit H's on the products side; -ve on the substrates side
    }

    @Override
    public String toString() {
      String fix = "if mismatch (";
      fix += Arrays.asList(this.atoms) + ":" + Arrays.asList(this.counts);
      if (this.substrateAdd != null) {
        fix += ") then +substrate(";
        for (int i = 0; i < this.substrateAdd.length; i++)
          fix += this.substrateAdd[i];
      }
      if (this.productAdd != null) {
        fix += ") and +product(";
        for (int i = 0; i < this.productAdd.length; i++)
          fix += this.productAdd[i];
      }
      fix += ") + deltaH(" + this.implicitHinFix + ")";
      return fix;
    }

    // checks if the delta matches this pattern (modulo H's). It returns the number of H's
    // that need to be added or deleted even after this pattern fix
    // RETURN is -ve if the atoms are lost, so need to added to the products side; +ve if gained, so add them to the substrates side
    Integer matchesPattern(HashMap<Atom, Integer> delta) {

      // debug: System.err.format("Attempting to match delta: %s with this pattern: %s\n", delta.toString(), this.toString());

      Atom Hatom = new Atom(Element.H);
      // first check the pattern matches almost everything except the H's
      // then return the delta in H's except those that got taken care of in the pattern itself...

      Integer h = null; // could remain null; if the delta does not contain any missing H's
      HashMap<Atom, Integer> d = new HashMap<Atom, Integer>();
      List<Atom> nodes = new ArrayList<Atom>();
      // copy everything except the H's
      for (Atom n : delta.keySet()) {
        if (n.equals(Hatom))
          h = delta.get(n);
        else {
          d.put(n, delta.get(n));
          nodes.add(n);
        }
      }
      // now everything should be equal as the only ambiguous count was the H's
      for (int i =0 ; i<this.atoms.length; i++) {
        Atom n  = this.atoms[i];
        if (n.equals(Hatom)) {
          System.err.println("Please do not put a H in the pattern. They will be fixed automatically.");
          System.exit(-1);
        }
        if (!d.containsKey(n) || d.get(n) != this.counts[i]) {
          // debug: if (!d.containsKey(n))
          // debug:   System.err.println("Returning null because delta does not contain key: " + n );
          // debug: else
          // debug:   System.err.format("Returning null because counts %d %d mismatch on %s\n", d.get(n), this.counts[i], n.toString() );

          return null; // pattern does not match
        }
        if (nodes.contains(n))
          nodes.remove(n);
      }
      if (!nodes.isEmpty()) {
        // debug: System.err.println("Returning null because nodes!=empty : " + nodes);
        return null; // there was something in there; other than H of course; that is still left; so this pattern does not match
      }

      // now we need to return the right number of H's, i.e., we need to be able to fix OH, COH, C=O etc...
      if (this.implicitHinFix == 0) { // if this pattern does not contain any H that are fixed
        if (h != null)
          // h != null, i.e., delta contained some difference then we need to return that
          // 1) the input "h" is -ve if the atoms are lost; +ve if gained;
          // 2) we return the -ve number if the H's need to go on the products side; +ve if they need to go on the substrates side
          // so return h as is.
          return h;
        else
          // the delta also did not have any missing H's so just return 0
          return 0;
      } else {
        // this pattern already encoded some implicitHinFix H atoms.
        // 1) implicitHinFix = +ve indicates they are implicitly on the products side, -ve on the substrates side
        // 2) the input delta "h" (!=null) is -ve if the atoms are lost; +ve if gained;
        // 3) we return the -ve number if the H's need to go on the products side; +ve if they need to go on the substrates side
        // so we need to return "h", *except* for the number that was already taken care of by implicitHinFix
        // BUT since implicit H's are +ve if they are on the products side, therefore we need to ADD them
        if (h != null)
          return h + this.implicitHinFix; // see explanation above for why "+"
          // e.g., consider h == -3, i.e., there are 3 atoms missing on the products side
          // and   consider implicitH == -2, i.e., there are implicitH's on the substrates side
          // then we need to output -3 + -2 = -5, i.e., 5 H's still need to be put on the products side...
        else
          return this.implicitHinFix;
          // Consider implicitH == -4, i.e., there are 4 implicitH's on the substrates side
          // then we need to output -4, i.e., 4 H's still need to be put on the products side...
      }
    }
  }

  private static MolGraph expandCRO2ERO(MolGraph g, MolGraph cro_g, HashMap<Integer, Integer> map) {
    MolGraph core = null;
    if (map != null) {
      g = g.permute(map); // instantiates new, so no need to worry about overwriting old.
    }
    core = cro_g.duplicate();
    Logger.printf(5, "G: " + g + "\nG CRO: " + core + "\n");

    return new DotNotationConjugationCheck(g).getConjugated(core);

    // our internal representation is now the DOT notation, so we have to use the above
    // conjugated check as opposed to the alternating double bond algorithm in ConjugationCheck
    // return new ConjugationCheck(g).getConjugated(core);
  }

  private static MolGraph getGraphPreserved(MolGraph g1, MolGraph g2, HashMap<Integer, Integer> map) {
    // Is getGraphDiff's complement....
    // Return (map(g1) \sqcap g2 where \sqcap is setintersect,

    // before permuting g1's atoms, we extend the map to cover all
    // vertices in g1. Those vertices that are unmapped are
    // mapped to indices larger than any that already exist in g2

    HashMap<Integer, Integer> extendedMap = extendMapToAllNodes(map,g1,g2);
    MolGraph remappedg1 = g1.permute(extendedMap);

    // now compute the intersect g1' /\ g2
    MolGraph intersect = remappedg1.intersect(g2);
    return intersect;
  }

  private static P<MolGraph, MolGraph> getGraphDiff(MolGraph g1, MolGraph g2, HashMap<Integer, Integer> map) {
    // Return (map(g1) - g2, g2 - map(g1)); where '-' is setminus,
    // i.e., this function computes (addition-graph, deletion-graph) for g2
    // So that you have the equality "g2 + addition-graph - deletion-graph = g1"

    MolGraph remappedg1 = g1.permute(map);

    // now do g1'-g2 and g2-g1'
    MolGraph addition = remappedg1.subtract(g2);
    MolGraph subtract = g2.subtract(remappedg1);
    // NOTE about subtraction:
    // Since the mapping for g1 was extended; there may be mapped vertices
    // (after the permutation) that map to indices larger than those present
    // in g2. Therefore, those indices might not have any counterpart
    // adjList in g2. "subtract" needs to check for null-ness of that.


    // if there were atoms that just got disconnected while going from substrate
    // to products, then they should appear as singular atoms in the addition
    // Similarly, isolated atoms that got attached should appear as singular
    // atoms in the subtract.
    addSingularAtoms(addition, remappedg1, g2);
    addSingularAtoms(subtract, g2, remappedg1);

    // return the (added subgraph, deleted subgraph) pair
    return new P<MolGraph, MolGraph>(addition, subtract);
  }

  private static void addSingularAtoms(MolGraph toUnionWith, MolGraph singularAtomsHere, MolGraph attachedAtomsHere) {
    List<Node<Atom>> solos = new ArrayList<Node<Atom>>();
    for (Integer nid : singularAtomsHere.GetNodeIDs()) {
      // if nid is unbonded in singularAtomsHere and bonded in attachedAtomsHere it goes into the pile of "solos"
      if (!hasBondedNeighbor(nid, singularAtomsHere) && hasBondedNeighbor(nid, attachedAtomsHere))
        solos.add(new Node<Atom>(nid, singularAtomsHere.GetNodeType(nid)));
    }

    toUnionWith.mergeDisjointGraph(new MolGraph(solos, new ArrayList<Edge<Atom, BondType>>()));
  }

  private static boolean hasBondedNeighbor(Integer id, MolGraph G) {
    List<P<Integer, BondType>> adj = G.AdjList().get(id);
    return adj != null && !adj.isEmpty(); // there is at least one neighbor in adjacency list
  }

  private static HashMap<Integer, Integer> extendMapToAllNodes(HashMap<Integer, Integer> map, MolGraph g1, MolGraph g2) {
    // extend the map to cover all vertices in g1. Those vertices that are unmapped are
    // mapped to indices larger than any that already exist in g2
    int newIdx = Collections.max(g2.GetNodeIDs()) + 1;
    HashMap<Integer, Integer> extended = new HashMap<Integer, Integer>();
    for (Integer nid : g1.GetNodeIDs()) {
      if (map.containsKey(nid))
        extended.put(nid, map.get(nid));
      else
        extended.put(nid, newIdx++);
    }
    return extended;
  }

  /*
     * Molecule: From Smiles to Graph
     */
    public static MolGraph ToGraph(List<String> smiles)
    {
      Indigo indigo = new Indigo();
      MolGraph g = new MolGraph();
      for (String s : smiles) {
        MolGraph gg = ToGraph(indigo, s);
        g.mergeGraph(gg);
      }
      return g;
    }

    public static MolGraph ToGraph(Indigo indigo, String smiles)
    {
        IndigoObject mol = indigo.loadMolecule(smiles);
        // we cannot unfold the hydrogens and then do the automap. Automap does
        // not take into account the H;s and so they remain unmapped. We have to
        // take care of them manually.
        // if (expandImplicitH) mol.unfoldHydrogens();
        return ToGraph(mol);
    }

    public static MolGraph ToGraph(IndigoObject mol)
    {
        List<Node<Atom>> nodes = new ArrayList<Node<Atom>>();
        List<Edge<Atom, BondType>> edges = new ArrayList<Edge<Atom, BondType>>();
        HashMap<Integer, Node<Atom>> atomInMol2Node = new HashMap<Integer, Node<Atom>>();
        int id = 0;
        for (IndigoObject atom : mol.iterateAtoms())
        {
            Element t =  Enum.valueOf(Element.class, atom.symbol());
            Atom a = new Atom(t);
        // System.out.format("Read %s{%d}: charge=%s,radical=%s,valence=%s\n", atom.symbol(), atom.index(), atom.charge(), atom.radicalElectrons(), atom.explicitValence());
      a.setCharge(atom.charge());
        a.setRadicalElectrons(atom.radicalElectrons());
      if (atom.explicitValence() != null)
        a.setExplicitValence(atom.explicitValence());
            Node<Atom> n = new Node<Atom>(id++, a);
            nodes.add(n);
            atomInMol2Node.put(atom.index(), n);

            // isn't this buggy: the implicit H's increment the id#s but they are not added to the atomInMol2Node hashmap
            // so when you look them up in the edge list, won't they not be present? Why does this not throw exceptions?
            // or are the implicit H bonds not even counted in the enumeration of bonds?
            for (int h = 0; h<atom.countImplicitHydrogens(); h++) {
              Node<Atom> implicitH = new Node<Atom>(id++, new Atom(Element.H));
              nodes.add(implicitH);
              edges.add(new Edge<Atom, BondType>(n, implicitH, BondType.Single));
            }
        }
        for (IndigoObject edge : mol.iterateBonds())
        {
            BondType t;
            Node<Atom> n1, n2;
            switch (edge.bondOrder()) {
                case 1: t = BondType.Single; break;
                case 2: t = BondType.Double; break;
                case 3: t = BondType.Triple; break;
                default: System.err.println("edge type unknown"); System.exit(-1); t = null;
            }
            n1 = atomInMol2Node.get(edge.source().index());
            n2 = atomInMol2Node.get(edge.destination().index());
            edges.add(new Edge<Atom, BondType>(n1, n2, t));
        }
        return new MolGraph(nodes, edges);
    }

    private static MolGraph ToGraph(IndigoObject mol, int atomStartOffset, IndigoObject reaction, HashMap<Integer, Integer> AtomBinMap) {

      // This is mostly copied from the ToGraph(mol) function.
      // The changes are annotated:
      // - ensuring mol is in a reaction context (using atomStartOffset)
      // - capturing the Atom-to-Atom map AAM (could be null in which case no capture)

    List<Node<Atom>> nodes = new ArrayList<Node<Atom>>();
    List<Edge<Atom, BondType>> edges = new ArrayList<Edge<Atom, BondType>>();
    HashMap<Integer, Node<Atom>> atomInMol2Node = new HashMap<Integer, Node<Atom>>();
    int id = atomStartOffset;
    for (IndigoObject atom : mol.iterateAtoms()) {

      // optionally capture the atom-to-atom mapping
      int bin = reaction.atomMappingNumber(atom);
      if (bin == 0) {
        // atoms that map to 0 could not be paired
        Logger.printf(5, "Unpaired atoms (%s[idx=%d])\n", atom.symbol(), atom.index());
      }
      if (AtomBinMap != null)
        AtomBinMap.put(id, bin);

      Atom a;
      if (atom.symbol().equals("*"))
        a = new Atom(Element.Unknown);
      else
        a = new Atom(Enum.valueOf(Element.class, atom.symbol()));

      if (a.elem != Element.Unknown) {
        if (atom.charge() != null)
          a.setCharge(atom.charge());
        if (atom.radicalElectrons() != null)
          a.setRadicalElectrons(atom.radicalElectrons());
        if (atom.explicitValence() != null)
          a.setExplicitValence(atom.explicitValence());
      }

      Node<Atom> n = new Node<Atom>(id++, a);
      nodes.add(n);
      Logger.println(5, "Added node: " + n + " with original index:" + atom.index() + " and bin:" + (AtomBinMap != null && AtomBinMap.containsKey(id-1)?AtomBinMap.get(id-1):"null"));
      atomInMol2Node.put(atom.index(), n);
    }
    for (IndigoObject edge : mol.iterateBonds()) {
      BondType t;
      Node<Atom> n1, n2;
      switch (edge.bondOrder()) {
      case 1: t = BondType.Single; break;
      case 2: t = BondType.Double; break;
      case 3: t = BondType.Triple; break;
      default: { System.err.println("Unrecognized bond type!"); System.exit(-1); t = null; }
      }
      n1 = atomInMol2Node.get(edge.source().index());
      n2 = atomInMol2Node.get(edge.destination().index());
      edges.add(new Edge<Atom, BondType>(n1, n2, t));
    }

    return new MolGraph(nodes, edges);
  }

    private static T<MolGraph, MolGraph, HashMap<Integer, Integer>> ToGraphs(IndigoObject reaction) throws AAMFailException {
      // first create the graph for reactants
      HashMap<Integer, Integer> mapSubsAtomsToBins = new HashMap<Integer, Integer>();
      MolGraph substr = new MolGraph();
      int startOffset = 0;
    for (IndigoObject reactant : reaction.iterateReactants()) {
      substr.mergeDisjointGraph(ToGraph(reactant, startOffset, reaction, mapSubsAtomsToBins)); // we don't care to capture the fwd AAM
      startOffset += reactant.countAtoms();
    }

    // now create the graph for products; and in the process also log what they map to im the reactants
      HashMap<Integer, Integer> mapProdAtomsToBins = new HashMap<Integer, Integer>();
      MolGraph prods = new MolGraph();
      startOffset = 0; // reset the atom count for the product list...
    for (IndigoObject product : reaction.iterateProducts()) {
      prods.mergeDisjointGraph(ToGraph(product, startOffset, reaction, mapProdAtomsToBins));
      startOffset += product.countAtoms();
    }
    return new T<MolGraph, MolGraph, HashMap<Integer, Integer>>(substr, prods, findmap(mapProdAtomsToBins, mapSubsAtomsToBins));
  }

  public static P<MolGraph, MolGraph> getAddedDeletedInQueryRxn(String qrxn) throws AAMFailException {
    Indigo i = new Indigo();
    Logger.println(2, "Converting back from: " + qrxn);
    IndigoObject ind_rxn = i.loadQueryReaction(qrxn);
    T<MolGraph, MolGraph, HashMap<Integer, Integer>> dissected = ToGraphs(ind_rxn);
    return new P<MolGraph, MolGraph>(dissected.fst(), dissected.snd());
  }

  private static HashMap<Integer, Integer> findmap(HashMap<Integer, Integer> mapProdAtomsToBins, HashMap<Integer, Integer> mapSubsAtomsToBins) throws AAMFailException {
    HashMap<Integer, List<Integer>> bin2atomSubs = invert(mapSubsAtomsToBins);
    HashMap<Integer, List<Integer>> bin2atomProd = invert(mapProdAtomsToBins);

    HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
    for (int bin : bin2atomProd.keySet()) {
      if (bin == 0)
        continue; // ignore bin 0 atoms since they are unmatched...
      List<Integer> substr = bin2atomSubs.get(bin);
      List<Integer> prods = bin2atomProd.get(bin);
      if (substr == null) {// prods should != null, otherwise this bin would not have existed
        System.err.printf("Ignoring product atoms (bin %d) with no substrates: %s", bin, prods);
        continue;
      }
      if (prods == null) { // substr should != null, otherwise this bin would not have existed
        System.err.printf("Ignoring product atoms (bin %d) with no products: %s", bin, substr);
        continue;
      }
      if (substr.size() == 1 && prods.size() == 1)
        map.put(prods.get(0), substr.get(0));
      else {
        System.err.println("More an one element in either product or substrate bin: " + bin);
        throw new AAMFailException("Bin " + bin + " problematic.");
      }
    }
    return map;
  }

  private static HashMap<Integer, List<Integer>> invert(HashMap<Integer, Integer> mapAtomsToBins) {
    HashMap<Integer, List<Integer>> binsToAtoms = new HashMap<Integer, List<Integer>>();
    for (Integer atom : mapAtomsToBins.keySet()) {
      Integer bin = mapAtomsToBins.get(atom);
      if (!binsToAtoms.containsKey(bin))
        binsToAtoms.put(bin, new ArrayList<Integer>());
      binsToAtoms.get(bin).add(atom);
    }
    return binsToAtoms;
  }

  /*
     * Molecule: From Graph to Smiles
     */
    public static String FromGraphWithoutUnknownAtoms(Indigo indigo, MolGraph g)
    {
        IndigoObject mol = MolFromGraph(indigo, g);
        return mol.canonicalSmiles();
    }
    public static String FromGraphWithUnknownAtoms(Indigo indigo, MolGraph g) {
      boolean debug;
      if (_CanonicalizeQuerySMILES) {
        return fromDisjointMolGraphToCanonicalSMILES(indigo, g, debug=false);
      } else {
        IndigoObject mol = fromDisjointMolGraphToIndigoObject(indigo, g);
          return mol.smiles();
      }
  }

    private static IndigoObject MolFromGraph(Indigo indigo, MolGraph g)
    {
      HashMap<Integer, IndigoObject> node2Atom = new HashMap<Integer, IndigoObject>();
        IndigoObject mol = indigo.createMolecule();
        for (int n : g.GetNodeIDs())
        {
            IndigoObject a = mol.addAtom(getAtom(g, n));
            node2Atom.put(n, a);
        }
        HashMap<Integer, List<Integer>> edges = g.getEdgeIDs();
        for (int n1 : edges.keySet())
        {
            for (int n2 : edges.get(n1))
                if (n1 < n2)
                    node2Atom.get(n1).addBond(node2Atom.get(n2), g.edge2IndigoBondNum(n1, n2));
        }
        return mol;
    }

    private static String getAtom(MolGraph g, int id)
    {
      Atom a = g.GetNodeType(id);
        if (a.elem == Element.Unknown)
          { System.err.println("For atoms with unknowns you need to indigo.createQueryMolecule..."); System.exit(-1); }
        String s = a.elem.name();
        return s;
    }

    // called from query equals... so will not care about AAMs....



    private static String fromDisjointMolGraphToCanonicalSMILES(Indigo indigo, MolGraph g, boolean debug) {
      HashMap<Integer, IndigoObject> node2Atom = new HashMap<Integer, IndigoObject>();
      HashMap<Integer, Integer> unknown2indigoID = new HashMap<Integer, Integer>();
        IndigoObject mol = indigo.createMolecule(); // createMolecule instead of createQueryMolecule because we are going to call canonicalSmiles on it...
        for (int n : g.GetNodeIDs())
        {
          P<String, Atom> atomNode = getAtom_PotentiallyUnknown(g, n, unknown2indigoID);
          String atom = atomNode.fst(); Atom node = atomNode.snd();

          int unk_id = -1;
          if (atom.startsWith("[*:")) {
            unk_id = Integer.parseInt(atom.substring(3, atom.length() - 1));
            // atom = "[#" + (NodeType.allAtomicNumsAreUnder + ":" + unk_id) + "]";
            atom = "[#" + (Element.expectedAtomicNumsAreUnder + ":0") + "]";
          }

          IndigoObject a = mol.addAtom(atom);

            if (node.elem != Element.Unknown) { // for unknown nodes the charge and radicalelectrons == null...
            // and the explicit valence is only non-null if explicitly set for atoms in concrete molecules...
              // System.out.format("Setting %s[charge=%d, radical=%d, valence=%d]\n", node.elem, node.getCharge(), node.getRadicalElectrons(), node.getExplicitValence());
            if (node.getCharge() != null)
                a.setCharge(node.getCharge());
              if (node.getRadicalElectrons() != null)
                a.setRadical(node.getRadicalElectrons());
              if (node.getExplicitValence() != null)
                a.setExplicitValence(node.getExplicitValence());
            }
            node2Atom.put(n, a);
        }
        HashMap<Integer, List<Integer>> edges = g.getEdgeIDs();
        for (int n1 : edges.keySet())
        {
            for (int n2 : edges.get(n1))
                if (n1 < n2)
                    node2Atom.get(n1).addBond(node2Atom.get(n2), g.edge2IndigoBondNum(n1, n2));
        }
        boolean keep_indices;
        String can = mol.canonicalSmiles();
        return replaceWithStars(can, keep_indices = false); // convert the SMARTS |$...[#100:0];[H]...$| to just the SMARTS with the [H]'s and other atoms embedded
  }

    private static String fromDisjointMolGraphToNonCanonicalButNumberedSMILES(Indigo indigo, MolGraph g) {
      HashMap<Integer, IndigoObject> node2Atom = new HashMap<Integer, IndigoObject>();
      HashMap<Integer, Integer> unknown2indigoID = new HashMap<Integer, Integer>();
        IndigoObject mol = indigo.createMolecule(); // createMolecule instead of createQueryMolecule because we are going to call canonicalSmiles on it...
        for (int n : g.GetNodeIDs())
        {
          P<String, Atom> atomNode = getAtom_PotentiallyUnknown(g, n, unknown2indigoID);
          String atom = atomNode.fst(); Atom node = atomNode.snd();

          int unk_id = -1;
          if (atom.startsWith("[*:")) {
            unk_id = Integer.parseInt(atom.substring(3, atom.length() - 1));
            // atom = "[#" + (NodeType.allAtomicNumsAreUnder + ":" + unk_id) + "]";
            atom = "[#" + (Element.expectedAtomicNumsAreUnder + ":" + unk_id) + "]";
          }

          IndigoObject a = mol.addAtom(atom);

            if (node.elem != Element.Unknown) { // for unknown nodes the charge and radicalelectrons == null...
            // and the explicit valence is only non-null if explicitly set for atoms in concrete molecules...
            if (node.getCharge() != null)
                a.setCharge(node.getCharge());
              if (node.getRadicalElectrons() != null)
                a.setRadical(node.getRadicalElectrons());
              if (node.getExplicitValence() != null)
                a.setExplicitValence(node.getExplicitValence());
            }
            node2Atom.put(n, a);
        }
        HashMap<Integer, List<Integer>> edges = g.getEdgeIDs();
        for (int n1 : edges.keySet())
        {
            for (int n2 : edges.get(n1))
                if (n1 < n2)
                    node2Atom.get(n1).addBond(node2Atom.get(n2), g.edge2IndigoBondNum(n1, n2));
        }
        boolean keep_indices;
        String can = mol.canonicalSmiles(); // this does not mean that you will get canonical smiles out. Because we replaced it with *:unk_id, they are treated differently
        return replaceWithStars(can, keep_indices = true);
  }

    @Deprecated // use the above...fromDisjointMolGraphToCanonicalSMILES
  private static IndigoObject fromDisjointMolGraphToIndigoObject(Indigo indigo, MolGraph g)
    {
      HashMap<Integer, IndigoObject> node2Atom = new HashMap<Integer, IndigoObject>();
      HashMap<Integer, Integer> unknown2indigoID = new HashMap<Integer, Integer>();
        IndigoObject mol = indigo.createQueryMolecule();
        for (int n : g.GetNodeIDs())
        {
          P<String, Atom> atomNode = getAtom_PotentiallyUnknown(g, n, unknown2indigoID);
          String atom = atomNode.fst(); Atom node = atomNode.snd();
            IndigoObject a = mol.addAtom(atom);
            a.setCharge(node.getCharge());
            a.setRadical(node.getRadicalElectrons());
            a.setExplicitValence(node.getExplicitValence());
            node2Atom.put(n, a);
        }
        HashMap<Integer, List<Integer>> edges = g.getEdgeIDs();
        for (int n1 : edges.keySet())
        {
            for (int n2 : edges.get(n1))
                if (n1 < n2)
                    node2Atom.get(n1).addBond(node2Atom.get(n2), g.edge2IndigoBondNum(n1, n2));
        }
        return mol;
    }

    private static P<String, Atom> getAtom_PotentiallyUnknown(MolGraph g, int id, HashMap<Integer, Integer> unknown2indigoID)
    {
        Atom a = g.GetNodeType(id);
        String name;
        switch (a.elem) {
        case Unknown:
          int indigoID;
          if (!unknown2indigoID.containsKey(id))
            unknown2indigoID.put(id, unknown2indigoID.size() + 1); // +1 because we want 1-index based and not 0-index based.
          indigoID = unknown2indigoID.get(id);
            String wcAtom = "[*:" + indigoID + "]";
            name = wcAtom;
            break;
        case H:
          name = "[H]";
          break;
        default:
          String s = a.elem.name();
          name = s.length() > 1 ? "[" + s + "]" : s;
          break;
        }
        return new P<String, Atom>(name, a);

        /*
        if (t == NodeType.Unknown) {
          int indigoID;
          if (!unknown2indigoID.containsKey(id))
            unknown2indigoID.put(id, unknown2indigoID.size() + 1); // +1 because we want 1-index based and not 0-index based.
          indigoID = unknown2indigoID.get(id);
          String wcAtom = "[*:" + indigoID + "]";
          return wcAtom;
        }
        if (t == NodeType.H)
          return "[H]";
        String s = t.name();
        if (s.length() > 1)
          return "[" + s + "]";
        else
          return s;
        */
    }

    /*
     * We sometimes need to compute the optimal pairing between sets of SMILES
     */

  // simplified version of below
  public static Set<P<MorS, MorS>> computePairings(List<MorS> molGraphsA, List<MorS> molGraphsB) {
    List<Double> zeroWeightsA = new ArrayList<Double>();
    for (int i = 0;i<molGraphsA.size(); i++) zeroWeightsA.add(0.0);
    List<Double> zeroWeightsB = new ArrayList<Double>();
    for (int i = 0;i<molGraphsB.size(); i++) zeroWeightsB.add(0.0);
    return computePairings(molGraphsA, molGraphsB, zeroWeightsA, zeroWeightsB).keySet();
  }

  public static HashMap<P<MorS, MorS>, Double> computePairings(List<MorS> n1, List<MorS> n2, List<Double> n1vals, List<Double> n2vals) {
    // Create a bipartite graph out of n1 and n2, with all possible edges, and with each weighted by the similarity between
    // the end points (or it could be that all outgoing edges from n\in n1 are weighted by the probability distribution of how similar n is to the endpoints)
    Graph<MorS, Double> bipartite = createBipartiteGraph(n1, n2);
    // Then do a maximal matching (i.e., each node is matched to one that it most resembles on the other side.
    GraphMatching<MorS> matching = new GraphMatching<MorS>(bipartite);
    List<P<Integer, Integer>> matchingSet = matching.getMaximumMatching();
    Logger.printf(10, "[SMILES] Maximal matching: %s\n", matchingSet);

    HashMap<P<MorS, MorS>, Double> finalPairWeights = new HashMap<P<MorS, MorS>, Double>();
    // Then for each pairing, take the average of the values and put that as the weight of the pairing
    @SuppressWarnings("unchecked") // we installed this hashMap into the bipartite graph below, so we know the cast will succeed
    List<Integer> nodeIds1 = (List<Integer>) bipartite.getMetaData(GraphMatching.PartitionA);
    @SuppressWarnings("unchecked")
    List<Integer> nodeIds2 = (List<Integer>) bipartite.getMetaData(GraphMatching.PartitionB);
    for (P<Integer, Integer> pair : matchingSet) {
      // lookup the id of the node in bipartite graph, and then lookup what index in the list it mapped to
      int index1 = nodeIds1.indexOf(pair.fst());
      int index2 = nodeIds2.indexOf(pair.snd());
      if (index1 == -1) {
        finalPairWeights.put(new P<MorS, MorS>(null, n2.get(index2)), n2vals.get(index2));
        continue;
      }
      if (index2 == -1) {
        finalPairWeights.put(new P<MorS, MorS>(n1.get(index1), null), n1vals.get(index1));
        continue;
      }
      Double node1val = n1vals.get(index1);
      Double node2val = n2vals.get(index2);
      // average isn't very accurate (because them the comparisons become brittle: 0.51 vs 0.49 etc)
      // max works better
      Double average = Math.max(node1val, node2val);
      finalPairWeights.put(new P<MorS, MorS>(n1.get(index1), n2.get(index2)), average);
    }
    return finalPairWeights;
  }

  private static Graph<MorS, Double> createBipartiteGraph(List<MorS> n1, List<MorS> n2) {
    // TODO Create a bipartite graph out of n1 and n2, with all possible edges,
    // and with each weighted by the similarity between the end points
    // (or it could be that all outgoing edges from n\in n1 are weighted
    // by the probability distribution of how similar n is to the endpoints)
    Graph<MorS, Double> g = null;
    List<Node<MorS>> nodes = new ArrayList<Node<MorS>>();
    List<Edge<MorS, Double>> edges = new ArrayList<Edge<MorS, Double>>();
    List<Integer> nodeIds1 = new ArrayList<Integer>();
    List<Integer> nodeIds2 = new ArrayList<Integer>();
    List<Node<MorS>> node1 = new ArrayList<Node<MorS>>();
    List<Node<MorS>> node2 = new ArrayList<Node<MorS>>();
    int nodeid = 0;
    for (int i = 0 ;i<n1.size(); i++) {
      int id = nodeid++;
      Node<MorS> n = new Node<MorS>(id, n1.get(i));
      nodeIds1.add(id);
      nodes.add(n); node1.add(n);
    }
    for (int i = 0 ;i<n2.size(); i++) {
      int id = nodeid++;
      Node<MorS> n = new Node<MorS>(id, n2.get(i));
      nodeIds2.add(id);
      nodes.add(n); node2.add(n);
    }
    for (int i = 0 ;i<n1.size(); i++) {
      for (int j = 0; j<n2.size(); j++)
      {
        MorS smile1 = n1.get(i), smile2 = n2.get(j);
        // compute the similarity [0,1] edgeweight
        Double edgeweight = MolSimilarity.similarity(MolSimilarity.Type.CorrHeavyAtomsCount, smile1, smile2);
        edges.add(new Edge<MorS, Double>(node1.get(i), node2.get(j), edgeweight));
      }
    }

    g = new Graph<MorS, Double>(nodes, edges);
    // also make sure that we install in the metadata of the graph the node lists
    g.setMetaData(GraphMatching.PartitionA, nodeIds1);
    g.setMetaData(GraphMatching.PartitionB, nodeIds2);
    return g;
  }

  public static HashMap<P<String, String>, Double> computePairingsSmiles(
      List<String> substrates, List<String> products,
      List<Double> substratePromiscuity, List<Double> productPromiscuity) {
    return MorS.convertToSmiles(
        computePairings(MorS.convertFromSmiles(substrates), MorS.convertFromSmiles(products), substratePromiscuity, productPromiscuity)
        );
  }

  public static Set<P<String, String>> computePairingsSmiles(List<String> smilesA, List<String> smilesB) {
    return MorS.convertToSmiles(
        computePairings(MorS.convertFromSmiles(smilesA), MorS.convertFromSmiles(smilesB))
        );
  }

  public static Set<P<MolGraph, MolGraph>> computePairingsMolGraphs(List<MolGraph> gA, List<MolGraph> gB) {
    return MorS.convertToGraph(
        computePairings(MorS.convertFromGraph(gA), MorS.convertFromGraph(gB))
        );
  }


}

class ExtendedSMILES extends SMILES // our own custom parser for annotated SMILES (e.g., with node ids on atoms)
{
    static List<String> AtomStrings;
    static {
      AtomStrings = new ArrayList<String>(); // Enum.GetNames(typeof(Graph.NodeType)).ToList();
      Element[] nt = Element.values();
    for (Element n : nt) AtomStrings.add(n.name());
    }

    // multiple disconnected compounds can be represented together; so we allow multiple incoming
    public static MolGraph ToGraph(List<String> smiles, boolean hasNodeIds) throws Exception
    {
        List<Edge<Atom, BondType>> edges = new ArrayList<Edge<Atom, BondType>>();
        List<Node<Atom>> nodes = new ArrayList<Node<Atom>>();
        HashMap<Integer, P<BondType, List<Node<Atom>>>> loops = new HashMap<Integer, P<BondType, List<Node<Atom>>>>();
        RefInt nodeid = new RefInt(0);
        for (String smile : smiles)
            addSubgraph(smile, null, nodes, edges, loops, nodeid, hasNodeIds);
        addLoopEdges(edges, loops);

        MolGraph g =  new MolGraph(nodes, edges);
        // g.LogSMILES = smiles; // log this smiles; in the Enumerator code, we are better of using this vanilla version
        return g;
    }

    private static void addLoopEdges(List<Edge<Atom, BondType>> edges, HashMap<Integer, P<BondType, List<Node<Atom>>>> loops) throws Exception
    {
        for (P<BondType, List<Node<Atom>>> e : loops.values())
        {
            if (e.snd().size() != 2) throw new Exception("Loops should be broken using exactly two nodes.");
            edges.add(new Edge<Atom, BondType>(e.snd().get(0), e.snd().get(1), e.fst()));
        }
    }

    private static void addSubgraph(String smile, Node<Atom> parent, List<Node<Atom>> nodes, List<Edge<Atom, BondType>> edges, HashMap<Integer, P<BondType, List<Node<Atom>>>> loops, RefInt nodeid, boolean hasIDs) throws Exception
    {
        RefInt charid = new RefInt(0);
        String idstr;

        BondType toParent = BondType.Single, bnd;
        while (charid.Int() < smile.length())
        {
            char c = smile.charAt(charid.Int());
            if ((bnd = isBond(c)) != BondType.Unknown)
            {
                toParent = bnd;
                charid.incr(); // charid++;
            }
            else if (c == '(')
            {
                int startloc = charid.Int() + 1; int matched = 1;
                while (matched != 0) { c = smile.charAt(charid.preincr()); if (c=='(') matched++; else if (c==')') matched--; }
                String bracstr = smile.substring(startloc, charid.Int() - startloc);
                addSubgraph(bracstr, parent, nodes, edges, loops, nodeid, hasIDs);
                charid.incr(); // charid++;
            }
            else if (c == '[')
            {
                // square bracs are reserved for elements with unusual valencies
                int startloc = charid.Int() + 1;
                while (c != ']') c = smile.charAt(charid.preincr());
                String nodestr = smile.substring(startloc, charid.Int() - startloc);
                charid.incr(); // charid++;

                idstr = optExtractID(hasIDs, smile, charid);
                if (true) throw new Exception("need to flag that this atom was indicated as having an unusual valence");
                // parent = processNode(nodestr, idstr, toParent, parent, edges, nodes, loops, ref nodeid, hasIDs);
                toParent = BondType.Single; // revert back to default state.
            }
            else
            {
                // now this has to be a single char/ two char atom
                String nodestr = getAtomicChars(smile, charid);
                // or at most a single atom with a loopid following it
                while(charid.Int() < smile.length() && Character.isDigit(smile.charAt(charid.Int())))
                      nodestr += smile.charAt(charid.postincr());
                // or at most an atom with a bond identifier and then a loopid
                if (charid.Int() + 1 < smile.length() && (bnd = isBond(smile.charAt(charid.Int()))) != BondType.Unknown && Character.isDigit(smile.charAt(charid.Int() + 1)))
                {
                    nodestr += smile.charAt(charid.postincr()); // copy the bond identifier character
                    // then copy all the id number digits
                    while (charid.Int() < smile.length() && Character.isDigit(smile.charAt(charid.Int())))
                        nodestr += smile.charAt(charid.postincr());
                }

                idstr = optExtractID(hasIDs, smile, charid);
                parent = processNode(nodestr, idstr, toParent, parent, edges, nodes, loops, nodeid, hasIDs);
                toParent = BondType.Single; // revert back to default state.
            }
        }
    }

    private static BondType isBond(char bondc)
    {
        switch (bondc)
        {
            case '-': return BondType.Single;
            case '=': return BondType.Double;
            case '#': return BondType.Triple;
            // case ':': bndTpe = Graph.EdgeType.Aromatic; return true;
            default: return BondType.Unknown;
        }
    }

    private static String getAtomicChars(String smile, RefInt charid) throws Exception
    {
        // check and return a one or two character string for the atom
        // increment charid accordingly.
        String second = null;
        String first = smile.charAt(charid.Int()) + "";
        if (charid.Int() + 1 < smile.length())
            second = first + smile.charAt(charid.Int() + 1);

        // handle the corner cases
        if (first == "*")
        {
            charid.incr(); return "*";
        }
        else if (second == null)
        {
            if (!matchAtom(first))
                throw new Exception("Could not match single character.");
            charid.incr();
            return first;
        }
        else
        {
            // check if we can match both the chars;
            if (matchAtom(second))
            {
                charid.incr(); charid.incr(); // charid += 2;
                return second;
            }

            // else attempt match with the single;
            // still fails then throw exception.
            if (!matchAtom(first))
                throw new Exception("Could not match single character.");

            charid.incr(); // charid++;
            return first;
        }
    }

    private static boolean matchAtom(String s)
    {
        return AtomStrings.contains(s);
    }

    private static String optExtractID(boolean hasIDs, String smile, RefInt charid) throws Exception
    {
        if (!hasIDs)
            return "";
        if (smile.charAt(charid.Int()) != '{')
            throw new Exception("SMILES apparently has node ids, but not found on this atom.");
        int startloc = charid.Int() + 1;
        while (smile.charAt(charid.Int()) != '}') charid.preincr();
        String nodestr = smile.substring(startloc, charid.Int() - startloc);
        charid.incr(); // charid++;
        return nodestr;
    }

    private static Node<Atom> processNode(String nodestr, String nodeIdstr, BondType toParent, Node<Atom> parent, List<Edge<Atom, BondType>> edges, List<Node<Atom>> nodes, HashMap<Integer, P<BondType, List<Node<Atom>>>> loops, RefInt nodeid, boolean hasId) throws Exception
    {
      Atom n; int loopId; BondType loopBnd;
        T<Atom, Integer, BondType> parsedNode;
        if ((parsedNode = parseAsNode(nodestr)) != null)
        {
          n = parsedNode.fst();
          loopId = parsedNode.snd();
          loopBnd = parsedNode.third();
            int id = hasId ? Integer.parseInt(nodeIdstr) : nodeid.postincr();
            Node<Atom> node = new Node<Atom>(id, n);
            nodes.add(node);
            if (parent != null)
            {
                // add edge to parent...
                Edge<Atom, BondType> edge = new Edge<Atom, BondType>(parent, node, toParent);
                edges.add(edge);
            }
            if (loopId != -1)
            {
                // this node forms the breakage point of the loop.
                if (!loops.containsKey(loopId))  {
                    P<BondType, List<Node<Atom>>> loope = new P<BondType, List<Node<Atom>>>(
                            loopBnd,
                            new ArrayList<Node<Atom>>());
                    loops.put(loopId, loope);
                }
                if (loopBnd != BondType.Single && loops.get(loopId).fst() != loopBnd)
                {
                    // upgrade loop edge type.
                    if (loops.get(loopId).fst() != BondType.Single)
                        throw new Exception("Not the default edge; conflicting loop break edge types.");
                    // else if the loop edge was indicated as single; then we can upgrade it given this new info from the node at the other end of the loop
                    loops.get(loopId).SetFst(loopBnd);
                }
                loops.get(loopId).snd().add(node);
            }
            return node;
        }
        else throw new Exception("Could not parse as node: " + nodestr);
    }

    private static T<Atom, Integer, BondType> parseAsNode(String s)
    {
      // out Graph.NodeType n, out int loopId, out Graph.EdgeType loopBnd
        Integer loopId = -1;
        BondType loopBnd = BondType.Single;
        Element n = null;

        if (s.charAt(0) == '*')
        {
            n = Element.Unknown; // unknownId = Int32.Parse(s.Substring(1));
            // we assume for the time being that unknowns cannot be the endpoints of a loop...
        }
        else
        {

            int last = s.length()-1;
            if (Character.isDigit(s.charAt(last)))
            {
                while (Character.isDigit(s.charAt(last))) last--;
                loopId = Integer.parseInt(s.substring(last + 1, s.length() - last - 1));

                if ((loopBnd = isBond(s.charAt(last))) != BondType.Unknown)
                    last--;
                s = s.substring(0, last + 1);
            }
            n = Enum.valueOf(Element.class, s);
        }
        if (n != null)
          return new T<Atom, Integer, BondType>(new Atom(n), loopId, loopBnd);
        else
          return null;
    }


}
