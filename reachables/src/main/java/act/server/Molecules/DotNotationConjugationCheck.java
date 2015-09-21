package act.server.Molecules;

import java.util.ArrayList;
import java.util.List;

import act.graph.Node;

public class DotNotationConjugationCheck {

  MolGraph G;
  public DotNotationConjugationCheck(MolGraph g) {
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
        for (Integer nid : this.G.getEdgeIDs().get(coreAtomId)) {
          Atom A = this.G.Nodes().get(nid);
          if (core.NodeExists(nid))
            continue;
          if (hasProxyAtomOnIt(A, nid)) {
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


  private boolean hasProxyAtomOnIt(Atom a, Integer nid) {
    for (Integer n : this.G.getEdgeIDs().get(nid)) {
      if (this.G.GetNodeType(n).elem.equals(DotNotation._proxyAtom))
        return true;
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
}
