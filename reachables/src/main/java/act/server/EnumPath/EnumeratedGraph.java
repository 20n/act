package act.server.EnumPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.graph.DiGraph;
import act.graph.Edge;
import act.graph.Graph;
import act.graph.Node;
import act.server.Logger;
import act.server.EnumPath.OperatorSet.OpID;
import act.server.EnumPath.ReachableChems.Color;
import act.server.Molecules.RO;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.helpers.P;
import act.shared.helpers.T;

class ReachableChems {
	Chemical c;
	OperatorSet operatorBasis;
	List<OperatorSet.OpID> alreadyAppliedOps;
	enum Color { ReachableFromStart, ReachableFromEnd, ReachableFromStartANDEnd }
	Color reachStatus;
	int distFromStart, distFromEnd;
	
	public ReachableChems(Chemical c, OperatorSet opSet, Color color, int distFromStart, int distFromEnd) {
		this.c = c;
		this.operatorBasis = opSet;
		this.alreadyAppliedOps = new ArrayList<OperatorSet.OpID>();
		this.reachStatus = color;
		this.distFromEnd = distFromEnd;
		this.distFromStart = distFromStart;
	}

	public RO lookupRO(OpID id) {
		return this.operatorBasis.lookupCRO(id);
	}
	
	public Chemical getChem() {
		return c;
	}

	public void addUsedOperator(OperatorSet.OpID opID) {
		this.alreadyAppliedOps.add(opID);
	}

	public void reachableFromBoth() {
		this.reachStatus = Color.ReachableFromStartANDEnd;
	}
	

	public boolean exhaustedAllOperators() {
		return operatorBasis.coveredAll(this.alreadyAppliedOps);
	}
}

class EnumeratedGraph {
	List<Node<ReachableChems>> worklist;
	List<Node<ReachableChems>> ambassadors;
	HashMap<String, Node<ReachableChems>> instantiatedChemsInG;
	DiGraph<ReachableChems, OperatorSet.OpID> G;
	NetworkStats stats;
	private final List<Integer> Start, End; // list of node ids we created for the start and end (used for terminating path lookup for connected paths)
	
	public EnumeratedGraph(List<Chemical> startingChem, OperatorSet ops, List<Chemical> endingChem) {
		checkSeparationBetweenStartEnd(startingChem, endingChem);

		this.instantiatedChemsInG = new HashMap<String, Node<ReachableChems>>();
		List<Node<ReachableChems>> nodes = new ArrayList<Node<ReachableChems>>();
		this.Start = new ArrayList<Integer>();
		int distFromStart = 0;
		int distFromEnd = Integer.MAX_VALUE;
		for (Chemical s : startingChem) {
			if (this.instantiatedChemsInG.containsKey(s.getSmiles()))
				continue; // already added....
			Node<ReachableChems> sNode = constructGraphNode(s, ops, Color.ReachableFromStart, distFromStart, distFromEnd);
			this.instantiatedChemsInG.put(s.getSmiles(), sNode);
			this.Start.add(sNode.id);
			nodes.add(sNode);
		}
		
		this.End = new ArrayList<Integer>();
		distFromStart = Integer.MAX_VALUE;
		distFromEnd = 0;
		for (Chemical e : endingChem) {
			if (this.instantiatedChemsInG.containsKey(e.getSmiles()))
				continue; // already added...
			Node<ReachableChems> eNode = constructGraphNode(e, ops, Color.ReachableFromEnd, distFromStart, distFromEnd);
			this.instantiatedChemsInG.put(e.getSmiles(), eNode);
			this.End.add(eNode.id);
			nodes.add(eNode);
		}
		
		// ambassadors are nodes that are reachableFromBoth..but at the beginning there are none
		this.ambassadors = new ArrayList<Node<ReachableChems>>();
		
		// Construct the worklist of ALL chemicals (both starting and ending)
		this.worklist = new ArrayList<Node<ReachableChems>>();
		this.worklist.addAll(nodes);
			
		// Then construct a graph with all of them, using the fwd and bwd operators
		List<Edge<ReachableChems, OperatorSet.OpID>> emptyEdgeList = new ArrayList<Edge<ReachableChems, OperatorSet.OpID>>();
		this.G = new DiGraph<ReachableChems, OperatorSet.OpID>(nodes, emptyEdgeList, false);
		
		this.stats = new NetworkStats();
	}

	public int size() {
		return this.G.size();
	}
	
	public void toDOT(String fname) throws IOException {
		this.G.writeDOT(fname);
	}
	
	public HashMap<Color, List<String>> smilesInGraph() {
		HashMap<Color, List<String>> allChems = new HashMap<Color, List<String>>();
		for (String c : this.instantiatedChemsInG.keySet()) {
			Color co = this.instantiatedChemsInG.get(c).atom.reachStatus;
			if (!allChems.containsKey(co))
				allChems.put(co, new ArrayList<String>());
			allChems.get(co).add(c);
		}
		return allChems;
	}
	
	private void checkSeparationBetweenStartEnd(List<Chemical> startingChem, List<Chemical> endingChem) {
		List<String> endingChemSmiles = new ArrayList<String>();
		for (Chemical c : endingChem)
			endingChemSmiles.add(c.getSmiles());
		for (Chemical c : startingChem) {
			if (!endingChemSmiles.contains(c.getSmiles()))
				continue;
			System.out.println("The starting and destination chemicals have an intersection: " + c);
			System.exit(1);
		}
	}

	public int getNumPaths() {
		return this.ambassadors.size();
	}

	public List<Path> getEndToEndPaths() {
		// See if there are nodes that are ReachableFromStartANDEnd.
		
		// If enough ambassadors exists, we create a path from each...
		// Propagate to right and left of ambassador until we reach the End, and Start nodes respectively...

		List<Path> paths = new ArrayList<Path>();
		for (Node<ReachableChems> n : this.ambassadors) {
			paths.add(constructPath(n));
		}
		return paths;
	}

	private Path constructPath(Node<ReachableChems> n) {
		boolean toEnd;
		List<Chemical> path = new ArrayList<Chemical>();
		List<OperatorSet.OpID> pathOpIDsUsed = new ArrayList<OperatorSet.OpID>();
		List<RO> pathOpsUsed = new ArrayList<RO>();
		Node<ReachableChems> middleChem = this.ambassadors.get(0);
		path.add(middleChem.atom.c); // path will have one more entry than the number of edges between the chems; as it should
		extendPath(path, pathOpsUsed, pathOpIDsUsed, toEnd = true, this.End, middleChem.id, this.G.AdjList(), Color.ReachableFromEnd);
		extendPath(path, pathOpsUsed, pathOpIDsUsed, toEnd = false, this.Start, middleChem.id, this.G.ReverseAdjList(), Color.ReachableFromStart);
		
		List<Long> ro2Ids = getIdsForROs(pathOpsUsed);
		return new Path(path, ro2Ids); // T<List<Chemical>, List<OperatorSet.OpID>, List<RO>>(path, pathOpIDsUsed, pathOpsUsed);
	}

	private List<Long> getIdsForROs(List<RO> rolist) {
		List<Long> ids = new ArrayList<Long>();
		for (RO ro : rolist)
			ids.add((long)ro.ID());
		return ids;
	}

	private void extendPath(List<Chemical> path, List<RO> pathOpsUsed, List<OperatorSet.OpID> pathOpIDsUsed, 
			boolean goingToEnd, List<Integer> terminal,
			int nodeid, HashMap<Integer, List<P<Integer, OpID>>> adjList,
			Color colorToFollow) {
		while (!terminal.contains(nodeid)) {
			// chase edge and reassign nodeid. add that node/edge to path
			List<P<Integer, OperatorSet.OpID>> outEdges = adjList.get(nodeid);
			// there has to be at least one edge that is reachable from the edge
			// of the graph..find that one...
			for (P<Integer, OperatorSet.OpID> outE : outEdges) {
				int newNode = outE.fst();
				Color color = this.G.GetNodeData(newNode).reachStatus;
				if (color == colorToFollow || color == Color.ReachableFromStartANDEnd) {
					ReachableChems chem = this.G.GetNodeData(newNode);
					if (goingToEnd) {
						// update the path... (as suffixes...)
						path.add(chem.c);
						pathOpIDsUsed.add(outE.snd());
						pathOpsUsed.add(chem.operatorBasis.lookupCRO(outE.snd()));
					} else {
						// update the path... (as prefixes...)
						path.add(0, chem.c);
						pathOpIDsUsed.add(0, outE.snd());
						pathOpsUsed.add(0, chem.operatorBasis.lookupCRO(outE.snd()));
					}
					// update the pointer and then iterate again...
					nodeid = newNode;
					break;
				}
			}
		}
	}

	public void noTransformation(Node<ReachableChems> originNode, OpID opIDUsed) {
		// This is the dual of addTransformation below and tells us that on originNode applying the opIDUsed operator
		// does NOT yield a new chemical. So maybe keep some bookkeeping information.
		this.stats.opInstantiationNOOP();
	}
	
	public void addTransformation(Chemical transformedChem, Node<ReachableChems> originNode, OpID opIDUsed) {
		// Check whether this chemical is already a node in the graph.. if yes:
		//	(a) the preexisting node's color is the same as originalNode's -- then do nothing, just add an edge
		//  (b) the preexisting node's color is the opposite of originalNode's -- i.e., new path found... color this node ReachableFromStartANDEnd
		
		int distFromStart = Integer.MAX_VALUE, distFromEnd = Integer.MAX_VALUE;
		ReachableChems.Color defReachStatus = originNode.atom.reachStatus; // by default it will be the old nodes status, unless a path 
		if (defReachStatus == Color.ReachableFromStart)
			distFromStart = originNode.atom.distFromStart + 1;
		else if (defReachStatus == Color.ReachableFromEnd)
			distFromEnd = originNode.atom.distFromEnd + 1;
		else { // Color.ReachableFromStartANDEnd
			System.err.println("We expanded a node reachable from both ends, how far is the new expanded node? Not implemented yet."); System.exit(-1);
		}
		Node<ReachableChems> potentialNewNode = constructGraphNode(transformedChem, originNode.atom.operatorBasis, defReachStatus, distFromStart, distFromEnd);

		Node<ReachableChems> alreadyThere;
		if ((alreadyThere = lookupNodeForChem(transformedChem.getSmiles())) != null) {
			if (alreadyThere.atom.distFromEnd > distFromEnd)
				alreadyThere.atom.distFromEnd = distFromEnd;
			if (alreadyThere.atom.distFromStart > distFromStart)
				alreadyThere.atom.distFromStart = distFromStart;
			potentialNewNode = alreadyThere; // lets not add a new node, just connect to the already existing one...
			if (alreadyThere.atom.reachStatus != defReachStatus) {
				// woa!! already present node has different reach status... 
				// need to update its status as being reachable from both!
				
				// note that even if the different reach status is ReachableFromBoth even then we have found an alternative subpath.. so count this as another ambassador
				alreadyThere.atom.reachableFromBoth();
				if (!this.ambassadors.contains(alreadyThere)) {
					this.ambassadors.add(alreadyThere); // == potentialNewNode
					this.stats.newAmbassadorCreated();
				}
			} else {
				// nothing to do but just add edge.. (in both cases of ReachableFromStartANDEnd and case of same status as new node)
			}
		} else {
			// the node does not already exist in the graph, so add it...
			this.G.AddNode(potentialNewNode);
			this.instantiatedChemsInG.put(transformedChem.getSmiles(), potentialNewNode);
			addToWorkList(potentialNewNode);
			this.stats.newNodeAdded();
		}
		
		// now add an edge in the graph with this new potentialNewNode with edge that is labelled with opIDUsed
		this.G.AddEdge(originNode, potentialNewNode, opIDUsed, false);
		this.stats.newEdgeAdded();
		this.stats.expandingAtDepth(Math.min(originNode.atom.distFromStart, originNode.atom.distFromEnd));
	}

	private void addToWorkList(Node<ReachableChems> node) {
		int priority = Math.min(node.atom.distFromStart, node.atom.distFromEnd); // min ensures that we consider all 0 dist nodes first before 1 dist node (from either end)
		int len = this.worklist.size();
		int i;
		for (i = 0; i<len; i++) {
			ReachableChems atom = this.worklist.get(i).atom;
			int wl_priority = Math.min(atom.distFromStart, atom.distFromEnd);
			if (priority < wl_priority)
				break;
		}
		if (i < len)
			this.worklist.add(i, node); // add at priority order.
		else
			this.worklist.add(node); // add at end.
	}

	private Node<ReachableChems> constructGraphNode(Chemical c, OperatorSet opSet, Color reachStatus, int distFromStart, int distFromEnd) {
		int newid = this.instantiatedChemsInG.size();
		return new Node<ReachableChems>(newid, new ReachableChems(c, opSet, reachStatus, distFromStart, distFromEnd));
	}

	public Node<ReachableChems> lookupNodeForChem(String chemSmiles) {
		if (!this.instantiatedChemsInG.containsKey(chemSmiles))
			return null;
		return this.instantiatedChemsInG.get(chemSmiles);
	}

	public Node<ReachableChems> pickNextChemToExpandFrom() {
		// pick a suitable element from the worklist
		Logger.println(3, "this is where we prioritize which element to pick...");
		if (this.worklist.size() == 0) {
			// exhausted the entire worklist!
			return null;
		}
		return this.worklist.get(0);
	}

	public void updateWorklist(Node<ReachableChems> node, OpID opUsedUp) {
		node.atom.addUsedOperator(opUsedUp);
		if (node.atom.exhaustedAllOperators())
			this.worklist.remove(node);
	}

	public NetworkStats statistics() {
		this.stats.updateToCurrentTime();
		this.stats.recomputeExpensiveGlobalStats();
		return this.stats;
	}
	
	public class NetworkStats {
		// constantly updated
		int num_nodes;
		int num_edges;
		int num_paths;
		int num_attempted_edges;
		int depth;
		long elapsed;
		private long startime;
		
		// expensive computations, only evaluated on demand
		float avg_fanout;
		int diameter;
		int num_fragments;
		int nodes_reachable_from_start;
		int nodes_reachable_from_end;
		int nodes_reachable_from_both;
		
		public NetworkStats() {
			this.num_nodes = G.size(); // init number of nodes includes the start and end chemicals..
			this.num_edges = this.num_paths = 0;
			this.num_attempted_edges = 0; // these are edges that were attempted but led nowhere, i.e., nominal self-loops
			this.elapsed = 0;
			this.startime = System.currentTimeMillis();
			
			this.avg_fanout = -1;
			this.diameter = -1;
			this.num_fragments = -1;
			this.nodes_reachable_from_start = -1;
			this.nodes_reachable_from_end = -1;
			this.nodes_reachable_from_both = -1;
			this.depth = 0;
		}
		
		public void updateToCurrentTime() { this.elapsed = System.currentTimeMillis() - this.startime; }
		public void recomputeExpensiveGlobalStats() {
			// compute the following: 
			// avg fanout
			// diameter
			// # of disconnected fragments (not connected components as we do not care about bidirectionality)
			// # of nodes reachable from start
			// # of nodes reachable from end

			this.avg_fanout = 0;
			this.nodes_reachable_from_end = 0;
			this.nodes_reachable_from_end = 0;
			this.nodes_reachable_from_both = 0;
			this.diameter = 0;
			this.num_fragments = 0;
			
			// fan out, num_start, num_end can be computed by iterating over the nodes
			int n = 0;
			long total_fanout = 0;
			int rstart = 0, rend = 0, rboth = 0;
			for (int nid : G.GetNodeIDs()) {
				n++;
				int fanout = G.AdjList().get(nid).size();
				total_fanout += fanout;
				Color c = G.GetNodeData(nid).reachStatus;
				switch (c) {
				case ReachableFromEnd : rend++; break;
				case ReachableFromStart : rstart++; break;
				case ReachableFromStartANDEnd : rboth++; break;
				}
			}

			this.avg_fanout = ((float)total_fanout) / n;
			this.nodes_reachable_from_end = rend;
			this.nodes_reachable_from_start = rstart;
			this.nodes_reachable_from_both = rboth;
			
			if (this.num_nodes != n) { System.err.format("Constantly updated num_nodes:%d != counted:%d\n", this.num_nodes, n); System.exit(-1); }
			if (this.nodes_reachable_from_both != this.num_paths) { System.err.format("ambasaddors:%d != counted:%d\n", this.num_paths, this.nodes_reachable_from_both); System.exit(-1); }
			
			this.diameter = computeDiameter();
			this.num_fragments = computeFragments();
		}
		
		private int computeFragments() {
			return -1;
		}

		private int computeDiameter() {
			return -1;
		}

		public void newAmbassadorCreated() {
			this.num_paths++;
		}
		public void newEdgeAdded() {
			this.num_edges++;
		}
		public void newNodeAdded() {
			this.num_nodes++;
		}
		public void opInstantiationNOOP() {
			this.num_attempted_edges++;
		}
		public void expandingAtDepth(int d) {
			if (d < this.depth)
			{ System.err.format("Expanded something at depth (%d) less than the current depth (%d)! ERROR.", d, this.depth); System.exit(-1); }
			this.depth = d;
		}
		
		public String headers() {
			return "Time\t" + 
					"Size\t" + 
					"Nodes\t" + 
					"Edges\t" + 
					"NOOP_Es\t" + 
					"Paths\t" +
					"AvgFan\t" +
					"RStart\t" +
					"REnd\t" +
					"RBoth\t" +
					"Diam\t" +
					"Frags\t" +
					"Depth"
					;
		}
		@Override
		public String toString() {
			return this.elapsed
					+ "\t" + (this.num_nodes + this.num_edges) 
					+ "\t" + this.num_nodes 
					+ "\t" + this.num_edges
					+ "\t" + this.num_attempted_edges
					+ "\t" + this.num_paths
					
					+ "\t" + this.avg_fanout
					+ "\t" + this.nodes_reachable_from_start
					+ "\t" + this.nodes_reachable_from_end
					+ "\t" + this.nodes_reachable_from_both

					+ "\t" + this.diameter
					+ "\t" + this.num_fragments
					+ "\t" + this.depth
					;
		}
	}
}