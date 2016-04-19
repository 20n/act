package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import com.act.biointerpretation.step2_desalting.Desalter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * This class reads in reactions from a read DB and processes each one such that cofactors are binned together
 * in either substrate/product cofactor lists. It removes both concrete cofactors (ie, ones with precise inchis)
 * as well as abstract ones (ie, FAKE inchis).  It sequentially removes the cofactors in a prioritized manner until only
 * one substrate and product remain.
 *
 * Uniqueness in this database is the matching of:
 * 1. the remaining substrate
 * 2. the remaining product
 * 3. the names of the substrate cofactors
 * 4. the names of the product cofactors
 *
 * Created by jca20n on 2/15/16.
 */
public class CofactorRemover {
  private static final String WRITE_DB = "jarvis";
  private static final String READ_DB = "synapse";
  private static final String FAKE = "FAKE";
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);
  private FakeCofactorFinder fakeFinder;
  private NoSQLAPI api;
  private CofactorsCorpus cofactorsCorpus;
  private Map<Long, Long> oldChemicalIdToNewChemicalId;

  public static void main(String[] args) throws Exception {
    NoSQLAPI.dropDB(WRITE_DB);
    CofactorRemover cofactorRemover = new CofactorRemover(new NoSQLAPI(READ_DB, WRITE_DB));
    cofactorRemover.run();
  }

  public CofactorRemover(NoSQLAPI api) throws IOException {
    // Delete all records in the WRITE_DB
    this.api = api;
    oldChemicalIdToNewChemicalId = new HashMap<>();
    fakeFinder = new FakeCofactorFinder();

    cofactorsCorpus = new CofactorsCorpus();
    cofactorsCorpus.hydrateCorpus();
  }

  public void run() {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();
    ReactionMerger rxnMerger = new ReactionMerger(api);

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

    while (iterator.hasNext()) {

      Reaction rxn = iterator.next();
      int oldUUID = rxn.getUUID();
      Set<JSONObject> oldProteinData = new HashSet<>(rxn.getProteinData());
      Set<Long> oldIds = new HashSet<>(Arrays.asList(rxn.getSubstrates()));
      oldIds.addAll(Arrays.asList(rxn.getProducts()));

      removeCoenzymesFromReaction(rxn);

      if (rxn.getSubstrates().length == 0 || rxn.getProducts().length == 0) {
        LOGGER.debug("Reaction does not have any products or substrates after coenzyme removal. The reaction id is: %d", rxn.getUUID());
        continue;
      }

      updateReactionProductOrSubstrate(rxn, true);
      updateReactionProductOrSubstrate(rxn, false);

      int newId = api.writeToOutKnowlegeGraph(rxn);

      rxn.removeAllProteinData();

      for (JSONObject protein : oldProteinData) {
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        protein.put("source_reaction_id", oldUUID);
        JSONObject newProteinData = rxnMerger.migrateProteinData(protein, Long.valueOf(newId), rxn);
        rxn.addProteinData(newProteinData);
      }

      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(rxn, newId);
    }

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  /**
   * The function removes similar chemicals from the substrates and products (conenzymes) and remove duplicates
   * within each category.
   * @param reaction The reaction being updated.
   */
  private void removeCoenzymesFromReaction(Reaction reaction) {
    Set<Long> intersectionBetweenSubstrateAndProductIds = new HashSet<>(Arrays.asList(reaction.getSubstrates()));
    intersectionBetweenSubstrateAndProductIds.retainAll(new HashSet<>(Arrays.asList(reaction.getProducts())));

    List<Long> newSubstrateIds = new ArrayList<>();
    List<Long> newProductIds = new ArrayList<>();

    for (Long substrateId : reaction.getSubstrates()) {
      if (!intersectionBetweenSubstrateAndProductIds.contains(substrateId)) {
        newSubstrateIds.add(substrateId);
      }
    }

    for (Long productId : reaction.getProducts()) {
      if (!intersectionBetweenSubstrateAndProductIds.contains(productId)) {
        newProductIds.add(productId);
      }
    }

    Long[] newReactionSubstrates = new Long[newSubstrateIds.size()];
    newSubstrateIds.toArray(newReactionSubstrates);
    reaction.setSubstrates(newReactionSubstrates);

    Long[] newReactionProducts = new Long[newProductIds.size()];
    newProductIds.toArray(newReactionProducts);
    reaction.setProducts(newReactionProducts);
  }

  /**
   * This function is the meat of the cofactor removal process. It picks out cofactors based on rankings until there
   * is only one left. Based on that, it makes sure the update reaction's coefficients are correctly arranged.
   * @param reaction The reaction that is being updated
   * @param isSubstrate A boolean where True indicates we are processing a substrate and false if for product.
   */
  private void updateReactionProductOrSubstrate(Reaction reaction, Boolean isSubstrate) {
    Long[] chemIds = isSubstrate ? reaction.getSubstrates() : reaction.getProducts();
    Map<Long, Chemical> oldIdToChemical = new HashMap<>();
    Set<Long> reactionCofactorsForOldIds = new HashSet<>();

    TreeMap<Integer, Long> cofactorRankToId = new TreeMap<>();
    Set<Long> idsWithFakeInchis = new HashSet<>();

    // First, we find all the possible candidates for cofactors in the reaction substrate or product lists.
    for (Long originalId : chemIds) {
      Chemical chemical = api.readChemicalFromInKnowledgeGraph(originalId);
      String inchi = chemical.getInChI();
      oldIdToChemical.put(originalId, chemical);

      if (cofactorsCorpus.getInchiToName().containsKey(inchi)) {
        cofactorRankToId.put(cofactorsCorpus.getInchiToRank().get(inchi), originalId);
      }

      if (inchi.contains(FAKE) && (fakeFinder.scanAndReturnCofactorNameIfItExists(chemical) != null)) {
        idsWithFakeInchis.add(originalId);
      }
    }

    // For all the possible cofactor matches, add the picked ones (based on rank and number of reactants left) to the
    // final list of cofactors.
    List<Long> orderedListOfCofactorIds =
        cofactorRankToId.entrySet().stream().map(entry -> entry.getValue()).collect(Collectors.toList());
    orderedListOfCofactorIds.addAll(idsWithFakeInchis.stream().collect(Collectors.toList()));

    Set<Long> setOfCofactorIds = new HashSet<>();

    int counter = 0;
    for (Long cofactorId : orderedListOfCofactorIds) {
      // Leave at least one molecule on either the substrate or product side of the reaction.
      if (counter >= chemIds.length - 1) {
        break;
      }
      counter++;

      // remove the cofactor ids from the nonCofactor list.
      setOfCofactorIds.add(cofactorId);
      Chemical chemical = oldIdToChemical.get(cofactorId);
      Long newId;

      // If the chemical's ID maps to a single pre-seen entry, use its existing old id
      if (oldChemicalIdToNewChemicalId.containsKey(cofactorId)) {
        reactionCofactorsForOldIds.add(cofactorId);
        continue;
      }

      chemical.setAsCofactor();
      newId = api.writeToOutKnowlegeGraph(chemical);
      reactionCofactorsForOldIds.add(cofactorId);
      oldChemicalIdToNewChemicalId.put(cofactorId, newId);
    }

    // Filter the unrecognized chemical ids (the ones that are not cofactors), write them to the db and add them to a set.
    Set<Long> setOfNonCofactors = new HashSet<>(Arrays.asList(chemIds));
    setOfNonCofactors.removeAll(setOfCofactorIds);

    for (Long nonCofactorId : setOfNonCofactors) {
      if (!oldChemicalIdToNewChemicalId.containsKey(nonCofactorId)) {
        Chemical chemical = oldIdToChemical.get(nonCofactorId);
        Long newId = api.writeToOutKnowlegeGraph(chemical);
        oldChemicalIdToNewChemicalId.put(nonCofactorId, newId);
      }
    }

    List<Long> newSubstratesOrProductsList = new ArrayList<>();
    List<Long> newSubstrateOrProductCofactorsList = new ArrayList<>();
    Map<Long, Integer> newSubstratesOrProductsCoefficientsList = new HashMap<>();

    for (Long oldId : chemIds) {
      Long newId = oldChemicalIdToNewChemicalId.get(oldId);
      if (reactionCofactorsForOldIds.contains(oldId)) {
        newSubstrateOrProductCofactorsList.add(newId);
      } else {
        newSubstratesOrProductsList.add(newId);
        if (isSubstrate) {
          newSubstratesOrProductsCoefficientsList.put(newId, reaction.getSubstrateCoefficient(oldId));
        } else {
          newSubstratesOrProductsCoefficientsList.put(newId, reaction.getProductCoefficient(oldId));
        }
      }
    }

    // Update the reaction based on the categorized cofactors/non-cofactors.
    Long[] newSubstratesOrProducts = new Long[newSubstratesOrProductsList.size()];
    newSubstratesOrProductsList.toArray(newSubstratesOrProducts);

    Long[] newSubstrateOrProductCofactors = new Long[newSubstrateOrProductCofactorsList.size()];
    newSubstrateOrProductCofactorsList.toArray(newSubstrateOrProductCofactors);

    if (isSubstrate) {
      reaction.setSubstrates(newSubstratesOrProducts);
      reaction.setSubstrateCofactors(newSubstrateOrProductCofactors);
      reaction.setAllSubstrateCoefficients(newSubstratesOrProductsCoefficientsList);
    } else {
      reaction.setProducts(newSubstratesOrProducts);
      reaction.setProductCofactors(newSubstrateOrProductCofactors);
      reaction.setAllProductCoefficients(newSubstratesOrProductsCoefficientsList);
    }
  }
}
