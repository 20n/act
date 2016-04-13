package com.act.biointerpretation.reactionmerging;

import act.server.NoSQLAPI;
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
  private NoSQLAPI api;
  // Cache ids locally in case the appear repeatedly in the substrates/products.
  private Map<Long, Long> oldChemToNew = new HashMap<>();

  public static void main(String[] args) {
    ReactionMerger merger = new ReactionMerger();
    merger.run();
  }

  public ReactionMerger() {
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
    Map<SubstratesProducts, PriorityQueue<Long>> reactionGroups = hashReactions(rxns);

    // Merge all the reactions into one.
    Boolean areSubstratesAndProductsMerged = false;
    mergeAllReactions(reactionGroups, areSubstratesAndProductsMerged);
  }

  protected static Map<SubstratesProducts, PriorityQueue<Long>> hashReactions(Iterator<Reaction> reactionIterator) {
    HashMap<SubstratesProducts, PriorityQueue<Long>> reactionGroups = new HashMap<>();

    // Add the next available reaction to the map of substrates+products -> ids.
    // TODO: spill this map to disk if the map gets too large.
    while (reactionIterator.hasNext()) {
      Reaction rxn = reactionIterator.next();
      SubstratesProducts sp = new SubstratesProducts(rxn);
      PriorityQueue<Long> pq = reactionGroups.get(sp);
      Long id = Long.valueOf(rxn.getUUID());

      if (pq != null) {
        pq.add(id);
      } else {
        pq = new PriorityQueue<>(1);
        pq.add(id);
        reactionGroups.put(sp, pq);
      }
    }

    return reactionGroups;
  }

  public static class SubstratesProducts {
    // TODO: also consider ec-umber, coefficients, and other reaction attributes.
    Set<Long> substrates = null, products = null,
        substrateCofactors = null, productCofactors = null, coenzymes = null;
    Map<Long, Integer> substrateCoefficients = null, productCoefficients = null;
    String ecnum = null;
    ConversionDirectionType conversionDirectionType = null;
    StepDirection pathwayStepDirection = null;

    public SubstratesProducts(Reaction reaction) {
      // TODO: should we copy these to be safe, or just assume nobody will mess with them?
      this.substrates = new HashSet<>(Arrays.asList(reaction.getSubstrates()));
      this.products = new HashSet<>(Arrays.asList(reaction.getProducts()));
      this.substrateCofactors = new HashSet<>(Arrays.asList(reaction.getSubstrateCofactors()));
      this.productCofactors = new HashSet<>(Arrays.asList(reaction.getProductCofactors()));
      this.coenzymes = new HashSet<>(Arrays.asList(reaction.getCoenzymes()));

      this.substrateCoefficients = new HashMap<>(this.substrates.size());
      for (Long id : reaction.getAllCoefficientsOfSubstrates()) {
        this.substrateCoefficients.put(id, reaction.getSubstrateCoefficient(id));
      }

      this.productCoefficients = new HashMap<>(this.products.size());
      for (Long id : reaction.getAllCoefficientsOfProducts()) {
        this.productCoefficients.put(id, reaction.getProductCoefficient(id));
      }

      this.ecnum = reaction.getECNum();
      this.conversionDirectionType = reaction.getConversionDirection();
      this.pathwayStepDirection = reaction.getPathwayStepDirection();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SubstratesProducts that = (SubstratesProducts) o;

      if (!substrates.equals(that.substrates)) return false;
      if (!products.equals(that.products)) return false;
      if (!substrateCofactors.equals(that.substrateCofactors)) return false;
      if (!productCofactors.equals(that.productCofactors)) return false;
      if (!coenzymes.equals(that.coenzymes)) return false;
      if (!substrateCoefficients.equals(that.substrateCoefficients)) return false;
      if (!productCoefficients.equals(that.productCoefficients)) return false;
      if (ecnum != null ? !ecnum.equals(that.ecnum) : that.ecnum != null) return false;
      return conversionDirectionType == that.conversionDirectionType &&
          pathwayStepDirection == that.pathwayStepDirection;

    }

    @Override
    public int hashCode() {
      int result = substrates.hashCode();
      result = 31 * result + products.hashCode();
      result = 31 * result + substrateCofactors.hashCode();
      result = 31 * result + productCofactors.hashCode();
      result = 31 * result + coenzymes.hashCode();
      result = 31 * result + substrateCoefficients.hashCode();
      result = 31 * result + productCoefficients.hashCode();
      result = 31 * result + (ecnum != null ? ecnum.hashCode() : 0);
      result = 31 * result + (conversionDirectionType != null ? conversionDirectionType.hashCode() : 0);
      result = 31 * result + (pathwayStepDirection != null ? pathwayStepDirection.hashCode() : 0);
      return result;
    }
  }

  private Reaction mergeReactions(List<Reaction> reactions, Boolean substratesAndProductsAlreadyMigrated) {
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

    migrateChemicals(mergedReaction, fr, substratesAndProductsAlreadyMigrated);

    // Update the reaction in the DB with the newly migrated protein data.
    api.getWriteDB().updateActReaction(mergedReaction, newId);

    return mergedReaction;
  }

  public void migrateChemicals(Reaction newRxn, Reaction oldRxn, Boolean substratesAndProductsAlreadyMigrated) {
    Long[] oldSubstrates = oldRxn.getSubstrates();
    Long[] oldProducts = oldRxn.getProducts();


    // TODO: The below solution seems hacky.
    // If the substrates and products are already migrated, the migrated variables are the same as the old substrates and
    // products.
    Long[] migratedSubstrates = substratesAndProductsAlreadyMigrated ? oldSubstrates : translateToNewIds(oldSubstrates);
    Long[] migratedProducts = substratesAndProductsAlreadyMigrated ? oldProducts : translateToNewIds(oldProducts);

    // Substrate/product counts must be identical before and after migration.
    if (migratedSubstrates.length != oldSubstrates.length ||
        migratedProducts.length != oldProducts.length) {
      throw new RuntimeException(String.format(
          "Pre/post substrate/product migration lengths don't match for source reaction %d: %d -> %d, %d -> %d",
          oldRxn.getUUID(), oldSubstrates.length, migratedSubstrates.length, oldProducts.length, migratedProducts.length
      ));
    }

    newRxn.setSubstrates(migratedSubstrates);
    newRxn.setProducts(migratedProducts);

    // Copy over substrate/product coefficients one at a time based on index, which should be consistent.
    for (int i = 0; i < migratedSubstrates.length; i++) {
      newRxn.setSubstrateCoefficient(migratedSubstrates[i], oldRxn.getSubstrateCoefficient(oldSubstrates[i]));
    }

    for (int i = 0; i < migratedProducts.length; i++) {
      newRxn.setProductCoefficient(migratedProducts[i], oldRxn.getProductCoefficient(oldProducts[i]));
    }
  }

  private Long[] translateToNewIds(Long[] oldIds) {
    Long[] newIds = new Long[oldIds.length];
    for (int i = 0; i < oldIds.length; i++) {
      Long newId = oldChemToNew.get(oldIds[i]);
      if (newId == null) {
        Chemical achem = api.readChemicalFromInKnowledgeGraph(oldIds[i]);
        newId = api.writeToOutKnowlegeGraph(achem);
        oldChemToNew.put(oldIds[i], newId);
      }
      newIds[i] = newId;
    }
    return newIds;
  }

  // Cache seen organism ids locally to speed up migration.
  private HashMap<Long, Long> organismMigrationMap = new HashMap<>();
  private Long migrateOrganism(Long oldOrganismId) {
    if (organismMigrationMap.containsKey(oldOrganismId)) {
      return organismMigrationMap.get(oldOrganismId);
    }

    String organismName = api.getReadDB().getOrganismNameFromId(oldOrganismId);

    Long newOrganismId = null;

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

    }

    organismMigrationMap.put(oldOrganismId, newOrganismId);

    return newOrganismId;
  }

  private JSONObject migrateProteinData(JSONObject oldProtein, Long newRxnId, Reaction rxn) {
    // Copy the protein object for modification.
    // With help from http://stackoverflow.com/questions/12809779/how-do-i-clone-an-org-json-jsonobject-in-java.
    JSONObject newProtein = new JSONObject(oldProtein, JSONObject.getNames(oldProtein));

    /* Metacyc entries write an array of NCBI organism ids per protein, but do not reference organism name collection
     * entries.  Only worry about the "organism" field, which refers to the ID of an organism name entry. */
    if (oldProtein.has("organism")) {
      // BRENDA protein entries just have one organism, so the migration is a little easier.
      Long oldOrganismId = oldProtein.getLong("organism");
      Long newOrganismId = migrateOrganism(oldOrganismId);
      newProtein.put("organism", newOrganismId);
    }
    // TODO: unify the Protein object schema so this sort of handling isn't necessary.

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
        throw new RuntimeException(String.format(
                "Assumption violation: sequence with source id %d refers to %d reactions (>1).  " +
            "Merging will not handly this case correctly.\n", seq.getUUID(), seq.getReactionsCatalyzed().size()));
      }

      Long oldSeqOrganismId = seq.getOrgId();
      Long newSeqOrganismId = migrateOrganism(oldSeqOrganismId);

      // Store the seq document to get an id that'll be stored in the protein object.
      int seqId = api.getWriteDB().submitToActSeqDB(
          seq.get_srcdb(),
          seq.get_ec(),
          seq.get_org_name(),
          newSeqOrganismId, // Use freshly migrated organism id to replace the old one.
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

  public void mergeAllReactions(Map<SubstratesProducts, PriorityQueue<Long>> reactionGroups, Boolean substratesAndProductsAlreadyMigrated) {
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

      mergeReactions(reactions, substratesAndProductsAlreadyMigrated);
    }
  }
}
