package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.graph.Node;
import act.server.SQLInterface.MongoDB;
import act.server.Search.AllPairs;
import act.shared.AAMFailException;
import act.shared.MalFormedReactionException;
import act.shared.NoSMILES4InChiException;
import act.shared.OperatorInferFailException;
import act.shared.Reaction;
import act.shared.helpers.P;

import com.ggasoftware.indigo.*; 

public class AROInference {
	int LIMIT = 5000; //limit of how many to process (able to infer some cro out of them)
	int MAXLEN = 128; //assuming no path lengths greater than this; currently, max is less than 40
	private AllPairs pairs; //used to check if path exists between to compounds
	private MongoDB db;
	private TheoryROClasses classes;
	private Map<Integer,P<List<String>, List<String>>> processedRxns;
	
	public AROInference() {
		db = new MongoDB();
		pairs = new AllPairs();
		classes = new TheoryROClasses(db);
		processedRxns = new HashMap<Integer,P<List<String>, List<String>>>();
	}
	
	public void processAll() {
		Set<Long> orgIDs = db.getOrganismIDs();
		System.out.println(orgIDs.size());
		HashMap<P<Long,Long>,Byte> newEdges = new HashMap<P<Long,Long>,Byte>();
		int lenCounts[] = new int[MAXLEN+1];
		
		int numOrg = 0;
		for(Long orgID : orgIDs) {
			numOrg++;
			pairs.getAllReactions(orgID);
			pairs.initialize();
			pairs.runFloyd();
			Set<Long> compounds = pairs.getCompounds();

			for(long substrate : compounds) {
				for(long product : compounds) {

					byte len = pairs.pathLength(substrate, product);

					if(len > 1 && 1.0/(2*len*len*len) > Math.random()) {
						P<Long,Long> edge = new P<Long,Long>(substrate,product);
						if(newEdges.containsKey(edge)) {
							byte oldLen = newEdges.get(edge);
							if(oldLen > len) {
								newEdges.put(edge, len);
								lenCounts[len]++;
								lenCounts[oldLen]--;
							}
						} else {
							newEdges.put(edge, len);
							lenCounts[len]++;
						}
					}
				}
			}
			System.out.println("done with " + numOrg + " organisms");
		}
		System.out.println("num of aros: " + classes.getROs().size());
		int numProcessed = 0;
		for(int c = 0; c <= MAXLEN; c++) {
			System.out.println(c+","+lenCounts[c]);
			numProcessed+=lenCounts[c];
		}
		System.out.println();
		System.out.println("num of reactions: " + numProcessed);
		
		//for user to see counts
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//bucket sort new edges
		HashMap<Byte,HashSet<P<Long,Long>>> sortedEdges = new HashMap<Byte,HashSet<P<Long,Long>>>();
		for(P<Long,Long> p : newEdges.keySet()) {
			Byte len = newEdges.get(p);
			if(!sortedEdges.containsKey(len)) sortedEdges.put(len,new HashSet<P<Long,Long>>());
			sortedEdges.get(len).add(p);
		}
		
		numProcessed = 0;
		int total = 0;
		int skip = 0;
		long s = System.currentTimeMillis();
		for(Byte c = 0; c <= MAXLEN/2; c++) {
			if(!sortedEdges.containsKey(c)) continue;
			HashSet<P<Long,Long>> edges = sortedEdges.get(c);
			System.out.println("Processing length " + c);
			for(P<Long,Long> e : edges) {
				P<List<String>, List<String>> processedRxn = processReaction(e.fst(),e.snd(),c);
				if(processedRxn != null) {
					numProcessed++;
					//processedRxns.put(getRxnID(e.fst(),e.snd()), processedRxn);
				}
				total++;
				if(numProcessed > LIMIT) break;
				//System.out.println("Num processed " + numProcessed + "  out of " + total);
				
			}
			if(numProcessed > LIMIT) break;
		}
		System.out.println("Time it took to diff:" + (System.currentTimeMillis() - s)/1000);
		
		
		/*
		int counter = 100;
		for(TheoryROs ro : classes.getROs()){
			List<Reaction> rxnSet = classes.getRORxnSet(ro);
			int diversity = rxnSet.size();
			ro.CRO().render("5_" +counter + "_" + diversity + ".png", "aro");
			if(diversity > 1 && diversity < 10) {
				for(Reaction r : rxnSet) {
					Indigo indigo = new Indigo();
					P<List<String>, List<String>> rxn = processedRxns.get(new Long(r.getUUID()));
					if(rxn!=null) {
						String rxnStr = SMILES.convertToSMILESRxn(rxn);
						IndigoObject reaction = indigo.loadReaction(rxnStr);
						SMILES.renderReaction(reaction,"5_"+counter+"_concrete_"+r.getUUID(), r.getReactionName(), indigo);
					} else {
						System.out.println("rxn id: " + r.getUUID());
					}
				}
			}
			counter++;
		}*/
		
		
	}
	
	private P<List<String>, List<String>> processReaction(long substrateID, long productID,int dist) {
		List<String> substrates = new ArrayList<String>();
		List<String> products = new ArrayList<String>();
	
		Indigo ind = new Indigo();
		IndigoInchi ic = new IndigoInchi(ind);
		String substrateInchi = db.getChemicalFromChemicalUUID(substrateID).getInChI().trim();
		String productInchi = db.getChemicalFromChemicalUUID(productID).getInChI().trim();
		try {
			IndigoObject substrateMol = ic.loadMolecule(substrateInchi);

			substrates.add(substrateMol.canonicalSmiles());
		} catch(Exception e) {
			return null;
		}
		
		try {
			IndigoObject productMol = ic.loadMolecule(productInchi);

			products.add(productMol.canonicalSmiles());
		} catch(Exception e) {
			return null;
		}
		
		P<List<String>, List<String>> rxn = null;
		try {
			rxn = ReactionDiff.balanceTheReducedReaction((int) substrateID, substrates, products);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		}
		Long[] substrateIDs = { substrateID };
		Long[] productIDs = { productID };
		Long[] orgIDs = {};
		Reaction r = new Reaction(getRxnID(substrateID, productID), substrateIDs, productIDs, null, substrateID + "->" + productID + " (dist: " + dist + ")", orgIDs);
		if (BadRxns.isBad(r, this.db))
			return null;
		//P<List<String>, List<String>> rxn = new P<List<String>, List<String>>(substrates,products);
		//String rxnStr = SMILES.convertToSMILESRxn(rxn);
		
		//IndigoObject mol = indigo.loadMolecule(rxn);
		//IndigoObject reaction = indigo.loadReaction(rxnStr);
		
		//SMILES.renderReaction(reaction, dist+"_"+substrateID + "_" + productID + "before"+".png", "before", indigo);
		try {
			BRO broFull = SMILES.computeBondRO(substrates, products);
			//System.out.println("Computing cro for " + substrateID + " " + productID);
			TheoryROs ro = SMILES.ToReactionTransform((int)(substrateID), rxn, broFull);

			classes.add(ro, new Reaction(getRxnID(substrateID, productID), null, null, null, substrateID + "->" + productID, orgIDs), false, false);
		} catch (AAMFailException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (MalFormedReactionException e) {
			BadRxns.logUnprocessedReaction(r, e.getMessage(), this.db); // unbalanced reactions are ignored, but logged
			return null;
		} catch (OperatorInferFailException e) {
			BadRxns.logROInferFail(r, e.getMessage(), this.db);
			return null;
		} catch (Exception e) {
			return null;
		}
		
		return rxn;
	}

	/**
	 * Invertible hash as rxn id.
	 * This way can recover substrate and product but this assumes their ids are < 2^16, which they currently are.
	 */
	private int getRxnID(long substrateID, long productID) {
		return ((int)substrateID << 16) + (int)productID;
	}
	
	
	//arbritrarily adds atoms; unused
	private int balanceRxn(MolGraph substrateG, MolGraph productG) {
		HashMap<Atom,Integer> typeCounts = new HashMap<Atom,Integer>();
		int numChanges = 0;
		for(int node : substrateG.GetNodeIDs()) {
			Atom nt = substrateG.GetNodeType(node);
			if(typeCounts.containsKey(nt))
				typeCounts.put(nt, typeCounts.get(nt)+1);
			else
				typeCounts.put(nt, 1);
		}
		/*
		 * Add missing product atoms to substrate
		 */
		for(int node : productG.GetNodeIDs()) {
			Atom nt = substrateG.GetNodeType(node);
			int count;
			if(typeCounts.containsKey(nt) && (count = typeCounts.get(nt)) > 0)
				typeCounts.put(nt, count-1);
			else {
				substrateG.AddNode(new Node<Atom>(substrateG.MaxNodeIDContained()+1,nt));
				numChanges++;
			}
		}
		/*
		 * Add missing substrate atoms to product
		 */
		for(Atom nt : typeCounts.keySet()) {
			int numMissing = typeCounts.get(nt);
			numChanges+=numMissing;
			for(int i = 0; i < numMissing; i++)
				productG.AddNode(new Node<Atom>(productG.MaxNodeIDContained()+1,nt));
		}
		return numChanges;
	}
	
	private boolean hasPath(Long substrate, Long product) {
		return pairs.hasPath(substrate, product);
	}
	

	
	public static void main(String[] args) {
		AROInference aro = new AROInference();
		aro.processAll();
	}
}
