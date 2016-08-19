package com.act.biointerpretation.reactionmerging;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import act.shared.helpers.P;
import com.act.biointerpretation.BiointerpretationProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
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
public class ReactionMerger extends BiointerpretationProcessor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionMerger.class);
  private static final String PROCESSOR_NAME = "Reaction Merger";

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  public ReactionMerger(NoSQLAPI noSQLAPI) {
    super(noSQLAPI);
  }

  @Override
  public void init() {
    // Do nothing for this class, as there's no initialization necessary.
    markInitialized();
  }

  @Override
  public void processReactions() {
    LOGGER.info("Reading all reactions");
    Iterator<Reaction> rxns = getNoSQLAPI().readRxnsFromInKnowledgeGraph();
    Map<SubstratesProducts, PriorityQueue<Long>> reactionGroups = hashReactions(rxns);
    LOGGER.info("Found %d reaction groups, merging", reactionGroups.size());
    mergeAllReactions(reactionGroups);
    LOGGER.info("Done merging reactions");
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
      for (Long id : reaction.getSubstrateIdsOfSubstrateCoefficients()) {
        this.substrateCoefficients.put(id, reaction.getSubstrateCoefficient(id));
      }

      this.productCoefficients = new HashMap<>(this.products.size());
      for (Long id : reaction.getProductIdsOfProductCoefficients()) {
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
    int newId = getNoSQLAPI().writeToOutKnowlegeGraph(mergedReaction);

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
        super.reactionMigrationMap.put((long) r.getUUID(), (long) newId);
        JSONObject newProteinData = migrateProteinData(protein);
        mergedReaction.addProteinData(newProteinData);
      }

      // Set the data source as MERGED if this is a combination of multiple sources.  The protein data will store which.
      if (mergedReaction.getDataSource() != Reaction.RxnDataSource.MERGED &&
          mergedReaction.getDataSource() != r.getDataSource()) {
        mergedReaction.setDataSource(Reaction.RxnDataSource.MERGED);
      }
    }

    migrateReactionChemicals(mergedReaction, fr);

    // Update the reaction in the DB with the newly migrated protein data.
    getNoSQLAPI().getWriteDB().updateActReaction(mergedReaction, newId);

    return mergedReaction;
  }

  protected void mergeAllReactions(Map<SubstratesProducts, PriorityQueue<Long>> reactionGroups) {
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
        reactions.add(getNoSQLAPI().readReactionFromInKnowledgeGraph(id));
      }

      mergeReactions(reactions);
    }
  }
}
