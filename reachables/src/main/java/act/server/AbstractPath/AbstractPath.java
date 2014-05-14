package act.server.AbstractPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import act.graph.Graph;
import act.graph.HyperGraph;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.ReactionDetailed;
import act.shared.helpers.P;


/*
 * BIG TODO: Consider simple graph; not a hypergraph: only changes will be to edge functions
 */


// Speculated Chem: The nodes of the graph
// Holds:
// 		- The current concrete chemical
//		- The index into the operator hierarchy that has already been queried; is 0 at the start; -1 when done.
class SpecChem {
	enum Color { red, green }
	
	int currentIndexIntoOperatorHier;
	Chemical chem;
	Color color;
	
	public SpecChem(Chemical c, Color color) {
		this.chem = c;
		this.currentIndexIntoOperatorHier = 0;
		this.color = color;
	}
	
	public void set(Color c) { this.color = c; }
}

// Instantiated Operator: The edges of the graph
// Holds:
//		- Which operators was applied to get from the src to the dst.
//		- If there are "delayed" chems that will appear later, then the list of those.
class InstOp {
	SpecChem src, dst;
	int operatorIndex;
}

class Path {
	Set<InstOp> pathedges;

	public List<List<ReactionDetailed>> flatten() {
		// TODO Auto-generated method stub
		return null;
	}
}

class PartialGraph {
	HyperGraph<SpecChem, InstOp> G;
	SpecChem initNode;
	OperatorHierarchy ops;
	
	public PartialGraph(Chemical target, List<Chemical> sinks, OperatorHierarchy ops) {
		this.ops = ops;
		this.G = new HyperGraph<SpecChem, InstOp>();
		this.G.addNode(this.initNode = new SpecChem(target, SpecChem.Color.red));
		
		for (Chemical c : sinks)
			this.G.addNode(new SpecChem(c, SpecChem.Color.green));
	}
	
	public void augment() {
		SpecChem nextViable = getNextViable();
		if (nextViable == null) { System.err.println("AbstractPath.PartialGraph.augment(): NO viable node found!?"); System.exit(-1); }
		Applier upd = ops.Transform(nextViable.chem, false /* go backwards */, nextViable.currentIndexIntoOperatorHier);
		nextViable.currentIndexIntoOperatorHier = upd.nextIndex; /* nextIndex */
		// TODO: do something with the new chemicals explored....
		// add them to the graph....
		// then add an hyperedge from them to this chem...
		
		// then see if the colors need to be updated...
		// nextViable.color == red by construction (that was a filter in getNextViable)
		// but now it could potentially be updated to GREEN...
	}

	private SpecChem getNextViable() {
		// TODO: find a node that is a) red, b) indexIntoHier != -1
		
		return null;
	}

	public Path getOnePath() {
		// start adding edges from the this.initNode all edges that have both src,dst green
		// it should eventually terminate with the only one sided nodes being the target
		// and the sinks....

		System.err.println("getOnePath..."); System.exit(-1);
		return null;
	}
	
	public boolean pathwayFound() {
		// if the init, which started off as RED, is now GREEN then a path has been located.
		return this.initNode.color == SpecChem.Color.green;
	}
}

public class AbstractPath {
	MongoDB DB;
	List<Long> src_metabolites;
	OperatorHierarchy ops;

	public AbstractPath(MongoDB mongoDB, List<Long> src_metabolites) {
		this.DB = mongoDB;
		this.src_metabolites = src_metabolites;
		this.ops = OperatorHierarchySimple.getInstance(this.DB);
	}

	public List<List<ReactionDetailed>> findPath(Chemical target) {
		PartialGraph G = new PartialGraph(target, getSinks(), this.ops);
		
		while (!G.pathwayFound())
			G.augment();
		
		return G.getOnePath().flatten();
	}

	private List<Chemical> getSinks() {
		List<Chemical> sinks = new ArrayList<Chemical>();
		for (Long id : this.src_metabolites)
			sinks.add(chemById(id));
		return sinks;
	}

	private Chemical chemById(Long id) {
		System.err.println("Chem by id..."); System.exit(-1);
		return null;
	}

}
