package act.server.Search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.VariousSearchers;
import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;
import act.shared.helpers.P;

public class DistanceRanker extends DjikstraHypergraphRanker <DistanceRankingNode>{
	
	public DistanceRanker(MongoDB db, ReactionsHypergraph g) {
		this.db = db;
		this.nodes = new HashMap<Long, DistanceRankingNode>();
		this.graph = g;
	}
	
	public DistanceRanker(MongoDB db, ReactionsHypergraph g, int k) {
		this.db = db;
		this.nodes = new HashMap<Long, DistanceRankingNode>();
		this.graph = g;
		this.k = k;
	}
	
	protected Set<Integer> getCost(Long reactionID) {
		Set<Integer> result = new HashSet<Integer>();
		Reaction reaction = db.getReactionFromUUID(reactionID);			
		Long[] reactants = reaction.getSubstrates();
		Integer totalCost = 0; 
		for (int i = 0; i < reactants.length; i++) {
			Long reactant = reactants[i];
			DistanceRankingNode node = nodes.get(reactant);
			if (node == null) {
				return result;
			}
			Integer bestDist = 1000;
			for (DjikstraPath path : node.getPaths()) {
				if (path.getCost() < bestDist) {
					bestDist = path.getCost();
				}
			}
			
			//if (totalCost < bestDist)
			totalCost += bestDist;
		}
		result.add(totalCost + 1); //for current reaction
		return result;
	}
	
	protected Set<Integer> join(List<Set<Integer>> imbalances) {
		Set<Integer> result = new HashSet<Integer>();
		for (Set<Integer> imbalance : imbalances) {
			result.addAll(imbalance);
		}
		
		Set<Integer> widened = new HashSet<Integer>();
		for (Integer imbalance : result) {
			if (imbalance > 20) {
				imbalance = 20; 
			} 
			widened.add(imbalance);
		}
		return widened;
	}

	@Override
	protected DistanceRankingNode createNode(Long id, Integer numParents) {
		DistanceRankingNode node = new DistanceRankingNode(id);
		/*
		if (numParents == 0) {
			node.insertCost(0, null);
		}*/
		return node;
	}

	@Override
	public void outputGraph(Long target, String outputFile, int thresh, int limit) {
		outputGraph(target, outputFile, "", thresh, limit);
	}
	
	public void outputGraph(Long target, String outputFile, String nameTag, int thresh, int limit) {
		String dirname = "DistanceRankerPaths";
		File dir = new File(dirname);
		dir.mkdir();
		
		String targetName = db.getShortestName(target);
		targetName = targetName.replaceAll(" ", "_");
		targetName = targetName.replaceAll("/", "_");
		
		ReactionsHypergraph graph = this.graph.restrictGraph(target, -1, -1);
		graph.setIdTypeDB_ID();
		
		ReactionsHypergraph cascade = graph.verifyPath(target);
		if (cascade != null) {
			cascade.setIdTypeDB_ID();
			cascade = cascade.restrictGraph(target, thresh, limit);
			cascade.setIdTypeDB_ID();
		} else {
			return;
		}
		
		Set<Long> reactionsToKeep = new HashSet<Long>();
		
		for (Long id : nodes.keySet()) {
			DistanceRankingNode node = nodes.get(id);
			if (node == null) return;
			if (node.getTopPaths().size() == 0) return;
			cascade.addChemicalInfo(id, node.toString());
			int bestDistance = getBestCost(node);
			bestDistance = bestDistance/10;
			if (bestDistance > 130) bestDistance = 130;
			String brightness = Integer.toHexString(100 + bestDistance);
			cascade.addChemicalColor(id, "#" + brightness + brightness + brightness);
		}
		
		DistanceRankingNode targetNode = nodes.get(target);
		if (targetNode == null) return;
		List<DjikstraPath> topPaths = targetNode.getTopPaths();
		Set<Set<Long>> set = new HashSet<Set<Long>>();
		
		int i = 0;
		String tableFields[] = {
			"Step",
			"ID",
			"EC Number",
			"Description",
			"ERO Class Size",
			"Alternatives",
			"Direction",
			"Expression Score",
			"Est. Delta G0 (kcal)"
		};
		for (DjikstraPath p : topPaths){
			System.out.println(i + " " + p.getReactions());
			i++;
			reactionsToKeep.addAll(p.getReactions());
			set.add(p.getReactions());
			ReactionsHyperpath<Long, Long> path = new ReactionsHyperpath<Long, Long>();
			
			path.setIdTypeDB_ID();
			path.setInitialSet(graph.getInitialSet());
			for (Long reactionID : p.getReactions()) {
				path.addReaction(reactionID, graph.getReactants(reactionID), graph.getProducts(reactionID));
			}
			path = path.verifyPath(target); //get sorted reactions
			path.setIdTypeDB_ID();
			path.setFields(Arrays.asList(tableFields));
			for (Long reactionID : path.getOrdering()) {
				String reactionInfo = reactionRankingInfo(reactionID);
				reactionInfo += " alternatives: " + representativeReaction.get(reactionID);
				path.addReactionInfo(reactionID, reactionInfo);
				
				Reaction reaction = db.getReactionFromUUID(reactionID);
				path.addField(reactionID, "ID", reactionID);
				path.addField(reactionID, "EC Number", reaction.getECNum());
				path.addField(reactionID, "Description", reaction.getReactionName());
				path.addField(reactionID, "ERO Class Size", ConfidenceMetric.getEROClassSize(reactionID, db));
				path.addField(reactionID, "Alternatives", representativeReaction.get(reactionID));
				path.addField(reactionID, "Direction", reactionID < 0 ? "Reversed" : "Forward");
				path.addField(reactionID, "Expression Score", ConfidenceMetric.expressionScore(reaction));
				
				Double estimatedEnergy = reaction.getEstimatedEnergy();
				String estimatedEnergyString = null;
				if (estimatedEnergy != null) estimatedEnergyString = String.format("%1$,.2f", estimatedEnergy);
				path.addField(reactionID, "Est. Delta G0 (kcal)", estimatedEnergyString);
			}
			
			path.setInitialSet(InitialSetGenerator.natives(db)); //for visualization, show all structures but common cofactor/natives
			int totalCost = p.getCost();
			String format = "%1$04d";
			try {
				String fname = nameTag + targetName + "_id" + target + "_" +  i + "_" + String.format(format, totalCost);
				path.writeDOT(dirname + "/" + fname + ".dot", 
						db, 
						dirname + "/chemicalImages");
				path.writeHTMLTable(dirname + "/" + fname + ".html", fname + ".svg");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		Set<Long> allReactionIDs = graph.getReactions();
		System.out.println(reactionsToKeep.size());
		for (Long reactionID : allReactionIDs) {
			/*
			if (!reactionsToKeep.contains(reactionID)) {
				graph.delete(reactionID);
			}*/
			String reactionInfo = reactionRankingInfo(reactionID);
			cascade.addReactionInfo(reactionID, reactionInfo);
		}
		
		cascade.addChemicalColor(target, "#0000FF");
		
		try {
			cascade.writeDOT(dirname + "/" + outputFile, db);
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}

	public int getBestCost(Long id) {
		return getBestCost(nodes.get(id));
	}
	
	private int getBestCost(DistanceRankingNode node) {
		return node.getTopPaths().get(0).getCost();
	}
	
	public List<Integer> getBestCosts(Long id) {
		DistanceRankingNode node = nodes.get(id);
		List<DjikstraPath> paths = node.getTopPaths();
		List<Integer> costs = new ArrayList<Integer>();
		for (DjikstraPath p : paths) {
			costs.add(p.getCost());
		}
		return costs;
	}
	
	public List<Set<Long>> getBestPaths(Long id) {
		DistanceRankingNode node = nodes.get(id);
		List<DjikstraPath> paths = node.getTopPaths();
		List<Set<Long>> result = new ArrayList<Set<Long>>();
		for (DjikstraPath p : paths) {
			result.add(p.getReactions());
		}
		return result;
	}

	private Map<Long, String> rankingInfoCache = new HashMap<Long, String>();
	private String reactionRankingInfo(Long reactionID) {
		if (!rankingInfoCache.containsKey(reactionID)) {
			Reaction reaction = db.getReactionFromUUID(reactionID);
			Double energy = reaction.getEstimatedEnergy();
			boolean reversed = reaction.getUUID() < 0;
			String reactionInfo = "id: " + reactionID + ", EC: " + reaction.getECNum()
					+ ", expression score: " + ConfidenceMetric.expressionScore(reaction)
					+ (energy != null ? //&& reversed && reaction.isReversible() != 1 ? 
							", est. deltaG0: " + energy.intValue()
							: "")
							+ ", direction: " + (reversed ? "reversed" : "forward")
							+ ", ero class size: " + ConfidenceMetric.getEROClassSize(reactionID, db)
							+ ", desc: " + reaction.getReactionName();
			rankingInfoCache.put(reactionID, reactionInfo);
		}
		
		return rankingInfoCache.get(reactionID);
	}
	
	@Override
	public int rankPath(ReactionsHyperpath path) {
		return path.getOrdering().size();
	}
	
	public static void main(String[] args) {
		MongoDB db = new MongoDB();
	    PathBFS pathFinder = new PathBFS(db, InitialSetGenerator.natives(db));
	    pathFinder.initTree();
		DistanceRanker dr = new DistanceRanker(db, pathFinder.getGraph());
		dr.simplifyGraph();
		//dr.rankPathsTo(16589L);
		//dr.outputGraph(16589L, "ranking_2-hydrazinyl-3-phenylpropanoic_acid.dot", -1, -1);
		
		dr.rankPathsTo(16028L);
		dr.outputGraph(16028L, "ranking_vanillin.dot", -1, -1);
		
		dr.rankPathsTo(10779L);
		dr.outputGraph(10779L, "ranking_butanol.dot", -1, -1);
		
		dr.rankPathsTo(4271L);
		dr.outputGraph(4271L, "ranking_farnesene.dot", -1, -1);
		
		dr.rankPathsTo(16162L);
		dr.outputGraph(16162L, "ranking_paracetamol.dot", -1, -1);
		
		dr.rankPathsTo(864L);
		dr.outputGraph(864L, "ranking_myrcene.dot", -1, -1);
		
	}

	private List<DjikstraPath> getParentsHelper(Long reactionID, DjikstraPath pathSoFar, List<Long> reactantIDs, int i) {
		List<DjikstraPath> result = new ArrayList<DjikstraPath>();
		if (reactantIDs.size() == i) {
			double prob = 1;
			Set<Long> products = new HashSet<Long>();
			for (Long id : pathSoFar.getReactions()) {
				prob *= (1000 - getReactionCost(id))/1000.0;
				if (id != reactionID) {
					products.addAll(graph.getProducts(id));
				}
				products.addAll(graph.getReactants(id));
			}
			int cost = (int)((1 - prob)*1000);
			pathSoFar.setCost(cost);
			pathSoFar.setChemicalsBeforeLastReaction(products);
			result.add(pathSoFar);
			return result;
		}
		
		DistanceRankingNode n = nodes.get(reactantIDs.get(i));
		List<DjikstraPath> topPaths = n.getTopPaths();
		for(DjikstraPath path : topPaths) {
			Set<Long> reactionIDs = path.getReactions();
			int cost = path.getCost();
			reactionIDs.addAll(pathSoFar.getReactions());
			cost += pathSoFar.getCost();
			result.addAll(getParentsHelper(reactionID, new DjikstraPath(cost, reactionIDs), reactantIDs, i + 1));
			if (result.size() >= k) return result;
		}
		return result;
	}
	
	@Override
	protected List<DjikstraPath> getParents(Long reactionID, Long reactantExpanding) {
		Set<Long> reactantIDSet = new HashSet<Long>(graph.getReactants(reactionID));
		DistanceRankingNode node = nodes.get(reactantExpanding);
		Set<Long> reactionIDs = node.getPaths().get(0).getReactions();
		if (graph.getInitialSet().contains(reactantExpanding) && reactionIDs.size() > 0) {
			return new ArrayList<DjikstraPath>();
		}
		for (Long r : reactionIDs) {
			reactantIDSet.removeAll(graph.getProducts(r));
		}
		reactionIDs.add(reactionID);
		reactantIDSet.remove(reactantExpanding);
		reactantIDSet.removeAll(graph.getInitialSet());
		List<Long> reactantIDs = new ArrayList<Long>(reactantIDSet);
		
		int cost = node.getCost(0);
		List<DjikstraPath> result = getParentsHelper(reactionID, new DjikstraPath(cost, reactionIDs), reactantIDs, 0);
		return result;
	}
	

	private Map<Long, Integer> reactionCosts = new HashMap<Long, Integer>();

	@Override
	protected int getReactionCost(Long reactionID) {
		if (!reactionCosts.containsKey(reactionID)) {
			Reaction reaction = db.getReactionFromUUID(reactionID);
			int cost = ConfidenceMetric.inconfidence(reaction, db);
			reactionCosts.put(reactionID, cost);
			//System.out.println(expressConfidence);
		}
		return reactionCosts.get(reactionID);
	}
	
	private SetBuckets<Long, Long> representativeReaction = new SetBuckets<Long, Long>();
	
	public void simplifyGraph() {
		SetBuckets<P, Long> edges = this.graph.simplifyReactions();
		Set<Long> toKeep = new HashSet<Long>();
		for (P key : edges.keySet()) {
			Set<Long> temp = edges.get(key);
			//pick best edge
			int bestCost = 999;
			Long bestReaction = null;
			for (Long r : temp) {
				int cost = ConfidenceMetric.inconfidence(db.getReactionFromUUID(r), db);
				if (cost < bestCost) {
					bestCost = cost;
					bestReaction = r;
				}
			}
			representativeReaction.putAll(bestReaction, temp);
			toKeep.add(bestReaction);
		}
		
		Set<Long> allReactionIDs = this.graph.getReactions();
		for (Long r : allReactionIDs) {
			if (!toKeep.contains(r))
				this.graph.delete(r);
		}
	}
}

class DistanceRankingNode extends DjikstraRankingNode{
	DistanceRankingNode(Long id) {
		this.id = id;
	}
	
	@Override
	public String toString() {
		String result;
		result = "id = " + id;
		result += " distances: ";
		for (DjikstraPath path : topPaths) {
			result += path.getCost() + ", ";
		}
		result += "";
		return result;
	}
}
