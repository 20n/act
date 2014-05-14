package act.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoRenderer;

import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.RONode;
import act.shared.helpers.P;

public class CommonPaths {
	private static MongoDBPaths db = new MongoDBPaths("pathway.berkeley.edu", 27017, "actv01"); // should get this from command line
	//private static MongoDBPaths db = new MongoDBPaths();
	private long nextID;
	private Integer[] rosIgnored = {0};//{1978733610};
	
	private Long curOrg;
	
	/**
	 * Maps ro id to tree of RO nodes
	 * Each root to node path represents a chain of observed ROs.
	 * 
	 */
	private Map<Integer,RONode> roTrees;
	
	/**
	 * Maps each reaction(reactant, product) to an operator
	 */
	private Map<P<Long,Long>,Integer> rxnsRO;
	
	private Set<Long> startCompounds;
	
	/**
	 * Reactant_id to List of Product_id (one for each rxn w/ that reactant)
	 */
	private Map<Long,Set<Long>> reactions;
	
	public CommonPaths() {
		roTrees = new HashMap<Integer,RONode>();
	}
	
	/**
	 * Given an organism id sets up 
	 * 	reactions
	 * 	startCompounds 
	 * 	rxnsRO
	 * (All are declared above)
	 * reactions is a mapping from a reactant_id to a list of product_ids
	 * 	eg, a -> b and a -> c would mean the mapping a to [b,c]
	 * Every compound appearing in the reactions should be in startCompounds
	 * rxnsRO maps the pair (reactant_id,product_id) to ro id
	 * 
	 */
	private void initRxns(Set<Long> orgIDs) {
		rxnsRO = new HashMap<P<Long,Long>,Integer>();
		startCompounds = new HashSet<Long>();
		reactions = new HashMap<Long,Set<Long>>();
		System.out.println(orgIDs);
		int totalScanned = 0;
		int noRO =0;
		for(Long orgID : orgIDs) {
			List<Long> reactionList = db.graphByOrganism(orgID);
			if(reactionList == null) return;
			for (Long reaction : reactionList) {
				P<Long, Long> filteredReaction = db.filterReactionByRarity(reaction);
				Integer ro = db.getROForRxnID(reaction, "CRO");
				if(ro==null || ro==0)
					noRO++;
				rxnsRO.put(filteredReaction, ro);
				startCompounds.add(filteredReaction.fst());
				
				if (reactions.containsKey(filteredReaction.fst())) {
					reactions.get(filteredReaction.fst()).add(filteredReaction.snd());
				}else {
					Set<Long> newSet = new HashSet<Long>();
					newSet.add(filteredReaction.snd());
					reactions.put(filteredReaction.fst(), newSet);
				}
				
				//if we are assuming reversible
				startCompounds.add(filteredReaction.snd());
				if (reactions.containsKey(filteredReaction.snd())) {
					reactions.get(filteredReaction.snd()).add(filteredReaction.fst());
				}else {
					Set<Long> newSet = new HashSet<Long>();
					newSet.add(filteredReaction.fst());
					reactions.put(filteredReaction.snd(), newSet);
				}
				totalScanned++;
			}
		}
		System.out.println(totalScanned);
		System.out.println("has RO " + (totalScanned - noRO));
		
	}
	
	String[] colors = {"red","blue","green","yellow","cyan"};
	
	private void graphToDot(Long orgID) {
		Map<Long,Set<Long>> organisms = db.getOrganisms();
		if(!organisms.containsKey(orgID)) {
			System.out.println("does not have species id " + orgID);
		}
		initRxns(organisms.get(orgID));
		BufferedWriter dotf;
		String graphname = "RO_colored_graph_of_organism_"+ orgID;
		int numWRO = 0;
		int numTotal = 0;
		try {
			dotf = new BufferedWriter(new FileWriter("graph_of_org_"+orgID+"v2", false));
			dotf.write("graph " + graphname + " {\n");
			dotf.write("\tnode [shape=plaintext]\n");
			for(Long reactant : reactions.keySet()) {
				for(Long product : reactions.get(reactant)) {
					P<Long,Long> reaction = new P<Long,Long>(reactant,product);
					Integer ro = rxnsRO.get(reaction);
					String color;
					if(ro!=null) {
						int red = ro % 256;
						int blue = (ro >>> 10 + 50) % 256;
						int green = (ro >>> 20 + 50) % 256;
						color = Integer.toHexString(red<<16 | blue<<8 | green);
						while(color.length() < 6) {
							color = "0" + color;
						}
						if(ro!=0)
							numWRO++;
						
					} else {
						color = "000000";
					}
					numTotal++;
					dotf.write("\t" + reactant + " -- " + product + "[color=\"#" + color + "\"]" + ";\n");
				}
			}
			dotf.write("}");
			dotf.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Fraction with ROs " + numWRO + "/" + numTotal + "=" + numWRO/numTotal);
	}
	
	private void displayStats(Long orgID) {
		System.out.println("Org ID: " + orgID);
		System.out.println("Start compounds: " + startCompounds.size());
		System.out.println("Reactions: " + reactions.size());
	}
	
	/**
	 * For each organism, call exploreOrganism.
	 * Adds paths to db and returns top n paths
	 * 
	 * Drop operpaths collection before calling findPaths
	 */
	private List<List<Integer>> findPaths(int n, int numOrgs) {
		int org = 0;
		long s = System.currentTimeMillis();
		Map<Long,Set<Long>> speciesOrg = db.getOrganisms();
		
		for (Long organism : speciesOrg.keySet()) {
			curOrg = organism;
			exploreOrganism(organism,speciesOrg.get(organism));
			org++;
			if(org >= numOrgs) break;
		}
		System.out.print("Time exploring: " + (System.currentTimeMillis() - s));
		for(RONode root : roTrees.values()) {
			db.submitToActROTree(root);
		}
		List<RONode> topNodes = db.getTopKNodes(n);
		List<List<Integer>> topPaths = new ArrayList<List<Integer>>();
		for (RONode topNode : topNodes) {
			RONode curNode = topNode;
			List<Integer> curPath = new LinkedList<Integer>();
			curPath.add(curNode.getRO());
			while (curNode.getParentID() != null) {
				curNode = db.getNodeByID(curNode.getParentID());
				curPath.add(curNode.getRO());
			}
			Collections.reverse(curPath);
			topPaths.add(curPath);
		}
		return topPaths;
	}
	
	public List<P<List<Long>,List<Long>>> getTopKPaths(int k) {
		List<P<List<Long>, List<Long>>> topPaths = new ArrayList<P<List<Long>, List<Long>>>();
		List<RONode> topNodes = db.getTopKNodes(k);
		for (RONode topNode : topNodes) {
			for(List<Long> p : topNode.getExampleRxn().keySet()) {
				List<Long> path = new ArrayList<Long>();
				for(Long id : p) {
					path.add(id);
				}
				topPaths.add(new P(path,topNode.getExampleRxn().get(p)));
				break;
			}
			
		}
		return topPaths;
	}
	
	/**
	 * Returns one example each from the top k paths, each as a path object.
	 * @param k
	 * @return
	 */
	public List<Path> getExamplePath(int k) {
		List<P<List<Long>, List<Long>>> unformattedPaths = getTopKPaths(k);
		List<Path> pathList = new ArrayList<Path>();
		for (P<List<Long>, List<Long>> path : unformattedPaths) {
			List<Chemical> chemicalList = new ArrayList<Chemical>();
			for (Long chemicalID : path.fst()) {
				chemicalList.add(db.getChemicalFromChemicalUUID(chemicalID));
			}
			Path newPath = new Path(chemicalList);
			pathList.add(newPath);
			this.renderCompoundImages(newPath);
		}
		return pathList;
	}
	
	private void exploreOrganism(Long speciesID, Set<Long> otherIDs) {
		initRxns(otherIDs);
		//System.out.println(otherIDs);
		displayStats(speciesID);
		for(Long s : startCompounds) {
			traverse(s);
		}
	}
	
	/*
	 * startID is chemical id we start exploring from
	 * reactions is a mapping from (reactant_id,reaction_id) to product_id
	 */
	private void traverse(Long startID) {
		Set<String> closed = new HashSet<String>();
		closed.add(db.getChemicalFromChemicalUUID(startID).getSmiles());
		traverse(startID,null,closed,0,new ArrayList<Long>());
	}
	
	private void traverse(Long curID, RONode curNode, Set<String> closed, int depth, List<Long> curPath) {
		Set<Long> products = reactions.get(curID);
		if(curNode==null)
			curPath.add(curID);
		if(products != null) {
			for(Long product : products) {
				String productSmiles = db.getChemicalFromChemicalUUID(product).getSmiles();
				if(closed.contains(productSmiles)) continue; //prevent cycles
				P<Long,Long> rxn = new P<Long,Long>(curID,product);
				Integer ro = rxnsRO.get(rxn);
				if(ro == null) continue; //skip if no matching ro
				
				RONode nextNode = null;
				if(curNode==null) {
					curPath.add(product);
					if(!roTrees.containsKey(ro)) {
						nextNode = new RONode(ro,nextID);
						//nextNode.addExampleRxn(new ArrayList<Long>(curPath));
						roTrees.put(ro,nextNode);
						nextID++;
					} else {
						nextNode = roTrees.get(ro);
					}
					
				} else {
					nextNode = curNode.getChild(ro);
					if(nextNode == null) {
						nextNode = new RONode(ro,nextID);
						curPath.add(product);
						//nextNode.addExampleRxn(new ArrayList<Long>(curPath));
						curNode.addChild(nextNode);
						nextID++;
					} else {
						curPath.add(product);
					}
				}
				nextNode.addExampleRxn(new ArrayList<Long>(curPath),curOrg);
				//nextNode.increment();
				
				closed.add(productSmiles);
				traverse(product,nextNode,closed,depth+1,curPath);
				closed.remove(productSmiles);
				curPath.remove(product);
			}
		}
		if(curNode!=null) curNode.setDepth(depth);
		//System.out.println("finished exploring node at depth:" + depth);
	}
	
	/**
	 * Adds to operpathsets give that operpaths is filled.
	 */
	public void findPathSets() {
		long pathID = 0;
		Map<Map<Integer,Integer>,List<P<List<Long>,List<Long>>>> commonSets = 
				new HashMap<Map<Integer,Integer>,List<P<List<Long>,List<Long>>>>();
		 
		while(true) {
			P<RONode,List<Integer>> p = db.getROPath(pathID);
			if(p == null) break;

			Map<Integer,Integer> key = new HashMap<Integer,Integer>();
			for(Integer ro : p.snd()) {
				if(!key.containsKey(ro))
					key.put(ro, 1);
				else
					key.put(ro, key.get(ro) + 1);
			}
			
			RONode node = p.fst();
			if(!commonSets.containsKey(key)) {
				commonSets.put(key, new ArrayList<P<List<Long>,List<Long>>>());
			}
			List<P<List<Long>,List<Long>>> myExamples = commonSets.get(key);
			for(List<Long> example : node.getExampleRxn().keySet()) {
				myExamples.add(new P<List<Long>,List<Long>>(example,node.getExampleRxn().get(example)));
			}
			pathID++;
			if(pathID%600 == 0) System.out.println("Done "+pathID);
		}
		
		int id = 0;
		for(Map<Integer,Integer> ros: commonSets.keySet()) {
			db.addROPathSet(id, ros, commonSets.get(ros));
			id++;
		}
	}
	
	//TODO should move this elsewhere
	public void renderCompoundImages(Path pathway) {
		Indigo indigo = new Indigo();
		IndigoRenderer renderer = new IndigoRenderer(indigo);
		indigo.setOption("render-output-format", "png");
		//indigo.setOption("render-image-size", 400, 200);
		for (Chemical compound : pathway.getCompoundList()) {
			if(compound.getSmiles()== null) continue;
			IndigoObject smileCompound = indigo.loadMolecule(compound.getSmiles());
			indigo.setOption("render-comment", compound.getCanon());
			
			new File("war/compounds").mkdir();
			new File("war/compounds/" + pathway.pathID).mkdir();
			renderer.renderToFile(smileCompound, "war/compounds/" + pathway.pathID +"/"+ pathway.getCompoundList().indexOf(compound) + ".png");
		}
	}
	
	public List<Path> getFakePath() {
		List<Path> fakePath = new ArrayList<Path>();
		for (int k = 0; k < 10; k++) {
			List<Chemical> chemicalList = new ArrayList<Chemical>();
			for (int i = 0; i < 10; i++) {
				chemicalList.add(db.getChemicalFromChemicalUUID(Long.parseLong("5")));
			}
			fakePath.add(new Path(chemicalList));
		}
		for (Path path : fakePath) {
		}
		return fakePath;
	}
	
	public void renderTopPathSets() {
		List<Long> ids = db.getTopKPathSets(10,Arrays.asList(rosIgnored));
		int i = 0;
		for(Long id : ids) {
			Set<P<List<Long>,List<Long>>> paths = db.getPathSetExamples(id);
			int pathnum = 0;
			for(P<List<Long>,List<Long>> path : paths) {
				Indigo indigo = new Indigo();
				List<String> smilesPath = new ArrayList<String>();
				String idPath = "";
				for(Long chem: path.fst()) {
					smilesPath.add(db.getChemicalFromChemicalUUID(chem).getSmiles());
					idPath += chem + " >> ";
				}
				idPath += " Organisms: " + path.snd();
				P<List<String>, List<String>> rxn = new P<List<String>, List<String>>(smilesPath,new ArrayList<String>());
				String rxnStr = SMILES.convertToSMILESRxn(rxn);
				String dir = "commonpathset_"+i+"_"+id;
				new File(dir).mkdir();
				SMILES.renderReaction(indigo.loadReaction(rxnStr), dir+"/"+pathnum, idPath, indigo);
				pathnum++;
			}
			i++;
		}
	}
	
	public static void main(String[] args){
		CommonPaths cp = new CommonPaths();
		//cp.graphToDot(new Long(562));
		cp.findPaths(0, 5000);
		cp.findPathSets();
		//cp.renderTopPathSets();
		
		/*
		List<P<List<Long>, List<Long>>> examples = cp.getTopKPaths(20);
		int i = 0;
		for(P<List<Long>, List<Long>> e : examples) {
			List<Long> p = e.fst();
			System.out.println(p);
			Indigo indigo = new Indigo();
			List<String> smilesPath = new ArrayList<String>();
			String idPath = "";
			for(Long c : p) {
				smilesPath.add(db.getChemicalFromChemicalUUID(c).getSmiles());
				idPath += c + " >> ";
			}
			idPath += " Organisms: " + e.snd();
			P<List<String>, List<String>> rxn = new P<List<String>, List<String>>(smilesPath,new ArrayList<String>());
			String rxnStr = SMILES.convertToSMILESRxn(rxn);
			
			SMILES.renderReaction(indigo.loadReaction(rxnStr), "commonpaths_"+i, idPath, indigo);
			System.out.println(rxnStr);
			i++;
		}*/
	}
	
	
}
