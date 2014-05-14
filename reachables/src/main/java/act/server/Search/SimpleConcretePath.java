package act.server.Search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.Reaction;
import act.shared.ReactionType;
import act.shared.SimplifiedReactionNetwork;
import act.shared.helpers.P;
import act.shared.helpers.T;

/**
 * Performs searching over the simplified reaction network.
 * It's simplified in the sense that reactions are reduced to
 * a single reactant to a single product based on rarity.
 */
public class SimpleConcretePath {
	private SimplifiedReactionNetwork srn;
	private List<List<Long>> paths;
	private List<List<Long>> deadEnds; //unused now
	private Set<Long> explored;
	private Set<Long> ignored;
	
	public SimpleConcretePath(SimplifiedReactionNetwork srn) {
		this.srn = srn;
	}
	
	public SimpleConcretePath() {
		List<Long> organisms = new ArrayList<Long>();
		//organisms.add(new Long(562)); // ecoli
		organisms.add(new Long(-1)); //all organisms
		srn = new SimplifiedReactionNetwork(null);
	}
	
	/**
	 * A set of chemical ids to not use in paths
	 * @param ignored
	 */
	public void setIgnored(Set<Long> ignored) {
		this.ignored = ignored;
	}
	
	/**
	 * See next function
	 * @param start
	 * @param end
	 */
	public void findSimplePaths(Long start, Long end, int maxPaths, int maxDepth) {
		Set<Long> goals = new HashSet<Long>();
		goals.add(end);
		findSimplePaths(start, goals, maxPaths, maxDepth);
	}
	
	/**
	 * Performs DFS with a limited depth.
	 * TODO: Replace with a better top-k paths algorithm.
	 * Call get paths to get the actual paths found after
	 * calling findSimplePaths.
	 * @param start
	 * @param goals
	 * @param maxDepth
	 * @param maxPaths - maximum number of paths before returning
	 */
	public void findSimplePaths(Long start, Set<Long> goals, int maxPaths, int maxDepth) {		
		paths = new ArrayList<List<Long>>();
		deadEnds = new ArrayList<List<Long>>();
		explored = new HashSet<Long>();
		
		/*
		for(Long g : goals) {
			if(!srn.hasChemical(g)){
				goals.remove(g);
			}
		}*/
		System.out.println("Starting from " + start);
		System.out.println("Finding " + goals);
		if (ignored != null)
			System.out.println("Ignoring " + ignored.size() + " chemicals");
		if(!srn.hasChemical(start) || goals.isEmpty()) return;
		System.out.println("find simple paths: has both end points");
		
		for (int pathLen = 0; pathLen < maxDepth; pathLen++) {
			HashSet<Long> onPath = new HashSet<Long>();
			onPath.add(start);
			List<Long> curPath = new ArrayList<Long>();
			Stack<List<Long>> toExplore = new Stack<List<Long>>();
			explored.add(start);
			curPath.add(start);
			toExplore.push(new ArrayList<Long>(srn.getProducts(start)));
			while(!toExplore.isEmpty()) {
				// get list of chemicals to explore next
				List<Long> nextChemicals = toExplore.peek();
				//if empty, done exploring from this subpath so go back one step
				if(nextChemicals.isEmpty()) {
					toExplore.pop();
					onPath.remove(curPath.remove(curPath.size()-1));
					continue;
				}

				//else add to subpath but avoid cycles
				Long c = nextChemicals.remove(0);
				if (ignored != null && ignored.contains(c)) continue;
				explored.add(c);
				if(!onPath.contains(c))
					curPath.add(c);
				else
					continue;

				//if we just added our target, found path
				if(goals.contains(c)) {
					if (curPath.size() == pathLen) {
						paths.add(new ArrayList<Long>(curPath));
						System.out.println("Found path " + curPath);
					}
					curPath.remove(c);
					if(paths.size() >= maxPaths) break;
					continue;
				} 

				//add new children to explore from this subpath
				if(curPath.size() <= pathLen) {
					toExplore.push(new ArrayList<Long>(srn.getProducts(c)));	
				} else {
					toExplore.push(new ArrayList<Long>());
				}
				onPath.add(c);
			}
			
			if(paths.size() >= maxPaths) break;
		}
		System.out.println("num paths found:" + paths.size());
		
		for(List<Long> path : paths) {
			System.out.println();
			System.out.println("Path" + path);
			Long s = path.get(0);
			for(Long l : path) {
				if(!l.equals(s)) {
					System.out.println(srn.getOriginalRxns(new P<Long,Long>(s, l)));
				}
				s = l;
			}
		}
		System.out.println("Num paths found: " + paths.size());
		System.out.println("Num explored: " + explored.size());
		System.out.println("Total nodes: " + srn.getChemicals().size());
	}
	
	public List<Path> getPaths() {
		return srn.convertCompoundListToPaths(paths);	
	}
	
	private Set<Chemical> getChemicals(Long[] chemicalIDs, MongoDB db) {
		Set<Chemical> chemicals = new HashSet<Chemical>();
		for (Long chemID : chemicalIDs) {
			chemicals.add(db.getChemicalFromChemicalUUID(chemID));
		}
		return chemicals;
	}
	
	public List<Path> getDeadEnds() {
		List<Path> paths = new ArrayList<Path>();
		for(List<Long> p : deadEnds) {
			List<Chemical> chemicals = new ArrayList<Chemical>();
			for(Long cID : p) chemicals.add(srn.getChemical(cID));
			boolean okPath = true;
			for(Chemical c : chemicals) {
				if(c.getSmiles() == null) {
					okPath = false;
					break;
				}
			}
			if(okPath)
				paths.add(new Path(chemicals));
		}
		return paths;
	}
	
	public int numPathsFound() {
		return paths.size();
	}
	
	
	
	/* 
	 * The following functions are just to get some 
	 * stats on the graph.
	 * 
	 */
	
	/**
	 * Determine the chemicals within steps reactions away
	 * from src and their minimum distances.
	 * (BFS)
	 */
	public static void findNeighbors(long src, int numSteps) {
		SimpleConcretePath scp = new SimpleConcretePath();
		List<P<Long, Integer>> distances = new ArrayList<P<Long, Integer>>();
		Set<Long> closed = new HashSet<Long>();
		Queue<P<Long, Integer>> toExplore = new LinkedList<P<Long, Integer>>();
		toExplore.add(new P<Long,Integer>(src, 0));
		closed.add(src);
		
		while(!toExplore.isEmpty()) {
			P<Long,Integer> node = toExplore.poll();
			distances.add(node);
			Long chemID = node.fst();
			Integer dist = node.snd();
			if(dist == numSteps) continue;
			
			Set<Long> products = scp.srn.getProducts(chemID);
			for(Long p : products) {
				if(closed.contains(p)) continue;
				closed.add(p);
				toExplore.add(new P<Long,Integer>(p, dist + 1));
			}
		}
		for(P<Long,Integer> d : distances) {
			Chemical c = scp.srn.getChemical(d.fst());
			System.out.println("Dist: " + d.snd() + 
					" Chemical: " + d.fst() + " " + c.getCanon());
		}
	}
	
	public static void calculateConnectedComponents() {
		SimpleConcretePath scp = new SimpleConcretePath();
		Set<Long> unreached = scp.srn.getChemicals();
		List<Set<Long>> components = new ArrayList<Set<Long>>();
		while(!unreached.isEmpty()) {
			Long start = unreached.iterator().next();
			scp.findSimplePaths(start,start,100,6);
			components.add(scp.explored);
			unreached.removeAll(scp.explored);
			scp.deadEnds = null;
			scp.paths = null;
			System.out.println("Explored from " + start);
			System.out.println(unreached.size());
		}
		System.out.println("Number of components: " + components.size());
		Map<Integer,Integer> groupBySizes = new HashMap<Integer,Integer>();
		
		for(Set<Long> component : components) {
			if(!groupBySizes.containsKey(component.size()))
				groupBySizes.put(component.size(), 1);
			else
				groupBySizes.put(component.size(), 
						groupBySizes.get(component.size())+1);
		}
		for(int size : groupBySizes.keySet()) {
			System.out.println(groupBySizes.get(size) + " components with " + size);
		}
	}
	
	public static void main(String[] args) {
		//calculateConnectedComponents();
		//findNeighbors(13737, 6);
		
		SimpleConcretePath scp = new SimpleConcretePath();
		//scp.srn.addEdge(new Chemical((long)-1), new Chemical((long)4), -1, SimplifiedReactionNetwork.ReactionType.CRO);
		scp.findSimplePaths((long)10779,(long)12974,5,5); //Try with these 10779 12974
		//System.out.println(scp.numPathsFound());
		/*CommonPaths cp = new CommonPaths();
		
		for (Path path : scp.getDeadEnds()) {
			cp.renderCompoundImages(path);
		}*/
	}
	
	
}
