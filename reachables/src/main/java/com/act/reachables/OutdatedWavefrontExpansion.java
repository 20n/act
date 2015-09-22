package com.act.reachables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import act.shared.Chemical;
import act.shared.helpers.P;

public class OutdatedWavefrontExpansion extends SteppedTask {
  Set<Long> R;
  HashMap<Long, List<Long>> rxn_needs;
  HashMap<Integer, Set<Long>> R_by_layers;
  int currentLayer;

  public OutdatedWavefrontExpansion() {
    this.R = new HashSet<Long>();
    this.R_by_layers = new HashMap<Integer, Set<Long>>();
    this.rxn_needs = computeRxnNeeds();
    this.currentLayer = 0;
  }

  private HashMap<Long, List<Long>> computeRxnNeeds() {
    HashMap<Long, List<Long>> needs = new HashMap<Long, List<Long>>();
    for (Long r : ActData.instance().rxnSubstrates.keySet()) {
      needs.put(r, new ArrayList<Long>(ActData.instance().rxnSubstrates.get(r)));
    }
    return needs;
  }

  protected Set<Long> productsOf(Set<Long> enabledRxns) {
    Set<Long> P = new HashSet<Long>();
    for (Long r : enabledRxns) {
      P.addAll(ActData.instance().rxnProducts.get(r));
    }
    return P;
  }

  @Override
  public double percentDone() {
    return anyEnabledReactions(null) ? 0 : 100;
  }

  @Override
  public void doMoreWork() {
    pushWaveFront(null, true /* do increment layer counter */);
  }

  private void addAllHostMetabolites(long hostID) {
    pushWaveFront(hostID, false /* do not increment layer counter */);
  }

  private static String _fileloc = "com.act.reachables.ConditionalReachable";
  private static void logProgress(String format, Object... args) {
    if (!GlobalParams.LOG_PROGRESS)
      return;

    System.err.format(_fileloc + ": " + format, args);
  }

  private static void logProgress(String msg) {
    if (!GlobalParams.LOG_PROGRESS)
      return;

    System.err.println(_fileloc + ": " + msg);
  }

  private void pushWaveFront(Long orgID, boolean incrementLayer) {
    Set<Long> enabledRxns = extractEnabledRxns(orgID);
    if (orgID != null)
      logProgress("Org: %d, num enabled rxns: %d\n", orgID, enabledRxns.size());
    Set<Long> newReachables = productsOf(enabledRxns);

    {
      // this is work done for layout, i.e., setting the layers, and first reach reactions
      Set<Long> uniqNew = new HashSet<Long>(newReachables);
      uniqNew.removeAll(R);
      if (uniqNew.size() > 0) {
        addToLayers(uniqNew, this.currentLayer, true /* add to existing layer */);
        if (incrementLayer)
          this.currentLayer++;
      }
      tagActivatingRxns(enabledRxns, uniqNew);
    }

    R.addAll(newReachables);
    if (orgID != null)
      logProgress("Org: %d, num newReachables in layer %d: %d\n", orgID, this.currentLayer-1, newReachables.size());
    updateEnabled(newReachables);
  }

  private void tagActivatingRxns(Set<Long> rxns, Set<Long> newChems) {

    for (Long rxn : rxns) {
      Set<Long> activated_products = new HashSet<Long>();
      for (Long p : ActData.instance().rxnProducts.get(rxn))
        if (newChems.contains(p))
          activated_products.add(p);
      if (activated_products.size() > 0) {
        // tag it as such
        for (Long s : ActData.instance().rxnSubstrates.get(rxn)) {
          for (Long p : activated_products) {
            Edge e = ActData.instance().rxnsInAct.get(new P<Long, Long>(s, p));
            Edge.setAttribute(e, "activates_product", true);
          }
        }
      }
    }
  }

  protected boolean anyEnabledReactions(Long orgID) {
    for (Long r : this.rxn_needs.keySet()) {
      if (orgID == null || ActData.instance().rxnOrganisms.get(r).contains(orgID))
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
        if (orgID == null || ActData.instance().rxnOrganisms.get(r).contains(orgID))
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

  @Override
  public void init() {
    for (Long c : ActData.instance().cofactors)
      R.add(c);
    for (Long n : ActData.instance().natives)
       R.add(n);

    addToLayers(R, this.currentLayer++, false /* add to new layer */);
    updateEnabled(R);
    // add all host organism reachables
    while (anyEnabledReactions(GlobalParams.gethostOrganismID()))
      addAllHostMetabolites(GlobalParams.gethostOrganismID());
  }

  private void addToLayers(Set<Long> nodes, int layer, boolean addToExisting) {
    Set<Long> addNodes = new HashSet<Long>(nodes);
    if (this.R_by_layers.containsKey(layer)) {
      if (addToExisting)
        addNodes.addAll(this.R_by_layers.get(layer));
      else
        logProgress("ERR: Layer nodes already installed and addToExisting not requested. How did new nodes appear at the same later!?");
    }
    for (Long c : nodes) {
      Node n = ActData.instance().chemsInAct.get(c);
      if (n != null)
        Node.setAttribute(n.getIdentifier(), "reachable_layer", layer);
    }
    this.R_by_layers.put(layer, addNodes);
  }

  @Override
  public void finalize(TaskMonitor tm) {
    int N = R.size();
    int i = 0;
    tm.setStatus("Reachable: " +  N + " nodes. Setting isReachable, and selecting nodes");
    Set<Node> reach1 = new HashSet<Node>();
    for (Long r : R) {
      tm.setPercentCompleted((int)(100 * ((double)(i++)/N)));
      if (!ActData.instance().chemsInAct.containsKey(r))
        continue; // in cases where the native is also a cofactor, it would not have a node.

      // set the attributes in the act network
      Long n1 = ActData.instance().chemsInAct.get(r).getIdentifier();
      Node.setAttribute(n1, "isReachable", true);
      reach1.add(ActData.instance().chemsInAct.get(r));
    }
    ActData.instance().Act.unselectAllNodes();
    ActData.instance().Act.setSelectedNodeState(reach1, true);
  }

}
