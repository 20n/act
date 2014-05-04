package com.act.reachables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.Chemical.REFS;
import act.shared.helpers.P;

import com.act.reachables.TaskMonitor;

public class LoadAct extends SteppedTask {
	private int step = 1000;
	private int loaded, total;
	boolean loadChemMetadata;
			
	public MongoDB db;
	public LoadAct(boolean load_chemicals) {
		loadChemMetadata = load_chemicals;
		this.db = Utils.createActConnection("localhost", 27017, "pathway.berkeley.edu", 30000 /* 28008 */);
		
		if (this.db == null) {
			System.err.println( "No connection to Act server. Tried local and pathway.berkeley.edu." );
			return;
		}

		ActData.Act = new Network("Act");
		ActData.ActRxns = new Network("Act Rxns");
		ActData.ActTree = new Network("Act Tree");
		
		// the following take time, so will be done in init();
		ActData.allrxnids = null;
		ActData.cofactors = null;
		ActData.natives = null;
		ActData.chemMetadata = null;
		ActData.chemInchis = null;
		ActData.chemMetadataText = null;
		ActData.chemToxicity = null;
		ActData.chem_ids = null; 
		ActData.markedReachable = null;
		
		ActLayout._hostOrganismIDs = new Long[ActLayout._hostOrganisms.length];
		for (int i = 0; i<ActLayout._hostOrganisms.length; i++) {
			ActLayout._hostOrganismIDs[i] = this.db.getOrganismId(ActLayout._hostOrganisms[i]);
		}
		
		total = -1;
		loaded = -1;
	}

	private List<Long> getCofactors() {
		List<Chemical> cs = this.db.getCofactorChemicals();
		List<Long> cids = new ArrayList<Long>();
		for (Chemical c : cs) 
			cids.add(c.getUuid());
		return cids;
	}

	private List<Long> getAllIDsSorted() {
		List<Long> allids = this.db.getAllReactionUUIDs();
		Collections.sort(allids);
		return allids;
	}

	private List<Chemical> getNatives() {
		return this.db.getNativeMetaboliteChems();
	}
		
	private HashMap<Long, Chemical> getMarkedReachables() {
		return this.db.getManualMarkedReachables();
	}
	
	private HashMap<Long, Chemical> getChemicals(TaskMonitor tm) {
		HashMap<Long, Chemical> c = new HashMap<Long, Chemical>();
		int N = ActData.chem_ids.size();
		int i = 0;
		for (Long id : ActData.chem_ids) {
			c.put(id, this.db.getChemicalFromChemicalUUID(id));
			tm.setStatus("Fetching chemical info: " + (i++) + "/" + N);
			tm.setPercentCompleted((int)(100 * ((double)i/N)));
		}
		return c;
	}

	private List<Reaction> getRxns(long low, long high) {
		List<Reaction> rxns = new ArrayList<Reaction>();
		DBIterator iterator = this.db.getIteratorOverReactions(low, high, true); // notimeout=true
		Reaction r;
		// since we are iterating until the end, the getNextReaction call will close the DB cursor...
		while ((r = this.db.getNextReaction(iterator)) != null) {
            // Long luuid = (long) r.getUUID();
            rxns.add(r);
		}
		return rxns;
	}

	public static HashMap<Reaction, Set<Edge>> addEdgesToNw(List<Reaction> rxns, String edgetype) {
		ActData.chem_ids.addAll(ActData.cofactors);
		for (Chemical n : ActData.natives) {
			ActData.chem_ids.add(n.getUuid());
		}
		
		HashMap<Reaction, Set<Edge>> edges = new HashMap<Reaction, Set<Edge>>();
		// add to act network
		for (Reaction rxn : rxns) {
			Long[] substrates = rxn.getSubstrates();
			Long[] products = rxn.getProducts();
			HashSet<Edge> rxn_edges = new HashSet<Edge>();
			for (long s : substrates) {
				ActData.chem_ids.add(s);
				for (long p : products)
					ActData.chem_ids.add(p);
			}
			for (long s : substrates) {
				if (isCofactor(s))
					continue;
				Node sub = Node.get(s + "", true);
				ActData.chemsInAct.put(s, sub);
				ActData.Act.addNode(sub);
				for (long p : products) {
					if (isCofactor(p))
						continue;
					Node prd = Node.get(p + "", true);
					ActData.Act.addNode(prd);
					ActData.chemsInAct.put(p, prd);

					Edge r = Edge.get(sub, prd, "Semantics.INTERACTION", "in_rxn", true);
					ActData.Act.addEdge(r);
					ActData.rxnsInAct.put(new P<Long, Long>(s, p), r);
					rxn_edges.add(r);
				}
			}
			ActData.rxnsEdgesInAct.put(rxn, rxn_edges);
			for (Edge e : rxn_edges)
				ActData.rxnEdgeToRxnInAct.put(e, rxn);
			annotateRxnEdges(rxn, rxn_edges, edgetype);
			edges.put(rxn, rxn_edges);
		}
		
		// add to act reaction network
		for (Reaction rxn : rxns) {
			long rxnid = rxn.getUUID();
			Node rxn_node = Node.get(rxnid + "_r", true);
			ActData.rxnNodesInActRxns.put(rxnid, rxn_node);
			ActData.ActRxns.addNode(rxn_node);
			Node.setAttribute(rxn_node.getIdentifier(), "isRxn", true);
			Node.setAttribute(rxn_node.getIdentifier(), "node.fillColor", "0,255,0");
			Node.setAttribute(rxn_node.getIdentifier(), "node.size", "10");
			
			Long[] substrates = rxn.getSubstrates();
			Long[] products = rxn.getProducts();
			for (long s : substrates) {
				if (isCofactor(s))
					continue;
				Node sub = Node.get(s + "_c", true);
				ActData.chemsInActRxns.put(s, sub);
				ActData.ActRxns.addNode(sub);

				Edge r = Edge.get(sub, rxn_node, "Semantics.INTERACTION", "substrate", true);
				ActData.ActRxns.addEdge(r);
			}
			for (long p : products) {
				if (isCofactor(p))
					continue;
				Node prd = Node.get(p + "_c", true);
				ActData.chemsInActRxns.put(p, prd);
				ActData.ActRxns.addNode(prd);

				Edge r = Edge.get(rxn_node, prd, "Semantics.INTERACTION", "product", true);
				ActData.ActRxns.addEdge(r);
			}
			
			// add to internal copy of network
			HashSet<Long> incomingCofactors = new HashSet<Long>();
			HashSet<Long> incoming = new HashSet<Long>();
			for (Long s : substrates) if (isCofactor(s)) incomingCofactors.add(s); else incoming.add(s);
			ActData.rxnSubstrates.put(rxnid, incoming);
			ActData.rxnSubstratesCofactors.put(rxnid, incomingCofactors);
			HashSet<Long> outgoingCofactors = new HashSet<Long>();
			HashSet<Long> outgoing = new HashSet<Long>();
			for (Long p : products) if (isCofactor(p)) outgoingCofactors.add(p); else outgoing.add(p);
			ActData.rxnProducts.put(rxnid, outgoing);
			ActData.rxnProductsCofactors.put(rxnid, outgoingCofactors);
			ActData.rxnOrganisms.put(rxnid, new HashSet<Long>(Arrays.asList(rxn.getOrganismIDs())));
			
			for (Long s : incoming) {
				if (!ActData.rxnsThatConsumeChem.containsKey(s))
					ActData.rxnsThatConsumeChem.put(s, new HashSet<Long>());
				ActData.rxnsThatConsumeChem.get(s).add(rxnid);
			}
			for (Long p : outgoing) {
				if (!ActData.rxnsThatProduceChem.containsKey(p))
					ActData.rxnsThatProduceChem.put(p, new HashSet<Long>());
				ActData.rxnsThatProduceChem.get(p).add(rxnid);
			}
		}
		
		return edges;
	}
	
    public static void annotateRxnEdges(Reaction rxn, HashSet<Edge> rxn_edges, String type) {
		for (Edge e : rxn_edges) {
			Edge.setAttribute(e.getIdentifier(), "isRxn", true);
			Edge.setAttribute(e.getIdentifier(), "type", type);
			Edge.setAttribute(e.getIdentifier(), "srcRxnID", rxn.getUUID());
			Edge.setAttribute(e.getIdentifier(), "srcRxn", rxn.getReactionName());
			if (rxn.getECNum() != null)
				Edge.setAttribute(e.getIdentifier(), "srcRxnEC", rxn.getECNum());	
			List<Long> orgIDs = Arrays.asList(rxn.getOrganismIDs());
			Edge.setAttribute(e.getIdentifier(), "organisms", orgIDs.toString());
			// annotate "in Escherichia coli" and "in Saccharomyces cerevisiae"
			for (int i = 0; i< ActLayout._hostOrganisms.length; i++) {
				Edge.setAttribute(e.getIdentifier(), "in " + ActLayout._hostOrganisms[i], 
						orgIDs.contains(ActLayout._hostOrganismIDs[i]));
			}
		}	
	}

	public static boolean isCofactor(long m) {
		return ActData.cofactors.contains(m);
	}

	@Override
	public double percentDone() {
		return 100.0 * ((double)this.loaded / this.total);
	}

	@Override
	public void doMoreWork() {
		long low = 0, high = 0;
		low = ActData.allrxnids.get(this.loaded);
		if (this.loaded + step - 1 >= ActData.allrxnids.size()) {
			high = ActData.allrxnids.get(ActData.allrxnids.size() - 1);
			this.loaded = ActData.allrxnids.size();
		} else {
			high = ActData.allrxnids.get(this.loaded + step - 1); // the high range is inclusive
			this.loaded += step;
		}
		List<Reaction> rxns = getRxns(low, high);
		addEdgesToNw(rxns, "brenda");
	}

	@Override
	public void init() {
		ActData.allrxnids = getAllIDsSorted();
		total = ActData.allrxnids.size();
		loaded = 0;
		ActData.cofactors = getCofactors();
		ActData.natives = getNatives();
		ActData.markedReachable = getMarkedReachables();
		ActData.chem_ids = new HashSet<Long>();
		ActData.chemsInAct = new HashMap<Long, Node>();
		ActData.rxnsInAct = new HashMap<P<Long, Long>, Edge>();
		ActData.rxnsEdgesInAct = new HashMap<Reaction, Set<Edge>>();
		ActData.rxnEdgeToRxnInAct = new HashMap<Edge, Reaction>();
		ActData.chemsInActRxns = new HashMap<Long, Node>();
		ActData.rxnNodesInActRxns = new HashMap<Long, Node>();
		ActData.rxnSubstrates = new HashMap<Long, Set<Long>>();
		ActData.rxnsThatConsumeChem = new HashMap<Long, Set<Long>>();
		ActData.rxnsThatProduceChem = new HashMap<Long, Set<Long>>();
		ActData.rxnProducts = new HashMap<Long, Set<Long>>();
		ActData.rxnOrganisms = new HashMap<Long, Set<Long>>();
		ActData.rxnSubstratesCofactors = new HashMap<Long, Set<Long>>();
		ActData.rxnProductsCofactors = new HashMap<Long, Set<Long>>();
		ActData.roPredictedRxn = new HashMap<Long, Reaction>();
	}
	
	@Override
	public void finalize(TaskMonitor tm) {
		if (loadChemMetadata) {
			ActData.chemMetadata = getChemicals(tm); 
			ActData.chemInchis = extractInchis(ActData.chemMetadata);
			ActData.chemMetadataText = new HashMap<Long, String>();
			ActData.chemToxicity = new HashMap<Long, Set<Integer>>();
			processChemicalText(ActData.chemMetadataText, ActData.chemToxicity);
			tm.setStatus("Assigning chemical attributes to nodes");
			setChemicalAttributes(tm);
		} else {
			tm.setStatus("Tagging natives");
			setNativeAttributes(tm);
		}

		tm.setStatus("Creating Simplified Act Tree Network");
		new CreateActTree();
	}
	
	private HashMap<String, Long> extractInchis(HashMap<Long, Chemical> allData) {
		HashMap<String, Long> inchis = new HashMap<String, Long>();
		for (Long id : allData.keySet()) {
			inchis.put(allData.get(id).getInChI(), id);
		}
		return inchis;
	}

	private void processChemicalText(HashMap<Long, String> txt, HashMap<Long, Set<Integer>> tox) {
		for (Long cID : ActData.chemMetadata.keySet()) {
			Chemical c = ActData.chemMetadata.get(cID);
			txt.put(cID, MongoDB.chemicalAsString(c, cID));
			
			String[] xpath = { "metadata", "toxicity" };
			Object o = c.getRef(REFS.DRUGBANK, xpath);
			if (o == null || !(o instanceof String))
				continue;
			// System.out.format("[%d] Annotation = %s\n", cID, o);
			Set<Integer> ld50s = extractLD50vals((String)o);
			// System.out.format("[%d] Extracted LD50's = %s\n", cID, ld50s);
			ActData.chemToxicity.put(cID, ld50s);
		}
	}

	private Set<Integer> extractLD50vals(String annotation) {
		// an example of what we want to process is: "Oral, mouse: LD50 = 338 mg/kg; Oral, rat: LD50 = 1944 mg/kg"
		// a second example of what to process is:   "Acute oral toxicity (LD<sub>50</sub>) in rats is 264 mg/kg."
		// a third example of what to process is :   "Oral mouse and rat LD<sub>50</sub> are 338 mg/kg and 425 mg/kg respectively"
		// a fourth example of what to process is:   "oral LD<sub>50</sub>s were 1,100-1,550 mg/kg; 1,450 mg/kg; and 1,490 mg/kg; respectively"
		
		// most of time it is specified as mg/kg but rarely we also have g/kg: "Oral LD50 in rat: >5 g/kg"
		Set<Integer> ld50s = new HashSet<Integer>();
		int idx = 0;
		int len = annotation.length();
		String[] locator = { "LD50", "LD<sub>50</sub>" };
		int[] locator_len = { locator[0].length(), locator[1].length() };
		int delta = 60; // 60 chars +- the locator
		for (int l = 0; l<locator.length; l++) {
			while ((idx = annotation.indexOf(locator[l], idx)) != -1) {
				// look around a few tokens to find numbers that we can use...
				int low = idx - delta < 0 ? 0 : idx - delta;
				int high = idx + delta > len ? len : idx + delta;
				String sub = annotation.substring(low, idx) + annotation.substring(idx + locator_len[l], high);
				Scanner scan = new Scanner(sub).useDelimiter("[^0-9,]+");
				while (scan.hasNext()) {
					String scanned = scan.next().replaceAll(",", "");
					// System.out.println("Scanned = " + scanned);
					try { ld50s.add(Integer.parseInt(scanned)); } 
					catch (NumberFormatException e) { /* System.out.println("NaN: " + scanned); */ } 
				}
				
				idx++; // so that we skip the occurrence that we just added
			}
		}
		return ld50s;
	}

	private void setChemicalAttributes(TaskMonitor tm) {
		int N = ActData.chemsInAct.size();
		int i = 0;
		for (Long id : ActData.chemsInAct.keySet()) {
			tm.setStatus("Setting attr: " + (i++) + "/" + N);
			tm.setPercentCompleted((int)(100 * ((double)i/N)));
			
			Chemical c = ActData.chemMetadata.get(id);
			String txt = ActData.chemMetadataText.get(id);
			Set<Integer> tox = ActData.chemToxicity.get(id);
			String n1 = ActData.chemsInAct.get(id).getIdentifier();
			String n2 = ActData.chemsInActRxns.get(id).getIdentifier();
			int fanout = ActData.rxnsThatConsumeChem.containsKey(id) ? ActData.rxnsThatConsumeChem.get(id).size() : -1;
			int fanin = ActData.rxnsThatProduceChem.containsKey(id) ? ActData.rxnsThatProduceChem.get(id).size() : -1;

			setMetadata(n1, tox, c, txt, fanout, fanin);
			setMetadata(n2, tox, c, txt, fanout, fanin);
		}
	}

	private void setMetadata(String n, Set<Integer> tox, Chemical c, String fulltxt, int fanout, int fanin) {
		Node.setAttribute(n, "isNative", c.isNative());	
		Node.setAttribute(n, "fanout", fanout);	
		Node.setAttribute(n, "fanin", fanin);	
		if (c.getCanon() != null) Node.setAttribute(n, "canonical", c.getCanon());
		if (c.getInChI() != null) Node.setAttribute(n, "InChI", c.getInChI());
		if (c.getSmiles() != null) Node.setAttribute(n, "SMILES", c.getSmiles());
		if (c.getShortestName() != null) Node.setAttribute(n, "Name", c.getShortestName());
		if (c.getBrendaNames() != null && c.getSynonyms() != null) Node.setAttribute(n, "Synonyms", c.getBrendaNames().toString() + c.getSynonyms().toString());
		setXrefs(n, c);
		if (fulltxt != null) Node.setAttribute(n, "fulltxt", fulltxt);
		if (tox != null && tox.size() > 0) {
			Node.setAttribute(n, "toxicity_all", tox.toString());
			int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
			for (int i : tox) { 
				max = max < i ? i : max;
				min = min > i ? i : min;
			}
			Node.setAttribute(n, "toxicity_min", min);
			Node.setAttribute(n, "toxicity_max", max);
		}
	}

	private void setXrefs(String node, Chemical c) {
		for (REFS typ : Chemical.REFS.values()) {
			if (c.getRef(typ) != null) {
				Double valuation = c.getRefMetric(typ);
				Node.setAttribute(node, typ.name(), c.getRef(typ).toString());
				Node.setAttribute(node, "metric" + typ.name(), valuation == null ? -999999999.0 : valuation);
				Node.setAttribute(node, "log10metric" + typ.name(), valuation == null ? -99.0 : Math.log10(valuation));
				Node.setAttribute(node, "has" + typ.name(), true);
			} else {
				Node.setAttribute(node, "has" + typ.name(), false);
			}
		}
	}

	private void setNativeAttributes(TaskMonitor tm) {
		int N = ActData.natives.size();
		int i = 0;
		for (Chemical c : ActData.natives) {
			tm.setStatus("Setting attr: " + (i++) + "/" + N);
			tm.setPercentCompleted((int)(100 * ((double)i/N)));
			if (!ActData.chemsInAct.containsKey(c.getUuid()))
				continue; // in cases where the native is also a cofactor, it would not have a node.
			
			// set the attributes in the act network
			String n1 = ActData.chemsInAct.get(c.getUuid()).getIdentifier();
			Node.setAttribute(n1, "isNative", c.isNative());

			// set the attributes in the act network
			String n2 = ActData.chemsInActRxns.get(c.getUuid()).getIdentifier();
			Node.setAttribute(n2, "isNative", c.isNative());
		}
	}
}
