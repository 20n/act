package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import com.act.biointerpretation.step2_desalting.Desalter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

/**
 *
 * This class reads in reactions from a read DB and processes each one such that cofactors are binned together
 * in either substrate/product cofactor lists. It removes both concrete cofactors (ie, ones with precise inchis)
 * as well as abstract ones (ie, FAKE inchis).  It sequentially removes the cofactors in a prioritized manner until only
 * one substrate and product remain.
 *
 * Created by jca20n on 2/15/16.
 */
public class CofactorRemover {
  private static final String WRITE_DB = "jarvis";
  private static final String READ_DB = "actv01";
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

  private void updateReactionProductOrSubstrate(Reaction reaction, Boolean isSubstrate) {
    Long[] chemIds = isSubstrate ? reaction.getSubstrates() : reaction.getProducts();
    Set<Long> reactionCofactors = new HashSet<>();

    for (Long originalId : chemIds) {
      Chemical chemical = api.readChemicalFromInKnowledgeGraph(originalId);
      String inchi = chemical.getInChI();
      Long newId;

      // If the inchi is in the cofactor list or a component of the inchi is in the cofactor list.
      if (cofactorsCorpus.getInchiToName().containsKey(inchi) || (inchi.contains(FAKE) &&
          (fakeFinder.scanAndReturnCofactorNameIfItExists(chemical) != null))) {
        // If the chemical's ID maps to a single pre-seen entry, use its existing old id
        if (oldChemicalIdToNewChemicalId.containsKey(originalId)) {
          reactionCofactors.add(originalId);
          continue;
        }

        chemical.setAsCofactor();
        newId = api.writeToOutKnowlegeGraph(chemical);
        reactionCofactors.add(originalId);

        oldChemicalIdToNewChemicalId.put(originalId, newId);
        continue;
      }

      if (!oldChemicalIdToNewChemicalId.containsKey(originalId)) {
        newId = api.writeToOutKnowlegeGraph(chemical);
        oldChemicalIdToNewChemicalId.put(originalId, newId);
      }
    }

    List<Long> newSubstratesOrProductsList = new ArrayList<>();
    List<Long> newSubstrateOrProductCofactorsList = new ArrayList<>();
    Map<Long, Integer> newSubstratesOrProductsCoefficientsList = new HashMap<>();

    for (Long oldId : chemIds) {
      Long newId = oldChemicalIdToNewChemicalId.get(oldId);
      if (reactionCofactors.contains(oldId)) {
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
