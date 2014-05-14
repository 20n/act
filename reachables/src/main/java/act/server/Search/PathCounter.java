package act.server.Search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
/*
 * Current implementation:
 * Use DFS from a starting node to eliminate cycles. This does not count reorderings of edges.
 * Then for a target node, if not initialized, count = sum of parents' counts.
 */
//STATUS: DEBUGGING, need better algorithm
public class PathCounter {
	private MongoDB db;
	private Map<Long,Node> explored;
	
	private class Node {
		private long myID;
		private long count;
		
		private Set<Node> parents;
		public Node(long id) {
			myID = id;
			parents = new HashSet<Node>();
		}
		
		public void addParent(Node parent) { parents.add(parent); }
		public void setCount(long c) { count = c; }
		public long getID() { return myID; }
		public Set<Node> getParents() { return parents; }
		public long getCount() { return count; }
	}
	
	
	public PathCounter() {
		db = new MongoDB();
		explored = new HashMap<Long,Node>();
	}
	
	private Node getNode(long id) {
		if(!explored.containsKey(id))
			return new Node(id);
		return explored.get(id);
	}
	
	public void init(Long startID){
		Set<Long> seen = new HashSet<Long>();
		Node startNode = getNode(startID);
		startNode.setCount(1);
		seen.add(startID);
		System.out.println("starting dfs");
		dfs(seen,startNode);
		System.out.println("completed dfs");
		System.out.println("num reached: " + explored.size());
		System.out.println("max depth: " + maxDepthTest);
	}
	
	private int curDepth;
	private int maxDepthTest;
	private void dfs(Set<Long> seen, Node curNode) {
		long cur = curNode.getID();
		if(explored.containsKey(cur)) return;
		
		curDepth++;
		if(curDepth > maxDepthTest) maxDepthTest = curDepth;
		
		List<Long> reactions = getRxnsWith(cur);
		for(Long rxn : reactions) {
			Long product = getSimplifiedProduct(cur,rxn);
			//System.out.println(product);
			if(product == -1) continue;
			if(seen.contains(product)) continue;
			seen.add(product);
			Node productNode = getNode(product);
			productNode.addParent(curNode);
			dfs(seen,productNode);
			seen.remove(product);
		}
		//System.out.println("done: " + cur);
		explored.put(cur, curNode);
		curDepth--;
	}
	
	public long getCount(Long id) {
		if(!explored.containsKey(id)) return 0;
		Node node = explored.get(id);
		if(node.getCount() != 0) return node.getCount();
		long count = 0;
		for(Node p : node.getParents()) {
			count += getCount(p.getID());
		}
		node.setCount(count);
		return count;
	}
	
	/*
	 * The following functions help with getting edges of the graph,
	 * in particular, right now the rarity-filtered concrete reactions.
	 */
	private List<Long> getRxnsWith(long id) {
		return db.getRxnsWith(id,false);
	}
	
	private long getSimplifiedProduct(Long reactantID, Long rxnID) {
		HashMap<Long,Double> reactants = db.getRarity(rxnID,false);
		
		long reactant = getRarest(reactants);
		//System.out.println(reactant);
		if(reactant!=reactantID) 
			return -1; //reactant is not rarest in this reaction so ignore
		HashMap<Long,Double> products = db.getRarity(rxnID,true);
		long product = getRarest(products);
		
		return product;
	}

	private long getRarest(HashMap<Long, Double> compounds) {
		long argMax = -1;
		Double max = 10000.0;
		for(Long r : compounds.keySet()) {
			Double rarity = compounds.get(r);
			if(rarity < max) {
				max = rarity;
				argMax = r;
			}
		}
		return argMax;
	}
	
	public static void main(String[] args){
		PathCounter pc = new PathCounter();
		long start = 173;
		long target = 683;
		pc.init(start);
		System.out.println("path count for (" + start + "," + target + "):" + pc.getCount(target));
	}
}
