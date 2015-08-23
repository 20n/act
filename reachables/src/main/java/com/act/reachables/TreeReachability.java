package com.act.reachables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.FattyAcidEnablers;
import act.shared.helpers.P;

public class TreeReachability {

	// these are computed once and are finalized thereafter
	Set<Long> cofactors_and_natives;
	Long root = -1L;
	Long rootProxyInLayer1 = -2L;
	
	// everything below changes as part of the reachables expansion
	Set<Long> R; // list of reachable chems
	HashMap<Integer, Set<Long>> R_by_layers;
	HashMap<Integer, Set<Long>> R_by_layers_in_host;
	
	HashMap<Long, Long> R_parent; // chosen parent at layer i-1 for a chem at layer i
	HashMap<Long, Set<Long>> R_owned_children; // final set of children owned by parent (key)
	HashMap<Long, Set<Long>> R_parent_candidates; // list of candidates in layer i-1 that could be parents for layer i chem
	HashMap<Long, List<Long>> rxn_needs; // list of precondition chemicals for each chem
	Set<Long> roots; // under "CreateUnreachableTrees" we also compute conditionally reachable trees rooted at important assumed nodes
	int currentLayer;

  // when computing reachables, we log the sequences 
  // that create new reachables
  Set<Long> seqThatCreatedNewReachable;
  // and the sequences that have enabled substrates, 
  // irrespective of whether they create new or not
  Set<Long> seqWithReachableSubstrates;
	
	// when doing assumed_reachable world, the parents of a node deep in the tree get stolen,
	// and then we have to attach the node directly to the root of the tree. We log those in 
	// this hashmap to make sure that we can annotate those edges in the front-end
	Set<Long> isAncestorAndNotDirectParent;
	
	TreeReachability() {
		this.R = new HashSet<Long>();
		this.R_by_layers = new HashMap<Integer, Set<Long>>();
		this.R_by_layers_in_host = new HashMap<Integer, Set<Long>>();
		this.cofactors_and_natives = new HashSet<Long>();
		this.R_parent = new HashMap<Long, Long>();
		this.R_parent_candidates = new HashMap<Long, Set<Long>>();
		this.R_owned_children = new HashMap<Long, Set<Long>>();
		this.rxn_needs = computeRxnNeeds();
		this.currentLayer = 0;
		this.isAncestorAndNotDirectParent = new HashSet<Long>();
    this.seqWithReachableSubstrates = new HashSet<Long>();
    this.seqThatCreatedNewReachable = new HashSet<Long>();

		this.roots = new HashSet<Long>();
		roots.add(this.root);
		// roots.add(this.rootProxyInLayer1);
	}
	
	public Tree<Long> computeTree(Set<Long> universal_natives) {
		

    if (universal_natives == null) {
		  // init, using some DB information if custom universal_natives are null
		  for (Long c : ActData.cofactors)
		  	addToReachablesAndCofactorNatives(c);
      for (Long c : ActData.natives)
        addToReachablesAndCofactorNatives(c);
		  // for (Chemical n : ActData.natives) 
		  // 	addToReachablesAndCofactorNatives(n.getUuid());
		  if (ActLayout._actTreeIncludeAssumedReachables)
		  	for (Long p : ActData.markedReachable.keySet()) 
		  		addToReachablesAndCofactorNatives(p);
    } else {
      // we are passed in a set of custom universal natives, use those
      for (Long u : universal_natives) {
        addToReachablesAndCofactorNatives(u);

        ActData.natives.add(u);
      }
    }

    System.out.println("Starting computeTree");
    System.out.println("Cofactors and natives = " + this.cofactors_and_natives);
		
		// add the natives and cofactors
		addToLayers(R, 0 /* this.currentLayer */, false /* addToExisting */, false /* isInsideHost */);
		this.currentLayer++;
		updateEnabled(R);
		
		setParentsForCofactorsAndNatives(cofactors_and_natives);
		Set<Long> doNotAssignParentsTo = new HashSet<Long>();
    List<Long> possibleBigMols = ActData.metaCycBigMolsOrRgrp; // those with InChI:/FAKE/ are either big molecules (no parents), or R group containing chemicals. Either, do not complain if we cannot find parents for them.
		
		if (ActLayout._actTreeCreateHostCentricMap) {
			// add all host organism reachables
			int host_layer = 0;
			while (anyEnabledReactions(ActLayout.gethostOrganismID())) {
        System.out.println("Current layeri (inside host expansion): " + this.currentLayer);
				boolean newAdded = pushWaveFront(ActLayout.gethostOrganismID(), host_layer);
				if (newAdded) { // temporary....
					pickParentsForNewReachables(this.currentLayer, host_layer++, doNotAssignParentsTo, possibleBigMols, null /*no assumptions*/);
				}
			}
			this.currentLayer++; // now outside host
		}
			
		// compute layers
		while (anyEnabledReactions(null)) {
      System.out.println("layer = " + this.currentLayer + "; num_reachables = " + this.R.size());
			boolean newAdded = pushWaveFront(null, this.currentLayer);
			pickParentsForNewReachables(this.currentLayer++, -1 /* outside host */, doNotAssignParentsTo, possibleBigMols, null /*no assumptions*/);
		}
		
		if (ActLayout._actTreeCreateUnreachableTrees) {
			List<EnvCond>[] workLists = worklistOfAssumedReachables();
			Set<Long> allReach = new HashSet<Long>();
			allReach.addAll(addUnreachableTrees(workLists[0], this.R)); // handle all singular assumed_reachables 
			allReach.addAll(addUnreachableTrees(workLists[1], allReach)); // handle all tuples of assumed_reachables
			this.R_assumed_reachable = allReach;
		} else {
			this.R_assumed_reachable = new HashSet<Long>();
		}
		
    addNodesThatHaveUserSpecifiedFields();

		Set<Long> still_unreach = new HashSet<Long>(ActData.chem_ids);
    still_unreach.removeAll(this.R);
		still_unreach.removeAll(this.R_assumed_reachable);
		System.out.format("Reachables size: %s\n", this.R.size());
		System.out.format("Assumed reachables size: %s\n", this.R_assumed_reachable.size());
		System.out.format("Still unreachable size: %s\n", still_unreach.size());
		
		return new Tree<Long>(getRoots(), this.R_parent, this.R_owned_children, constructAttributes());
	}

  private void addNodesThatHaveUserSpecifiedFields() {
    Long artificialSubtreeID = -100L;

    for (String f : ActData.chemicalsWithUserField.keySet()) {
      List<Long> molecules = ActData.chemicalsWithUserField.get(f);

      Long artRootID = artificialSubtreeID--; 
      // make this new predicate a child of the root
      this.R_owned_children.get(this.root).add(artRootID);
      this.R_parent.put(artRootID, this.root);

      // add all artifially reachable molecules to layer 2 and parent to layer 1
      this.R_by_layers.get(1).add(artRootID);

      for (Long c : molecules) {
        // if the molecule is already reachable, we should 
        // not add it in the artificial reachable clade
        if (this.R.contains(c)) {
          // log that it was already reached organically
          ActData.chemicalsWithUserField_treeOrganic.add(c);
          continue; 
        }

        // log that it could not be reached organically
        ActData.chemicalsWithUserField_treeArtificial.add(c);

        // set it to be artificially reachable
        this.R.add(c);
        this.R_by_layers.get(2).add(c);

        // set its parent to the artificial predicate node
        this.R_parent.put(c, artRootID);
        if (!this.R_owned_children.containsKey(artRootID))
          this.R_owned_children.put(artRootID, new HashSet<Long>());
        this.R_owned_children.get(artRootID).add(c);
      }
    }

  }

	private void addToReachablesAndCofactorNatives(Long c) {
		this.R.add(c); this.cofactors_and_natives.add(c);
	}

	private List<EnvCond>[] worklistOfAssumedReachables() {
		// read all reactions in rxn_needs, check their "needs" and create a speculation tuple
		// out of those needs. Ensure that you keep a count of the number of times the tuple is
		// seen. Then output the sorted list.
		
		HashMap<EnvCond, Integer> counts = new HashMap<EnvCond, Integer>();
		for (Long r : this.rxn_needs.keySet()) {
			EnvCond tuple = new EnvCond(this.rxn_needs.get(r));
			counts.put(tuple, 1 + (counts.containsKey(tuple) ? counts.get(tuple) : 0));
		}
		
		return sortByCountsAndPrioritizeSingulars(counts);
	}

	private List<EnvCond>[] sortByCountsAndPrioritizeSingulars(HashMap<EnvCond, Integer> counts) {
		List<P<EnvCond, Integer>> sc = new ArrayList<P<EnvCond, Integer>>();
		for (EnvCond se : counts.keySet()) 
			sc.add(new P<EnvCond, Integer>(se, counts.get(se)));
		Collections.sort(sc, new DescendingComparor<EnvCond>());

		List<EnvCond> singles = new ArrayList<EnvCond>();
		List<EnvCond> tuples = new ArrayList<EnvCond>();
		for (P<EnvCond, Integer> e : sc)
			if (e.fst().speculatedChems().size() > 1)
				tuples.add(e.fst());
			else
				singles.add(e.fst());
		List<EnvCond>[] a = new List[] { singles, tuples };
		return a;
	}
	
	public class DescendingComparor<T> implements Comparator<P<T,Integer>> {
		@Override
		public int compare(P<T,Integer> o1, P<T,Integer> o2) {
			return o2.snd().compareTo(o1.snd()); // o2.compare(o1): invert comparison to sort in descending 
		}
		
	}

	private Set<Long> addUnreachableTrees(List<EnvCond> worklist, Set<Long> alreadyReached) {
		System.out.format("Will process %d assumptions\n", worklist.size());
		/*
		// cache the reachables (R, R_by_layers, anything else?)
		// P {
		// 	for each remaining unreachable X
		//    assume_reachable(X), compute new reachables and put the set of them as reachable_under(X)
		//    pop the reachables back to the cached values
		// 	for each remaining unreachable X
		//    find X with the largest reachable_under(X) 
		//    pickParents for X's tree
		//  set reachable for all new acquires 
		// }
		// for each remaining unreachable_tuple Y, do P with X=Y
		*/
		
		// save the current state by doing a deep copy
		saveState(); // saves this.R and this.rxn_needs

		List<P<EnvCondEffect, Integer>> assumptionOutcomes = new ArrayList<P<EnvCondEffect, Integer>>();
		int count = 0; int total_sz = worklist.size(), sz;
		while ((sz = worklist.size()) > 0) {
			if ((10 * sz) / total_sz != (10 * (sz + 1) / total_sz))
				System.out.format("Completed %d0 percent of unreachable computation\r", 10 - (10 * sz) / total_sz);
			if (ActLayout._limitedPreconditionsConsidered != 0 && count++ >= ActLayout._limitedPreconditionsConsidered)
				break; // premature termination, dictated by the front-end.
			
			EnvCond envCond = worklist.remove(0);
			restoreState(); // pop to normal reachability: restore this.R, this.rxn_needs
			
			this.currentLayer++; // System.out.println("=============> this possibly adds an a gap layer between the disconnected trees in the forest. Not critical. Can be removed. ");
			
			int startingLayer = this.currentLayer;
			Set<Long> assumedReachable = envCond.speculatedChems();
			addToLayers(assumedReachable, this.currentLayer++, false /* addToExisting */, false /* isInsideHost */);
			this.R.addAll(assumedReachable);
			updateEnabled(assumedReachable);

			while (anyEnabledReactions(null)) {
				// reads:   rxn_needs
				// updates: rxn_needs (removes preconditions of new reaches)
				// add to:  R, R_by_layer, R_parent_candidates, 
				boolean newAdded = pushWaveFront(null, this.currentLayer++);
			}

			// delta from saved_R, modulo the chems we assumed and added as is
			Set<Long> newReach = deepCopy(this.R); 
			newReach.removeAll(this.R_saved);
			int newReachCount = newReach.size() - envCond.speculatedChems().size();
			EnvCondEffect effect = new EnvCondEffect(envCond, newReachCount, newReach,
					startingLayer, this.currentLayer);
			// System.out.format("[%d] Assuming %s leads to %d new reachables.\n", count, envCond, newReachCount);
			assumptionOutcomes.add(new P<EnvCondEffect, Integer>(effect, newReachCount));
			
			/*
			for(int layer = startingLayer; layer < this.currentLayer; layer++) {
				Set<Long> layerNodes;
				if ((layerNodes = this.R_by_layers.get(layer)) != null)
					System.out.format("Layer %d within [%d-%d] has %d nodes = [%s]\n", layer, startingLayer, this.currentLayer, layerNodes.size(), layerNodes);
			}
			System.out.format("TreeRoot[%s, L%d-L%d] holds[sz=%d] = %s\n", envCond, startingLayer, this.currentLayer, newReachCount, newReach);
			 */
		}

		restoreState();  // pop to normal reachability: restore this.R, this.rxn_needs
		Set<Long> allReach = deepCopy(alreadyReached);
		
    List<Long> possibleBigMols = ActData.metaCycBigMolsOrRgrp; // those with InChI:/FAKE/ are either big molecules (no parents), or R group containing chemicals. Either, do not complain if we cannot find parents for them.
		Collections.sort(assumptionOutcomes, new DescendingComparor<EnvCondEffect>());
		for (int idx = 0; idx < assumptionOutcomes.size(); idx++) {			
			EnvCondEffect newTreeData = assumptionOutcomes.get(idx).fst();
			if (newTreeData.sizeNewReach <= ActLayout._actTreeMinimumSizeOfConditionalTree)
				continue; // assumed nodes that do not enable even a single other are irrelevant
			
			// for now we only handle the case of single node precondition. 
			// Ideally, for tuples, we will create a circle of the nodes that 
			// make the preconditon, and then have the tree hang off it
			for (Long r : newTreeData.e.speculatedChems())
				if (!allReach.contains(r)) {
					this.roots.add(r); // the precondition is a root of a new disconnected tree
					// System.out.format("New root: %s\n", r);
				}
			
			for (int layer = newTreeData.startingLayer + 1; layer < newTreeData.endingLayer; layer++) {
				// reads:   R_by_layers[current, current-1], R_parent_candidates
				// adds to: R_parent, R_owned_children
				Set<Long> doNotAssignParentsTo = allReach; // because these are already in some part of the tree
				pickParentsForNewReachables(layer, -1 /* outside host */, doNotAssignParentsTo, possibleBigMols, newTreeData.e);
			}
			
			// everyone under this assumed subtree was either "parented" in this iteration, 
			// or in a previous iteration, when a larger parent acquired it. In any case,
			// they are all now reachable.
			allReach.addAll(newTreeData.newReach);
		}
		
		return allReach;
	}

	class EnvCondEffect {
		EnvCond e;
		int sizeNewReach;
		int startingLayer, endingLayer;
		Set<Long> newReach;
		EnvCondEffect(EnvCond e, int sz, Set<Long> newReach, int startL, int endL) {
			this.e = e;
			this.sizeNewReach = sz;
			this.newReach = newReach;
			this.startingLayer = startL;
			this.endingLayer = endL;
		}
	}
	
	Set<Long> R_assumed_reachable;
	Set<Long> R_saved;
	HashMap<Long, List<Long>> rxn_needs_saved;
	
	private void saveState() {
		this.R_saved = deepCopy(this.R); 
		this.rxn_needs_saved = deepCopy(this.rxn_needs);
	}
	
	private void restoreState() {
		this.R = deepCopy(this.R_saved);
		this.rxn_needs = deepCopy(this.rxn_needs_saved);
	}

	private <T,S> HashMap<T, List<S>> deepCopy(HashMap<T, List<S>> map) {
		HashMap<T, List<S>> copy = new HashMap<T, List<S>>();
		for (T r : map.keySet())
			copy.put(r, new ArrayList<S>(map.get(r)));
		return copy;
	}

	private <T> Set<T> deepCopy(Set<T> parentR) {
		return new HashSet<T>(parentR);
	}
	
	private Set<Long> getRoots() {
		return this.roots;
	}

	private HashMap<Long, Object> constructAttributes() {
		
		HashMap<Long, Object> attributes = new HashMap<Long, Object>();
		for (int layer : this.R_by_layers.keySet()) {
			for (Long n : this.R_by_layers.get(layer)) {
				HashMap<String, Integer> attr = new HashMap<String, Integer>();
				boolean isInsideHost = layer == 1;
				if (isInsideHost) { // host reachables
					// globalLayer->1, hostLayer=getHostLayerOf(n)
					attr.put("globalLayer", 1);
					attr.put("hostLayer", getHostLayerOf(n));
				} else {
					// globalLayer->layer, hostLayer=-1
					attr.put("globalLayer", layer);
					attr.put("hostLayer", -1);
				}
				attributes.put(n, attr);
			}
		}
		
		// for any of the disjoint assumed_reachable trees, create dummy layer values
		HashMap<String, Integer> condReachAttr = new HashMap<String, Integer>();
		condReachAttr.put("globalLayer", -2);
		condReachAttr.put("hostLayer", -2);
		for (Long condReach : this.R_assumed_reachable) {
			if (attributes.containsKey(condReach))
				continue;
			attributes.put(condReach, condReachAttr);
		}

		HashMap<String, Integer> rootAttr = new HashMap<String, Integer>();
		rootAttr.put("globalLayer", -1);
		rootAttr.put("hostLayer", -1);
		for (Long root : getRoots()) 
			attributes.put(root, rootAttr);
		attributes.put(this.rootProxyInLayer1, rootAttr); 
		// exception: rootProxy is not in the roots list because we want a 1-1 between roots and disjoint trees

		HashMap<String, Integer> isAttachedToAncestor = new HashMap<String, Integer>();
		isAttachedToAncestor.put("attachedDirectlyToRoot", 1);
		for (Long orphan : this.isAncestorAndNotDirectParent) {
			attributes.put(orphan, isAttachedToAncestor);
		}
		
		return attributes;
	}

	private HashMap<Long, List<Long>> computeRxnNeeds() {

    // use the following as the universe of reactions to enumerate over
    HashMap<Long, Set<Long>> substrates_dataset = ActLayout.USE_RXN_CLASSES ? ActData.rxnClassesSubstrates : ActData.rxnSubstrates;

		HashMap<Long, List<Long>> needs = new HashMap<Long, List<Long>>();
    int ignored_nosub = 0, ignored_noseq = 0, total = 0;
    System.out.format("Processing all rxns for rxn_needs: %d\n", substrates_dataset.size());
		for (Long r : substrates_dataset.keySet()) {
      Set<Long> substrates = substrates_dataset.get(r);

      // do not add reactions whose substrate list is empty (happens when we parse metacyc)
      if (ActLayout._actTreeIgnoreReactionsWithNoSubstrates)
        if (substrates.size() == 0) { 
          // System.out.format("Rxn %d has 0 substrates. Ignored: %s\n", r, ActData.rxnEasyDesc.get(r));
          ignored_nosub++;
          continue;
        }

      // do not add reactions that don't have a sequence; unless the flag to be liberal is set
      if (ActLayout._actTreeOnlyIncludeRxnsWithSequences) {
        if (!ActData.rxnHasSeq.get(r)) {
          // System.out.format("Rxn %d has no sequence. Ignored: %s\n", r, ActData.rxnEasyDesc.get(r));
          ignored_noseq++;
          continue;
        }
      }

      total++;
			needs.put(r, new ArrayList<Long>(substrates));
		}
    if (ActLayout._actTreeIgnoreReactionsWithNoSubstrates)
      System.out.format("Ignored %d reactions that had zero substrates. Total were %d\n", ignored_nosub, total);
    if (ActLayout._actTreeOnlyIncludeRxnsWithSequences)
      System.out.format("Ignored %d reactions that had no sequence. Total were %d\n", ignored_noseq, total);
		return needs;
	}

	protected Set<Long> productsOf(Set<Long> enabledRxns) {
    // use the following as the universe of reactions to enumerate over
    HashMap<Long, Set<Long>> substrates_dataset = ActLayout.USE_RXN_CLASSES ? ActData.rxnClassesSubstrates : ActData.rxnSubstrates;
    HashMap<Long, Set<Long>> products_dataset = ActLayout.USE_RXN_CLASSES ? ActData.rxnClassesProducts : ActData.rxnProducts;

		Set<Long> P = new HashSet<Long>();
		for (Long r : enabledRxns) {
			P.addAll(products_dataset.get(r));
			
			for (Long p : products_dataset.get(r)) {
				if (cofactors_and_natives.contains(p))
					continue;
				// Add the substrates of the reactions as the options for parents for the products,
				// the elements in P might not be new reachables, but that is ok, since we will only assign a parent in layer i-1

				Set<Long> candidates = new HashSet<Long>();
				candidates.addAll(substrates_dataset.get(r));
				// -- without adding cofactors, there are reactions in which the products will have no option of parents, 
				// so we have to allow cofactors in the parent candidates but at the same time, some are really bad parents, 
				// e.g., water and ATP, so at the end we blacklist them as owning parents
				candidates.addAll(ActData.rxnSubstratesCofactors.get(r)); 
				if (!this.R_parent_candidates.containsKey(p))
					this.R_parent_candidates.put(p, new HashSet<Long>());
				this.R_parent_candidates.get(p).addAll(candidates);

				// if (this.R_parent_candidates.get(p) == null)
				//	System.out.format("For child %d, candidate parents is null!\n", p);
			}
		}
		return P;
	}

	private Long pickMostSimilar(Long p, Set<Long> ss) {
		String prod = ActData.chemId2Inchis.get(p);
    Integer numCprod, numCsubstrate;
		if (prod == null || (numCprod = countCarbons(prod)) == null)
			return null;
		int closest = 10000000; // these many carbons away
		Long closestID = null;
		for (Long s : ss) {
			String substrate = ActData.chemId2Inchis.get(s);
			if (substrate == null || (numCsubstrate = countCarbons(substrate)) == null)
				continue;
			int delta = Math.abs(numCsubstrate - numCprod);
			if (closest > delta) {
				closest = delta;
				closestID = s;
			}
		}
		return closestID;
	}

  private Integer countCarbons(String inchi) {
    String[] spl = inchi.split("/");
    if (spl.length <= 2)
      return null;

    String formula = spl[1];
    Pattern regex = Pattern.compile("C([0-9]+)");
    Matcher m = regex.matcher(formula);
    if (m.matches()) {
      return Integer.parseInt(m.group(1));
    } else {
      return formula.contains("C") ? 1 : 0;
    }
  }

	/* checks "rxn_needs" for the enabled reactions
	 * picks up the enabled products using the enabled reactions
	 * adds whichever ones are new reachables to "R"
	 * 
	 * marks the products with possible parents as the substrates
	 * of the enabled reactions into "R_parent_candidates"
	 * 
	 * adds to layer either this.R_by_layers_in_host (orgID!=null) 
	 * or this.R_by_layers (orgID==null)
	 * 
	 * updates the "rxn_needs" by removing the preconditions in 
	 * case new reactions are enabled with these new Reachables
	 * 
	 * so: reads and updates this.rxn_needs, add to this.R, 
	 *     adds to this.R_parent_candidates, 
	 *     adds to this.R_by_layer (or in_host if orgID!=null)
	 */
	private boolean pushWaveFront(Long orgID, int layer) {
		boolean isInsideHost = orgID != null;
		
		Set<Long> enabledRxns = extractEnabledRxns(orgID);

		if (isInsideHost)
			System.out.format("Org: %d, num enabled rxns: %d\n", orgID, enabledRxns.size());
		Set<Long> newReachables = productsOf(enabledRxns);

		Set<Long> uniqNew = new HashSet<Long>(newReachables);
		uniqNew.removeAll(R);
		if (uniqNew.size() > 0) {
			addToLayers(uniqNew, layer, true /* add to existing layer */, isInsideHost);

		}

		R.addAll(newReachables);
		updateEnabled(newReachables);
		
		return uniqNew.size() > 0; // at least one new node in this layer
	}

	protected boolean anyEnabledReactions(Long orgID) {
		for (Long r : this.rxn_needs.keySet()) {
			if (orgID == null || ActData.rxnOrganisms.get(r).contains(orgID)) 
				if (this.rxn_needs.get(r).isEmpty()) 
					return true;
		}
		return false;
	}
	
	protected Set<Long> extractEnabledRxns(Long orgID) {
		Set<Long> enabled = new HashSet<Long>();
		for (Long r : this.rxn_needs.keySet())
			if (this.rxn_needs.get(r).isEmpty()) {
				// if no orgID specified: add all rxns from any organism, 
				// if orgID is specified: only if the reaction happens in the org
				if (orgID == null || ActData.rxnOrganisms.get(r).contains(orgID)) 
					enabled.add(r);
			}
		for (Long r : enabled)
			this.rxn_needs.remove(r);
		return enabled;
	}

	protected void updateEnabled(Set<Long> newReachables) {
		for (Long r : this.rxn_needs.keySet()) {
			List<Long> needs = new ArrayList<Long>();
			for (Long l : this.rxn_needs.get(r)) {
				if (!newReachables.contains(l))
					needs.add(l);
			}
			this.rxn_needs.put(r, needs);
		}
	}
	
	private void setParentsForCofactorsAndNatives(Set<Long> R) {
		// the native and cofactors have the root as their parent
		for (Long child : R) {
			this.R_parent.put(child, this.root);
		}
		this.R_parent.put(this.rootProxyInLayer1, this.root);
		
		Set<Long> childrenOfTheRoot = new HashSet<Long>(R);
		childrenOfTheRoot.add(this.rootProxyInLayer1);
		this.R_owned_children.put(this.root, childrenOfTheRoot);
	}

	/*
	 * reads R_by_layers (or _in_host) for current layer and layer-1
	 * for every node in current layer, finds 
	 * candidate parents from "R_parent_candidates" minus cofactors
	 * and one that is most_similar structurally (if none then all
	 * parents are candidates
	 * 
	 * Then goes through the possible parents and greedily picks ones
	 * that can acquire most children, installs them in R_parent, R_owned_children
	 * 
	 * so: reads from R_by_layers[current, current-1], and R_parent_candidates
	 *     and writes to R_parent, R_owned_children
	 */
	private void pickParentsForNewReachables(int layer, int host_layer, Set<Long> doNotChangeNeighborhoodOf, List<Long> possibleBigMolecules, EnvCond treeRoot) {
		Set<Long> reachInNewLayer;
		Set<Long> reachInLayerAbove;
		
		// System.out.format("picking parent (layer,host_layer) = (%d,%d)\n", layer, host_layer);
		if (host_layer == -1) { 
			// this is a non-host layer update
			reachInNewLayer = this.R_by_layers.get(layer);
			reachInLayerAbove = this.R_by_layers.get(layer - 1);
		} else {
			// this is layer update within the host
			reachInNewLayer = this.R_by_layers_in_host.get(host_layer);
			if (host_layer == 0) // the layer above is not in the host, it is layer=0
				reachInLayerAbove = this.R_by_layers.get(0 /*natives and cofactors layer*/);
			else
				reachInLayerAbove = this.R_by_layers_in_host.get(host_layer - 1);
		}
		
		// System.out.format("Calling pickparent on L%d for treeroot %s\n", layer, treeRoot);
		// Set<Long> notConsidered = intersect(doNotChangeNeighborhoodOf, reachInNewLayer);
		// if (notConsidered.size() > 0) System.out.format("Not assigning parent to: %s (they are already reachable elsewhere)\n", notConsidered);
		
		// for each of the keys that could be a parent (in the tree), its set of possible children 
		HashMap<Long, Set<Long>> possible_children = new HashMap<Long, Set<Long>>();
		
		// init
		for (Long p : reachInLayerAbove) 
			possible_children.put(p, new HashSet<Long>());
		
		if (reachInNewLayer == null)
			return;

		// for each child in "layer", lookup its candidate parents and add child to parent's possible ownership
		for (Long child : reachInNewLayer) {
			boolean at_least_one_parent = false;
			
			Set<Long> candidates = this.R_parent_candidates.get(child);
			removeBlackListedCofactorsAsParents(candidates, child);
			Long most_similar = pickMostSimilar(child, candidates);
			
			if (most_similar != null && possible_children.containsKey(most_similar)) {
				// ideal case, most_similar substrate is computable, and the possible_children containment
				// indicates that the most_similar is also in the layer directly below, and therefore the best parent possible
				possible_children.get(most_similar).add(child);
				at_least_one_parent = true;
			} else {
				// the ideal case does not pan out: because either the most_similar is uncomputed
				// or that the most similar is in a layer much below and the immediate precursor dependency was
				// some other substrate needed for the reaction. Ah well. Lets just attach this child to this other dependency
				for (Long parent_candidate : candidates) {
					// if possible_children map does not contain a parent_candidate key means it is not in the 
					// immediate layer below and cannot be considered a direct ancestor parent, so continue;
					if (!possible_children.containsKey(parent_candidate)) {
						continue;
					}
					possible_children.get(parent_candidate).add(child);
					at_least_one_parent = true;
				}
			}
			if (!at_least_one_parent && layer == 2) {
				// there might be no parents to claim a child in layer 2 because it might have parents that are cofactors or natives
				// which means that it would have been in layer 1, except for the restriction that layer 1 is only ecoli enzymes
				// so we will attach it to the proxy node for the natives and cofactors which will show up at the same level as layer 1 nodes
				if (!possible_children.containsKey(this.rootProxyInLayer1))
					possible_children.put(this.rootProxyInLayer1, new HashSet<Long>());
				possible_children.get(this.rootProxyInLayer1).add(child);
			}
		}

		// look at "layer - 1" and greedy assign parents there as many "layer" nodes as possible
		Set<Long> still_orphan = new HashSet<Long>(reachInNewLayer);
		// the nodes in "doNotChangeNeighborhoodOf" do not need to find parents; already assigned elsewhere
		still_orphan.removeAll(doNotChangeNeighborhoodOf);
		
    // metacyc gives us some molecules with db.chemicals.findOne({InChI:/FAKE/}). These are either
    // big molecules (proteins, rna, dna and), or big molecule attached SM, or small molecule abstractions
    // either way.. do not worry about assigning parents to them.
    still_orphan.removeAll(possibleBigMolecules);
		
		// greedily assign children to parents
		while (still_orphan.size() > 0) {
			// there is still a child left, find the parent that can adopt the most number of children
			// cannot pick a parent that is in "doNotChangeNeighborhoodOf", so pass that along to the function below
			Long largest_parent = pick_with_largest_candidate_children(possible_children, doNotChangeNeighborhoodOf);
			Set<Long> adopted_children = possible_children.get(largest_parent); // these children have found a parent
			if (adopted_children == null) {
				if (treeRoot == null) {
					// this is the case where we are computing the true reachables and not the assumed_reachables
					// i.e., not under an artificial world with extra assumptions. Therefore, there should be no
					// orphans here. They should all be accounted for.
					System.out.format("Nodes that will remain orphan: %s\n", still_orphan);
					System.exit(-1);
				} else {
					// this is the other case where we have a "different world assumption", i.e., there are assumed
					// tree roots and their corresponding descendents. Now here a problem arises when greedily assigning
					// parent *across worlds* (i.e., across different assumptions). Here a larger tree might have stolen
					// some of the parents on the route from nodes to the treeRoot. In that case, this orphan has
					// nowhere to go: a) It cannot be part of this "world" because the parents on the route to the treeRoot
					// are missing in this world, and b) it cannot be part of the world where its parent was adopted, because
					// the reachability of this node depends on there being reachables other than its parent, which are
					// only present in this world and not in the alternative where its parent went.
					
					// SO: we have to make an exception, and attach this node directly to the treeRoot; BUT annotate
					// that this is not a direct descendent of the root. Then we can highlight those edges in the front=end.

					for (Long n : still_orphan) {
						// first, if there are multiple elements in the root assumptions, then pick the most similar
						Set<Long> candidates = deepCopy(treeRoot.speculatedChems());
						removeBlackListedCofactorsAsParents(candidates, n);
						Long most_similar = pickMostSimilar(n, candidates);
						if (most_similar == null) {
							// we do not have structure information (either for n, or for any of the candidates)
							// so just pick the first guy in the list of candidates to be the parent 
							// (candidates has the invariant of being at least size>=1)
							most_similar = candidates.toArray(new Long[] {})[0];
						}
						// define most_similar as n's parent
						addChildToParentsChildren(most_similar/*parent*/, n/*child*/);
						this.isAncestorAndNotDirectParent.add(n);
					}
					still_orphan.clear();
					break; // all orphans are not attached to the root; exit loop.
				}
			}
			
			possible_children.remove(largest_parent); // this parent should not be considered in the future
			
			adopted_children.removeAll(doNotChangeNeighborhoodOf);
			installParentWithChildren(largest_parent, adopted_children);
			// System.out.format("Parent %d (L%d) -> Children %s (L%d)\n", largest_parent, layer-1, adopted_children, layer);
			
			for (Long child : adopted_children) {
				// child has a parent now, so remove from still_orphan list
				still_orphan.remove(child);

				// clean up the possible_children lists for other parents
				for (Long other_parent : possible_children.keySet()) {
					if (possible_children.get(other_parent).contains(child))
						possible_children.get(other_parent).remove(child);
				}
			}
		}
	}
	
	private <T> Set<T> intersect(Set<T> A, Set<T> B) {
		Set<T> intersection = new HashSet<T>();
		if (A == null || B == null)
			return intersection;
		for (T a : A)
			if (B.contains(a))
				intersection.add(a);
		return intersection;
	}

	private void installParentWithChildren(Long parent, Set<Long> children) {
		// set the parent's set of children
		this.R_owned_children.put(parent, children);
		for (Long child : children) {
			// set the child's parent
			this.R_parent.put(child, parent);
		}
	}
	
	private void addChildToParentsChildren(Long parent, Long child) {
		// add to the parent's set of children (as opposed to setting as in the above function)
		if (this.R_owned_children.get(parent) == null)
			this.R_owned_children.put(parent, new HashSet<Long>());
		this.R_owned_children.get(parent).add(child);
		
		// set the child's parent
		this.R_parent.put(child, parent);
	}
	
	private void removeBlackListedCofactorsAsParents(Set<Long> candidates, Long child) {
		for (MongoDB.SomeCofactorNames cofactor : MongoDB.SomeCofactorNames.values()) {
			Long cofactorDBId = cofactor.getMongoDBId();
			// we check size>1 because we do not want to leave the child without any parents to choose from.
			if (candidates.contains(cofactorDBId))
				if (candidates.size() > 1)
					candidates.remove(cofactorDBId);
				else 
					System.out.format("**** RemovingBlackListCofactors: %s is a candidate parent, and the ONLY one for %d.\n", cofactor, child);
		}
	}
	
	
	private Long pick_with_largest_candidate_children(HashMap<Long, Set<Long>> map, Set<Long> blackList) {
		int max = -1; Long maxId = -1L;
		for (Long k : map.keySet()) {
			if (blackList.contains(k))
				continue;
			if (max < map.get(k).size()) {
				max = map.get(k).size(); maxId = k; 
			}
		}
		return maxId;
	}

	private int getLayerOf(Long c) {
		for (int l : this.R_by_layers.keySet()) 
			if (this.R_by_layers.get(l).contains(c))
				return l;
		return -1;
	}

	private int getHostLayerOf(Long c) {
		for (int l : this.R_by_layers_in_host.keySet()) 
			if (this.R_by_layers_in_host.get(l).contains(c))
				return l;
		if (this.R_by_layers.get(0).contains(c))
			return -2;
		return -1;
	}

	private void addToLayers(Set<Long> nodes, int layer, boolean addToExisting, boolean isInsideHost) {
		Set<Long> addNodes = new HashSet<Long>(nodes);
		HashMap<Integer, Set<Long>> map = isInsideHost ? this.R_by_layers_in_host : this.R_by_layers;
		
		// sanity check....
		if (map.containsKey(layer) && !addToExisting) { 
			System.out.println("ERR: Layer already installed and addToExisting not requested!?");
			System.exit(-1);
		}
		
		// really add to the particular map: if isInsideHost it will add to the host map else to the global one
		if (map.containsKey(layer))
			addNodes.addAll(map.get(layer));
		map.put(layer, addNodes);
		
		// diagnostics...
		// System.out.format("Adding to layer %d (insideHost=%s) numNodes=%d\n", layer, isInsideHost, addNodes.size());

		// even if we add to the host map, the global one has the aggregated set of host reachables in layer 1
		if (isInsideHost) {
			Set<Long> aggregatedLayer1Nodes = new HashSet<Long>(nodes);
			if (this.R_by_layers.containsKey(1))
				aggregatedLayer1Nodes.addAll(this.R_by_layers.get(1));
			this.R_by_layers.put(1, aggregatedLayer1Nodes);
		}
	}

}
