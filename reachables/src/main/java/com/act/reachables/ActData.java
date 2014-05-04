package com.act.reachables;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

public class ActData {
	static ConditionalReachable _LastReachabilityComputation = null;
	static Network Act, ActRxns, ActTree;
	
	static List<Long> allrxnids;
	static Set<Long> chem_ids;
	static List<Long> cofactors;
	static List<Chemical> natives;
	static HashMap<Long, Chemical> markedReachable;
	static HashMap<Long, Chemical> chemMetadata;
	static HashMap<String, Long> chemInchis;
	static HashMap<Long, String> chemMetadataText;
	static HashMap<Long, Set<Integer>> chemToxicity;
	static HashMap<Long, Node> chemsInAct;
	static HashMap<P<Long, Long>, Edge> rxnsInAct;
	static HashMap<Reaction, Set<Edge>> rxnsEdgesInAct;
	static HashMap<Edge, Reaction> rxnEdgeToRxnInAct;
	static HashMap<Long, Node> chemsInActRxns;
	static HashMap<Long, Node> rxnNodesInActRxns;
	static HashMap<Long, Set<Long>> rxnSubstrates;
	static HashMap<Long, Set<Long>> rxnProducts;
	static HashMap<Long, Set<Long>> rxnOrganisms;
	static HashMap<Long, Set<Long>> rxnSubstratesCofactors;
	static HashMap<Long, Set<Long>> rxnProductsCofactors;
	static HashMap<Long, Set<Long>> rxnsThatConsumeChem;
	static HashMap<Long, Set<Long>> rxnsThatProduceChem;
	
	static HashMap<Long, Reaction> roPredictedRxn;
}

