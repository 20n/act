package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.Utils.ReactionComponent;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import com.act.biointerpretation.step4_mechanisminspection.BlacklistedInchisCorpus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.act.biointerpretation.Utils.ReactionComponent.PRODUCT;
import static com.act.biointerpretation.Utils.ReactionComponent.SUBSTRATE;

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
  private static final Logger LOGGER = LogManager.getFormatterLogger(CofactorRemover.class);

  private static final String WRITE_DB = "jarvis";
  private static final String READ_DB = "synapse";
  private static final String FAKE = "FAKE";

  private FakeCofactorFinder fakeFinder;
  private NoSQLAPI api;
  private CofactorsCorpus cofactorsCorpus;
  private Map<Long, Long> oldChemicalIdToNewChemicalId;
  private Set<Long> knownCofactorOldIds;

  private BlacklistedInchisCorpus blacklistedInchisCorpus;


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
    knownCofactorOldIds = new HashSet<>();
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

    findAllCofactors();
    removeAllCofactors();

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  private void findAllCofactors() {
    Iterator<Chemical> chemicals = api.readChemsFromInKnowledgeGraph();
    while (chemicals.hasNext()) {
      Chemical chem = chemicals.next();
      checkIfCofactorAndMigrate(chem); // Ignore results, as the cached mapping will be used for cofactor removal.
    }

    LOGGER.info("Found %d cofactors amongst %d migrated chemicals",
        knownCofactorOldIds.size(), oldChemicalIdToNewChemicalId.size());
  }

  private void removeAllCofactors() {
    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

    ReactionMerger rxnMerger = new ReactionMerger(api);

    while (iterator.hasNext()) {

      // Get reaction from the read db
      Reaction rxn = iterator.next();
      int oldUUID = rxn.getUUID();

      // Remove all coenzymes from the reaction
      removeCoenzymesFromReaction(rxn);

      // Make sure the there are enough products and substrates in the processed reaction
      if (rxn.getSubstrates().length == 0 || rxn.getProducts().length == 0) {
        LOGGER.warn(String.format("Reaction does not have any products or substrates after coenzyme removal. The reaction id is: %d", rxn.getUUID()));
        continue;
      }

      // Bump up the cofactors to the cofactor list and update all substrates/products and their coefficients accordingly.
      updateReactionProductOrSubstrate(rxn, SUBSTRATE);
      updateReactionProductOrSubstrate(rxn, PRODUCT);

      int newId = api.writeToOutKnowlegeGraph(rxn);

      Set<JSONObject> oldProteinData = new HashSet<>(rxn.getProteinData());
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
  }

  /**
   * The function removes similar chemicals from the substrates and products (conenzymes) and remove duplicates
   * within each category.
   * @param reaction The reaction being updated.
   */
  private void removeCoenzymesFromReaction(Reaction reaction) {
    // Build ordered sets of the substrates/products.
    LinkedHashSet<Long> substrates = new LinkedHashSet<>(Arrays.asList(reaction.getSubstrates()));
    LinkedHashSet<Long> products = new LinkedHashSet<>(Arrays.asList(reaction.getProducts()));

    // Compute the intersection between the sets.
    Set<Long> intersection = new HashSet<>(substrates);
    intersection.retainAll(products);

    // A - int(A, B) = A / B
    substrates.removeAll(intersection);
    products.removeAll(intersection);

    // Update the reaction with the new (ordered) substrates/products + coenzymes.
    reaction.setSubstrates(substrates.toArray(new Long[substrates.size()]));
    reaction.setProducts(products.toArray(new Long[products.size()]));

    // Keep any existing coenzymes, but don't use them when computing the difference--they might be there for a reason.
    intersection.addAll(Arrays.asList(reaction.getCoenzymes()));
    reaction.setCoenzymes(intersection.toArray(new Long[intersection.size()]));
  }

  /**
   * This function is the meat of the cofactor removal process. It picks out cofactors based on rankings until there
   * is only one left. Based on that, it makes sure the update reaction's coefficients are correctly arranged.
   * @param reaction The reaction that is being updated
   * @param component A substrate or product
   */
  private void updateReactionProductOrSubstrate(Reaction reaction, ReactionComponent component) {
    Long[] chemIds, originalCofactorIds;
    if (component == SUBSTRATE) {
      chemIds = reaction.getSubstrates();
      originalCofactorIds = reaction.getSubstrateCofactors();
    } else {
      chemIds = reaction.getProducts();
      originalCofactorIds = reaction.getProductCofactors();
    }

    Map<Boolean, List<Long>> partitionedIds =
        Arrays.asList(chemIds).stream().collect(Collectors.partitioningBy(knownCofactorOldIds::contains));

    // Strictly map the old cofactor/non-cofactor ids to their new counterparts.
    List<Long> oldCofactorIds = partitionedIds.containsKey(true) ? partitionedIds.get(true) : Collections.EMPTY_LIST;

    // Retain previously partitioned cofactors if any exist.
    if (originalCofactorIds != null && originalCofactorIds.length > 0) {
      // Use a set to unique the partitioned and previously specified cofactors.  Original cofactors go first.
      LinkedHashSet<Long> uniqueCofactorIds = new LinkedHashSet<>(Arrays.asList(originalCofactorIds));
      uniqueCofactorIds.addAll(oldCofactorIds);
      oldCofactorIds = new ArrayList<>(uniqueCofactorIds);
    }

    List<Long> newCofactorIds = new ArrayList<>(oldCofactorIds.size());
    for (Long oldId : oldCofactorIds) {
      Long newId = oldChemicalIdToNewChemicalId.get(oldId);
      if (newId == null) {
        throw new RuntimeException(String.format("Unable to map checmical %d for reaction %d to new id",
            oldId, reaction.getUUID()));
      }
      newCofactorIds.add(newId);
    }

    List<Long> oldNonCofactorIds =
        partitionedIds.containsKey(false) ? partitionedIds.get(false) : Collections.EMPTY_LIST;
    List<Pair<Long, Long>> oldToNewNonCofactorIds = new ArrayList<>(oldNonCofactorIds.size());
    for (Long oldId : oldNonCofactorIds) {
      Long newId = oldChemicalIdToNewChemicalId.get(oldId);
      if (newId == null) {
        throw new RuntimeException(String.format("Unable to map checmical %d for reaction %d to new id",
            oldId, reaction.getUUID()));
      }
      oldToNewNonCofactorIds.add(Pair.of(oldId, newId));
    }

    // Update the reaction based on the categorized cofactors/non-cofactors.
    Map<Long, Integer> coefficients = new HashMap<>(oldToNewNonCofactorIds.size());
    if (component == SUBSTRATE) {
      reaction.setSubstrateCofactors(newCofactorIds.toArray(new Long[newCofactorIds.size()]));
      reaction.setSubstrates(oldToNewNonCofactorIds.stream().map(Pair::getRight).toArray(Long[]::new));
      // Don't use streams here, as null coefficients can cause them to choke.
      for (Pair<Long, Long> p : oldToNewNonCofactorIds) {
        coefficients.put(p.getRight(), reaction.getSubstrateCoefficient(p.getLeft()));
      }
      reaction.setAllSubstrateCoefficients(coefficients);
    } else {
      reaction.setProductCofactors(newCofactorIds.toArray(new Long[newCofactorIds.size()]));
      reaction.setProducts(oldToNewNonCofactorIds.stream().map(Pair::getRight).toArray(Long[]::new));
      for (Pair<Long, Long> p : oldToNewNonCofactorIds) {
        coefficients.put(p.getRight(), reaction.getProductCoefficient(p.getLeft()));
      }
      reaction.setAllProductCoefficients(coefficients);
    }
  }

  private boolean checkIfCofactorAndMigrate(Chemical chemical) {
    Long oldId = chemical.getUuid();

    // If the chemical's ID maps to a single pre-seen entry, reuse its previous determination.
    if (oldChemicalIdToNewChemicalId.containsKey(oldId)) {
      return knownCofactorOldIds.contains(oldId);
    }

    // First, check if the InChI needs to be updated.  A few cofactors are known to have broken InChIs.
    String inchi = blacklistedInchisCorpus.renameInchiIfFoundInBlacklist(chemical.getInChI());
    chemical.setInchi(inchi);

    boolean isCofactor = false;
    if (cofactorsCorpus.getInchiToName().containsKey(inchi)) {
      isCofactor = true;
    } else if (inchi.contains(FAKE) && (fakeFinder.scanAndReturnCofactorNameIfItExists(chemical) != null)) {
      // TODO: Abstract the Fake inchi checks into its own utility class.
      isCofactor = true;
    }

    // Set isCofactor *without* looking at previous determinations.  This is the single source of truth for cofactors.
    chemical.setIsCofactor(isCofactor);
    if (isCofactor) {
      knownCofactorOldIds.add(oldId);
    }

    Long newId = api.writeToOutKnowlegeGraph(chemical);
    oldChemicalIdToNewChemicalId.put(oldId, newId);

    return isCofactor;
  }
}
