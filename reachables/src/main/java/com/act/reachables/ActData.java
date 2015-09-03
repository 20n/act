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
	static Set<Long> chemsReferencedInRxns;                 // every chemid referenced in cofactors, natives, or any reaction in DB
	static Set<Long> cofactors;                             // chemicals with isCofactor : true in DB
	static Set<Long> natives;                               // chemicals marked as isNative : true in DB
	static List<Long> metaCycBigMolsOrRgrp;                 // chemicals whose inchi matches db.chemicals.find({InChI:/FAKE/})

	static HashMap<String, List<Long>> chemicalsWithUserField;  // if a user asks us to output an artificial 
                                                              // subset of chemicals that have certain fields,
                                                              // e.g., xref.CHEBI, xref.DEA etc.
  static Set<Long> chemicalsWithUserField_treeOrganic;    // in the final tree; these nodes were reachable organically
  static Set<Long> chemicalsWithUserField_treeArtificial; // in the final tree; these nodes were added artificially as 
                                                          // they were not organically reachable

  static HashMap<Long, Boolean> chemIdIsAbstraction;      // the chemicals that have R in inchis and therefore abstractions
	static HashMap<Long, String> chemId2Inchis;             // map chemid -> inchi
	static HashMap<Long, String> chemId2ReadableName;       // map chemid -> name
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
	static HashMap<Long, String> rxnECNumber;               // the reaction's readable string desc
	static HashMap<Long, Reaction.RxnDataSource> rxnDataSource; // the reaction's provenance 
  static HashMap<Long, Boolean> rxnHasSeq;                // do we know an enzyme catalyzing this rxn?


  // The raw dataset comes in with multiple reactions
  // with the same chemistry, i.e., the same substrates
  // and products. Since reactions with the same chemistry
  // will be semantically equivalent in the reachables computation
  // i.e., they will lead to the same expansion, we call them
  // a class, where a class is defined as P(substrate_set, product_set)
  // (see LoadAct.addToNw where we create and use this "class id")
  // 
  // The expansion code picks between raw rxns or classes
  // on the basis of the parameter GlobalParams.USE_RXN_CLASSES
  // 
  // Expansion in WavefrontExpansion.{computeRxnNeeds, productsOf}, 
  // picks either the classes or the raw rxns to expand over.
  //
  // The first three below are used in LoadAct and WavefrontExpansion
  // and the remaining two are for when we are dumping out cascade
  // metadata in scala/reachables.scala

	static HashMap<Long, Set<Long>> rxnClassesSubstrates;   // rxnid -> non-cofactor substrates (representative rxns that form classes)
	static HashMap<Long, Set<Long>> rxnClassesProducts;     // rxnid -> non-cofactor products (representative rxns that form classes)
	static Set<P<Set<Long>, Set<Long>>> rxnClasses;         // set for classes (substrates, products)

	static HashMap<Long, Set<Long>> rxnClassesThatConsumeChem;    // non-cofactor chemicals -> rxns that have them as substrates
	static HashMap<Long, Set<Long>> rxnClassesThatProduceChem;    // non-cofactor chemicals -> rxns that have them as products
}

/*
   [1] Chemical toxicity fields are not that populated. But it is pure text with values such as "Oral, rat LD50: 1890 mg/kg "
        db.chemicals.find({"xref.DRUGBANK.metadata.toxicity" : {$exists: true}}, {"xref.DRUGBANK.metadata.toxicity": 1})
        We process the text by looking for LD50 and 60 characters in its vicinity and then attempting to parse the units and val
 */
