package act.server.Molecules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import act.graph.EdmondsMatching;
import act.server.Logger;
import act.shared.helpers.P;
import act.shared.helpers.T;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;

public class DotNotation {

  /*
   * PUBLIC INTERFACE, from strings of substrates and products to DOT notation reaction
   */
  public static IndigoObject ToDotNotationRxn(P<List<String>, List<String>> rxn, Indigo indigo) {
    IndigoObject r = indigo.createReaction();

    for (String s : rxn.fst())
      r.addReactant(indigo.loadMolecule(s));
    for (String p : rxn.snd())
      r.addProduct(indigo.loadMolecule(p));

    String ss = r.smiles();// if this fails then we have a problematic element in our molecules (maybe an extended smiles?)
    Logger.println(5, "[DotNotation] Before (full rxn): " + ss);

    IndigoObject rr = indigo.createReaction();
    for (IndigoObject s : r.iterateReactants()) {
      Logger.println(5, "[DotNotation] Before (substrate): " + s.smiles());
      rr.addReactant(ToDotNotationMol(s));
    }
    for (IndigoObject p : r.iterateProducts()) {
      Logger.println(5, "[DotNotation] Before (product): " + p.smiles());
      rr.addProduct(ToDotNotationMol(p));
    }

    ss = rr.smiles();
    Logger.println(5, "[DotNotation] After (full rxn): " + ss);

    return rr;
  }

  /*
   * PUBLIC INTERFACE, from molecule object to DOT notation molecule
   */
  public static IndigoObject ToDotNotationMol(IndigoObject molecule) {
    boolean debug = false;
    molecule.dearomatize(); // remove any aromatic representations

    // log explicitValence to reput later. Consider the case of N(V)=N. By putting an Ac, the implicit H's dissappears
    HashMap<Integer, Integer> explicitValence = new HashMap<Integer, Integer>();
    for (IndigoObject atom : molecule.iterateAtoms())
      if (atom.explicitValence() != null)
        explicitValence.put(atom.index(), atom.explicitValence());

    for (IndigoObject bond : molecule.iterateBonds()) {
      switch (bond.bondOrder()) {
      case 1: // single bond, do nothing
        break;
      case 2: // double bond, put two proxy atoms on each atom on the side
        bond.setBondOrder(1);
        addProxyAtom(molecule, bond.source(), 1);
        addProxyAtom(molecule, bond.destination(), 1);
        break;
      case 3: // triple bond, put two proxy atoms on each atom on the side
        bond.setBondOrder(1);
        addProxyAtom(molecule, bond.source(), 2);
        addProxyAtom(molecule, bond.destination(), 2);
        break;
      case 4:
        System.err.println("Woa! We dearomatized. Why is an aromatic bond here."); System.exit(-1);
        break;
      }
    }
    if (debug)
      Logger.println(7, "[DotNotation] Added proxies: " + molecule.smiles());

    // first handle all negative charges as they are just electrons
    for (IndigoObject atom : molecule.iterateAtoms())
      if (atom.charge() < 0) {
        addProxyAtom(molecule, atom, -atom.charge()); // the negative needs to be inverted
        atom.setCharge(0); // all negative charge has been moved as radicals on the atom
      }
    if (debug)
      Logger.println(7, "[DotNotation] Removed -ves: " + molecule.smiles());

    // now handle all positive charges by removing appropriate number of proxy atoms
    for (IndigoObject atom : molecule.iterateAtoms())
      if (atom.charge() > 0) {
        int i = countNeighborProxyAtoms(atom);
        if (i == 0) continue; // no proxy atoms to remove...
        if (i >= atom.charge()) {
          removeProxyAtom(atom, atom.charge()); // each charge unit takes off one proxy atom
          atom.setCharge(0); // the charge has been neutralized
        } else {
          atom.setCharge(atom.charge() - i); // there is still some charge left on it, and that is delta from the number of D's already there
          removeProxyAtom(atom, i); // remove all i proxy atoms
        }
      }

    // reset the explicit valence, because adding proxies might have taken it over
    for (Integer idx : explicitValence.keySet())
      molecule.getAtom(idx).setExplicitValence(explicitValence.get(idx));

    if (debug)
      Logger.println(5, "[DotNotation] Converted: " + molecule.smiles());

    return molecule;
  }

  public static Element _proxyAtom = Element.getMaxExtraHeavyAtom();
  public static int _proxyIsotope = Element.getMaxExtraHeavyAtom().atomicNumber() + 1;

  private static void removeProxyAtom(IndigoObject atom, int c) {
    if (c <= 0) {
      System.err.println("Can only remove a positive number of proxy atoms. While asked: " + c);
      System.exit(-1);
    }
    for (IndigoObject n : atom.iterateNeighbors()) {
      if (n.isotope() == _proxyIsotope && n.atomicNumber() == _proxyAtom.atomicNumber()) {
        n.remove();
        c--;
        if (c == 0)
          break;
      }
    }
  }

  private static void removeProxyAtom(MolGraph mol, Integer nodeID, int howmany) {
    for (P<Integer, BondType> n : mol.AdjList().get(nodeID)) {
      Atom nAtom = mol.GetNodeType(n.fst());
      if (nAtom.elem.atomicNumber() == _proxyAtom.atomicNumber()) {
        // remove id n.fst() from the graph...
        mol.removeNode(n.fst());
        howmany--;
        if (howmany == 0)
          break;
      }
    }
  }

  private static int countNeighborProxyAtoms(IndigoObject atom) {
    int count = 0;
    for (IndigoObject n : atom.iterateNeighbors()) {
      if (n.isotope() == _proxyIsotope && n.atomicNumber() == _proxyAtom.atomicNumber())
        count++;
    }
    return count;
  }

  private static int countNeighborProxyAtoms(int nodeID, MolGraph mol) {
    int count = 0;
    if (mol.AdjList().containsKey(nodeID)) {
      for (P<Integer, BondType> n : mol.AdjList().get(nodeID)) {
        Atom nAtom = mol.GetNodeType(n.fst());
        // we do not carry over the isotope number into the MolGraph, so we just compare the atomic number for right now.
        if (nAtom.elem.atomicNumber() == _proxyAtom.atomicNumber())
          count++;
      }
    }
    return count;
  }

  private static void addProxyAtom(IndigoObject mol, IndigoObject atom, int n) {
    if (n < 0) {
      System.err.println("Cannot add negative number of proxy atom to molecule.");
      System.exit(-1);
    }
    for (int i = 0; i<n; i++) {
      IndigoObject proxy = mol.addAtom(_proxyAtom.name());
      proxy.setIsotope(_proxyIsotope);
      atom.addBond(proxy, 1); // add a proxy atom with a single bond to the current atom
    }
  }

  private static void updateNegativeChargesFromProxyAtoms(MolGraph mol) {
    Set<Integer> nodeids = new HashSet<Integer>(mol.Nodes().keySet());
    for (Integer aID : nodeids) {
      Atom a = mol.Nodes().get(aID);
      int num = countNeighborProxyAtoms(aID, mol);
      if (num > 0) {
        a.setCharge(a.getCharge() - num);
        removeProxyAtom(mol, aID, num);
      }
    }
  }


  /*
   * PUBLIC INTERFACE, from dot notation molgraph to normal
   */

  public static String ToNormalMol(IndigoObject mol, Indigo indigo) {
    mol.foldHydrogens();
    MolGraph m = SMILES.ToGraph(mol);
    MolGraph normal = ToNormalMol(m, m); // TODO: unsure what the origin molecule is for
    return SMILES.FromGraphWithUnknownAtoms(indigo, normal);
  }

  public static MolGraph ToNormalMol(MolGraph mol, MolGraph molOrigin) {

    // iterate {
    //     create to-be-matched graph, with edge weights as the sum of atomic weight endpoints
    //     if graph has any edges: do maximal matching, create bonds from matching, else: break;
    // }
    // for any remaining radicals, convert them to negative charges
    //
    // create to-be-matched graph:
    //    if bond (iterating over bonds in the original graph molOrigin) has endpoints with radicals then add a candidate edge
    MolGraph molecule = mol.duplicate();
    while (true) {
      // created a weighted graph with edge weights as the sum of atomic numbers on the sides
      // For right now, we just create an unweighted graph, which suffices for most canonicalization
      // except for some corner cases.
      UndirectedGraph<Integer, DefaultEdge> g = createGraphFromProxyAtomized(molecule, molOrigin);
      if (g.edgeSet().isEmpty())
        break;
      // System.out.println("Running matching on: " + g);
      // compute maximal matching
      // Set<DefaultEdge> matching = new EdmondsBlossomShrinking<Integer, DefaultEdge>().findMatch(g);
      Set<DefaultEdge> matching = EdmondsMatching.findMatch(g);

      // System.out.println("matching: " + matching);
      // from matching create extra bonds over and above those already present
      updateBondsFromMatching(molecule, g, matching);
    }

    // if any left over radicals remain, make them into negative charges.
    updateNegativeChargesFromProxyAtoms(molecule);
    return molecule;
  }

  private static void updateBondsFromMatching(MolGraph mol, UndirectedGraph<Integer, DefaultEdge> g, Set<DefaultEdge> matching) {
    // for all edges
    //    if matching contains edge then increment the bond number in g, remove radicals on either side

    for (DefaultEdge e : matching) {
      Integer sID = g.getEdgeSource(e);
      Integer dID = g.getEdgeTarget(e);

      // get current bond and source and destination atoms in the molgraph
      // Atom s = mol.Nodes().get(sID);
      // Atom d = mol.Nodes().get(dID);

      // increment bond number of the molecule
      incrementBondType(mol, sID, dID);

      // remove the radical on both the source and destination
      removeProxyAtom(mol, sID, 1);
      removeProxyAtom(mol, dID, 1);
    }
  }

  private static void incrementBondType(MolGraph mol, Integer sID, Integer dID) {
    BondType b = mol.GetEdgeType(sID, dID);
    BondType B;

    if (b == null)
      B = BondType.Single;
    else
      switch (b) {
      case Unknown: System.err.println("Unknown should not have been a candidate in the matching"); System.exit(-1);
      case Triple: System.err.println("Triple should not have been a candidate in the matching"); System.exit(-1);
      case Single: B=BondType.Double; break;
      case Double: B=BondType.Triple; break;
      default: B=null; // should not happen
      }

    List<P<Integer, BondType>> adj = mol.AdjList().get(sID);
    boolean added = false;
    for (int i = 0; i<adj.size(); i++) {
      if (adj.get(i).fst() == dID) {
        adj.remove(i);
        adj.add(i, new P<Integer, BondType>(dID, B));
        added = true;
      }
    }
    if (!added)
      adj.add(new P<Integer, BondType>(dID, B));
  }

  private static UndirectedGraph<Integer, DefaultEdge> createGraphFromProxyAtomized(MolGraph molDiff, MolGraph molOrigin) {
    // for all edges (in molOrigin)
    //     if both endpoints of edge (in molDiff) have at least one radical,
    //    add edge to candidate_set, set its weight to sum of atomic weights (for arbitrary ordering)
    // create a graph with candidate_set

    Set<Integer> nodes = new HashSet<Integer>();
    Set<T<Integer, Integer, Double>> edges = new HashSet<T<Integer, Integer, Double>>();
    for (T<Integer, Integer, BondType> edge : molOrigin.ComputeEdges()) {
      int sID = edge.fst(), dID = edge.snd();
      BondType b = molDiff.GetEdgeType(sID, dID); // edge.third(); do not get the BOND type from the original, get it from the diff
      // b can be null if there is no edge in the diff (this is likely, coz the double bonds were moved to proxyatoms)

      Atom s = molDiff.Nodes().get(sID);
      Atom d = molDiff.Nodes().get(dID);
      if (s != null && d != null // only count atoms and bonds if the atoms exist in the diff
          && countNeighborProxyAtoms(sID, molDiff) > 0
          && countNeighborProxyAtoms(dID, molDiff) > 0
          && b != BondType.Triple // if triple, and we matched, would we go to a 4 bond!?
          && b != BondType.Unknown // should never happen
          // but b can be null if there is no bond in the diff, and thats ok
          ) {

        // add edge to candidates
        double weight = s.elem.atomicNumber() + d.elem.atomicNumber();
        // for right now we ignore the weight, but later on we should create a WeightedGraph
        // as opposed to an UndirectedGraph

        nodes.add(sID); nodes.add(dID);
        edges.add(new T<Integer, Integer, Double>(sID, dID, weight));
      }
    }
    UndirectedGraph<Integer, DefaultEdge> g = new SimpleGraph<Integer, DefaultEdge>(DefaultEdge.class);
    for (Integer n : nodes) {
      g.addVertex(n);
    }
    for (T<Integer, Integer, Double> e : edges) {
      g.addEdge(e.fst(), e.snd());
    }

    return g;
  }

}
