package com.act.biointerpretation.step1_reactionmerging;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.helpers.P;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 */
public class ReactionMerger {
  public static boolean LOG_TIMINIG_INFO = false;

  private NoSQLAPI api;

  public static void main(String[] args) {
    ReactionMerger merger = new ReactionMerger();
    merger.run();
  }

  public ReactionMerger() {
    NoSQLAPI.dropDB("drknow");
    this.api = new NoSQLAPI("lucille", "drknow");
  }

  public ReactionMerger(String srcDB, String destDB) {
    this.api = new NoSQLAPI(srcDB, destDB);
  }

  public ReactionMerger(NoSQLAPI noSQLAPI) {
    this.api = noSQLAPI;
  }

  public void run() {
    Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
    int reactionsConsidered = 0;

    // Add the next available reaction to the map of substrates+products -> ids.
    // TODO: spill this map to disk if the map gets too large.
    while (rxns.hasNext()) {
      Reaction rxn = rxns.next();
      addReaction(rxn);
      reactionsConsidered++;
    }

    // Merge all the reactions into one.
    mergeAllReactions();
  }

  private static Long lastLoggedTime = null;

  private void logTime(String msg) {
    if (!LOG_TIMINIG_INFO) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    long timeElapsed = lastLoggedTime == null ? currentTime : currentTime - lastLoggedTime;
    lastLoggedTime = currentTime;
    System.out.format("%s\t%d\n", msg, timeElapsed);
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
        fr.getSubstrateCofactors(),
        fr.getProductCofactors(),
        fr.getCoenzymes(),
        fr.getECNum(),
        fr.getConversionDirection(),
        fr.getPathwayStepDirection(),
        fr.getReactionName(),
        fr.getRxnDetailType()
    );
    mergedReaction.setDataSource(fr.getDataSource());
    // Write stub reaction to DB to get its id, which is required for migrating sequences.
    int newId = api.writeToOutKnowlegeGraph(mergedReaction);

    // TODO: are there other fields we need to capture in this merge?
    // TODO: add source ids for the various attributes to make debugging easier.
    for (Reaction r : reactions) {
      // TODO: should these be sorted before adding?
      for (P<Reaction.RefDataSource, String> ref : r.getReferences()) {
        mergedReaction.addReference(ref.fst(), ref.snd());
      }
      for (JSONObject protein : r.getProteinData()) {
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        protein.put("source_reaction_id", r.getUUID());
        JSONObject newProteinData = migrateProteinData(protein, Long.valueOf(newId), r);
        mergedReaction.addProteinData(newProteinData);
      }

      // Set the data source as MERGED if this is a combination of multiple sources.  The protein data will store which.
      if (mergedReaction.getDataSource() != Reaction.RxnDataSource.MERGED &&
          mergedReaction.getDataSource() != r.getDataSource()) {
        mergedReaction.setDataSource(Reaction.RxnDataSource.MERGED);
      }
    }

    // Update the reaction in the DB with the newly migrated protein data.
    api.getWriteDB().updateActReaction(mergedReaction, newId);

    return mergedReaction;
  }

  public void migrateChemicals(Reaction rxn) {
    Long[] substrates = rxn.getSubstrates();
    Long[] products = rxn.getProducts();

    Long[] newSubstrates = new Long[substrates.length];
    Long[] newProducts = new Long[products.length];

    // Cache ids locally in case the appear repeatedly in the substrates/products.
    Map<Long, Long> oldChemToNew = new HashMap<>();

    // Migrate the substrates/products to the new DB.
    for (int i = 0; i < substrates.length; i++) {
      Long newId = oldChemToNew.get(substrates[i]);
      if (newId == null) {
        Chemical achem = api.readChemicalFromInKnowledgeGraph(substrates[i]);
        newId = api.writeToOutKnowlegeGraph(achem);
        oldChemToNew.put(substrates[i], newId);
      }
      newSubstrates[i] = newId;
    }
    rxn.setSubstrates(newSubstrates);

    for (int i = 0; i < products.length; i++) {
      Long newId = oldChemToNew.get(products[i]);
      if (newId == null) {
        Chemical achem = api.readChemicalFromInKnowledgeGraph(products[i]);
        newId = api.writeToOutKnowlegeGraph(achem);
        oldChemToNew.put(products[i], newId);
      }
      newProducts[i] = newId;
    }
    rxn.setProducts(newProducts);
  }

  private JSONObject migrateProteinData(JSONObject oldProtein, Long newRxnId, Reaction rxn) {
    // Copy the protein object for modification.
    // With help from http://stackoverflow.com/questions/12809779/how-do-i-clone-an-org-json-jsonobject-in-java.
    JSONObject newProtein = new JSONObject(oldProtein, JSONObject.getNames(oldProtein));

    Long oldOrganismId = oldProtein.getLong("organism");
    Long newOrganismId = null;
    if (oldOrganismId != null) {
      String organismName = api.getReadDB().getOrganismNameFromId(oldOrganismId);
      // Assume any valid organism entry will have a name.
      if (organismName != null) {
        // TODO: reading from the writeDB is not so good, but we need to not insert twice.  Is there a better way?
        long writeDBOrganismId = api.getWriteDB().getOrganismId(organismName);
        if (writeDBOrganismId != -1) { // -1 is used in MongoDB.java for missing values.
          // Reuse the existing organism entry if we can find a matching one.
          newOrganismId = writeDBOrganismId;
        } else {
          // Use -1 for no NCBI Id.  Note that the NCBI parameter isn't even stored in the DB at present.
          Organism newOrganism = new Organism(oldOrganismId, -1, organismName);
          api.getWriteDB().submitToActOrganismNameDB(newOrganism);
          newOrganismId = newOrganism.getUUID();
        }

        newProtein.put("organism", newOrganismId);
      }
    }

    Set<Long> rxnIds = Collections.singleton(newRxnId);

    // Can't use Collections.singletonMap, as MongoDB expects a HashMap explicitly.
    HashMap<Long, Set<Long>> rxnToSubstrates = new HashMap<>(1);
    // With help from http://stackoverflow.com/questions/3064423/in-java-how-to-easily-convert-an-array-to-a-set.
    rxnToSubstrates.put(newRxnId, new HashSet<>(Arrays.asList(rxn.getSubstrates())));

    HashMap<Long, Set<Long>> rxnToProducts = new HashMap<>(1);
    rxnToProducts.put(newRxnId, new HashSet<>(Arrays.asList(rxn.getProducts())));

    JSONArray sequences = oldProtein.getJSONArray("sequences");
    List<Long> newSequenceIds = new ArrayList<>(sequences.length());
    for (int i = 0; i < sequences.length(); i++) {
      Long sequenceId = sequences.getLong(i);
      Seq seq = api.getReadDB().getSeqFromID(sequenceId);

      // Assumption: seq refer to exactly one reaction.
      if (seq.getReactionsCatalyzed().size() > 1) {
        System.err.format("Assumption violation: sequence with source id %d refers to %d reactions (>1).  " +
            "Merging will not handly this case correctly.\n", seq.getUUID(), seq.getReactionsCatalyzed().size());
      }

      // Assumption: the reaction and seq will share an organism ID (why would they not?!
      if (!oldOrganismId.equals(seq.getOrgId())) {
        System.err.format("Assumption violation: reaction and seq don't share an organism id: %d != %d\n",
            oldOrganismId, seq.getOrgId());
      }

      // Store the seq document to get an id that'll be stored in the protein object.
      int seqId = api.getWriteDB().submitToActSeqDB(
          seq.get_srcdb(),
          seq.get_ec(),
          seq.get_org_name(),
          newOrganismId, // Use the reaction's new organism id to replace the old one.
          seq.get_sequence(),
          seq.get_references(),
          rxnIds, // Use the reaction's new id (also in substrates/products) instead of the old one.
          rxnToSubstrates,
          rxnToProducts,
          seq.getCatalysisSubstratesUniform(), // These should not have changed due to the migration.
          seq.getCatalysisSubstratesDiverse(),
          seq.getCatalysisProductsUniform(),
          seq.getCatalysisProductsDiverse(),
          seq.getSAR(),
          MongoDBToJSON.conv(seq.get_metadata())
      );
      // TODO: we should migrate all the seq documents with zero references over to the new DB.

      // Convert to Long to match ID type seen in MongoDB.  TODO: clean up all the IDs, make them all Longs.
      newSequenceIds.add(Long.valueOf(seqId));
    }
    // Store the migrated sequence ids for this protein.
    newProtein.put("sequences", new JSONArray(newSequenceIds));

    return newProtein;
  }

  private void mergeAllReactions() {
    /* Maintain stability by constructing the ordered set of minimum group reaction ids so that we can iterate
     * over reactions in the same order they occur in the source DB.  Stability makes life easier in a number of ways
     * (easier testing, deterministic output, general sanity) so we go to the trouble here. */
    final HashMap<Long, PriorityQueue<Long>> minGroupIdsToGroups = new HashMap<>(reactionGroups.size());
    for (Map.Entry<SubstratesProducts, PriorityQueue<Long>> entry : reactionGroups.entrySet()) {
      minGroupIdsToGroups.put(entry.getValue().peek(), entry.getValue());
    }

    List<Long> orderedIds = Arrays.asList(minGroupIdsToGroups.keySet().toArray(new Long[minGroupIdsToGroups.size()]));
    Collections.sort(orderedIds);

    for (Long nextId : orderedIds) {
      PriorityQueue<Long> groupIds = minGroupIdsToGroups.get(nextId);
      List<Reaction> reactions = new ArrayList<>(groupIds.size());
      for (Long id : groupIds) {
        // Since we've only installed reaction IDs based on instances we've seen, this should be safe.
        reactions.add(api.readReactionFromInKnowledgeGraph(id));
      }

      mergeReactions(reactions);
    }
  }
}
