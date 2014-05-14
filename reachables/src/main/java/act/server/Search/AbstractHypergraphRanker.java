package act.server.Search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;

/**
 * This essentially adds to the output of BFS, the optimistically minimum costs to get to
 * each chemical.
 * 
 * Each chemical keeps track of number of parent reactions, 
 * reactions that produce the chemical,
 * that haven't been applied yet. 
 * Once all parent reactions have been applied, the chemical can be expanded.
 * 
 * Problem if there's a cycle. Then the number of parents will always be greater
 * than 0 until the chemical is expanded. So if nothing with 0, expand smallest count
 * 
 * param <N> - node type
 * param <L> - lattice type (to be implemented)
 */
public abstract class AbstractHypergraphRanker<N extends RankingNode, L> {
	protected ReactionsHypergraph graph;
	
	protected MongoDB db;
	protected Map<Long, N> nodes;
	
	/**
	 * Creates and initializes a new node. 
	 * If numParents is 0, node represents a starting compound.
	 * 
	 * @param id
	 * @param numParents
	 * @return
	 */
	protected abstract N createNode(Long id, Integer numParents);
	
	/**
	 * Given the reaction and previously expanded nodes, 
	 * compute the possible costs of the pathways after using the reaction
	 * @param reactionID
	 * @return
	 */
	protected abstract Set<Integer> getCost(Long reactionID);
	
	/**
	 * Given the costs of a set of parent reactions, 
	 * compute the 
	 * @param costs
	 * @return
	 */
	protected abstract Set<Integer> join(List<Set<Integer>> costs);
	
	/**
	 * Outputs a graph in a file called outputFile. 
	 */
	public abstract void outputGraph(Long target, String outputFile, int thresh, int limit);
	
	/**
	 * Returns a subset of reactionIDs representing all reactions that are 
	 * feasible (all reactants exist) given the set of reactants.
	 * @param reactionIDs
	 * @param availableReactants
	 * @return
	 */
	protected Set<Long> filterReactions(Set<Long> reactionIDs, Set<Long> availableReactants) {
		Set<Long> result = new HashSet<Long>();
		for (Long reactionID : reactionIDs) {
			Reaction reaction = db.getReactionFromUUID(reactionID);			
			Long[] reactants = reaction.getSubstrates();
			boolean feasible = true;
			for (Long s : reactants) {
				if (!availableReactants.contains(s))
					feasible = false;
			}
			if (feasible) {
				result.add(reactionID);
			}
		}
		return result;
	}
	
	/**
	 * Assigns each chemical node costs.
	 * Starting chemicals (nodes) are assigned a cost of 0.
	 * 
	 * The basic algorithm works as follows:
	 *   toExpand = {}, expanded = {}, nodes = {}
	 *   add starting chemicals to toExpand and to nodes 
	 *   while toExpand is not empty
	 *     node = pick and remove node from toExpand
	 *     expanded = expanded U node
	 *     parents = reactions that produce node
	 *     for p in parents 
	 *       parentCosts = parentCosts U {getCost(p)}
	 *     node.costs = join(parentCosts)
	 *     
	 *     reactions = feasible reactions given expanded
	 *     for r in reactions
	 *        children = get products of r
	 *        for c in children
	 *          if c not in expanded and not in starting chemicals
	 *            nodes = nodes U {c}
	 * 
	 * Because each node's cost is only set once, and before it is set, reactions
	 * that it enables are not considered, cycles are never considered. However, by the time
	 * the node's cost is assigned, there's no guarantee that all reactions that produce it
	 * are feasible (only one is guaranteed for it to be in toExpand). Thus, when picking
	 * a node from toExpand, we favor those with fewer infeasible parents. 
	 * 
	 * This does not guarantee the optimal: Say a reaction uses A to produce B. B may be picked
	 * first because A's number of infeasible parents is greater. This can happen if there are
	 * more cycles with reactions going to A (or one of A's ancestor nodes)
	 * 
	 * Other possible options:
	 * 1) allow cycles and iterate until steady-state is reached
	 * 2) pick node based on cost
	 * 
	 * @param target
	 */
	public void rankPathsTo(Long target) {
		System.out.println("Target is " + target);
		ReactionsHypergraph graph = this.graph.restrictGraph(target, 10000, 100000);
		graph.setIdTypeDB_ID();
		Set<Long> startingChemicalIDs = this.graph.getInitialSet();
		
		/*
		 * Queue sorted by number of unapplied parent reactions. 
		 */
		PriorityQueue<N> toExpand = new PriorityQueue<N>();
		
		for (Long id : startingChemicalIDs) {
			nodes.put(id, createNode(id, 0));
			toExpand.add(nodes.get(id));
		}
		System.err.println("starting ranking");
		Map<Long, N> expanded = new HashMap<Long, N>();
		
		
		while (!toExpand.isEmpty()) {
			N node = toExpand.poll();
			if (expanded.containsKey(node.getID())) continue;
			// put new nodes in toExpand that haven't been expanded yet
			expanded.put(node.getID(), node);
			if (!startingChemicalIDs.contains(node.getID())) {
				Set<Long> parents = graph.getReactionsTo(node.getID());
				List<Set<Integer>> parentCosts = new ArrayList<Set<Integer>>();
				for (Long parent : parents) {
					parentCosts.add(getCost(parent));
				}
				Set<Integer> costs = join(parentCosts);
				if (node.getCosts().equals(costs)) {
					continue;
				}
				node.setCosts(costs);
			}
			
			// add new chemicals to toExpand
			Set<Long> reactions = graph.getReactionsFrom(node.getID());
			if (reactions == null) continue;
			reactions = filterReactions(reactions, expanded.keySet());
			for (Long rid : reactions) {
				
				Set<Long> childrenID = new HashSet<Long>(
						Arrays.asList(db.getReactionFromUUID(rid).getProducts()));
				// get products of reaction. if in expanded, ignore. 
				// if not in nodes yet, add them.
				for (Long childID : childrenID) {
					if (expanded.containsKey(childID) || startingChemicalIDs.contains(childID)) continue;
					if (!nodes.containsKey(childID)) {
						int numParents = graph.getReactionsTo(childID).size();
						nodes.put(childID, createNode(childID, numParents));
					}
					
					N child = nodes.get(childID);
					//update number of unapplied parent reactions
					child.decrementParent();
					if (toExpand.contains(child)) toExpand.remove(child);
					
					// add to priority queue
					toExpand.add(child);
				}
			}
		}
	}
	
	/**
	 * Returns cost of path given path.
	 * @param path
	 * @param args
	 * @return
	 */
	public abstract int rankPath(ReactionsHyperpath path);
}
