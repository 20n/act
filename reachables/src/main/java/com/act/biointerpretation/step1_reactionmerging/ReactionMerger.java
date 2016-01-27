package com.act.biointerpretation.step1_reactionmerging;

import act.api.NoSQLAPI;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * This creates Dr. Know from Lucille.  Dr. Know is the database in which all Reactions
 * have been merged based on the sameness of the reactions and product ids.
 * <p>
 * TODO:  Need to merge the information in Reactions.  Will affect what proteins
 * end up in the output, but should not affect cascades.
 * <p>
 * Created by jca20n on 9/8/15.
 */
public class ReactionMerger {
  private NoSQLAPI api;

  public static void main(String[] args) {
    ReactionMerger merger = new ReactionMerger();
    merger.run();
  }

  public ReactionMerger() {
    NoSQLAPI.dropDB("drknow");
    this.api = new NoSQLAPI("lucille", "drknow");
  }

  public void run() {
    System.out.println("Starting ReactionMerger");
    //Populate the hashmap of duplicates keyed by a hash of the reactants and products
    long start = new Date().getTime();
    Map<String, Set<Long>> hashToDuplicates = new HashMap<>();

    Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
    while (rxns.hasNext()) {
      try {
        Reaction rxn = rxns.next();
        String hash = getHash(rxn);
        long id = rxn.getUUID();
        Set<Long> existing = hashToDuplicates.get(hash);
        if (existing == null) {
          existing = new HashSet<>();
        }
        if (existing.contains(id)) {
          System.err.println("Existing already has this id: I doubt this should ever be triggered");
        } else {
          existing.add(id);
          hashToDuplicates.put(hash, existing);
//                    System.out.println(hash);
        }

      } catch (Exception err) {
        //int newid = api.writeToOutKnowlegeGraph(rxn);
        err.printStackTrace();
      }
    }

    long end = new Date().getTime();

    System.out.println("Hashing out the reactions: " + (end - start) / 1000 + " seconds");


    //Create one Reaction in new DB for each hash
    Map<Long, Long> oldChemToNew = new HashMap<>();
    log_time("start");
    int hash_cnt = 0;
    for (String hash : hashToDuplicates.keySet()) {
      Set<Long> ids = hashToDuplicates.get(hash);

      //Merge the reactions into one
      for (Long rxnid : ids) {
        Reaction rxn = api.readReactionFromInKnowledgeGraph(rxnid);

        Long[] substrates = rxn.getSubstrates();
        Long[] products = rxn.getProducts();

        //Put in the new subsrate Ids and save any new chems
        for (int i = 0; i < substrates.length; i++) {
          Long newId = oldChemToNew.get(substrates[i]);
          if (newId == null) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(substrates[i]);
            newId = api.writeToOutKnowlegeGraph(achem);
            oldChemToNew.put(substrates[i], newId);
          }
          substrates[i] = newId;
        }
        rxn.setSubstrates(substrates);

        //Put in the new product Ids and save any new chems
        for (int i = 0; i < products.length; i++) {
          Long newId = oldChemToNew.get(products[i]);
          if (newId == null) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(products[i]);
            newId = api.writeToOutKnowlegeGraph(achem);
            oldChemToNew.put(products[i], newId);
          }
          products[i] = newId;
        }
        rxn.setProducts(products);

        //Write the reaction
        api.writeToOutKnowlegeGraph(rxn);
        break; //currently just keeps the first one, need to change such that all reactions are merged into one
      }
      hash_cnt++;
      if (hash_cnt % 100000 == 0)
        log_time("" + (hash_cnt++));
    }

    long end2 = new Date().getTime();
    System.out.println("Putting rxns in new db: " + (end2 - end) / 1000 + " seconds");
    System.out.println("done");
  }

  private static Long lastLoggedTime = null;

  private void log_time(String msg) {
    long currentTime = System.currentTimeMillis();
    long timeElapsed = lastLoggedTime == null ? currentTime : currentTime - lastLoggedTime;
    lastLoggedTime = currentTime;
    System.out.format("%s\t%d\n", msg, timeElapsed);
  }

  private String getHash(Reaction rxn) {
    StringBuilder out = new StringBuilder();
    Long[] substrates = rxn.getSubstrates();
    Long[] products = rxn.getProducts();

    Arrays.sort(substrates);
    Arrays.sort(products);

    // Add the ids of the substrates
    for (int i = 0; i < substrates.length; i++) {
      out.append(" + ");
      out.append(substrates[i]);
    }

    out.append(" >> ");

    // Add the ids of the products
    for (int i = 0; i < products.length; i++) {
      out.append(" + ");
      out.append(products[i]);
    }

    return out.toString();
  }

  private static class SubstratesProducts {
    // TODO: also consider ec-umber, coefficients, and other reaction attributes.
    Long[] substrates = null;
    Long[] products = null;
    ConversionDirectionType conversionDirectionType = null;
    StepDirection pathwayStepDirection = null;

    public SubstratesProducts(Reaction reaction) {
      // TODO: should we copy these to be safe, or just assume nobody will mess with them?
      this.substrates = reaction.getSubstrates();
      this.products = reaction.getProducts();
      this.conversionDirectionType = reaction.getConversionDirection();
      this.pathwayStepDirection = reaction.getPathwayStepDirection();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SubstratesProducts that = (SubstratesProducts) o;

      // Probably incorrect - comparing Object[] arrays with Arrays.equals
      if (!Arrays.equals(substrates, that.substrates)) return false;
      // Probably incorrect - comparing Object[] arrays with Arrays.equals
      if (!Arrays.equals(products, that.products)) return false;
      if (conversionDirectionType != that.conversionDirectionType) return false;
      return pathwayStepDirection == that.pathwayStepDirection;
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(substrates);
      result = 31 * result + Arrays.hashCode(products);
      result = 31 * result + (conversionDirectionType != null ? conversionDirectionType.hashCode() : 0);
      result = 31 * result + (pathwayStepDirection != null ? pathwayStepDirection.hashCode() : 0);
      return result;
    }
  }

  // TODO: would this be better with a pair of the min element and the set of matched reactions.
  private HashMap<SubstratesProducts, PriorityQueue<Long>> reactionGroups = new HashMap<>();

  public boolean addReaction(Reaction reaction) {
    SubstratesProducts sp = new SubstratesProducts(reaction);
    PriorityQueue<Long> pq = reactionGroups.get(sp);
    Long id = Long.valueOf(reaction.getUUID());

    if (pq != null) {
      pq.add(id);
      return false;
    }

    pq = new PriorityQueue<>(1);
    pq.add(id);
    reactionGroups.put(sp, pq);
    return true;
  }

  private Reaction mergeReactions(List<Reaction> reactions) {
    if (reactions.size() < 1) {
      return null;
    }
    Reaction fr = reactions.get(0); // fr = First reaction; we'll refer to it a lot in a moment.
    Reaction mergedReaction = new Reaction(
        -1, // Assume the id will be set when the reaction is written to the DB.
        fr.getSubstrates(),
        fr.getProducts(),
        fr.getECNum(),
        fr.getConversionDirection(),
        fr.getPathwayStepDirection(),
        fr.getReactionName(),
        fr.getType()
    );
    mergedReaction.setDataSource(fr.getDataSource());

    // TODO: are there other fields we need to capture in this merge?
    // TODO: add source ids for the various attributes to make debugging easier.
    for (Reaction r : reactions) {
      // TODO: should these be sorted before adding?
      for (P<Reaction.RefDataSource, String> ref : r.getReferences()) {
        mergedReaction.addReference(ref.fst(), ref.snd());
      }
      for (JSONObject protein : r.getProteinData()) {
        mergedReaction.addProteinData(protein);
      }

      // Set the data source as MERGED if this is a combination of multiple sources.  The protein data will store which.
      if (mergedReaction.getDataSource() != Reaction.RxnDataSource.MERGED &&
          mergedReaction.getDataSource() != r.getDataSource()) {
        mergedReaction.setDataSource(Reaction.RxnDataSource.MERGED);
      }
    }

    return mergedReaction;
  }

  public Iterator<Reaction> getReactionMergeIterator(MongoDB sourceDB) {
    /* Maintain stability by constructing the ordered set of minimum group reaction ids so that we can iterate
     * over reactions in the same order they occur in the source DB.  Stability makes life easier in a number of ways
     * (easier testing, deterministic output, general sanity) so we go to the trouble here. */
    final HashMap<Long, PriorityQueue<Long>> minGroupIdsToGroups = new HashMap<>(reactionGroups.size());
    for (Map.Entry<SubstratesProducts, PriorityQueue<Long>> entry : reactionGroups.entrySet()) {
      minGroupIdsToGroups.put(entry.getValue().peek(), entry.getValue());
    }

    // We do this in multiple statements so the type system doesn't get list;
    Long[] minIds = minGroupIdsToGroups.keySet().toArray(new Long[minGroupIdsToGroups.size()]);
    List<Long> orderedIds = Arrays.asList(minIds);
    Collections.sort(orderedIds);

    final Iterator<Long> minGroupIdsIterator = orderedIds.iterator();
    return new Iterator<Reaction>() {
      @Override
      public boolean hasNext() {
        return minGroupIdsIterator.hasNext();
      }

      @Override
      public Reaction next() {
        Long nextId = minGroupIdsIterator.next();
        PriorityQueue<Long> groupIds = minGroupIdsToGroups.get(nextId);
        List<Reaction> reactions = new ArrayList<>(groupIds.size());
        for (Long id : groupIds) {
          // Since we've only installed reaction IDs based on instances we've seen, this should be safe.
          reactions.add(sourceDB.getReactionFromUUID(id));
        }
        return mergeReactions(reactions);
      }
    };
  }
}
