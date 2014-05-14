package act.server.Molecules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import act.server.Molecules.MolGraphFactorer.Indices;
import act.server.Molecules.Element;
import act.shared.helpers.P;

abstract class NeighborAggregationFns {
	abstract public Double Metric1Hop(Atom n, BondType e); 
	abstract public Double Metric2Hop(Atom n1, BondType e1, Atom n2, BondType e2);
	// the hop1 metrics get added as is; but the hop2 metrics can be weighted lower
	abstract public Double Hop2Weight(Double hop2_contribution); 
}

class SimpleNeighborAggregator extends NeighborAggregationFns {
	private static SimpleNeighborAggregator instance;
	public static SimpleNeighborAggregator getInstance() {
		if (instance == null)
			instance = new SimpleNeighborAggregator();
		return instance;
	}
	
	static HashMap<Element, Integer> nodeMetric;
	static HashMap<BondType, Integer> edgeMetric;
	static {
		nodeMetric = new HashMap<Element, Integer>();
		edgeMetric = new HashMap<BondType, Integer>();
		
		Element[] nts = Element.values();
		BondType[] ets = BondType.values();

		for (int i = 0 ; i < nts.length; i++) nodeMetric.put(nts[i], i);
		for (int i = 0 ; i < ets.length; i++) edgeMetric.put(ets[i], i);
	}
	
	@Override
	public Double Metric1Hop(Atom n, BondType e) {
		return nodeMetric.get(n) * edgeMetric.get(e) * 1.0 /* convert to double */; 
	}

	@Override
	public Double Metric2Hop(Atom n1, BondType e1, Atom n2, BondType e2) {
		return nodeMetric.get(n1) * edgeMetric.get(e1) 
				* nodeMetric.get(n2) * edgeMetric.get(e2) 
				* 1.0 /* convert to double */; 
	}

	@Override
	public Double Hop2Weight(Double a) {
		// the second hop weight is exponentially less important than the first hop
		// so we take the log. But we do not want to go below 0; so we keep it at 1+a
		Double scaled = Math.log10(1 + a);
		// System.out.println("Second hop scaled down; From: " + a + " To: " + scaled);
		return scaled;
	}
	
}

class GraphAlignment {
	MolGraphFactorer G1, G2;
	
	GraphAlignment(MolGraphFactorer g1, MolGraphFactorer g2) {
		this.G1 = g1; this.G2 = g2;
	}

	// G2.G "approximately =" pi_g1(G1.G)
	// i.e., G2 + (diff.add - diff.del) = G1
	// i.e., G2 + modifications = G1
	// i.e., we want G2 to substrates and G1 to be products
	public List<P<MolGraph, MolGraph>> computeGraphDiff(int k) {
		List<HashMap<Integer, Integer>> maps_g1 = inferMostLikelyAlignments(k);
		List<P<MolGraph, MolGraph>> diffs = new ArrayList<P<MolGraph, MolGraph>>();
		for (HashMap<Integer, Integer> pi_g1 : maps_g1)
			try {
				diffs.add(getGraphDiff(this.G1.G, this.G2.G, pi_g1)); 
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Could not compute graph diff:\n" + this.G1.G + "\n vs \n" + this.G2.G);
				System.exit(-1);
			}
		return diffs;
	}
	
	private List<HashMap<Integer, Integer>> inferMostLikelyAlignments(int k) {
		// Compare the graphfactors of G1 vs G2 and make likely alignments
		// each likely alignment will yield an operator. 
		// The alignment is a matching of atom to atom that minimizes global distance
		// i.e., the sum of distances between pairs of atoms is k-minimal
		
		// The atoms are in bins corresponding to their NodeType. Then:
		// Since the atoms are ordered in only one dimension and each has to be 
		// associated with exactly one other atom the minimal alignment can
		// probably be approximated by the greedy algorithm that computes
		// the n^2 matrix of differences; then greedily picks an atom mapping
		// that has the lowest distance.
		
		// We assume atom preservation, so lets see how many atoms there are:
		int sz = 1 + this.G1.G.MaxNodeIDContained(); // [0,maxId], both inclusive, therefore + 1
		int sz2 = 1 + this.G2.G.MaxNodeIDContained(); // [0,maxId], both inclusive, therefore + 1
		if (sz2 != sz) {
			System.err.println("Imbalanced reaction? Atom set size not the same: "
					+ "size(1)=" + sz + " vs size(2)=" + sz2 
					+ "\n" + this.G1.G + "\n vs \n" + this.G2.G);
			System.exit(-1);
		}
		
		// Lets first compute the matrix of distance of each atom to all the 
		// others (infinity if their NodeTypes differ)
		// This is the matrix with rows as the graph G1's nodes, and columns as graph G2's nodes
		Double[][] distances = computeDistanceMatrix(sz, this.G1.atomFactors, this.G2.atomFactors);

		// For getting the second most likely, in the iterative greedy steps,
		// when confronted with a decision where the first and second greedy
		// choices lead to the same outcome (of penalty) then we pick the second
		// And so on... (for the third, fourth etc.)
		
		// Now lets pick the kth optimal (approximate; through greedy) alignment
		List<HashMap<Integer, Integer>> alignments = new ArrayList<HashMap<Integer, Integer>>();
		for (int topNth = 0 ; topNth < k; topNth++)
			alignments.add(greedyAlignment(topNth, distances));
		
		return alignments;
	}

	private HashMap<Integer, Integer> greedyAlignment(int howOptimal, Double[][] distances) {
		// Computes pi such that G2 "approximately =" pi(G1) 
		
		// distances[i][j] is a distance of node_g1[i] to node_g2[j]; it is not symmetric
		// our intention is to calculate mapping of G1's indices so that it approx-equals G2.
		// therefore we need to iterate over i and for it pick an j which matches it best
		
		// For a first iteration, we just pick the most optimal.. later on we will
		// count down numBadDecisions such that we pick a less and less optimal solution
		@SuppressWarnings("unused")
		int numBadDecisionLeft = howOptimal;
		System.out.println("We are not computing k optimals yet. Just the optimal.");
		
		HashMap<Integer, List<P<Integer, Double>>> preferences = new HashMap<Integer, List<P<Integer, Double>>>();
		for (int I = 0; I < distances.length; I++) {
			List<P<Integer, Double>> I_preferences = new ArrayList<P<Integer, Double>>();
			for (int J = 0; J < distances[I].length; J++) {
				// As long as the distance is not \inf, I can potentially choose
				// this match, and incur the appropriate penalty... But higher
				// the penalty the lower my preference for it (so we sort later)
				if (distances[I][J] != Double.POSITIVE_INFINITY)
					I_preferences.add(new P<Integer, Double>(J, distances[I][J]));
			}
			// Sorts the specified list into ascending order, according to the natural ordering of its elements.
			Collections.sort(I_preferences, new Comparator<P<Integer, Double>>() {
				@Override
				public int compare(P<Integer, Double> dist1, P<Integer, Double> dist2) {
					return dist1.snd().compareTo(dist2.snd());
				}});
			if (I_preferences.size() == 0) {
				System.out.println("Prefer nothing! " + I);
				for (int k = 0; k<distances[I].length; k++)
					System.out.print(distances[I][k] + ",");
				System.out.println();
				System.exit(-1);
			} else
				System.out.println("size(Preference[" + I + "])=" + I_preferences.size());
			preferences.put(I, I_preferences); // i'th list of the preference list of I
		}
		
		HashMap<Integer, Integer> permutation = new HashMap<Integer, Integer>();
		Double totalCost = 0.0;
		Integer urgent;
		while ((urgent = getNextUrgent(preferences)) != null) {
			// pick the most optimal choice for urgent.
			P<Integer, Double> choice = preferences.get(urgent).remove(0);
			Integer mapto = choice.fst();
			Double cost = choice.snd();
			permutation.put(urgent, mapto);
			System.out.println("Mapping " + urgent + " -> " + mapto);
			totalCost += cost;
			
			// update the preferences list:
			// a) remove urgent from it
			// b) remove mapto from the available choices for all others
			preferences.remove(urgent); // a)
			for (Integer remainingLessUrgent : preferences.keySet()) {
				List<P<Integer, Double>> prefList = preferences.get(remainingLessUrgent);
				List<P<Integer, Double>> newPrefs = new ArrayList<P<Integer, Double>>();
				for (P<Integer, Double> p : prefList)
					if (p.fst() != mapto)
						newPrefs.add(p);
				preferences.put(remainingLessUrgent, newPrefs); // b)
			}
		}
		System.out.println("Sending out permutation: " + permutation);
		return permutation;
	}

	private Integer getNextUrgent(HashMap<Integer, List<P<Integer, Double>>> preferences) {
		double optimal = Double.POSITIVE_INFINITY; 
		Integer whichOpt = -1;
		for (Integer i : preferences.keySet()) {
			double urgency = computeUrgency(preferences.get(i));
			if (urgency < optimal) {
				optimal = urgency; 
				whichOpt = i;
			}
		}
		return whichOpt == -1 ? null : whichOpt;
	}

	private double computeUrgency(List<P<Integer, Double>> preflist) {
		// There are two factors: 
		// a) |x| = the length of the remaining preflist x
		// b) the elements x[i].
		// A list is more urgent if it is small, and if the elements are on average large.
		// i.e., urgency propto 1/|x| /\ urgency propto 1/x[i]
		// As a heuristic we calculate urgency = 100.0 / \sum i * x[i];
		// another potential is i ^ x[i], but that got in 10^-31 numbers etc... :)
		
		Double accumulate = 1.0;
		for (int i = 0; i<preflist.size(); i++) {
			P<Integer, Double> xi = preflist.get(i);
			// System.out.println("Urgency update: i+1=" + (i+1) + " xi[i]=" + xi.snd);
			accumulate += (i+1) * xi.snd(); // Math.pow(i, xi.snd);
		}
		Double urgency = 100.0 / accumulate; // arbitary high mark.
		System.out.println("Urgency {should be between (1,100]} is: " + urgency);
		return urgency;
	}

	private Double[][] computeDistanceMatrix(int sz, Indices metric1, Indices metric2) {
		// compute the matrix of distance of each atom to all the others 
		// the metric1 and metric2 are maps from NodeType -> index -> position_in_space
		// we compute for every two indexes their difference in position_in_space
		// (the distance infinity if their NodeTypes differ)
		
		// we assume atom conservation; therefore we are going to output a square matrix
		Double[][] distances = new Double[sz][sz];
		HashMap<Integer, P<Atom, Double>> locations1, locations2;
		locations1 = metric1.computeDistribution();
		locations2 = metric2.computeDistribution();
		for (int i = 0; i< sz; i++) {
			Atom type1 = locations1.get(i).fst();
			Double loc1 = locations1.get(i).snd();
			for (int j = 0; j<sz; j++) {
				Atom type2 = locations2.get(j).fst();
				Double loc2 = locations2.get(j).snd();
				if (type1.equals(type2))
					distances[i][j] = Double.POSITIVE_INFINITY;
				else
					distances[i][j] = Math.abs(loc1 - loc2);
			}
		}
		return distances;
	}

	private P<MolGraph, MolGraph> getGraphDiff(MolGraph g1, MolGraph g2, HashMap<Integer, Integer> map) throws Exception {
		// Return (map(g1) - g2, g2 - map(g1)); where '-' is setminus,
		// i.e., this function computes (addition-graph, deletion-graph) for g2
		// So that you have the equality "g2 + addition-graph - deletion-graph = g1"
		MolGraph remappedg1 = (MolGraph)g1.permute(map);
		MolGraph addition = (MolGraph)remappedg1.subtract(g2);
		MolGraph subtract = (MolGraph)g2.subtract(remappedg1);
		return new P<MolGraph, MolGraph>(addition, subtract);
	}
}

public class MolGraphFactorer {
	MolGraph G;
	Indices atomFactors;
	NeighborAggregationFns aggregationFns;
	
	MolGraphFactorer(MolGraph G, NeighborAggregationFns fns) {
		this.G = G;
		this.aggregationFns = fns;
		this.atomFactors = computeAtomFactors(G);
	}
	
	// wrapper class to keep the map of { C -> { 1->1.9, 2->2.4, 3->10 }, N -> {} ... }  
	class Indices {
		HashMap<Atom, HashMap<Integer, Double>> factors;
		Indices() { this.factors = new HashMap<Atom, HashMap<Integer, Double>>(); }
		public HashMap<Integer, P<Atom, Double>> computeDistribution() {
			HashMap<Integer, P<Atom, Double>> D = new HashMap<Integer, P<Atom, Double>>();
			for (Atom typ : factors.keySet())
				for (Integer atom : factors.get(typ).keySet())
					D.put(atom, new P<Atom, Double>(typ, factors.get(typ).get(atom)));
			return D;
		}
		public void addNodeType(Atom t, Integer node, Double factor) {
			if (!this.factors.containsKey(t))
				this.factors.put(t, new HashMap<Integer, Double>());
			this.factors.get(t).put(node, factor);
		}
	}
	
	private Indices computeAtomFactors(MolGraph g) {
		Indices factors = new Indices();
		for (Integer node : g.Nodes().keySet()) {
			Atom t = g.Nodes().get(node);
			Double factor = computeNeighborHoodFactor(node, g);
			factors.addNodeType(t, node, factor);
		}
		return factors;
	}

	private Double computeNeighborHoodFactor(Integer node, MolGraph graph) {
		List<P<Integer, BondType>> neighborEdges = graph.AdjList().get(node);
		// Computes a metric over the 2-neighborhood of the 'node'
		// e.g., take the set{(neighbor_1hop, edget_1hop)} as the first level approximation 
		// and hash that into a constant1. Then take the set{ (neighbor_1hop, edget_1hop, neighbor_2hop, edget_2hop} } 
		// and hash that to a constant2. Then compute weighted sum of constant1, constant2
		// 
		// BASICALLY a distance metric on the neighborhood local graph.
		
		Double metric = 0.0;
		for (P<Integer, BondType> hop1 : neighborEdges) {
			Atom n = graph.GetNodeType(hop1.fst());
			Double hop1_contribution = this.aggregationFns.Metric1Hop(n, hop1.snd());
			metric += hop1_contribution;
			
			List<P<Integer, BondType>> neighbor2Edges = graph.AdjList().get(hop1.fst());
			for (P<Integer, BondType> hop2 : neighbor2Edges) {
				if (hop2.fst() == hop1.fst()) // this edge goes back to the 1hop atom, so dont double count
					continue;
				Atom n2 = graph.GetNodeType(hop2.fst());
				Double hop2_contribution = this.aggregationFns.Metric2Hop(n, hop1.snd(), n2, hop2.snd());
				metric += this.aggregationFns.Hop2Weight(hop2_contribution);
			}
		}
		
		return metric;
	}	
}
