package com.act.reachables;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

public class ActData {
	static ConditionalReachable _LastReachabilityComputation = null;
	static Network Act; 
  static Network ActTree;
	
	static List<Long> allrxnids;                            // sorted list of all reaction uuids (from db.actfamilies)
	static Set<Long> chem_ids;                              // every chemid referenced in cofactors, natives, or any reaction in DB
	static List<Long> cofactors;                            // chemicals with isCofactor : true in DB
	static List<Chemical> natives;                          // chemicals marked as isNative : true in DB
	static List<Long> metaCycBigMolsOrRgrp;                 // chemicals whose inchi matches db.chemicals.find({InChI:/FAKE/})
	static HashMap<Long, Chemical> markedReachable;         // manually marked reachable in DB, cases where the there 
                                                          // no direct path from 192 universals, but known it is in all cells

	static HashMap<String, List<Long>> chemicalsWithUserField;  // if a user asks us to output an artificial 
                                                              // subset of chemicals that have certain fields,
                                                              // e.g., xref.CHEBI, xref.DEA etc.
  static Set<Long> chemicalsWithUserField_treeOrganic;    // in the final tree; these nodes were reachable organically
  static Set<Long> chemicalsWithUserField_treeArtificial; // in the final tree; these nodes were added artificially as 
                                                          // they were not organically reachable

	static HashMap<Long, String> chemId2Inchis;             // map chemid -> inchi
	static HashMap<String, Long> chemInchis;                // reverse index of inchi -> chemid
	static HashMap<Long, Set<Integer>> chemToxicity;        // If the chemical has xref.DRUGBANK.metadata.toxicity with LD50 [1]
	static HashMap<Long, Node> chemsInAct;                  // map of chemicals seen in any rxn -> its node object in network
	static HashMap<P<Long, Long>, Edge> rxnsInAct;          // map of rxns (exploded to all pairs bw sub x prod) to edge in network
	static HashMap<Long, Set<Long>> rxnSubstrates;          // rxnid -> non-cofactor substrates
	static HashMap<Long, Set<Long>> rxnSubstratesCofactors; // rxnid -> cofactor substrates
	static HashMap<Long, Set<Long>> rxnProducts;            // rxnid -> non-cofactor products
	static HashMap<Long, Set<Long>> rxnProductsCofactors;   // rxnid -> cofactor products
	static HashMap<Long, Set<Long>> rxnOrganisms;           // rxnid -> set of organism ids associated with rxn
	static HashMap<Long, Set<Long>> rxnsThatConsumeChem;    // non-cofactor chemicals -> rxns that have them as substrates
	static HashMap<Long, Set<Long>> rxnsThatProduceChem;    // non-cofactor chemicals -> rxns that have them as products
	static HashMap<Long, String> rxnEasyDesc;               // the reaction's readable string desc
	static HashMap<Long, Reaction.RxnDataSource> rxnDataSource; // the reaction's provenance 
	static HashMap<Long, Set<Long>> rxnSeqRefs;            // the protein sequences catalyzing rxn 
	static HashMap<Long, Set<Long>> rxnOrgRefs;            // the organisms those sequences are found in
}

/*
   [1] Chemical toxicity fields are not that populated. But it is pure text with values such as "Oral, rat LD50: 1890 mg/kg "
        db.chemicals.find({"xref.DRUGBANK.metadata.toxicity" : {$exists: true}}, {"xref.DRUGBANK.metadata.toxicity": 1})
        We process the text by looking for LD50 and 60 characters in its vicinity and then attempting to parse the units and val
 */
