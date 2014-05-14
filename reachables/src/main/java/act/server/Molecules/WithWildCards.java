package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.graph.Node;

public class WithWildCards {

	MolGraph mainParentGraph;
	MolGraph components;
	HashMap<Integer, Atom> constraints;

	public MolGraph getComponents() { return this.components; }
	
	public WithWildCards(MolGraph g) {
		this.mainParentGraph = g;
		this.constraints = new HashMap<Integer, Atom>();
		this.components = null; // instantiated below
	}

	public void createWCAtoms(MolGraph subgraphThatWasModified) {
		MolGraph wcgraph = subgraphThatWasModified.duplicate();
		HashMap<Integer, List<Integer>> neighbors = this.mainParentGraph.getEdgeIDs();
		
		
		List<Integer> subgraphids = new ArrayList<Integer>(wcgraph.Nodes().keySet());
		for (Integer id : subgraphids) {
			for (Integer nid : neighbors.get(id)) {
				
				Atom A = this.mainParentGraph.Nodes().get(nid);
				
				if (wcgraph.NodeExists(nid)) {
					// node already exists in the diff, 
					// we need to carry over the bonds
					// the bonds between the nodes from the parent graph
					BondType b = this.mainParentGraph.GetEdgeType(id, nid);
					wcgraph.SetEdgeType(id, nid, b);
					
					// There should be no reason to set the node data from the original graph, as below, is there?
					// wcgraph.SetNodeData(nid, A);
				} else {
					// node does not exist in subgraph, but edge does, so potentially a wildcard;
					
					// but before we put a wildcard in there, we should first check if the atom
					// corresponds to a dot. Because if it is then we just keep that
					if (A.elem.equals(DotNotation._proxyAtom)) {
						// aha. this is not a wildcard then, its just a dot
						// add the node back in
						wcgraph.CreateNewNodeAndAddEdgeTo(id, nid, new Atom(DotNotation._proxyAtom), BondType.Single);
						
					} else {
						// log the constraint as to what this wild card is abstracting
						this.constraints.put(id, A);
						
						// make this id a wildcard atom in the molecule
						makeWCAtom(wcgraph, nid);
						
						// if there was a dot on the atom here, then we need to resurrect that dot
						Integer idOfDotAdjToStar = null;
						if ((idOfDotAdjToStar = neighborsContainDot(neighbors.get(nid), this.mainParentGraph.Nodes())) != null) {
							wcgraph.CreateNewNodeAndAddEdgeTo(nid, idOfDotAdjToStar, new Atom(DotNotation._proxyAtom), BondType.Single);
						}
					}
				}
			}
		}
		
		// now split up the various submolecules
		this.components = toConnectedComponents(wcgraph);
		
	}

	private Integer neighborsContainDot(List<Integer> neighborIds, HashMap<Integer, Atom> atoms) {
		for (Integer n : neighborIds) {
			if (atoms.get(n).elem.equals(DotNotation._proxyAtom))
				return n;
		}
		return null;
	}

	private void makeWCAtom(MolGraph molInWhichToAddWCAtom, Integer id) {
		
		Node<Atom> wc_atom = new Node<Atom>(id, new Atom(Element.Unknown));
		molInWhichToAddWCAtom.AddNode(wc_atom);
		
		// add all edges it shares with already existing atoms in the subgraph...
		for (Integer nn : this.mainParentGraph.getEdgeIDs().get(id)) {
			if (molInWhichToAddWCAtom.NodeExists(nn)) { // this atom is present in the subgraph, add the edge nn<->id
				// only the id really matters, the node<nodetype> is only used for pretty printing; so we might as well create a new node...
				Node<Atom> nnNode = new Node<Atom>(nn, this.mainParentGraph.Nodes().get(nn)); // or we could core.getNode(nn)
				molInWhichToAddWCAtom.AddEdge(wc_atom, nnNode, this.mainParentGraph.GetEdgeType(id, nn));
			}
		}
	}

	private MolGraph toConnectedComponents(MolGraph sub) {
		return sub;
	}

}
