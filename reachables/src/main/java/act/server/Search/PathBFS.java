package act.server.Search;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import act.server.VariousSearchers;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.Reaction;
import act.shared.ReactionDetailed;

public class PathBFS {
	int numPaths = 1; //how many paths to find before we stop
	int foundPaths = 0;
	int steps; //a counter used for logging info
	
	// The following calls are made *many times* to the DB (so better be indexed):
	// 1) DB.getReactant(long rxnId), 2) DB.getProducts(long rxnId), 3) DB.getRxnsWith(long cmpdId)
	//
	// For 1,2), db.actfamilies.find({_id: 4343}) has to be fast
	// For 3), db.actfamilies.find({'enz_summary.substrates': {$elemMatch: {'pubchem':NumberLong(173)}}}) has to be fast 
	// 
	// For 1,2) we can db.actfamilies.ensureIndex({ _id: 1 }, {unique: true}); to indicate that the _id is distinct
	// For 3) db.actfamilies.ensureIndex({'enz_summary.substrates': 1}) indexes each element of the arrays in enz_summary.substrates
	//      .... but this does not really help (if you do query.explain() it shows stats)
	//			 i.e., db.actfamilies.find({'enz_summary.substrates': {$elemMatch: {'pubchem':NumberLong(173)}}}).explain()
	//			 while an exact query on the full array shows the index makes the right hits
	// 			 i.e., db.actfamilies.find({'enz_summary.substrates': [ {'pubchem':NumberLong(173)} , {'pubchem':NumberLong(938)}, {'pubchem:NumberLong(953)} ] }).explain())
	//		.... elemMatch has problems: This thread says that elemMatch is for "multiple values in an array element". There might be no need to use it here..
	//			     (thread: http://groups.google.com/group/mongodb-user/browse_thread/thread/76146efac85be629?fwc=1)
	//		.... and once we remove the elemMatch, we get matches for the contains query.. and also index use.
	
	private MongoDB db;
	private Set<Long> natives;
	
	private HashMap<Long,ReactionNode> processedRxns;
	private HashMap<Long,CompoundNode> processedCompounds;
	
	private HashMap<Long,List<Long>> cachedRxns;
	
	private ArrayList<Set<Long>> tree;
	private Set<Long> cur;
	private Set<Long> initialSet;
	
	private Set<Long> restrictedReactions;
	
	private boolean TIMING = false;
	private boolean useReversed = false;
	private Set<Long> chemicalsToAvoid;
	
	private class CompoundNode {
		List<Long> parents; //reactions this compound resulted from during forward search
		CompoundNode(Long id) {
			this.parents = new ArrayList<Long>();
		}
		
		public void addParent(long p) {
			parents.add(p);
		}
		
		public int numParents() {
			return parents.size();
		}
	}
	
	private class ReactionNode {
		List<Long> parents; //compounds this reaction resulted from during forward search
		
		ReactionNode(Long id, List<Long> parents) {
			this.parents = parents;
		}
	}
	
	public PathBFS(MongoDB db, Set<Long> nativeIDs) {
		this.db = db;
		this.natives = nativeIDs;
		this.cachedRxns = new HashMap<Long,List<Long>>();
		processedRxns = new HashMap<Long,ReactionNode>();
        processedCompounds = new HashMap<Long,CompoundNode>();
	}
	
	public PathBFS(MongoDB db, HashSet<Long> nativeMetabolites, boolean backwards) {
		this(db, nativeMetabolites);
	}
	
	public void setReverse(boolean useReverse) {
		useReversed = useReverse;
	}
	
	/**
	 * Search will only use these reactions. 
	 * If null (or uncalled), all reactions will be considered.
	 */
	public void setRestrictedReactions(Set<Long> reactionIDs) {
		restrictedReactions = reactionIDs;
	}
	
	/**
	 * Search will avoid these chemicals. 
	 * If null (or uncalled), all chemicals are allowed.
	 */
	public void setChemicalsToAvoid(Set<Long> chemicalIDs) {
		chemicalsToAvoid = chemicalIDs;
	}
	
	public List<List<ReactionDetailed>> getPaths(Chemical target) {
		if(target==null) {
			System.out.println("Target is null");
			return new ArrayList<List<ReactionDetailed>>();
		}
		List<List<Reaction>> reactionsPaths = performSearch(target.getUuid());
		
		List<List<ReactionDetailed>> result = new ArrayList<List<ReactionDetailed>>();
		if(reactionsPaths == null) return new ArrayList<List<ReactionDetailed>>();
		for(List<Reaction> path : reactionsPaths) {
			List<ReactionDetailed> reactionsDetailed = new ArrayList<ReactionDetailed>();
			for(Reaction r: path) {
				reactionsDetailed.add(convertToDetailedReaction(r));
			}
			result.add(reactionsDetailed);
		}
		return result;
	}
	
	/**
	 * Returns a list of Path objects. Can be used by front end.
	 * @param targetID
	 * @return
	 */
	public List<Path> getPaths(Long targetID) {
		Chemical c = db.getChemicalFromChemicalUUID(targetID);
		List<List<ReactionDetailed>> paths = getPaths(c);
		List<Path> pathways = new ArrayList<Path>();
		for(List<ReactionDetailed> path : paths) {
			List<Long> rxns = new ArrayList<Long>();
			for(ReactionDetailed r : path) {
				rxns.add((long) r.getUUID());
			}
			Path pathway = new Path(new ArrayList<Chemical>());
			pathway.setReactionIDList(rxns);
			pathways.add(pathway);
		}
		return pathways;
	}
	
	/**
	 * Used by expand method.
	 * @param id
	 * @return
	 */
	private List<Long> getChildren(Long id) {
		List<Long> products = db.getProducts(id);
		List<Long> nonCofactorProducts = new ArrayList<Long>();
		for (Long product : products) {
			nonCofactorProducts.add(product);
		}
		return nonCofactorProducts;
	}
	
	/**
	 * Used by expand method.
	 * @param id
	 * @return
	 */
	private List<Long> getParents(Long id) {
		List<Long> reactants = db.getReactants(id);
		List<Long> nonCofactorReactants = new ArrayList<Long>();
		for (Long reactant : reactants) {
			nonCofactorReactants.add(reactant);
		}
		return nonCofactorReactants;
	}
	
	/**
	 * Used by performSearch
	 * @param start
	 * @param newCompounds
	 * @param end
	 * @return
	 */
	private boolean isGoal(Set<Long> start, Set<Long> newCompounds, Long end) {
		if(processedCompounds.containsKey(end)) {
			foundPaths = processedCompounds.get(end).numParents();
			return true;
		}
		return false;
	}
	
    /**
     * 
     * @param cur - The set of all compounds that we've reached.
     * @param processedRxns set of reactions to exclude, mapping reactions to parent compounds
     * @param newCompounds - set of compounds to query reactions for
     * @param processedCompounds - mapping chemical to reaction it came from
     * @return set of reactions performed
     */
    private Set<Long> expand(Set<Long> cur, Set<Long> newCompounds) {
    	steps++;
        HashSet<Long> rxns = new HashSet<Long>();
        HashMap<Long, ReactionNode> checkedRxns = new HashMap<Long, ReactionNode>();
        long s = System.currentTimeMillis();
        for (Long r : newCompounds) {
        	getReactions(rxns, r);
        }
        //System.out.println("# of new compounds " + newCompounds.size());
        newCompounds.clear();
        
        long t = System.currentTimeMillis();
        if (TIMING)
        	System.out.println("fetching time " + (t-s));

        for (Long r : rxns) {
        	if (processedRxns.containsKey(r)) continue;

        	List<Long> parents = this.getParents(r);
        	if (cur.containsAll(parents)) {
        		checkedRxns.put(r, new ReactionNode(r, parents));
        	} 
        }
        if (TIMING)
        	System.out.println("checking time " + (System.currentTimeMillis() - t));
        for (Long rxn : checkedRxns.keySet()) {
            ReactionNode rxnNode = checkedRxns.get(rxn);
			processedRxns.put(rxn, rxnNode);
            List<Long> children = this.getChildren(rxn);
            for (Long c : children) {
            	if (!processedCompounds.containsKey(c))  {
            		CompoundNode a = new CompoundNode(c);
            		a.addParent(rxn);
            		processedCompounds.put(c, a);
            	} else {
            		processedCompounds.get(c).addParent(rxn);
            	}
            	if (cur.add(c)) {
            		newCompounds.add(c);
            	}
            }
        }
        if (TIMING)
        	System.out.println(" total expand time " + (System.currentTimeMillis() - s));
        System.out.println("# of reached compounds " + cur.size() + " at step " + steps);
        return checkedRxns.keySet();
    }

	private void getReactions(HashSet<Long> rxns, Long chemID) {
		List<Long> reactions;
		if(cachedRxns.containsKey(chemID)) {
			reactions = cachedRxns.get(chemID);
		} else {
			reactions = db.getRxnsWith(chemID, false);
			if (useReversed) {
				reactions.addAll(db.getRxnsWith(chemID, true));
			}
			cachedRxns.put(chemID, reactions);		
		}
		for(Long rxn : reactions) {
			if (restrictedReactions == null || restrictedReactions.contains(rxn)) {
				Reaction reaction = db.getReactionFromUUID(rxn);
				Long[] products = reaction.getProducts();
				boolean avoid = false;
				if (chemicalsToAvoid != null) {
					for (Long p : products) {
						if (chemicalsToAvoid.contains(p)) {
							avoid = true;
						}
					}
				}
				if (!avoid)
					rxns.add(rxn);
			}
		}
	}
    
	/**
	 * Initializes the full tree (ie BFS without searching for a particular target)
	 */
	public void initTree() {
		performSearch(-1L);
	}
	

	
    /**
     * Performs search given starting compounds and a target.
     * Returns multiple paths.
     */
    private List<List<Reaction>> performSearch(Long end) {
    	List<List<Reaction>> retPaths = new ArrayList<List<Reaction>>();
    	initialSet = new HashSet<Long>();
    	
    	
    	List<Chemical> cofactors = this.db.getCofactorChemicals();
    	this.initialSet = new HashSet<Long>();
    	for (Chemical cofactor : cofactors) {
    		this.initialSet.add(cofactor.getUuid());
    	}
    	for(Long l: natives) {
			initialSet.add(l);
		}
    	
    	if (initialSet.contains(end)) {
    		System.out.println("Target is a native");
    		return retPaths;
    	}
    	
    	if (tree == null) {
    		foundPaths = 0;
    		steps = 0;
    		processedRxns.clear();
    		processedCompounds.clear();

    		cur = new HashSet<Long>(); //current set of compounds that can be reached
    		Set<Long> newCompounds = new HashSet<Long>(); //new compounds just found from expanding
    		newCompounds.addAll(initialSet);
    		cur.addAll(initialSet);

    		tree = new ArrayList<Set<Long>>(); //ordered list of new compounds from each expansion

    		System.out.println("Starting BFS with " + initialSet.size() + " starting compounds");

    		boolean btFrom = false;
    		Set<Long> toBacktrace = new HashSet<Long>();

    		while (true) {
    			btFrom = isGoal(initialSet, newCompounds, end);
    			if (btFrom) toBacktrace.add(end);
    			if (foundPaths >= numPaths) break;

    			Set<Long> newRxns = expand(cur, newCompounds);
    			if (newRxns.isEmpty()) break;
    			tree.add(newRxns);
    		}


    		System.out.println("done BFS");
    	}
    	retrievePaths(initialSet, end, retPaths);
    	return retPaths;
    }
    
    public ReactionsHypergraph<Long, Long> getGraph() {
    	ReactionsHypergraph<Long, Long> result = new ReactionsHypergraph<Long, Long>();
    	for (Long rid : processedRxns.keySet()) {
    		ReactionNode node = processedRxns.get(rid);
    		result.addReactants(rid, node.parents);
    	}
    	for (Long cid : processedCompounds.keySet()) {
    		CompoundNode node = processedCompounds.get(cid);
    		result.addReactions(cid, node.parents);
    	}
    	result.setInitialSet(natives);
    	result.setIdTypeDB_ID();
    	return result;
    }
    
    @Deprecated
    public ReactionsHypergraph<Long, Long> getGraphTo(Long target, int inEdgeThreshold) {
    	return getGraphTo(target, inEdgeThreshold, 500);
    }
    
    @Deprecated
    /**
     * It does not deal with having both reverse and forward reactions well right now.
     * @param target
     * @return a graph representing all paths to target.
     */
    public ReactionsHypergraph<Long, Long> getGraphTo(Long target, int inEdgeThreshold, int nodeLimit) {
    	ReactionsHypergraph<Long, Long> graph = new ReactionsHypergraph<Long, Long>();
    	Set<Long> expanded = new HashSet<Long>();
    	if (!initialSet.contains(target))
    		expanded.addAll(initialSet);
    	LinkedList<Long> fringe = new LinkedList<Long>();
    	fringe.add(target);
    	while(!fringe.isEmpty()) {
    		Long product = fringe.pop();
    		int skippedReactions = 0;
    		if (expanded.contains(product)) {
				continue;
			}
    		expanded.add(product);
    		CompoundNode n = processedCompounds.get(product);
    		if(n == null) continue;
    		List<Long> reactions = n.parents;
    		if (reactions == null) continue;
    		if (graph.getNumChemicals() > nodeLimit) break;
    		if (reactions.size() > inEdgeThreshold) {
    			graph.addChemicalInfo(product, "too many reactions: " + reactions.size());
    			continue;
    		}
    		
    		for (Long reaction : reactions) {
    			ReactionNode reactionNode = processedRxns.get(reaction);
    			List<Long> reactants = reactionNode.parents;
    			Long[] products = db.getReactionFromUUID(reaction).getProducts();
        		if (graph.getNumChemicals() > nodeLimit) {
        			skippedReactions++;
        			continue;
        		}
    			graph.addReaction(reaction, reactants, Arrays.asList(products));
    			fringe.addAll(reactants);
    		}
    		if (skippedReactions > 0) {
    			graph.addChemicalInfo(product, "skipped reactions: " + skippedReactions);
    			continue;
    		}
    	}
    	System.out.println("Chemicals: " + graph.getNumChemicals());
    	System.out.println("Reactions: " + graph.getNumReactions());
    	
    	Set<String> temp = new HashSet<String>();
    	for (Long i : initialSet) {
    		temp.add(i + "");
    	}
    	//HypergraphEnumerator enumerator = new HypergraphEnumerator(temp);
    	//return enumerator.cycleBreak(graph);
    	return graph;
    }
    
    /**
     * This is called after generating the tree.
     * It's a wrapper around findPath to get multiple paths.
     * The algorithm for multiple paths isn't very nice right now though:
     * only the last step is being changed to allow for more paths.
     * @param start
     * @param end
     * @param retPaths
     */
	private void retrievePaths(Set<Long> start, Long end,
			List<List<Reaction>> retPaths) {
		//Generate the actual paths
    	if(processedCompounds.get(end) != null){
    		CompoundNode n = processedCompounds.get(end);
    		for(int i = 0; i < n.numParents(); i++) {
    			LinkedList<Long> required = new LinkedList<Long>();
    			HashSet<Long> sigRxns = new HashSet<Long>();
    			HashSet<Long> obtained = new HashSet<Long>();

    			obtained.addAll(start);
    			obtained.add(end);
    			required.addAll(processedRxns.get(n.parents.get(i)).parents);
    			sigRxns.add(n.parents.get(i));
    			if (backtracePaths(required,obtained,sigRxns,end))
    				retPaths.add(sortPath(sigRxns));
    		}
    	}
	}

	/**
	 * Given that the tree is found and a set of significant reactions are found,
	 * sort the significant reactions based on the tree.
	 * 
	 * @param sigRxns
	 * @return
	 */
	private List<Reaction> sortPath(HashSet<Long> sigRxns) {
		List<Reaction> retPath = new ArrayList<Reaction>();
		for(Set<Long> s : tree) {
		    ArrayList<Long> toConvert = new ArrayList<Long>();
		    for(Long r : s) {
		        if(sigRxns.contains(r)) {
		            toConvert.add(r);
		            Reaction temp = db.getReactionFromUUID(r);
		            retPath.add(temp);
		        }
		    }
		}
		return retPath;
	}
    
	/**
	 * Given the required chemicals and chemicals already obtained,
	 * find the set of significant reactions, reactions that'll produce
	 * the required set. This is limiting in that it only looks at one possible path.
	 * 
	 * Returns success. It should always succeed except for when it requires band.
	 * 
	 * This method relies on having already generated a DAG with BFS.
	 * @param required
	 * @param obtained
	 * @param sigRxns
	 * @param band - disallow requirement of these chemicals
	 */
    private boolean backtracePaths(LinkedList<Long> required, Set<Long> obtained, Set<Long> sigRxns, Long band) {
    	obtained = new HashSet<Long>(obtained);
    	while(!required.isEmpty()) {
    		Long p = required.pop();
    		if (p.equals(band)) return false;
            if (obtained.contains(p)) continue;
            Long rxn = processedCompounds.get(p).parents.get(0);
            List<Long> getReqs = processedRxns.get(rxn).parents;
            if(getReqs != null) {
	            for(Long compound : getReqs) {
	            	required.add(compound);
	            }	
            }
            obtained.add(p);
            sigRxns.add(rxn);
    	}
    	return true;
    }
    
	public List<ReactionDetailed> findPathDummy(Chemical chem) {
		// TODO stub method until the above is implemented... 
		// just outputs a bunch of random reactions for testing client...
		// Is referenced in ActAdminServiceImpl.findPathway()...
		List<ReactionDetailed> rr = new ArrayList<ReactionDetailed>();
		for (long i = 42; i < 47; i++) {
			rr.add(convertToDetailedReaction(((MongoDB)this.db).getReactionFromUUID(i)));
		}
		return rr;
	}

	// helper function to convert compressed Reaction into more verbose ReactionDetailed for client consumption
	private ReactionDetailed convertToDetailedReaction(Reaction r) {
		String imageURL = "http://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?t=l&cid=";
		int slen, plen;
		Chemical[] sC = new Chemical[slen = r.getSubstrates().length];
		String[] sU = new String[slen];
		Chemical[] pC = new Chemical[plen = r.getProducts().length];
		String[] pU = new String[plen];
		
		String[] sImage = new String[slen];
		String[] pImage = new String[plen];

		for (int i = 0; i< slen; i++) {
			sC[i] = ((MongoDB)this.db).getChemicalFromChemicalUUID(r.getSubstrates()[i]);
			sU[i] = "http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + sC[i].getPubchemID();
			sImage[i] = imageURL + sC[i].getPubchemID();
		}
		for (int i = 0; i< plen; i++) {
			pC[i] = ((MongoDB)this.db).getChemicalFromChemicalUUID(r.getProducts()[i]);
			pU[i] = "http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + pC[i].getPubchemID();
			pImage[i] = imageURL + pC[i].getPubchemID();
		}
		
		
		ReactionDetailed detail = new ReactionDetailed(r, sC, sU, pC, pU);
		detail.setProductImages(pImage);
		detail.setSubstrateImages(sImage);
		return detail;
	}
	
	public static List<Chemical> getReachables(MongoDB db) {
		HashSet<Long> metabolites = VariousSearchers.chemicalsToIDs(db.getNativeMetaboliteChems());
		PathBFS pathFinder = new PathBFS(db, metabolites);
		pathFinder.initTree();
		FileWriter fstream;
		BufferedWriter out;
		ArrayList<Chemical> reachables = new ArrayList<Chemical>();
		for (long chemid : pathFinder.cur) {
			Chemical chemical = db.getChemicalFromChemicalUUID(chemid);
			reachables.add(chemical);
		}
		return reachables;
	}

	
	/**
	 * Some test targets
	 * 4-aminophenol 13737
	 * butanol 10779
	 * tropine 15456 # no paths
	 * butyrophenone 360
	 * tricyclene 880
	 * 3-hydroxykynurenine 874
	 * myrtenol 956
	 * citral 988
	 * 
	 * Writes all reachables to a file.
	 **/
	public static void main(String[] args) {
		MongoDB db = new MongoDB();
		HashSet<Long> metabolites = VariousSearchers.chemicalsToIDs(db.getNativeMetaboliteChems());
		
		if (args.length == 0 || args.length > 2) {
			System.out.println("Usage: <output file name> <additional starting compound ids");
			System.out.println("Calculates reachables and saves result in file");
			System.out.println("Starting compounds include all native metabolites and cofactors");
			System.out.println("The second argument is optional. It would be a file with a list of ids");
		}
		
		if (args.length > 1) {
			try {
				FileInputStream fstream = new FileInputStream(args[1]);
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				String strLine;
				while ((strLine = br.readLine()) != null)   {
					metabolites.add(Long.parseLong(strLine));
				}
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	
		PathBFS pathFinder = new PathBFS(db, metabolites);
		pathFinder.initTree();
		FileWriter fstream;
		BufferedWriter out;
		try {
			fstream = new FileWriter(args[0]);
			out = new BufferedWriter(fstream);
			
			
			for (long chemid : pathFinder.cur) {
				Chemical chemical = db.getChemicalFromChemicalUUID(chemid);
				out.write("\"" + chemical.getShortestName() + "\"");
				out.write(",\"" + chemical.getSmiles() + "\"");
				out.write(",\"" + chemical.getInChI() + "\"");
				out.write("\n");
				//out.write(chemical.getUuid() + "\n");
			}
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 
		
	}

	
}
