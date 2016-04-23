package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import com.act.biointerpretation.step4_mechanisminspection.BlacklistedInchisCorpus;
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
  private static final Logger LOGGER = LogManager.getLogger(CofactorRemover.class);
  private FakeCofactorFinder fakeFinder;
  private NoSQLAPI api;
  private CofactorsCorpus cofactorsCorpus;
  private Map<Long, Long> oldChemicalIdToNewChemicalId;
  private Map<Long, Chemical> readDBChemicalIdToChemical;
  private BlacklistedInchisCorpus blacklistedInchisCorpus;
  private enum REACTION_COMPONENT {
    SUBSTRATE,
    PRODUCT
  }

  public static void main(String[] args) throws Exception {
    NoSQLAPI.dropDB(WRITE_DB);
    CofactorRemover cofactorRemover = new CofactorRemover(new NoSQLAPI(READ_DB, WRITE_DB));
    cofactorRemover.loadCorpus();
    cofactorRemover.run();
  }

  public CofactorRemover(NoSQLAPI api) throws IOException {
    // Delete all records in the WRITE_DB
    this.api = api;
    oldChemicalIdToNewChemicalId = new HashMap<>();
    readDBChemicalIdToChemical = new HashMap<>();
    fakeFinder = new FakeCofactorFinder();
  }

  public void loadCorpus() throws IOException {
    cofactorsCorpus = new CofactorsCorpus();
    cofactorsCorpus.loadCorpus();

    blacklistedInchisCorpus = new BlacklistedInchisCorpus();
    blacklistedInchisCorpus.loadCorpus();
  }

  public void run() {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();
    ReactionMerger rxnMerger = new ReactionMerger(api);

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

    while (iterator.hasNext()) {

      // Get reaction from the read db
      Reaction rxn = iterator.next();
      int oldUUID = rxn.getUUID();
      Set<JSONObject> oldProteinData = new HashSet<>(rxn.getProteinData());

      // Remove all coenzymes from the reaction
      removeCoenzymesFromReaction(rxn);

      // Make sure the there are enough products and substrates in the processed reaction
      if (rxn.getSubstrates().length == 0 || rxn.getProducts().length == 0) {
        LOGGER.warn(String.format("Reaction does not have any products or substrates after coenzyme removal. The reaction id is: %d", rxn.getUUID()));
        continue;
      }

      // Bump up the cofactors to the cofactor list and update all substrates/products and their coefficients accordingly.
      updateReactionProductOrSubstrate(rxn, REACTION_COMPONENT.SUBSTRATE);
      updateReactionProductOrSubstrate(rxn, REACTION_COMPONENT.PRODUCT);

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

    reaction.setSubstrates(newSubstrateIds.toArray(new Long[newSubstrateIds.size()]));
    reaction.setProducts(newProductIds.toArray(new Long[newProductIds.size()]));
  }

  /**
   * This function is the meat of the cofactor removal process. It picks out cofactors based on rankings until there
   * is only one left. Based on that, it makes sure the update reaction's coefficients are correctly arranged.
   * @param reaction The reaction that is being updated
   * @param component A substrate or product
   */
  private void updateReactionProductOrSubstrate(Reaction reaction, REACTION_COMPONENT component) {
    Long[] chemIds = (component == REACTION_COMPONENT.SUBSTRATE) ? reaction.getSubstrates() : reaction.getProducts();
    Set<Long> oldIdsThatAreReactionCofactors = new HashSet<>();

    TreeMap<Integer, List<Long>> cofactorRankToId = new TreeMap<>();
    Set<Long> idsWithFakeInchis = new HashSet<>();

    // First, we find all the possible candidates for cofactors in the reaction substrate or product lists.
    for (Long originalId : chemIds) {
      Chemical chemical = api.readChemicalFromInKnowledgeGraph(originalId);
      String inchi = blacklistedInchisCorpus.renameInchiIfFoundInBlacklist(chemical.getInChI());

      if (!readDBChemicalIdToChemical.containsKey(originalId)) {
        readDBChemicalIdToChemical.put(originalId, chemical);
      }

      if (cofactorsCorpus.getInchiToName().containsKey(inchi)) {
        List<Long> idsOfSameRank = cofactorRankToId.get(cofactorsCorpus.getInchiToRank().get(inchi));
        if (idsOfSameRank == null) {
          idsOfSameRank = new ArrayList<>();
          cofactorRankToId.put(cofactorsCorpus.getInchiToRank().get(inchi), idsOfSameRank);
        }
        idsOfSameRank.add(originalId);
        continue;
      }

      // TODO: Abstract the Fake inchi checks into its own utility class.
      if (inchi.contains(FAKE) && (fakeFinder.scanAndReturnCofactorNameIfItExists(chemical) != null)) {
        idsWithFakeInchis.add(originalId);
      }
    }

    // For all the possible cofactor matches, add the picked ones (based on rank and number of reactants left) to the
    // final list of cofactors. The order is imposed by cofactorRankToId's key set, which is in ascending order.
    List<Long> orderedListOfCofactorIds = new ArrayList<>();
    for (Map.Entry<Integer, List<Long>> entry : cofactorRankToId.entrySet()) {
      orderedListOfCofactorIds.addAll(entry.getValue());
    }
    orderedListOfCofactorIds.addAll(idsWithFakeInchis.stream().collect(Collectors.toList()));

    // If all the chem ids are cofactors, then use the lowest priority chemical as the representation product/substrate.
    // Else, all the substrates/products are just the difference between all the chemicals and the cofactor ids.
    Set<Long> setOfNonCofactors = new HashSet<>(Arrays.asList(chemIds));
    setOfNonCofactors.removeAll(orderedListOfCofactorIds);

    if (setOfNonCofactors.size() == 0) {
      setOfNonCofactors.add(orderedListOfCofactorIds.get(orderedListOfCofactorIds.size() - 1));
      orderedListOfCofactorIds.remove(orderedListOfCofactorIds.size() - 1);
    }

    for (Long cofactorId : orderedListOfCofactorIds) {
      Chemical chemical = readDBChemicalIdToChemical.get(cofactorId);
      Long newId;

      // If the chemical's ID maps to a single pre-seen entry, use its existing old id
      if (oldChemicalIdToNewChemicalId.containsKey(cofactorId)) {
        oldIdsThatAreReactionCofactors.add(cofactorId);
        continue;
      }

      chemical.setAsCofactor();
      newId = api.writeToOutKnowlegeGraph(chemical);
      oldIdsThatAreReactionCofactors.add(cofactorId);
      oldChemicalIdToNewChemicalId.put(cofactorId, newId);
    }

    for (Long nonCofactorId : setOfNonCofactors) {
      if (!oldChemicalIdToNewChemicalId.containsKey(nonCofactorId)) {
        Chemical chemical = readDBChemicalIdToChemical.get(nonCofactorId);
        Long newId = api.writeToOutKnowlegeGraph(chemical);
        oldChemicalIdToNewChemicalId.put(nonCofactorId, newId);
      }
    }

    List<Long> newSubstratesOrProductsList = new ArrayList<>();
    List<Long> newSubstrateOrProductCofactorsList = new ArrayList<>();
    Map<Long, Integer> newSubstratesOrProductsCoefficientsList = new HashMap<>();

    for (Long oldId : chemIds) {
      Long newId = oldChemicalIdToNewChemicalId.get(oldId);
      if (oldIdsThatAreReactionCofactors.contains(oldId)) {
        newSubstrateOrProductCofactorsList.add(newId);
      } else {
        newSubstratesOrProductsList.add(newId);
        if (component == REACTION_COMPONENT.SUBSTRATE) {
          newSubstratesOrProductsCoefficientsList.put(newId, reaction.getSubstrateCoefficient(oldId));
        } else {
          newSubstratesOrProductsCoefficientsList.put(newId, reaction.getProductCoefficient(oldId));
        }
      }
    }

    // Update the reaction based on the categorized cofactors/non-cofactors.
    if (component == REACTION_COMPONENT.SUBSTRATE) {
      reaction.setSubstrates(newSubstratesOrProductsList.toArray(new Long[newSubstratesOrProductsList.size()]));
      reaction.setSubstrateCofactors(
          newSubstrateOrProductCofactorsList.toArray(new Long[newSubstrateOrProductCofactorsList.size()]));
      reaction.setAllSubstrateCoefficients(newSubstratesOrProductsCoefficientsList);
    } else {
      reaction.setProducts(newSubstratesOrProductsList.toArray(new Long[newSubstratesOrProductsList.size()]));
      reaction.setProductCofactors(
          newSubstrateOrProductCofactorsList.toArray(new Long[newSubstrateOrProductCofactorsList.size()]));
      reaction.setAllProductCoefficients(newSubstratesOrProductsCoefficientsList);
    }
  }
}
