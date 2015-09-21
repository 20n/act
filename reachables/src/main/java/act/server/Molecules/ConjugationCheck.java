package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.graph.Node;

public class ConjugationCheck {

  MolGraph G;
  public ConjugationCheck(MolGraph g) {
    this.G = g;
  }

  public MolGraph getConjugated(MolGraph core) {

    // 1. add the pi cloud....
    boolean addedAtom;
    do {
      addedAtom = false;
      // N <- neighborhood(core)
      // for all atoms A in N | if A has at least one non-single bond edge
      //     1. add A to the core
      //     2. also add the edge shared, and any edges to other core atoms
      //     3. set addedAtom = true
      List<Integer> coreids = new ArrayList<Integer>(core.Nodes().keySet());
      for (Integer coreAtomId : coreids) {
        // NodeType atom = core.Nodes().get(coreAtomId);
        for (Integer nid : this.G.getEdgeIDs().get(coreAtomId)) {
          Atom A = this.G.Nodes().get(nid);
          if (core.NodeExists(nid))
            continue;
          if (isStarred(A, nid)) {
            // add A to core; along with edges it shares with atoms already in the core..
            addToCore(core, A, nid);
            addedAtom = true;
          }
        }
      }
    } while (addedAtom);

    // 2. add the sigma bond atoms to the current core
    List<Integer> coreids = new ArrayList<Integer>(core.Nodes().keySet());
    for (Integer id : coreids) {
      for (Integer nid : this.G.getEdgeIDs().get(id)) {
        Atom A = this.G.Nodes().get(nid);
        if (core.NodeExists(nid))
          continue;
        addToCore(core, A, nid);
      }
    }

    return core;
  }


  private boolean isStarred(Atom A, int id) {
    for (Integer n : this.G.getEdgeIDs().get(id)) {
      switch (this.G.GetEdgeType(id, n)) {
      case Single: break;
      case Double: return true;
      case Triple: return true;
      default: System.err.println("Has strange edge type."); System.exit(-1);
      }
    }
    return false;
  }

  private void addToCore(MolGraph core, Atom A, int id) {
    // create and add a new node...
    Node<Atom> newNode = new Node<Atom>(id, A);
    core.AddNode(newNode);

    // add all edges it shares with already existing atoms in the core...
    for (Integer nn : this.G.getEdgeIDs().get(id)) {
      if (core.NodeExists(nn)) { // this atom is already present in the core, add the edge nn<->neighborInG
        // only the id really matters, the node<nodetype> is only used for pretty printing; so we might as well create a new node...
        Node<Atom> nnNode = new Node<Atom>(nn, this.G.Nodes().get(nn)); // or we could core.getNode(nn)
        core.AddEdge(newNode, nnNode, this.G.GetEdgeType(id, nn));
      }
    }
  }

  /*
   * OLD CODE
   */
  public MolGraph oldGetConjugated(MolGraph core) {
    HashMap<Integer, Atom> atoms = this.G.Nodes();
    HashMap<Integer, List<Integer>> neighbors = this.G.getEdgeIDs();
    boolean addedAtom;
    do {
      addedAtom = false;
      // for all atoms in the current core, see if any of their neighbors are conjugated; if yes then
      // 1. add that atom to the core
      // 2. also add the edge shared, and any edges to other core atoms
      // 3. set addedAtom = true
      for (Integer coreAtomId : core.Nodes().keySet()) {
        Atom atom = core.Nodes().get(coreAtomId);
        int atomNeighCnt = neighbors.get(coreAtomId).size();
        // since the atom id maps are consistent, we do not need to check if the nodetype is the same (it necessarily is)
        for (Integer neighborInG : neighbors.get(coreAtomId)) {
          Atom nAtom = atoms.get(neighborInG);
          int nAtomNeighCnt = neighbors.get(neighborInG).size();
          if (core.NodeExists(neighborInG))
            continue;
          if (oldIsConjugated(atom, atomNeighCnt, nAtom, nAtomNeighCnt, this.G.GetEdgeType(coreAtomId, neighborInG))) {
            // add the atom to core..
            Node<Atom> newNode = new Node<Atom>(neighborInG, nAtom);
            core.AddNode(newNode);
            // add all edges it shares with already existing atoms in the core...
            for (Integer nn : neighbors.get(neighborInG)) {
              if (core.NodeExists(nn)) { // this atom is already present in the core, add the edge nn<->neighborInG
                // only the id really matters, the node<nodetype> is only used for pretty printing; so we might as well create a new node...
                Node<Atom> nnNode = new Node<Atom>(nn, atoms.get(nn)); // or we could core.getNode(nn)
                core.AddEdge(newNode, nnNode, this.G.GetEdgeType(neighborInG, nn));
              }
            }
            addedAtom = true;
          }
        }
      }
    } while (addedAtom);

    return core;
  }

  private boolean oldIsConjugated(Atom a1, int a1BondsCnt, Atom a2, int a2BondsCnt, BondType bond) {
      //Hydrogen has no P orbitals, not electronically possible
    if (a1.elem == Element.H || a2.elem == Element.H) return false;

      //If either is carbon with more than 3 attached atoms, it isn't electronically possible
    if (a1.elem == Element.C && a1BondsCnt > 3) return false;
    if (a2.elem == Element.C && a2BondsCnt > 3) return false;

      //If either is oxygen with more than 1  attached atoms, it isn't electronically possible
    if (a1.elem == Element.O && a1BondsCnt > 1) return false;
    if (a2.elem == Element.O && a2BondsCnt > 1) return false;

    return true;
    // TODO: fix this function!
    /*
    System.err.println("isconjugated..."); System.exit(-1);

      // Sulfur in bio comes in three states:  one likes 2 bonds and behaves like O (can be 2 sigmas or 1 pi that can conjugate),
      // then it has the positively charged state like in S-adenosyl methionine where it can't conjugate, and the third is the
      // sulfate/sulfonate state, which likes two double bonds, so it conjugates .
    System.err.println("do something about sulfur..."); System.exit(-1);

      //If it got through the gauntlet, that means there was an case it couldn't classify
      throw Exception();
    */
  }

}
