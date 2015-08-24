package com.act.reachables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
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
	private int step = 100;
	private int loaded, total;
  boolean SET_METADATA_ON_NW_NODES = false;
  private List<String> fieldSetForChemicals;
  Set<String> optional_universal_inchis;

  public static Network getReachablesTree(Set<String> natives, boolean restrictToSeq, String[] extra_chem_fields) {
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = restrictToSeq;
    
    // init loader
    LoadAct act = new LoadAct(natives);

    // set fields to include in the tree even if they are not reachables
    if (extra_chem_fields != null)
      for (String f : extra_chem_fields)
        act.setFieldForExtraChemicals(f);

    // load data from db; and compute reachables tree
    act.run(); 

    return ActData.ActTree;
  }

  public static Network getReachablesTree(Set<String> natives, boolean restrictToSeq) {
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = restrictToSeq;
    
    // init loader
    LoadAct act = new LoadAct(natives);

    // load data from db; and compute reachables tree
    act.run(); 

    return ActData.ActTree;
  }

  public static Network getReachablesTree(Set<String> natives) {
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = true;
    
    // init loader
    LoadAct act = new LoadAct(natives);

    // load data from db; and compute reachables tree
    act.run(); 

    return ActData.ActTree;
  }

  public static String toInChI(Long id) {
    return ActData.chemId2Inchis.get(id);
  }
			
	public MongoDB db;
	private LoadAct(Set<String> optional_universal_inchis) {
    this.optional_universal_inchis = optional_universal_inchis;
    this.fieldSetForChemicals = new ArrayList<String>();
    this.db = new MongoDB("localhost", 27017, "actv01");
		
		if (this.db == null) {
			System.err.println( "No connection to Act MongoDB." );
			return;
		}

		ActData.Act = new Network("Act");
		ActData.ActTree = new Network("Act Tree");

		// the following take time, so will be done in init();
		ActData.allrxnids = null;
		ActData.cofactors = null;
		ActData.natives = null;
		ActData.chemInchis = null;
		ActData.chemId2Inchis = null;
		ActData.chemToxicity = null;
		ActData.chem_ids = null; 
		ActData.markedReachable = null;
		
		GlobalParams._hostOrganismIDs = new Long[GlobalParams._hostOrganisms.length];
		for (int i = 0; i<GlobalParams._hostOrganisms.length; i++) {
			GlobalParams._hostOrganismIDs[i] = this.db.getOrganismId(GlobalParams._hostOrganisms[i]);
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

	private List<Long> getNatives() {
		List<Chemical> cs = this.db.getNativeMetaboliteChems();
		List<Long> cids = new ArrayList<Long>();
		for (Chemical c : cs) 
			cids.add(c.getUuid());
		return cids;
	}

  public void setFieldForExtraChemicals(String f) {
    this.fieldSetForChemicals.add(f);
  }

  private HashMap<String, List<Long>> getChemicalWithUserSpecFields() {
    HashMap<String, List<Long>> specials = new HashMap<String, List<Long>>();

    for (String f : this.fieldSetForChemicals) {
      List<Chemical> cs = this.db.getChemicalsThatHaveField(f);
      specials.put(f, extractChemicalIDs(cs));
    }
    return specials;
  }
		
	private List<Long> getMetaCycBigMolsOrRgrp() {
		List<Chemical> cs = this.db.getFAKEInChIChems();
    return extractChemicalIDs(cs);
	}

  private List<Long> extractChemicalIDs(List<Chemical> cs) {
		List<Long> cids = new ArrayList<Long>();
		for (Chemical c : cs) 
			cids.add(c.getUuid());
		return cids;
  }
		
	private HashMap<Long, Chemical> getMarkedReachables() {
		return this.db.getManualMarkedReachables();
	}
	
  private void addReactionsToNetwork() {
		List<Reaction> rxns = new ArrayList<Reaction>();
		DBIterator iterator = this.db.getIteratorOverReactions(true);
		Reaction r;
    Map<Reaction.RxnDataSource, Integer> counts = new HashMap<>();
    for (Reaction.RxnDataSource src : Reaction.RxnDataSource.values())
      counts.put(src, 0);
		// since we are iterating until the end, 
    // the getNextReaction call will close the DB cursor...

		while ((r = this.db.getNextReaction(iterator)) != null) {
      // this rxn comes from a datasource, METACYC, BRENDA or KEGG.
      // ensure the configuration tells us to include this datasource...
      Reaction.RxnDataSource src = r.getDataSource();
      counts.put(src, counts.get(src) + 1);
      System.out.format("Pulled: %s\r", counts.toString());
      if (GlobalParams._ReachablesIncludeRxnSources.contains(src))
        rxns.add(r);

      // does the real adding to Network
		  addToNw(r);
		}
    System.out.println();

    System.out.format("Rxn aggregate into %d classes.\n", ActData.rxnClasses.size());
  }

	public static void addToNw(Reaction rxn) {
		// add to act network
		long rxnid = rxn.getUUID();
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
			ActData.Act.addNode(sub, s);
			for (long p : products) {
				if (isCofactor(p))
					continue;
				Node prd = Node.get(p + "", true);
				ActData.Act.addNode(prd, p);
				ActData.chemsInAct.put(p, prd);

				Edge r = Edge.get(sub, prd, "Semantics.INTERACTION", "in_rxn", true);
				ActData.Act.addEdge(r);
				ActData.rxnsInAct.put(new P<Long, Long>(s, p), r);
				rxn_edges.add(r);
			}
		}
    boolean ANNOTATE_RXN_EDGES = false;
    if (ANNOTATE_RXN_EDGES) 
		  annotateRxnEdges(rxn, rxn_edges);

    // add to internal copy of network
    ActData.rxnEasyDesc.put(rxnid, rxn.getReactionName());
    ActData.rxnDataSource.put(rxnid, rxn.getDataSource());
    ActData.rxnHasSeq.put(rxnid, rxn.hasProteinSeq());

    // add to rxnSubstrates, and rxnSubstratesCofactors
    HashSet<Long> incomingCofactors = new HashSet<Long>();
    HashSet<Long> incoming = new HashSet<Long>();
    for (Long s : substrates) 
      if (isCofactor(s)) 
        incomingCofactors.add(s); 
      else 
        incoming.add(s);
    ActData.rxnSubstrates.put(rxnid, incoming);
    ActData.rxnSubstratesCofactors.put(rxnid, incomingCofactors);

    // add to rxnProducts, and rxnProductsCofactors
    HashSet<Long> outgoingCofactors = new HashSet<Long>();
    HashSet<Long> outgoing = new HashSet<Long>();
    for (Long p : products) 
      if (isCofactor(p)) 
        outgoingCofactors.add(p); 
      else 
        outgoing.add(p);
    ActData.rxnProducts.put(rxnid, outgoing);
    ActData.rxnProductsCofactors.put(rxnid, outgoingCofactors);
    
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

    // now see if this is a new "class" of rxn, 
    // we only use classes to expand reactions
    P<Set<Long>, Set<Long>> rxnClass = new P<>(incoming, outgoing);
    if (!ActData.rxnClasses.contains(rxnClass)) {
      // the first reaction that shows up in this class, get to
      // represent the entire class. So we install it in the 
      // datasets mirroring the non-class structures...

      ActData.rxnClassesSubstrates.put(rxnid, incoming);
      ActData.rxnClassesProducts.put(rxnid, outgoing);
      ActData.rxnClasses.add(rxnClass);

      for (Long s : incoming) {
        if (!ActData.rxnClassesThatConsumeChem.containsKey(s))
          ActData.rxnClassesThatConsumeChem.put(s, new HashSet<Long>());
        ActData.rxnClassesThatConsumeChem.get(s).add(rxnid);
      }
      for (Long p : outgoing) {
        if (!ActData.rxnClassesThatProduceChem.containsKey(p))
          ActData.rxnClassesThatProduceChem.put(p, new HashSet<Long>());
        ActData.rxnClassesThatProduceChem.get(p).add(rxnid);
      }
    }

	}

  public static void annotateRxnEdges(Reaction rxn, HashSet<Edge> rxn_edges) {
		for (Edge e : rxn_edges) {
			Edge.setAttribute(e, "isRxn", true);
			Edge.setAttribute(e, "datasource", rxn.getDataSource());
			Edge.setAttribute(e, "srcRxnID", rxn.getUUID());
			Edge.setAttribute(e, "srcRxn", rxn.getReactionName());
			if (rxn.getECNum() != null)
				Edge.setAttribute(e, "srcRxnEC", rxn.getECNum());	
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
    System.out.format("Pulling %d reactions from MongoDB:\n", this.total); 
    addReactionsToNetwork();
    this.loaded = this.total;
	}

  private static void debug(String msg) {
    String loc = "com.act.reachables.LoadAct";
    System.err.println(loc + ": " + msg);
  }
	
	@Override
	public void init() {
		ActData.allrxnids = getAllIDsSorted();
		total = ActData.allrxnids.size();
		loaded = 0;
		ActData.cofactors = getCofactors();
		ActData.natives = getNatives();
    ActData.chemicalsWithUserField = getChemicalWithUserSpecFields();
    ActData.chemicalsWithUserField_treeOrganic = new HashSet<Long>();
    ActData.chemicalsWithUserField_treeArtificial = new HashSet<Long>();
    ActData.metaCycBigMolsOrRgrp = getMetaCycBigMolsOrRgrp();
		ActData.markedReachable = getMarkedReachables();
		ActData.chem_ids = new HashSet<Long>();
		ActData.chemsInAct = new HashMap<Long, Node>();
		ActData.rxnsInAct = new HashMap<P<Long, Long>, Edge>();
		ActData.rxnSubstrates = new HashMap<Long, Set<Long>>();
		ActData.rxnsThatConsumeChem = new HashMap<Long, Set<Long>>();
		ActData.rxnsThatProduceChem = new HashMap<Long, Set<Long>>();
		ActData.rxnProducts = new HashMap<Long, Set<Long>>();
		ActData.rxnOrganisms = new HashMap<Long, Set<Long>>();
		ActData.rxnSubstratesCofactors = new HashMap<Long, Set<Long>>();
		ActData.rxnProductsCofactors = new HashMap<Long, Set<Long>>();
    ActData.rxnEasyDesc = new HashMap<Long, String>();
    ActData.rxnDataSource = new HashMap<Long, Reaction.RxnDataSource>();
    ActData.rxnHasSeq = new HashMap<Long, Boolean>();

		ActData.rxnClassesSubstrates = new HashMap<Long, Set<Long>>();
		ActData.rxnClassesProducts = new HashMap<Long, Set<Long>>();
    ActData.rxnClasses = new HashSet<P<Set<Long>, Set<Long>>>();
		ActData.rxnClassesThatConsumeChem = new HashMap<Long, Set<Long>>();
		ActData.rxnClassesThatProduceChem = new HashMap<Long, Set<Long>>();

		ActData.chem_ids.addAll(ActData.cofactors);
    ActData.chem_ids.addAll(ActData.natives);
    
    for (String f : ActData.chemicalsWithUserField.keySet()) 
        ActData.chem_ids.addAll(ActData.chemicalsWithUserField.get(f));

	}
	
	@Override
	public void finalize(TaskMonitor tm) {
			ActData.chemToxicity = new HashMap<Long, Set<Integer>>();
      ActData.chemInchis = new HashMap<String, Long>();
      ActData.chemId2Inchis = new HashMap<Long, String>();
      processChemicals();
		  // TODO: do we, or dont we need to set natives?
      // setNativeAttributes();

      debug("ComputeReachablesTree.. starting");
      Set<Long> given_natives = universal_natives_ids();

      // computes reachables tree and writes it into ActData.ActTree
		  new ComputeReachablesTree(this.db, given_natives);
	}

  private Set<Long> universal_natives_ids() {
    if (this.optional_universal_inchis == null)
      return null;

    Set<Long> natives_ids = new HashSet<Long>();
    for (String inchi : this.optional_universal_inchis) {

      if (!ActData.chemInchis.containsKey(inchi)) {
        System.out.println("LoadAct/ComputeReachablesTree: SEVERE WARNING: Starting native not in db.");
        System.out.println("LoadAct/ComputeReachablesTree:               : InChI = " + inchi);
        continue;
      }

      natives_ids.add(ActData.chemInchis.get(inchi));
    }

    return natives_ids;
  }

  private void processChemicals() {
		int N = ActData.chem_ids.size();
		int count = 0;
    debug("Extracting metadata from chemicals.");
		for (Long id : ActData.chem_ids) {
      System.out.format("\t processChemicals: %d\r", count++);
      Chemical c = this.db.getChemicalFromChemicalUUID(id);
      ActData.chemInchis.put(c.getInChI(), id);
      ActData.chemId2Inchis.put(id, c.getInChI());

      if (SET_METADATA_ON_NW_NODES) {
			  String[] xpath = { "metadata", "toxicity" };
			  Object o = c.getRef(REFS.DRUGBANK, xpath);
			  if (o == null || !(o instanceof String))
			  	continue;
			  Set<Integer> ld50s = extractLD50vals((String)o);
			  ActData.chemToxicity.put(id, ld50s);

        // set chemical attributes
        String txt = null; // D MongoDB.chemicalAsString(c, id);
			  Set<Integer> tox = ActData.chemToxicity.get(id);
			  String n1 = ActData.chemsInAct.get(id).getIdentifier();
			  int fanout = ActData.rxnsThatConsumeChem.containsKey(id) ? ActData.rxnsThatConsumeChem.get(id).size() : -1;
			  int fanin = ActData.rxnsThatProduceChem.containsKey(id) ? ActData.rxnsThatProduceChem.get(id).size() : -1;

			  setMetadata(n1, tox, c, txt, fanout, fanin);

      }
		}
    System.out.println();
  }

	private void setNativeAttributes() {
		int N = ActData.natives.size();
		int i = 0;
		for (Long cid : ActData.natives) {
			if (!ActData.chemsInAct.containsKey(cid))
				continue; // in cases where the native is also a cofactor, it would not have a node.
			
			// set the attributes in the act network
			String n1 = ActData.chemsInAct.get(cid).getIdentifier();
			Node.setAttribute(n1, "isNative", true);

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
					try { ld50s.add(Integer.parseInt(scanned)); } 
					catch (NumberFormatException e) { /* System.out.println("NaN: " + scanned); */ } 
				}
				
				idx++; // so that we skip the occurrence that we just added
			}
		}
		return ld50s;
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

}
