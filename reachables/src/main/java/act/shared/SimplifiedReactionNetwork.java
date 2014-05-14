package act.shared;

import java.util.HashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDBGraph;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.helpers.P;
import act.shared.helpers.T;

/**
 * Class for getting simplified reaction network.
 * All reactions are reduced to single reactant to single product.
 *
 */
public class SimplifiedReactionNetwork {
	private MongoDBPaths db;
	private MongoDBGraph graphDB;
	private List<Long> organisms;
	private boolean skipNoSMILES = true;
	private Long minID;
	
	private Map<String, Chemical> inchiToChemical;
	
	/**
	 * Mapping reactant to products it can go to.
	 */
	private Map<Long,Set<Long>> reactions;
	
	private Set<Long> chemicals;
	
	private Set<Long> ignoredChemicals;
	
	/**
	 * Reactions from all organism and assume reversible.
	 */
	public SimplifiedReactionNetwork(List<Chemical> toFilterOutCofactors) {
		this("tempGraph", toFilterOutCofactors, true);
	}
	
	public SimplifiedReactionNetwork(String graphName, List<Chemical> toFilterOutCofactors) {
		this(graphName, toFilterOutCofactors, true);
	}
	
	public SimplifiedReactionNetwork(String graphName, List<Chemical> toFilterOutCofactors, boolean initialize) {
		this(new MongoDBPaths("pathway.berkeley.edu", 27017, "actv01") /* should get this from command line */, 
			graphName, toFilterOutCofactors, initialize);
	}
	
	/**
	 * Include reactions from graph stored 
	 * in the collection with the given graph name.
	 * initialize - if true: if empty db, initialize with actfamilies else initialize with given db.graphName
	 */
	public SimplifiedReactionNetwork(MongoDBPaths mongo, String graphName, List<Chemical> toFilterOutCofactors, boolean initialize) {
		db = mongo;
		organisms = new ArrayList<Long>();
		chemicals = new HashSet<Long>();
		reactions = new HashMap<Long,Set<Long>>();
		inchiToChemical = new HashMap<String, Chemical>();
		ignoredChemicals = new HashSet<Long>();
		//if (toFilterOutCofactors == null)
		toFilterOutCofactors = db.getCofactorChemicals(); //always filter out cofactors
		for (Chemical c : toFilterOutCofactors) {
			ignoredChemicals.add(c.getUuid());
		}
		System.out.println("Ignored chemicals: " + ignoredChemicals.size());
		
		minID = Long.MAX_VALUE;
		graphDB = db.getGraph(graphName);
		if (graphDB.getNumNodes() == 0 && initialize) { //if empty, initialize collection with actfamilies
			List<Long> organisms = new ArrayList<Long>();
			organisms.add((long)-1);
			initialize(organisms, true);
		} else if (initialize) {
			DBIterator it = graphDB.getNodeIDIterator();
		 	Long id;
			while ((id = graphDB.getNextNodeID(it)) != null) {
				chemicals.add(id);
				Chemical chemical = graphDB.getNode(id);
				
				if (skipNoSMILES) {
					if (chemical.getSmiles() == null) {
						continue;
					}
				}
				inchiToChemical.put(chemical.getInChI(), chemical);
				
				if (!reactions.containsKey(id)) 
					reactions.put(id, new HashSet<Long>());
				Set<Long> products = reactions.get(id);
				
				List<Long> edges = graphDB.getProductsFrom(id);
				for (Long e : edges)
					products.add(e);
			}
		}
		System.out.println("Network created: Nodes: " + chemicals.size());
		System.out.println(inchiToChemical.size());
		minID = graphDB.getMinNodeID();
	}
	
	/**
	 * Initialize using actfamilies' reactions
	 * @param organismIDs
	 * @param reversible
	 */
	private void initialize(List<Long> organismIDs, boolean reversible) {
		organisms = organismIDs;
		chemicals = new HashSet<Long>();
		reactions = new HashMap<Long,Set<Long>>();
		
		List<Chemical> chemicals = db.constructAllChemicalsFromActData(null, null);
		for (Chemical c : chemicals) {
			if (skipNoSMILES && c.getSmiles() == null) continue;
			this.addChemicalObject(c);
		}
		
		for (Long o : organisms) {
			List<Long> reactionList = db.graphByOrganism(o);
			for(Long reactionID : reactionList) {
				Reaction reaction = db.getReactionFromUUID(reactionID);
				addReaction(reversible, reaction, ReactionType.CONCRETE, 1.0);
			}
		}
	}
	
	private List<P<Long,Long>> getSimilarPairs(Reaction reaction) {
		return getSimilarPairs(reaction, ignoredChemicals);
	}
	
	public List<P<Long,Long>> getSimilarPairs(Reaction reaction, Set<Long> ignoredChemicals) {
		/* for fast testing...
			List<P<Long,Long>> list = new ArrayList<P<Long,Long>>();
			//System.out.println(reaction);
			list.add(db.filterReactionByRarity((long) reaction.getUUID()));
			if(true)
				return list;
		*/
		
		Map<P<Chemical, Chemical>, Double> similarities = new HashMap<P<Chemical, Chemical>, Double>(); 
		for (Long substrateID : reaction.substrates) {
			Chemical substrate = getChemical(substrateID);
			if (substrate == null || ignoredChemicals.contains(substrateID)) continue;
			for (Long productID : reaction.products) {
				Chemical product = getChemical(productID);
				if (product == null || ignoredChemicals.contains(productID)) continue;
				similarities.put(new P<Chemical, Chemical>(substrate, product), 0.0);
			}
		}
		
		List<Double> similarityValues = new ArrayList<Double>();
		for(P<Chemical, Chemical> pair : similarities.keySet()) {
			double similarity;
			try {
				//if we do not have one of these chemicals, the similarity remains 0
				if(pair.fst() == null || pair.snd() == null) continue;
				
				if (Double.isNaN((similarity = similarityAlreadyInDB(pair.fst().getUuid(), pair.snd().getUuid())))) {
               
					Indigo indigo = new Indigo();
					IndigoInchi indigoInchi = new IndigoInchi(indigo);
					
					IndigoObject reactant = indigoInchi.loadMolecule(pair.fst().getInChI());
					IndigoObject product = indigoInchi.loadMolecule(pair.snd().getInChI());
					similarity = indigo.similarity(reactant, product);
					if (similarity == 1.0)
						System.out.println("Pair similarity: " + pair.fst().getUuid() + " " + pair.snd().getUuid() + " " + similarity);

					// System.out.println("Insert into database" + pair.fst().getUuid() + " " + pair.snd().getUuid() + " " + similarity);
					this.db.insertChemicalSimilarity(pair.fst().getUuid(), pair.snd().getUuid(), similarity, MongoDB.SimilarityMetric.TANIMOTO);
				} else {
					// System.out.println("Lookup from DB: " + pair.fst().getUuid() + " " + pair.snd().getUuid() + " " + similarity);
				}
			} catch (IndigoException e) {
				// lets be conservative... if we cannot parse the molecules then assume that it is similar to everything else, i.e., similarity = 1.0
				similarity = 1.0F;
				
				System.err.format("SRN: addSimilarPairs: %s \n", e.getMessage());
				// e.printStackTrace();
			}
			similarities.put(pair, similarity);
			similarityValues.add(similarity);
		}
		
		// Find biggest gap
		Collections.sort(similarityValues);
		double bestGap = 0;
		double bestThreshold = 0;
		double prev = 0;
		for(double v : similarityValues) {
			double gap = v - prev;
			if (bestGap < gap) {
				bestGap = gap;
				bestThreshold = v;
			}
		}
		List<P<Long,Long>> similarPairs = new ArrayList<P<Long,Long>>();
		for(P<Chemical, Chemical> pair : similarities.keySet()) {
			if (similarities.get(pair) >= bestThreshold) {
				similarPairs.add(
						new P<Long,Long>(pair.fst().getUuid(), pair.snd().getUuid()));
			}
		}
		if (bestThreshold == 1.0) {
			// System.out.println(reaction.getUUID() + "Similarity threshold " + bestThreshold + " " + reaction);
			// System.out.println(reaction.getUUID() + "Similarity threshold " + bestThreshold + " for " + similarPairs);
			System.out.format("[rxnID:%d] Similarity threshold = %s for pairs %s\n", reaction.getUUID(), bestThreshold, similarPairs);
		}

		return similarPairs;
	}

	private double similarityAlreadyInDB(Long c1, Long c2) {
		if (c1 < 0 || c2 < 0)
			return Double.NaN;
		return this.db.getSimilarity(c1, c2, MongoDB.SimilarityMetric.TANIMOTO);
	}

	private P<Integer, Integer> addReaction(boolean reversible, Reaction reaction, ReactionType type, Double weight) {
		int edgesAdded = 0, edgesBetweenPositivesAdded = 0;
		long reactionID = (long)reaction.getUUID();
		System.out.println("adding rxnid " + reactionID);
		List<P<Long,Long>> pairs = this.getSimilarPairs(reaction);
		if (reversible) {
			Set<P<Long,Long>> revPairs = new HashSet<P<Long,Long>>();
			for (P<Long,Long> p : pairs) {
				revPairs.add(new P<Long,Long>(p.snd(),p.fst()));
			}
			pairs.addAll(revPairs);
		}
		
		for(P<Long,Long> rxnPair: pairs) {
			System.out.println("adding rxnid " + rxnPair);
			if(!reactions.containsKey(rxnPair.fst())) reactions.put(rxnPair.fst(), new HashSet<Long>());
			
			Set<Long> products = reactions.get(rxnPair.fst());
			SimplifiedReaction simpleReaction = new SimplifiedReaction(reactionID, rxnPair.fst(), rxnPair.snd(), type);
			simpleReaction.setFullReaction(reaction.toString());
			simpleReaction.setWeight(weight);

			products.add(rxnPair.snd());
			addEdge(simpleReaction);
			edgesAdded++;
			if (rxnPair.fst() >= 0 && rxnPair.snd() >= 0)
				edgesBetweenPositivesAdded++;
	
		}
		
		return new P<Integer, Integer>(edgesAdded, edgesBetweenPositivesAdded);
	}

	public void addEdge(SimplifiedReaction simpleReaction) {
		graphDB.addEdge(simpleReaction);
	}
	
	public boolean hasChemical(long chem) { return chemicals.contains(chem); }
	
	public Set<Long> getProducts(long substrateID) { return getNeighbors(substrateID, true); }
	
	private Set<Long> getNeighbors(long chemID, boolean products) {
		Set<Long> neighbors = reactions.get(chemID);
		if(neighbors == null) return new HashSet<Long>();
		return neighbors;
	}
	
	public Map<Long,Set<Long>> getAdjacencyList() { return reactions; }
	
	/**
	 * This is useful if we are relying on the network to 
	 * remember chemical objects rather than just an id.
	 * @param chemicals
	 */
	public void addChemicalObjects(List<Chemical> chemicals) {
		for (Chemical c : chemicals) {
			addChemicalObject(c);
		}
	}
	
	/**
	 * See the above method.
	 * @param chemical
	 */
	public void addChemicalObject(Chemical chemical) {
		long id = chemical.getUuid();
		if (!this.chemicals.contains(id)) {
			Indigo indigo = new Indigo();
			IndigoInchi indigoInchi = new IndigoInchi(indigo);
			try {
				IndigoObject mol = indigoInchi.loadMolecule(chemical.getInChI());
				String canonInchi = indigoInchi.getInchi(mol); 
				chemical.setInchi(canonInchi);
				chemical.setSmiles(mol.smiles());
				inchiToChemical.put(canonInchi, chemical);
			} catch (Exception e) {
				System.err.println("failed to load " + chemical.getInChI());
				return;
			}
			
			graphDB.addNode(chemical);
			
			this.chemicals.add(id);
			
			this.inchiToChemical.put(chemical.getInChI(), chemical);
			if (id < minID) {
				minID = id;
			}
		}
	}
	
	public P<Integer, Integer> addEdges(Reaction reaction, ReactionType type, Double weight) {
		return this.addReaction(false, reaction, type, weight);
	}
	
	/**
	 * Retrieve chemical object from db or from an added edge.
	 * @param chemicalID
	 * @return
	 */
	public Chemical getChemical(long chemicalID) {
		return graphDB.getNode(chemicalID);
	}
	
	/**
	 * Retrieve chemical object from db or from an added edge.
	 * @param chemicalID
	 * @return
	 */
	public Chemical getChemical(String chemInChI) {
		//return graphDB.getNode(chemInChI);
		Chemical chemical = this.inchiToChemical.get(chemInChI);
		if (chemical!=null) {
			if (ignoredChemicals.contains(chemical.getUuid())) {
				chemical.setAsCofactor();
			}
		}
		return chemical;
	}
	
	/**
	 * Given reduced reaction, retrieve the id of the original reaction.
	 * The id is also tagged with the ReactionType.
	 * @param reduced
	 * @return
	 */
	public Set<T<Long, ReactionType, Double>> getOriginalRxns(P<Long,Long> reduced) {
		List<SimplifiedReaction> reactions = graphDB.getReactionsBetween(reduced.fst(), reduced.snd());
		Set<T<Long,ReactionType, Double>> reaction_type = new HashSet<T<Long,ReactionType,Double>>(); 
		for(SimplifiedReaction reaction : reactions) {
			reaction_type.add(new T<Long,ReactionType,Double>(reaction.getUuid(), reaction.getType(), reaction.getWeight()));
		}
		return reaction_type;
	}
	
	public Set<Long> getChemicals() { return new HashSet<Long>(chemicals); }
	
	public long getMinChemicalId() {
		return minID;
	}
	
	/**
	 * Return set of chemicals with paths from src.
	 * @param src
	 * @return
	 */
	public Set<Long> getConnectedChemicals(long src) {
		Set<Long> closed = new HashSet<Long>();
		Queue<Long> toExplore = new LinkedList<Long>();
		toExplore.add(src);
		while(!toExplore.isEmpty()) {
			Long chemID = toExplore.poll();
			if(closed.contains(chemID)) continue;
			
			Set<Long> products = reactions.get(chemID);
			if(products==null) continue;
			for(Long p : products) toExplore.add(p);
			closed.add(chemID);
		}
		return closed;
	}
	
	public MongoDB getDB() { return db; }
	
	public Map<String, Chemical> getChemicalInchis() {
		return this.inchiToChemical;
	}
	
	public List<Path> convertCompoundListToPaths(List<List<Long>> paths) {
		List<Path> pathways = new ArrayList<Path>();
		int pathID = 0;
		for(List<Long> p : paths) {
			List<Chemical> chemicals = new ArrayList<Chemical>();
			List<Long> rxns = new ArrayList<Long>();
			List<Reaction> reactions = new ArrayList<Reaction>();
			List<T<Long,ReactionType,Double>> edges = new ArrayList<T<Long, ReactionType, Double>>();
			int i = 0;
			long reactant = -1;
			long product = -1;
			List<Set<Chemical>> reactants = new ArrayList<Set<Chemical>>(), 
					products = new ArrayList<Set<Chemical>>();
			
			for(Long cID : p) {
				Chemical c = this.getChemical(cID);
				if(c == null) {
					System.err.println("SCP getPaths id: " + cID + " has no chemical object");
					continue;
				}
				chemicals.add(c);
				if(i == 0) {
					reactant = cID;
					i++;
					continue;
				}
				product = cID;
				i++;
				//System.out.println(reactant + " " + product);
				
				
				/**
				 * Get an example full reaction that gave us that edge.
				 */
				T<Long,ReactionType,Double> reactionData = this.getOriginalRxns(new P<Long,Long>(reactant,product)).
					iterator().next();
				edges.add(reactionData);
				Long rxnID = reactionData.fst();
				rxns.add(rxnID);
				if (reactionData.snd().equals(ReactionType.CONCRETE)) {
					Reaction reaction = db.getReactionFromUUID(rxnID);
					reactions.add(reaction);
					Long[] productIDs = reaction.getProducts();
					Long[] reactantIDs = reaction.getSubstrates();
					Set<Chemical> productSet = new HashSet<Chemical>();
					Set<Chemical> reactantSet = new HashSet<Chemical>();
					boolean reversed = false;
					for (Long pid : productIDs) {
						if (pid.equals(reactant)) {
							reversed = true;
							continue;
						}
						if (pid.equals(product))
							continue;
						productSet.add(db.getChemicalFromChemicalUUID(pid));
					}
					for (Long rid : reactantIDs) {
						if (rid.equals(reactant) || rid.equals(product))
							continue;
						reactantSet.add(db.getChemicalFromChemicalUUID(rid));
					}

					/**
					 * The reaction may have been reversed.
					 */
					if (reversed) {
						Set<Chemical> temp = productSet;
						productSet = reactantSet;
						reactantSet = temp;
					}
					products.add(productSet);
					reactants.add(reactantSet);
					reactant = product;
				} else {
					products.add(new HashSet<Chemical>());
					reactants.add(new HashSet<Chemical>());
					reactions.add(new Reaction());
					reactant = product;
				}
			}
			System.out.println(rxns);
			
			Path pathway = new Path(chemicals);
			pathway.setPathID(pathID);
			pathway.setReactionIDList(rxns);
			pathway.setProducts(products);
			pathway.setReactants(reactants);
			pathway.setEdgeList(edges);
			pathways.add(pathway);
			
			pathID++;
			
		}
		return pathways;
	}
}
