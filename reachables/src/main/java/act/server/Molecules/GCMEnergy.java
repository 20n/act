package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.server.Search.Counter;
import act.server.Search.SetBuckets;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoObject;

public class GCMEnergy {
  static private boolean DEBUG = false;
  final static private double ERROR_TOLERANCE = 1.0;
  static private int numTests;
  static private Map<String, Double> failed = new HashMap<String, Double>();
  static private Set<String> decomposeFailed = new HashSet<String>();

  static private class MolecularProperties {
    Counter<Integer> benzeneMembership = new Counter<Integer>();
    SetBuckets<Integer, Integer> benzeneBondMembership = new SetBuckets<Integer, Integer>();
    SetBuckets<Integer, Integer> ringMembership = new SetBuckets<Integer, Integer>();
    SetBuckets<Integer, Integer> ringBondMembership = new SetBuckets<Integer, Integer>();
  }


  static private void tests() {
    //Sulfur compounds
    testCalculation("C(C(C(=O)[O-])[NH3+])SSCC(C(=O)[O-])[NH3+]", -159.4);
    testCalculation("CSCCC(C(=O)[O-])[NH3+]", -75.2);
    testCalculation("C(CS)C(C(=O)[O-])[NH3+]", -79.3);
    testCalculation("C(C(C(=O)[O-])[NH3+])S", -81.0);
    testCalculation("CSC", 1.9);
    testCalculation("CCS", -0.6);
    testCalculation("O=C([O-])C([NH3+])CSCCC(C(=O)[O-])[NH3+]", -153.9);

    //Phosphorous
    testCalculation("O=P([O-])(OC[C@@H](O)[C@@H](O)[C@@H](O)[C@H](O)C(=O)CO)[O-]", -253.4);
    testCalculation("C(OP([O-])(=O)[O-])C(=O)C(=O)[O-]", -150.6);
    testCalculation("CC(=CCOP(=O)([O-])OP(=O)([O-])[O-])C", -24.5);
    testCalculation("O=C(C)OP(=O)([O-])([O-])", -88.1);

    //Nitrogen
    testCalculation("C(NC(=[NH2+])NC(CC(=O)[O-])C(=O)[O-])CCC(C(=O)[O-])[NH3+]", -217.5);
    testCalculation("[NH3+]C(CCCNC(N)=[NH2+])C([O-])=O", -67.7);
    testCalculation("C(NC([NH2])=O)CCC([NH3+])C(=O)[O-]", -121.0);
    testCalculation("CN(CC([O-])=O)C([NH2])=[NH2+]", -63.9);
    testCalculation("O=C([O-])C([NH3+])(C)CO", -123.8);
    testCalculation("C[NH3+]", -11.4);

    //Oxygen
    testCalculation("O=CC(O)C(O)C(O)C(O)C(O)CO", -252.7);
    testCalculation("O=C(C(O)C(O)C(O)C(O)CO)CO", -252.9);
    testCalculation("C(CC(=O)[O-])C(C(=O)[O-])C(O)C(=O)[O-]", -277.8);
    testCalculation("CC(O)C(=O)[O-]", -124.4);
    testCalculation("CCCCCCCCCCCCCCCC(=O)[O-]", -63.8);
    testCalculation("C(CO)O", -78.7);
    testCalculation("O=CC", -33.4);

    //Cyclic molecules
    testCalculation("C1(=CC(NC(=O)N1)=O)", -67.4);
    testCalculation("O=P(O)(O)OP(=O)(O)OP(=O)(O)OC[C@H]3O[C@@H](n2cnc1c(ncnc12)N)[C@H](O)[C@@H]3O", -60.4);
    testCalculation("C([O-])(=O)C([NH3+])CC1(C=CC=CC=1)", -50.7);
    testCalculation("C1(NC=NC=1CC(CO)[NH3+])", -9.0);
    testCalculation("C(OP(=O)([O-])[O-])C1(OC(C(O)C(O)1)N2(C=CC(=O)NC(=O)2))", -194.1);
    testCalculation("C1([C@@H]([C@H]([C@H](CO1)O)O)O)O", -178.6);

    testCalculation("C(NC1(C=CC(C(=O)NC(C(=O)[O-])CCC([O-])=O)=CC=1))C3(CNC2(=C(C(=O)NC(N)=N2)N3))", -85.2);
    //or is it -141.9 from http://biocyc.org/ECOLI/NEW-IMAGE?type=COMPOUND&object=THF

    //Others
    testCalculation("C(=O)C(C(C(COP([O-])([O-])=O)O)O)O", -180.3);

    System.out.println("Number of tests: " + numTests);
    System.out.println("Number of tests passed: " +
        (numTests - failed.size() - decomposeFailed.size()));
    System.out.println("Number failed to decompose: " + decomposeFailed.size());

    System.out.println("Failed to decompose the following");
    for (String s : decomposeFailed) System.out.println(s);
    System.out.println("Failed due to error too large");
    Set<String> failedSmiles = failed.keySet();
    for (String s : failedSmiles) System.out.println(s + " with an error of " + failed.get(s));
  }

  static private void testCalculation(String smiles, double answer) {
    numTests++;
    Double calculated = calculate(smiles);
    if (calculated == null) {
      decomposeFailed.add(smiles);
    } else {
      double error = Math.abs(calculated - answer);
      if (error > ERROR_TOLERANCE) {
        failed.put(smiles, error);
      }
    }
  }

  static private void debug(String s) {
    if (DEBUG)
      System.out.println(s);
  }

  /**
   * Corrections
   * - only CH
   * - each three atom ring
   * - each amide
   * - each heteroaromatic ring (following Huckel's rule; except they are treated like reg. non-benzene)
   *
   * Decomposition rules:
   *
   * *-[S-1](O)(O)(O)
   * *-[SH]
   * -S-S-
   * -S-
   *
   * *-O-[P-1](O)(O)-*
   * *-CO-O[P-2](O)(O)(O)
   * *-O[P-2](O)(O)(O) (prim, second, tert)
   * *-[P-2](O)(O)(O)
   * *-O-[P-1](O)(O)-O-*
   * *-O-[P-1](O)(O)-*
   *
   * *=N(-*)-* (two fused rings)
   * *(*)N=* (double bond and one single bond in ring
   * *=N-* (ring)
   * [NH](-*)-* (ring)
   * *-N(-*)(-*) (ring)
   * *-[NH2]
   * *-[NH3+]
   * *=N
   * *=[NH2+]
   * *=[NH]
   * [NH2+](-*)(-*)
   * [NH](-*)(-*)
   * *-N(-*)-*
   * *-[NH+](-*)-*
   *
   * *-O-CO-* (ring)
   * OC(-*)(-*) (ring)
   * *-O-* (ring)
   * -CH=O
   * -OH (attached to benzene ring)
   * -OH (primary)
   * -OH (secondary)
   * -OH (tertiary)
   * *-CO[O-]
   * *-CO-O-*
   * OC(-*)-*
   * *-O-*
   *
   * C(-*)(-*)=* (two fused benzene rings)
   * C(-*)(-*)=* (two fused non-benzene rings)
   * C(-*)(-*)=* (two fused rings; one benzene, and one non-benzene)
   * *-[CH]=* (benzene ring)
   * C(-*)(-*)=* (formal double and single bond in benzene ring)
   * C(-*)(-*)=* (two single bounds in a non-benzene ring)
   * *-[CH]=* (non-benzene ring)
   * C(-*)(-*)=*  (double bound and single bond in non-benzene ring)
   * *=[CH]
   * *=C-*
   * *-[CH]=*
   * *=[CH2]
   * *=C(-*)(-*)
   *
   * *-CH(-*)(-*) (in two fused non-benzene)
   * C(-*)(-*)(-*)-* (two non-benzene rings)
   * [CH2](-*)-* (non-benzene ring)
   * [CH](-*)(-*)-* (non-benzene ring)
   * C(-*)(-*)(-*)- (non-benzene ring)
   * *-[CH3]
   * *-[CH2]-*
   * [CH](-*)(-*)-*
   * C(-*)(-*)(-*)-*
   */
  static Indigo indigo = new Indigo();
  static public Double calculate(String smiles) throws IndigoException {
    IndigoObject mol = indigo.loadMolecule(smiles);
    IndigoObject matcher = indigo.substructureMatcher(mol);

    double totalEnergy = -23.6;
    debug("origin -23.6");
    //Hydrocarbon molecule?
    boolean isHydrocarbon = true;
    for (int i = 0; i < mol.countAtoms(); i++) {
      if (!mol.getAtom(i).symbol().equals("C"))
        isHydrocarbon = false;
    }
    if (isHydrocarbon) {
      totalEnergy += 4;
      debug("Hydrocarbon " + 4);
    }

    IndigoObject threeRingIter = mol.iterateRings(3, 3);
    for (IndigoObject ring : threeRingIter) {
      for (IndigoObject atom : ring.iterateAtoms()) {
        debug(atom.symbol());
      }
      totalEnergy += 28.9;
      debug("Three atom ring");
    }

    //Amides
    Set<Integer> usedNitrogens = new HashSet<Integer>();
    IndigoObject amideQuery = indigo.loadQueryMolecule("NC(=O)");
    IndigoObject amideIter = matcher.iterateMatches(amideQuery);
    for (IndigoObject m : amideIter) {
      IndigoObject nitrogen = amideQuery.getAtom(0);
      if (m.mapAtom(nitrogen) != null) {
        Integer idx = m.mapAtom(nitrogen).index();
        if (!usedNitrogens.contains(idx)) {
          totalEnergy -= 10.4;
          debug("amide energy -10.4");
          usedNitrogens.add(idx);
        }
      }
    }

    MolecularProperties props = new MolecularProperties();
    //Heteroaromatic rings
    mol.aromatize();
    IndigoObject ringIter = mol.iterateSSSR();
    int ringNum = 0;
    for (IndigoObject ring : ringIter) {
      boolean isAromatic = true;
      boolean isHetero = false;
      boolean isBenzene = false;
      int carbonCount = 0;
      for (IndigoObject bond : ring.iterateBonds()) {
        if (bond.bondOrder() != 4) {
          isAromatic = false;
        }
        props.ringBondMembership.put(bond.index(), ringNum);
      }

      for (IndigoObject atom : ring.iterateAtoms()) {
        String symbol = atom.symbol();
        if (!symbol.equals("C")) {
          isHetero = true;
        } else {
          carbonCount++;
        }
        props.ringMembership.put(atom.index(), ringNum);
      }
      if (carbonCount == 6 && isAromatic) {
        isBenzene = true;
      }
      if (isBenzene) {
        for (IndigoObject atom : ring.iterateAtoms()) {
          props.benzeneMembership.inc(atom.index());
        }
        for (IndigoObject bond : ring.iterateBonds()) {
          props.benzeneBondMembership.put(bond.index(), ringNum);
        }
      }
      if (isAromatic && isHetero) {
        totalEnergy -= 5.9;
        debug("heteroaromatic -5.9");
      }
      ringNum++;
    }

    Set<Integer> matched = new HashSet<Integer>();
    //Sulfur groups
    totalEnergy += match(mol, matcher, "[S-1](O)(O)(O)(-*)", 4, matched, -105.8);
    totalEnergy += match(mol, matcher, "[SH]-*", 1, matched, 13.4);
    totalEnergy += match(mol, matcher, "S(S(-*))(-*)", 2, matched, 5.8);
    totalEnergy += match(mol, matcher, "S(-*)(-*)", 1, matched, 9.5);

    //Phosphorous groups
    totalEnergy += match(mol, matcher, "O(-P([O-])(=O)-*)(-*)", 4, matched, 14.8, "in ring:0,1,4,5", props);
    totalEnergy += match(mol, matcher, "O(-P([OH])(=O)-*)(-*)", 4, matched, 14.8, "in ring:0,1,4,5", props); //same as above
    totalEnergy += match(mol, matcher, "C(=O)(-OP([O-])([O-])(=O))(-*)", 7, matched, -72.5);
    totalEnergy += match(mol, matcher, "C(=O)(-OP([O-])([OH])(=O))(-*)", 7, matched, -72.5); //same as above
    totalEnergy += match(mol, matcher, "O(P([O-])([O-])(=O))(-*)", 5, matched, -29.5);
    totalEnergy += match(mol, matcher, "O(P([OH])([OH])(=O))(-*)", 5, matched, -29.5); //same as above
    totalEnergy += match(mol, matcher, "P([O-])([O-])(=O)(-*)", 4, matched, 9.5);
    totalEnergy += match(mol, matcher, "P([OH])([OH])(=O)(-*)", 4, matched, 9.5); //same as above
    totalEnergy += match(mol, matcher, "O(-P([O-])(=O)-O-[!P])(-[!P])", 5, matched, -29.8);
    totalEnergy += match(mol, matcher, "O(-P([OH])(=O)-O-[!P])(-[!P])", 5, matched, -29.8); //same as above
    totalEnergy += match(mol, matcher, "O(-P([O-])(=O)-*)(-*)", 4, matched, -5.2);
    totalEnergy += match(mol, matcher, "O(-P([OH])(=O)-*)(-*)", 4, matched, -5.2); //same as above
    totalEnergy += match(mol, matcher, "O(-P([O-])(=O)-O-*)(-*)", 5, matched, -29.8);
    totalEnergy += match(mol, matcher, "O(-P([OH])(=O)-O-*)(-*)", 5, matched, -29.8); //same as above

    //Nitrogen
    totalEnergy += match(mol, matcher, "N(-*)(-*)(-*)", 1, matched, 18.9, "fused rings", props);
    totalEnergy += match(mol, matcher, "[N+](=*)(-*)(-*)", 1, matched, 0.4, "two bonds in ring", props);
    totalEnergy += match(mol, matcher, "[NH0](=*)(-*)", 1, matched, 10.4, "in ring", props);

    //this is sort of a hack to ensure triple bond with N gets matched before two aromatic bonds
    totalEnergy += match(mol, matcher, "N(:*)(:*)(:*)", 1, matched, 7.6, "in ring", props); //same as below;
    totalEnergy += match(mol, matcher, "N(-*)(:*)(:*)", 1, matched, 7.6, "in ring", props); //same as below;

    totalEnergy += match(mol, matcher, "[NH0](:*)(:*)", 1, matched, 10.4, "in ring", props); //same as above
    totalEnergy += match(mol, matcher, "[NH](-*)(-*)", 1, matched, 9.5, "in ring", props);
    totalEnergy += match(mol, matcher, "[NH](:*)(:*)", 1, matched, 9.5, "in ring", props); //same as above
    totalEnergy += match(mol, matcher, "N(-*)(-*)(-*)", 1, matched, 7.6, "in ring", props);

    totalEnergy += match(mol, matcher, "[NH2]-*", 1, matched, 10.3);
    totalEnergy += match(mol, matcher, "[NH3+]-*", 1, matched, 4.3);
    totalEnergy += match(mol, matcher, "N#*", 1, matched, 14.9);
    totalEnergy += match(mol, matcher, "[NH2+](=*)", 1, matched, .4);
    totalEnergy += match(mol, matcher, "[NH]=*", 1, matched, 13.6);
    totalEnergy += match(mol, matcher, "[NH2+](-*)(-*)", 1, matched, 6.9);
    totalEnergy += match(mol, matcher, "[NH](-*)(-*)", 1, matched, 7.6);
    totalEnergy += match(mol, matcher, "N(-*)(-*)(-*)", 1, matched, 6.3);
    totalEnergy += match(mol, matcher, "[NH+](-*)(-*)(-*)", 1, matched, 8.6);

    //Oxygen groups
    totalEnergy += match(mol, matcher, "O(-C(=O)-*)-*", 3, matched, -54.6, "in ring:0,1,3,4", props);
    totalEnergy += match(mol, matcher, "C(=O)(-*)-*", 2, matched, -27.4, "in ring:0,2,3", props);
    totalEnergy += match(mol, matcher, "O(-*)-*", 1, matched, -24.3, "in ring", props);
    totalEnergy += match(mol, matcher, "[CH](=O)-*", 2, matched, -17.8);

    totalEnergy += match(mol, matcher, "C(=O)([OH])(-*)", 3, matched, -72.0); //kind of a hack for matching C(=O)([O-])(-*)

    totalEnergy += match(mol, matcher, "[OH]-[c]", 1, matched, -31.8, "benzene [OH]-[c]", props);
    totalEnergy += match(mol, matcher, "[OH]-[CH2]", 1, matched, -29.3);
    totalEnergy += match(mol, matcher, "[OH]-[CH]", 1, matched, -32.0);
    totalEnergy += match(mol, matcher, "[OH]-[CH0]", 1, matched, -30.5);
    totalEnergy += match(mol, matcher, "C(=O)([O-])(-*)", 3, matched, -72.0);
    totalEnergy += match(mol, matcher, "C(=O)(-O-*)(-*)", 3, matched, -73.6);
    totalEnergy += match(mol, matcher, "C(=O)(-*)(-*)", 2, matched, -27.2);
    totalEnergy += match(mol, matcher, "O(-*)(-*)", 1, matched, -22.5);

    //Unsaturated carbon/hydrogen groups
    totalEnergy += match(mol, matcher, "C(:*)(:*)(:*)", 1, matched, 2.5, "fused benzene rings", props);
    totalEnergy += match(mol, matcher, "C(:*)(:*)(:*)", 1, matched, 16.8, "fused rings", props); //same as below
    totalEnergy += match(mol, matcher, "C(:*)(:*)(-*)", 1, matched, 16.8, "fused rings", props); //same as below
    totalEnergy += match(mol, matcher, "C(=*)(-*)(-*)", 1, matched, 16.8, "fused rings", props);
    totalEnergy += match(mol, matcher, "C(:*)(:*)(-*)", 1, matched, 6.0, "fused rings; one benzene", props);
    totalEnergy += match(mol, matcher, "C(:*)(:*)(:*)", 1, matched, 6.0, "fused rings; one benzene", props); //same as above
    totalEnergy += match(mol, matcher, "[cH](:*)(:*)", 1, matched, 8.4, "benzene [cH](:*)(:*)", props); //in benzene
    totalEnergy += match(mol, matcher, "c(:*)(:*)(-*)", 1, matched, 1.5, "benzene C(:*)(:*)(-*)", props); //in benzene
    totalEnergy += match(mol, matcher, "C(-*)(-*)(=*)", 1, matched, 22.8, "two bonds in ring", props);
    totalEnergy += match(mol, matcher, "[CH](-*)(=*)", 1, matched, 9.6, "in ring", props);
    totalEnergy += match(mol, matcher, "[CH](:*)(:*)", 1, matched, 9.6, "in ring", props); //same as above
    totalEnergy += match(mol, matcher, "C(=*)(-*)(-*)", 1, matched, 8.2, "two bonds in ring", props);
    totalEnergy += match(mol, matcher, "C(:*)(:*)(-*)", 1, matched, 8.2, "two bonds in ring", props); //same as above
    totalEnergy += match(mol, matcher, "[CH](#*)", 1, matched, 35.7);
    totalEnergy += match(mol, matcher, "C(#*)(-*)", 1, matched, 24.0);
    totalEnergy += match(mol, matcher, "[CH](-*)(=*)", 1, matched, 11.1);
    totalEnergy += match(mol, matcher, "[CH2](=*)", 1, matched, 18.4);
    totalEnergy += match(mol, matcher, "C(=*)(-*)(-*)", 1, matched, 5);

    //Carbon/Hydrogen groups
    totalEnergy += match(mol, matcher, "[CH](-*)(-*)(-*)", 1, matched, -0.9, "fused rings", props);
    totalEnergy += match(mol, matcher, "C(-*)(-*)(-*)(-*)", 1, matched, -12.0, "in two rings", props);
    totalEnergy += match(mol, matcher, "[CH2](-*)(-*)", 1, matched, 6.1, "in ring", props);
    totalEnergy += match(mol, matcher, "[CH](-*)(-*)(-*)", 1, matched, -2.2, "in ring", props);
    totalEnergy += match(mol, matcher, "C(-*)(-*)(-*)(-*)", 1, matched, -12.8, "in ring", props);
    totalEnergy += match(mol, matcher, "[CH3](-*)", 1, matched, 7.9);
    totalEnergy += match(mol, matcher, "[CH2](-*)(-*)", 1, matched, 1.7);
    totalEnergy += match(mol, matcher, "[CH](-*)(-*)(-*)", 1, matched, -4.8);
    totalEnergy += match(mol, matcher, "C(-*)(-*)(-*)(-*)", 1, matched, -12.8);

    int unmatchedCount = mol.countAtoms() - matched.size();
    debug(totalEnergy + " kcal\nUnmatched: " + unmatchedCount);
    for (int i = 0; i < mol.countAtoms(); i++) {
      if (!matched.contains(i))
        debug(i + " " + mol.getAtom(i).symbol());
    }
    if (unmatchedCount == 0) {
      return totalEnergy;
    }
    return null;
  }

  private static boolean conditionMatch(String cond, IndigoObject query, IndigoObject mapper,
      MolecularProperties props) {
    if (cond == null) {
      return true;
    } else if (cond.equals("benzene [cH](:*)(:*)") || cond.equals("benzene C(:*)(:*)(-*)")) {
      int count = 0;
      for (IndigoObject atom : query.iterateAtoms()) {
        if (mapper.mapAtom(atom) != null) {
          int index = mapper.mapAtom(atom).index();
          if (props.benzeneMembership.get(index) == 0)  {
            return false;
          }
          count++;
          if (count == 3) break;
        }
      }
      return true;
    } else if (cond.equals("benzene [OH]-[c]")) {
      IndigoObject atom = query.getAtom(1);
      if (mapper.mapAtom(atom) != null) {
        int index = mapper.mapAtom(atom).index();
        if (props.benzeneMembership.get(index) > 0)
          return true;
      }
      return false;
    } else if (cond.equals("fused rings")) {
      for (IndigoObject bond : query.iterateBonds()) {
        if (mapper.mapBond(bond) != null) {
          int index = mapper.mapBond(bond).index();
          if (props.ringBondMembership.get(index) != null &&
              props.ringBondMembership.get(index).size() == 2)
              return true;
        }
      }
      return false;
    } else if (cond.equals("fused benzene rings")) {
      for (IndigoObject bond : query.iterateBonds()) {
        if (mapper.mapBond(bond) != null) {
          int index = mapper.mapBond(bond).index();
          if (props.benzeneBondMembership.get(index) != null &&
              props.benzeneBondMembership.get(index).size() == 2)
              return true;
        }
      }
      return false;
    } else if (cond.equals("two bonds in ring")) {
      IndigoObject firstBond = query.getBond(0);
      IndigoObject secondBond = query.getBond(1);
      return props.ringBondMembership.get(mapper.mapBond(firstBond).index()) != null &&
          props.ringBondMembership.get(mapper.mapBond(secondBond).index()) != null;
    } else if (cond.equals("in ring:0,1,4,5")) {
      int index = mapper.mapAtom(query.getAtom(0)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(0)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(1)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(4)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(5)).index();
      if (props.ringMembership.get(index) == null) return false;
      return true;
    } else if (cond.equals("in ring:0,1,3,4")) {
      int index = mapper.mapAtom(query.getAtom(0)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(1)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(3)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(4)).index();
      if (props.ringMembership.get(index) == null) return false;
      return true;
    } else if (cond.equals("in ring:0,2,3")) {
      int index = mapper.mapAtom(query.getAtom(0)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(2)).index();
      if (props.ringMembership.get(index) == null) return false;
      index = mapper.mapAtom(query.getAtom(3)).index();
      if (props.ringMembership.get(index) == null) return false;
      return true;
    } else if (cond.equals("in ring")) {
      for (IndigoObject atom : query.iterateAtoms()) {
        if (mapper.mapAtom(atom) != null) {
          int index = mapper.mapAtom(atom).index();
          if (props.ringMembership.get(index) != null)
            return true;
          else
            return false;
        }
      }
    } else if (cond.equals("in two rings")) {
      IndigoObject atom = query.getAtom(0);
      int index = mapper.mapAtom(atom).index();
      if (props.ringMembership.get(index) == null ||
          props.ringMembership.get(index).size() < 2)
          return false;
      return true;
    } else if (cond.equals("fused rings; one benzene")) {
      for (IndigoObject bond : query.iterateBonds()) {
        if (mapper.mapBond(bond) != null) {
          int index = mapper.mapBond(bond).index();
          if (props.ringBondMembership.get(index) != null &&
              props.ringBondMembership.get(index).size() == 2 &&
              props.benzeneBondMembership.get(index) != null)
              return true;
        }
      }
      return false;
    }

    if (cond != null) {
      System.out.println("Warning, condition not found in condition match: " + cond);
    }

    return false;
  }

  private static double match(IndigoObject mol, IndigoObject matcher,
      String querySmiles, int numMatchAndRemove, Set<Integer> matched, double energy) {
    return match(mol, matcher, querySmiles, numMatchAndRemove, matched, energy, null, null);
  }

  private static double match(IndigoObject mol, IndigoObject matcher,
      String querySmiles, int numMatchAndRemove, Set<Integer> matched, double energy,
      String cond, MolecularProperties props) throws IndigoException {
    double energyAdded = 0;
    List<Integer> toRemove = new ArrayList<Integer>();
    IndigoObject query = indigo.loadQueryMolecule(querySmiles);
    IndigoObject matchIter = matcher.iterateMatches(query);
    for (IndigoObject m : matchIter) {
      int count = 0;
      IndigoObject iterateAtoms = query.iterateAtoms();
      for (IndigoObject atom : iterateAtoms) {
        if (m.mapAtom(atom) != null) {
          int index = m.mapAtom(atom).index();
          if (matched.contains(index)) {
            break;
          }
          toRemove.add(index);
          count++;
          if (count == numMatchAndRemove) break;
        }
      }
      if (count == numMatchAndRemove && conditionMatch(cond, query, m, props)) {
        matched.addAll(toRemove);
        energyAdded += energy;
        debug("match " + querySmiles + " energy " + energy);
      }
      toRemove.clear();
    }
    return energyAdded;
  }

  // Note this code requires coefficients to be installed
  // See Installer/BalanceEquations
  public static double computeReactionEnergy(long reactionID) {
    MongoDB db = new MongoDB();
    Reaction reaction = db.getReactionFromUUID(reactionID);
    double total = 0;
    for (Long p : reaction.getProducts()) {
      Chemical product = db.getChemicalFromChemicalUUID(p);
      String smiles = product.getSmiles();
      double e = calculate(smiles);
      System.out.println(smiles + " energy: " + e);
      total += e * reaction.getProductCoefficient(p);
    }
    for (Long r : reaction.getSubstrates()) {
      Chemical reactant = db.getChemicalFromChemicalUUID(r);
      String smiles = reactant.getSmiles();
      calculate(smiles);
      double e = calculate(smiles);
      System.out.println(smiles + " energy: " + e);
      total -= e * reaction.getSubstrateCoefficient(r);
    }
    return total;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    //System.out.println(computeReactionEnergy(38660) + " kcal");
    tests();
    //calculate("C1=CC(=CC=C1N)N");
    //calculate("C(NC1(C=CC(C(=O)NC(C(=O)[O-])CCC([O-])=O)=CC=1))C3(CNC2(=C(C(=O)NC(N)=N2)N3))");
    // palminoyl coa calculate("CCCCCCCCCCCCCCCC(SCCNC(=O)CCNC(=O)C(O)C(C)(C)COP(=O)(OP(=O)(OCC1(C(OP([O-])(=O)[O-])C(O)C(O1)N3(C2(=C(C(N)=NC=N2)N=C3))))[O-])[O-])=O");
    // 3-OH hexanoyl coa calculate("CCCC(CC(SCCNC(CCNC(C(C(COP(=O)([O-])OP(OCC1(OC(C(C1OP([O-])([O-])=O)O)N3(C=NC2(C(=NC=NC=23)N))))([O-])=O)(C)C)O)=O)=O)=O)O");
    // coa calculate("CC(C)(C(O)C(=O)NCCC(=O)NCCS)COP(=O)(OP(=O)(OCC1(OC(C(C1OP([O-])(=O)[O-])O)N3(C2(=C(C(N)=NC=N2)N=C3))))[O-])[O-]");
    // propionyl calculate("CCC(=O)SCCNC(=O)CCNC(=O)C(O)C(C)(C)COP(=O)(OP(=O)(OCC1(C(OP([O-])(=O)[O-])C(O)C(O1)N3(C2(=C(C(N)=NC=N2)N=C3))))[O-])[O-]");
  }

}
