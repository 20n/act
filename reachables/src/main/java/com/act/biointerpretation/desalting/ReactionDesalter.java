package com.act.biointerpretation.desalting;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.BiointerpretationProcessor;
import com.act.biointerpretation.Utils.ReactionComponent;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.act.biointerpretation.Utils.ReactionComponent.PRODUCT;
import static com.act.biointerpretation.Utils.ReactionComponent.SUBSTRATE;

/**
 * ReactionDesalter itself does the processing of the database using an instance of Desalter.
 * This class creates Synapse from Dr. Know.  Synapse is the database in which the chemicals
 * have been inspected for containing multiple species or ionized forms, and corrected.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionDesalter extends BiointerpretationProcessor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionDesalter.class);
  private static final String PROCESSOR_NAME = "Desalter";

  private static final String FAKE = "FAKE";

  // Don't use the superclass's maps, as we might convert one chemical into many.
  private Map<Long, List<Long>> oldChemicalIdToNewChemicalIds = new HashMap<>();
  private Map<String, Long> inchiToNewId = new HashMap<>();
  private Map<Pair<Long, Long>, Integer> desalterMultiplerMap = new HashMap<>(); // Old + new ids -> coeff. multipler.
  private Desalter desalter;
  private int desalterFailuresCounter = 0;

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  public ReactionDesalter(NoSQLAPI inputApi) {
    super(inputApi);
  }

  @Override
  public void init() throws IOException, ReactionException, LicenseProcessingException {
    desalter = new Desalter(new ReactionProjector());
    desalter.initReactors();
    markInitialized();
  }

  /**
   * This function reads the products and reactions from the db, desalts them and writes it back.
   */
  @Override
  public void run() throws IOException, LicenseProcessingException, ReactionException {
    failIfNotInitialized();

    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();

    desaltAllChemicals();
    desaltAllReactions();

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  public void desaltAllChemicals() throws IOException, LicenseProcessingException, ReactionException {
    Iterator<Chemical> chemicals = getNoSQLAPI().readChemsFromInKnowledgeGraph();
    while (chemicals.hasNext()) {
      Chemical chem = chemicals.next();
      desaltChemical(chem); // Ignore results, as the cached mapping will be used for reaction desalting.
    }
    LOGGER.info("Encountered %d failures while desalting all molecules", desalterFailuresCounter);
  }

  public void desaltAllReactions() throws IOException, LicenseProcessingException, ReactionException {
    //Scan through all Reactions and process each one.
    Iterator<Reaction> reactionIterator = getNoSQLAPI().readRxnsFromInKnowledgeGraph();

    while (reactionIterator.hasNext()) {
      Reaction oldRxn = reactionIterator.next();

      // I don't like modifying reaction objects in place, so we'll create a fresh one and write it to the new DB.
      Reaction desaltedReaction = new Reaction(
          -1, // Assume the id will be set when the reaction is written to the DB.
          new Long[0],
          new Long[0],
          new Long[0],
          new Long[0],
          new Long[0],
          oldRxn.getECNum(),
          oldRxn.getConversionDirection(),
          oldRxn.getPathwayStepDirection(),
          oldRxn.getReactionName(),
          oldRxn.getRxnDetailType()
      );

      // Add the data source and references from the source to the destination
      desaltedReaction.setDataSource(oldRxn.getDataSource());
      for (P<Reaction.RefDataSource, String> ref : oldRxn.getReferences()) {
        desaltedReaction.addReference(ref.fst(), ref.snd());
      }

      migrateReactionSubsProdsWCoeffs(desaltedReaction, oldRxn);

      int newId = getNoSQLAPI().writeToOutKnowlegeGraph(desaltedReaction);

      migrateAllProteins(desaltedReaction, oldRxn, Long.valueOf(oldRxn.getUUID()));

      // Update the reaction in the DB with the newly migrated protein data.
      getNoSQLAPI().getWriteDB().updateActReaction(desaltedReaction, newId);
    }

  }

  private void migrateReactionSubsProdsWCoeffs(Reaction newReaction, Reaction oldReaction) {
    {
      Pair<List<Long>, Map<Long, Integer>> newSubstratesAndCoefficients =
          buildIdAndCoefficientMapping(oldReaction, SUBSTRATE);
      newReaction.setSubstrates(newSubstratesAndCoefficients.getLeft().toArray(
          new Long[newSubstratesAndCoefficients.getLeft().size()]));
      newReaction.setAllSubstrateCoefficients(newSubstratesAndCoefficients.getRight());

      List<Long> newSubstrateCofactors = buildIdMapping(oldReaction.getSubstrateCofactors());
      newReaction.setSubstrateCofactors(newSubstrateCofactors.toArray(new Long[newSubstrateCofactors.size()]));
    }

    {
      Pair<List<Long>, Map<Long, Integer>> newProductsAndCoefficients =
          buildIdAndCoefficientMapping(oldReaction, PRODUCT);
      newReaction.setProducts(newProductsAndCoefficients.getLeft().toArray(
          new Long[newProductsAndCoefficients.getLeft().size()]));
      newReaction.setAllProductCoefficients(newProductsAndCoefficients.getRight());

      List<Long> newproductCofactors = buildIdMapping(oldReaction.getProductCofactors());
      newReaction.setProductCofactors(newproductCofactors.toArray(new Long[newproductCofactors.size()]));
    }
  }

  private List<Long> buildIdMapping(Long[] oldChemIds) {
    LinkedHashSet<Long> newIDs = new LinkedHashSet<>(oldChemIds.length);

    for (Long oldChemId : oldChemIds) {
      List<Long> newChemIds = oldChemicalIdToNewChemicalIds.get(oldChemId);
      if (newChemIds == null) {
        throw new RuntimeException(
            String.format("Found old chemical id %d that is not in the old -> new chem id map", oldChemId));
      }

      newIDs.addAll(newChemIds);
    }

    List<Long> results = new ArrayList<>();
    // TODO: does ArrayList's constructor also add all the hashed elements in order?  I know addAll does.
    results.addAll(newIDs);
    return results;
  }

  private Pair<List<Long>, Map<Long, Integer>> buildIdAndCoefficientMapping(Reaction oldRxn, ReactionComponent sOrP) {
    Long[] oldChemIds = sOrP == SUBSTRATE ? oldRxn.getSubstrates() : oldRxn.getProducts();
    List<Long> resultIds = new ArrayList<>(oldChemIds.length);
    Map<Long, Integer> newIdToCoefficientMap = new HashMap<>(oldChemIds.length);

    for (Long oldChemId : oldChemIds) {
      Integer originalRxnCoefficient = sOrP == SUBSTRATE ?
          oldRxn.getSubstrateCoefficient(oldChemId) : oldRxn.getProductCoefficient(oldChemId);

      List<Long> newChemIds = oldChemicalIdToNewChemicalIds.get(oldChemId);
      if (newChemIds == null) {
        throw new RuntimeException(
            String.format("Found old chemical id %d that is not in the old -> new chem id map", oldChemId));
      }

      for (Long newChemId : newChemIds) {
        // Deduplicate new chemicals in the list based on whether we've assigned coefficients for them or not.
        if (newIdToCoefficientMap.containsKey(newChemId)) {
          Integer coefficientAccumulator = newIdToCoefficientMap.get(newChemId);

          // If only one coefficient is null, we have a problem.  Just write null and hope we can figure it out later.
          if ((coefficientAccumulator == null && originalRxnCoefficient != null) ||
              (coefficientAccumulator != null && originalRxnCoefficient == null)) {
            LOGGER.error("Found null coefficient that needs to be merged with non-null coefficient. " +
                "New chem id: %d, old chem id: %d, coefficient value: %d, old rxn id: %d",
                newChemId, oldChemId, originalRxnCoefficient, oldRxn.getUUID());
            newIdToCoefficientMap.put(newChemId, null);
          } else if (coefficientAccumulator != null && originalRxnCoefficient != null) {
            /* If neither are null, multiply the coefficient to be added by the desalting multiplier and sum that
             * product with the existing count for this molecule. */
            Integer desalterMultiplier = desalterMultiplerMap.get(Pair.of(oldChemId, newChemId));
            originalRxnCoefficient *= desalterMultiplier;

            newIdToCoefficientMap.put(newChemId, coefficientAccumulator + originalRxnCoefficient);
          } // Else both are null we don't need to do anything.

          // We don't need to add this new id to the list of substrates/products because it's already there.
        } else {
          resultIds.add(newChemId); // Add the new id to the subs/prods list.
          Integer desalterMultiplier = desalterMultiplerMap.get(Pair.of(oldChemId, newChemId));
          if (originalRxnCoefficient == null) {
            if (!desalterMultiplier.equals(1)) {
              LOGGER.warn("Ignoring >1 desalting multipler due to existing null coefficient.  " +
                    "New chem id: %d, old chem id: %d, coefficient value: null, multiplier: %d, old rxn id: %d",
                newChemId, oldChemId, desalterMultiplier, oldRxn.getUUID());
            }
            newIdToCoefficientMap.put(newChemId, null);
          } else {
            newIdToCoefficientMap.put(newChemId, originalRxnCoefficient * desalterMultiplier);
          }
        }
      }
    }
    return Pair.of(resultIds, newIdToCoefficientMap);
  }


  /**
   * This function desalts a single chemical and returns the resulting ids of the modified chemicals that have been
   * written to the destination DB.  The results of the desalting process are also cached for later use in mapping
   * chemicals from the old DB to the new.  If the chemicals cannot be desalted, we just migrate the chemical unaltered.
   *
   * @param chemical A chemical to desalt.
   * @return A list of output ids of desalted chemicals
   */
  private List<Long> desaltChemical(Chemical chemical) throws IOException, ReactionException {
    Long originalId = chemical.getUuid();

    // If the chemical's ID maps to a single pre-seen entry, use its existing old id
    if (oldChemicalIdToNewChemicalIds.containsKey(originalId)) {
      LOGGER.error("desaltChemical was called on a chemical that was already desalted: %d", originalId);
    }

    // Otherwise need to clean the chemical
    String inchi = chemical.getInChI();

    // If it's FAKE, just go with it
    if (inchi.contains(FAKE)) {
      long newId = getNoSQLAPI().writeToOutKnowlegeGraph(chemical); //Write to the db
      List<Long> singletonId = Collections.unmodifiableList(Collections.singletonList(newId));
      inchiToNewId.put(inchi, newId);
      desalterMultiplerMap.put(Pair.of(originalId, newId), 1);
      oldChemicalIdToNewChemicalIds.put(originalId, singletonId);
      return singletonId;
    }

    Map<String, Integer> cleanedInchis = null;
    try {
      cleanedInchis = desalter.desaltMolecule(inchi);
    } catch (Exception e) {
      // TODO: probably should handle this error differently, currently just letting pass unaltered
      LOGGER.error(String.format("Exception caught when desalting chemical %d: %s", originalId, e.getMessage()));
      desalterFailuresCounter++;
      long newId = getNoSQLAPI().writeToOutKnowlegeGraph(chemical); //Write to the db
      List<Long> singletonId = Collections.singletonList(newId);
      inchiToNewId.put(inchi, newId);
      desalterMultiplerMap.put(Pair.of(originalId, newId), 1);
      oldChemicalIdToNewChemicalIds.put(originalId, singletonId);
      return Collections.singletonList(newId);
    }

    List<Long> newIds = new ArrayList<>();
    // For each cleaned chemical, put in DB or update ID
    for (Map.Entry<String, Integer> pair : cleanedInchis.entrySet()) {
      String cleanInchi = pair.getKey();
      // If the cleaned inchi is already in DB, use existing ID, and hash the id
      long newId;

      if (inchiToNewId.containsKey(cleanInchi)) {
        newId = inchiToNewId.get(cleanInchi);
      } else {
        // Otherwise update the chemical, put into DB, and hash the id and inchi
        chemical.setInchi(cleanInchi);
        newId = getNoSQLAPI().writeToOutKnowlegeGraph(chemical); // Write to the db
        inchiToNewId.put(cleanInchi, newId);
      }
      /* The desalter converts complex molecules into a set of unique fragments, but maitains a count of those
       * fragments.  That fragment count must be multiplied by any reaction-specific coefficient to get the
       * true count of fragments participating in a reaction.
       *
       * Because different salts may have different coefficients, we must key the multiplier on (oldId, newId) to
       * ensure that multiple occurrences of the same desalted molecule in a reaction don't overwrite each other.
       *
       * We must save every (oldId, newId) pair, as each pair will be unique even if newId was seen before. */
      desalterMultiplerMap.put(Pair.of(originalId, newId), pair.getValue());

      newIds.add(newId);
    }

    // Store and return the cached list of chemical ids that we just created.  Make them immutable for safety's sake.
    List<Long> resultsToCache = Collections.unmodifiableList(newIds);
    oldChemicalIdToNewChemicalIds.put(originalId, resultsToCache);
    return resultsToCache;
  }
}
