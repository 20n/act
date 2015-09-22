package com.act.reachables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import act.shared.Chemical;
import act.shared.helpers.P;
import act.shared.FattyAcidEnablers;

public class ConditionalReachable extends OutdatedWavefrontExpansion {

  boolean conditionalReachPhase;
  Set<Long> R_saved;
  HashMap<Long, List<Long>> rxn_needs_saved;
  Set<Long> unR_saved;

  HashMap<Long, List<EnvCond>> reachableUnder;
  HashMap<EnvCond, Integer> extraReached;
  List<P<EnvCond, Integer>> extraReachedSortedBySize;
  List<EnvCond> guesses;
  int size_guesses;
  int partial; // >0 indicates do only those partial many, 0 indicates not partial

  boolean debug = false;

  public ConditionalReachable(int partial) {
    super(); // will get me R, and substrate preconditions of rxns (rxn_needs)
    this.conditionalReachPhase = false; // first do normal reachability, then conditional
    this.extraReached = new HashMap<EnvCond, Integer>(); // the number of nodes reached if this node is enabled
    this.reachableUnder = new HashMap<Long, List<EnvCond>>();
    this.guesses = null;
    this.size_guesses = -1;
    this.partial = partial;
  }

  @Override
  public double percentDone() {
    if (!conditionalReachPhase) {
      if (anyEnabledReactions(null))
        return 0; // if any rxns are enabled then we are still in normal phase
      else {
        conditionalReachPhase = true;
        this.guesses = getEnvironmentConditionTuples();
        // picking which partial set to lookup is pretty arbitrary, the
        // getEnvCondTuples function above returns them in ascending order
        // of their immediate fanout. But that does not mean those preconditions
        // will eventually lead to a large subtree. So we just pick the first-partial num.
        if (partial > 0)
          this.guesses = this.guesses.subList(0, partial);

        // save the current state by doing a deep copy
        saveState();

        return 50; // normal reachability done, move onto conditional reachability phase
      }
    } else {
      if (debug) logProgress("At " + this.guesses.size() + "/" + this.size_guesses);
      return 100 - 50 * ((double) this.guesses.size() / this.size_guesses);
    }
  }

  private List<EnvCond> getEnvironmentConditionTuples() {
    // read all reactions in rxn_needs, check their "needs" and create a speculation tuple
    // out of those needs. Ensure that you keep a count of the number of times the tuple is
    // seen. Then output the sorted list.

    HashMap<EnvCond, Integer> counts = new HashMap<EnvCond, Integer>();
    for (Long r : super.rxn_needs.keySet()) {
      EnvCond tuple = new EnvCond(super.rxn_needs.get(r));
      if (counts.containsKey(tuple))
        counts.put(tuple, counts.get(tuple) + 1);
      else
        counts.put(tuple, 1);
    }

    return sortByCounts(counts);
  }

  private List<EnvCond> sortByCounts(HashMap<EnvCond, Integer> counts) {
    List<P<EnvCond, Integer>> sc = new ArrayList<P<EnvCond, Integer>>();
    for (EnvCond se : counts.keySet())
      sc.add(new P<EnvCond, Integer>(se, counts.get(se)));
    Collections.sort(sc, new PairComparator<EnvCond>());

    List<EnvCond> s = new ArrayList<EnvCond>();
    for (P<EnvCond, Integer> e : sc)
      s.add(e.fst());
    return s;
  }

  public class PairComparator<T> implements Comparator<P<T,Integer>> {
    @Override
    public int compare(P<T,Integer> o1, P<T,Integer> o2) {
      return o1.snd().compareTo(o2.snd());
    }

  }

  private void saveState() {
    this.R_saved = deepCopy(super.R);
    this.rxn_needs_saved = deepCopy(super.rxn_needs);

    this.unR_saved = new HashSet<Long>(ActData.instance().chemsReferencedInRxns);
    this.unR_saved.removeAll(this.R_saved);
  }

  private void restoreState() {
    super.R = deepCopy(this.R_saved);
    super.rxn_needs = deepCopy(this.rxn_needs_saved);
  }

  private HashMap<Long, List<Long>> deepCopy(HashMap<Long, List<Long>> map) {
    HashMap<Long, List<Long>> copy = new HashMap<Long, List<Long>>();
    for (Long r : map.keySet())
      copy.put(r, new ArrayList<Long>(map.get(r)));
    return copy;
  }

  private Set<Long> deepCopy(Set<Long> parentR) {
    return new HashSet<Long>(parentR);
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

  @Override
  public void doMoreWork() {
    if (!conditionalReachPhase)
      super.doMoreWork();
    else {
      EnvCond envCond = this.guesses.remove(0);
      if (debug) logProgress("Assume: " + envCond);

      // pop the stack back to normal reachability
      restoreState();

      super.R.addAll(envCond.speculatedChems()); // ASSUME(reachable(new node))
      super.updateEnabled(envCond.speculatedChems());
      while (super.anyEnabledReactions(null)) {
        super.doMoreWork(); // compute reachability
      }

      // delta from saved_R, modulo the chems we assumed and added as is
      int newReachCount = super.R.size() - this.R_saved.size() - envCond.speculatedChems().size();
      storeNewlyReached(envCond, newReachCount, super.R, this.R_saved);
    }
  }

  private void storeNewlyReached(EnvCond ec, int N, Set<Long> newReach, Set<Long> oldReach) {
    for (Long id : newReach) {
      if (oldReach.contains(id))
        continue;
      if (this.reachableUnder.containsKey(id))
        this.reachableUnder.get(id).add(ec);
      else {
        List<EnvCond> l = new ArrayList<EnvCond>(); l.add(ec);
        this.reachableUnder.put(id, l);
      }
    }

    // save the number of nodes, and nodes themselves, that are enabled by ec
    this.extraReached.put(ec, N);
    if (debug) logProgress("\t-> " + N);
  }

  @Override
  public void finalize(TaskMonitor tm) {
    int N = this.extraReached.size();
    int i = 0;
    EnvCond high = null;
    int highest = Integer.MIN_VALUE;
    tm.setStatus("Conditional Reachability evaluated for " +  N + " nodes. Setting isConditionalReachable.");

    List<P<EnvCond, Integer>> sc = new ArrayList<P<EnvCond, Integer>>();
    HashMap<Long, Integer> chemEnvironmentalImp = new HashMap<Long, Integer>();
    for (EnvCond c : this.extraReached.keySet()) {
      int enables = this.extraReached.get(c);
      sc.add(new P<EnvCond, Integer>(c, enables));

      tm.setPercentCompleted((int)(100 * ((double)(i++)/N)));
      for (Long cc : c.speculatedChems()) {
        if (!ActData.instance().chemsInAct.containsKey(cc))
          continue; // in cases where the native is also a cofactor, it would not have a node.

        Integer enables_through_some_other_pairing = (Integer)Node.getAttribute(ActData.instance().chemsInAct.get(cc).getIdentifier(), "ifReachThenEnables");
        if (enables_through_some_other_pairing != null && enables < enables_through_some_other_pairing)
          continue;

        // new max enables found through this pairing....
        // set the attributes in the act network
        Long n1 = ActData.instance().chemsInAct.get(cc).getIdentifier();
        Node.setAttribute(n1, "ifReachThenEnables", enables);

        // log it
        chemEnvironmentalImp.put(cc, enables);
      }
      if (high == null || highest < enables) {
        high = c;
        highest = enables;
      }
    }

    // cache the sorted sized clusters
    Collections.sort(sc, new PairComparator<EnvCond>());
    this.extraReachedSortedBySize = sc;

    // dump to log... <install loc>/output.log
    logEnvCondsAndNodes(sc, chemEnvironmentalImp);

    // add reachability ease
    addReachabilityEase(sc);

    // we wish to highlight n1 and n2
    ActData.instance().Act.setSelectedNodeState(allNodes(ActData.instance().chemsInAct, high), true);

    // cache this reachability computation (useful in other actions later)
    ActData.instance()._LastReachabilityComputation = this;

    // announce it to the user
    //     "Computed ifReachThenEnables values. Highest enabler is node " +
    //        high + " who makes " + highest + " others reachable." );
  }

  private void addReachabilityEase(List<P<EnvCond, Integer>> sc) {
    HashMap<EnvCond, Integer> precondition_ease = new HashMap<EnvCond, Integer>();
    for (P<EnvCond, Integer> p : sc)
      precondition_ease.put(p.fst(), p.snd());
    int ease = -1;
    for (Long cid : ActData.instance().chemsReferencedInRxns) {
      if (!ActData.instance().chemsInAct.containsKey(cid))
        continue;

      if (isReachable(cid)) {
        // reachable without any preconditions
        ease = 1000;
      } else {
        // potentially null, but never is because each node is at
        // least reachable from the one reaction coming into it.
        // unless there are nodes that are only consumed
        int max = 0;
        List<EnvCond> underConditions = envCondForReachability(cid);
        if (underConditions != null) {
          for (EnvCond ec : underConditions) {
            Integer pc = precondition_ease.get(ec);
            if (max < pc) max = pc;
          }
          ease = max;
        } else {
          ease = -1;
        }
      }

      Long n1 = ActData.instance().chemsInAct.get(cid).getIdentifier();
      Node.setAttribute(n1, "reachabilityEase", ease);
    }
  }

  private void logEnvCondsAndNodes(List<P<EnvCond, Integer>> ecs, HashMap<Long, Integer> chemImp) {
    List<Long> chems = new ArrayList<Long>(ActData.instance().chemsReferencedInRxns);
    Collections.sort(chems);

    logProgress("========================================");
    logProgress("===========Chemical Metadata============");
    logProgress("No chemical metadata loaded.");
    logProgress("===============================================");
    logProgress("====Reasons for chemicals being unreachable====");
    logProgress("Chemical ID\tWould be reachable if these other groups are reachable");
    for (Long id : chems) {
      logProgress("%d\t%s\n", id, namify(GetChemReachability(id)));
    }
    logProgress("===============================================");
    logProgress("===How many chemicals are enabled by a tuple===");
    logProgress("Number of new reachables\tIf this tuple is reachable");
    for (P<EnvCond, Integer> ec : ecs) {
      int num_enabled = ec.snd();
      if (num_enabled < 5) continue;
      logProgress("%d\t%s\n", num_enabled, ec.fst());
    }
    logProgress("===============================================");
    logProgress("==== What enabling chemicals have the most ====");
    logProgress("=== potential reachables (potential because ===");
    logProgress("=== they may always need another substrate)  ==");
    logProgress("Number of new reachables\tIf this chem is reachable\tIs chem reachable itself\tInChI\tNames");
    List<Entry<Long, Integer>> m2l = new ArrayList<Entry<Long, Integer>>(chemImp.entrySet());
    Collections.sort(m2l, new CmpSnd<Long>());
    for (Entry<Long, Integer> e : m2l) {
      int num_enabled = e.getValue();
      Long chemid = e.getKey();
      if (num_enabled < 5)
        continue; // not worth making an exception for something that enables less than 5 chemicals
      logProgress("%d\t%s\t%ss\n", num_enabled, chemid, isReachable(chemid));
    }
    logProgress("========================================");
  }

  private String namify(List<EnvCond> conditions) {
    if (conditions == null)
      return "null";
    List<String> names = new ArrayList<String>();
    for (EnvCond e : conditions)
      names.add(namify(e));
    return names.toString();
  }

  private String namify(EnvCond e) {
    String print = "";
    for (Long id : e.speculatedChems()) {
      /* We do not load chemical metadata in one big blob anymore. If needed, query the db to get the specific data you
       * need on individual chemicals. */
      Chemical c = null;
      String name = c == null ? id.toString() : c.getShortestBRENDAName();
      print += print.equals("") ? name : " + " + name;
      print += "(" + id + ")";
    }
    return "\"" + print + "\"";
  }
  public List<EnvCond> GetChemReachability(Long id) {
    if (isReachable(id))
      // reachable without any preconditions
      return new ArrayList<EnvCond>();
    else
      // potentially null, but never is because each node is at
      // least reachable from the one reaction coming into it.
      // unless there are nodes that are only consumed
      return envCondForReachability(id);
  }

  public List<String> GetChemReachabilityReadable(Long id) {
    List<EnvCond> r = GetChemReachability(id);
    List<String> all_opts = new ArrayList<String>();
    for (EnvCond e : r) {
      all_opts.add(e.toReadableString(15));
    }
    return all_opts;
  }

  private List<EnvCond> envCondForReachability(Long id) {
    return this.reachableUnder.get(id);
  }

  public boolean isReachable(Long id) {
    return this.R_saved.contains(id);
  }

  public class CmpSnd<T> implements Comparator<Entry<T,Integer>> {
    @Override
    public int compare(Entry<T,Integer> o1, Entry<T,Integer> o2) {
      return o1.getValue().compareTo(o2.getValue());
    }

  }

  private Set<Node> allNodes(HashMap<Long, Node> map, EnvCond tuple) {
    Set<Node> n = new HashSet<Node>();
    for (Long c : tuple.speculatedChems())
      n.add(map.get(c));
    return n;
  }

  public HashMap<Integer, Set<Long>> getL12Layers() {
    return super.R_by_layers;
  }
}
