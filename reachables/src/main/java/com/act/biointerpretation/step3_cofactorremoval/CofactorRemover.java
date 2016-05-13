package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.BiointerpretationProcessor;
import com.act.biointerpretation.Utils.ReactionComponent;
import com.act.biointerpretation.step4_mechanisminspection.BlacklistedInchisCorpus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
public class CofactorRemover extends BiointerpretationProcessor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(CofactorRemover.class);
  private static final String PROCESSOR_NAME = "Cofactor Remover";

  private static final String FAKE = "FAKE";

  private FakeCofactorFinder fakeFinder;
  private CofactorsCorpus cofactorsCorpus;
  private Set<Long> knownCofactorOldIds = new HashSet<>();
  private Set<Long> knownCofactorNewIds = null;

  private BlacklistedInchisCorpus blacklistedInchisCorpus;

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  public CofactorRemover(NoSQLAPI api) {
    super(api);
    fakeFinder = new FakeCofactorFinder();
  }

  public void init() throws IOException {
    cofactorsCorpus = new CofactorsCorpus();
    cofactorsCorpus.loadCorpus();

    blacklistedInchisCorpus = new BlacklistedInchisCorpus();
    blacklistedInchisCorpus.loadCorpus();

    markInitialized();
  }

  @Override
  protected Chemical runSpecializedChemicalProcessing(Chemical chem) {
    return assignCofactorStatus(chem);
  }

  private Chemical assignCofactorStatus(Chemical chemical) {
    Long oldId = chemical.getUuid();

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

    return chemical;
  }

  @Override
  protected void afterProcessChemicals() {
    LOGGER.info("Found %d cofactors amongst %d migrated chemicals",
        knownCofactorOldIds.size(), getOldChemIdToNewChemId().size());
    LOGGER.info("Building cofactor status map for new chemical ids to facilitate cofactor removal");

    knownCofactorNewIds = new HashSet<>(knownCofactorOldIds.size());
    for (Long oldId : knownCofactorOldIds) {
      knownCofactorNewIds.add(mapOldChemIdToNewId(oldId));
    }

    if (knownCofactorNewIds.size() != knownCofactorOldIds.size()) {
      String msg = String.format("Old and new cofactor id sets to not match in size: %d vs. %d",
          knownCofactorOldIds.size(), knownCofactorNewIds.size());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
    LOGGER.info("New cofactor id map constructed, ready to process reactions.");
    /* TODO: we want to prevent any further access to the old map of ids to avoid accidental use instead of
     * knownCofactorNewIds.  Is there a better way than this? */
    knownCofactorOldIds = null;
  }

  @Override
  protected Reaction preProcessReaction(Reaction rxn) {
    findAndIsolateCoenzymesFromReaction(rxn);
    // Make sure the there are enough co/products and co/substrates in the processed reaction
    if ((rxn.getSubstrates().length == 0 && rxn.getSubstrateCofactors().length == 0) ||
        (rxn.getProducts().length == 0 && rxn.getProductCofactors().length == 0)) {
      LOGGER.warn("Reaction %d does not have any products or substrates after coenzyme removal.", rxn.getUUID());
      return null;
    }

    return rxn;
  }

  /**
   * The function removes similar chemicals from the substrates and products (conenzymes) and remove duplicates
   * within each category.
   * @param reaction The reaction being updated.
   */
  private void findAndIsolateCoenzymesFromReaction(Reaction reaction) {
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

  @Override
  protected Reaction runSpecializedReactionProcessing(Reaction rxn, Long newId) {
    // Bump up the cofactors to the cofactor list and update all substrates/products and their coefficients accordingly.
    updateReactionProductOrSubstrate(rxn, SUBSTRATE);
    updateReactionProductOrSubstrate(rxn, PRODUCT);
    return rxn;
  }

  /**
   * This function is the meat of the cofactor removal process.  It extracts all cofactors based on their ids and
   * places them in the appropriate collection within the reaciton.
   * @param reaction The reaction to update.
   * @param component Update substrates or products.
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
        Arrays.asList(chemIds).stream().collect(Collectors.partitioningBy(knownCofactorNewIds::contains));

    List<Long> cofactorIds = partitionedIds.containsKey(true) ? partitionedIds.get(true) : Collections.EMPTY_LIST;
    List<Long> nonCofactorIds = partitionedIds.containsKey(false) ? partitionedIds.get(false) : Collections.EMPTY_LIST;

    // Retain previously partitioned cofactors if any exist.
    if (originalCofactorIds != null && originalCofactorIds.length > 0) {
      // Use an ordered set to unique the partitioned and previously specified cofactors.  Original cofactors go first.
      LinkedHashSet<Long> uniqueCofactorIds = new LinkedHashSet<>(Arrays.asList(originalCofactorIds));
      uniqueCofactorIds.addAll(cofactorIds);
      /* We do this potentially expensive de-duplication step only in the presumably rare case that we find a reaction
       * that already has cofactors set.  A reaction that has not already undergone cofactor removal is very unlikely to
       * have cofactors partitioned from substrates/products. */
      cofactorIds = new ArrayList<>(uniqueCofactorIds);
    }

    // Coefficients for cofactors should automatically fall out when we update the substrate/product list.
    if (component == SUBSTRATE) {
      reaction.setSubstrateCofactors(cofactorIds.toArray(new Long[cofactorIds.size()]));
      reaction.setSubstrates(nonCofactorIds.toArray(new Long[nonCofactorIds.size()]));
      /* Coefficients should already have been set when the reaction was migrated to the new DB, so no need to update.
       * Note that this assumption depends strongly on the current coefficient implementation in the Reaction model. */
    } else {
      reaction.setProductCofactors(cofactorIds.toArray(new Long[cofactorIds.size()]));
      reaction.setProducts(nonCofactorIds.toArray(new Long[nonCofactorIds.size()]));
    }
  }

  /**
   * Removes cofactors from a single reaction by its ID.
   *
   * Important: do not call this on an object that has been/will be used to process an entire DB (via the `run` method,
   * for example).  The two approaches to cofactor removal use the same cache objects which will be corrupted if the
   * object is reused (hence this method being protected).
   *
   * @param rxnId The id of the reaction to process.
   * @return The original and modified reaction object.
   * @throws IOException
   */
  protected Pair<Reaction, Reaction> removeCofactorsFromOneReaction(Long rxnId) throws IOException {
    Reaction oldRxn = getNoSQLAPI().readReactionFromInKnowledgeGraph(rxnId);
    if (oldRxn == null) {
      LOGGER.error("Could not find reaction %d in the DB", rxnId);
      return null;
    }

    Set<Long> allChemicalIds = new HashSet<>();
    allChemicalIds.addAll(Arrays.asList(oldRxn.getSubstrates()));
    allChemicalIds.addAll(Arrays.asList(oldRxn.getProducts()));
    allChemicalIds.addAll(Arrays.asList(oldRxn.getSubstrateCofactors()));
    allChemicalIds.addAll(Arrays.asList(oldRxn.getProductCofactors()));
    allChemicalIds.addAll(Arrays.asList(oldRxn.getCoenzymes()));

    for (Long id : allChemicalIds) {
      Chemical chem = getNoSQLAPI().readChemicalFromInKnowledgeGraph(id);
      if (chem == null) {
        LOGGER.error("Unable to find chemical %d for reaction %d in the DB", id, rxnId);
        return null;
      }
      // Simulate chemical migration so we play nicely with the cofactor remover.
      getOldChemIdToNewChemId().put(id, id);
      getNewChemIdToInchi().put(id, chem.getInChI());

      chem = assignCofactorStatus(chem);
      if (chem.isCofactor()) {
        LOGGER.info("Found participating cofactor %d: %s", chem.getUuid(), chem.getInChI());
      }
    }

    Reaction newRxn = new Reaction(
        -1,
        oldRxn.getSubstrates(),
        oldRxn.getProducts(),
        oldRxn.getSubstrateCofactors(),
        oldRxn.getProductCofactors(),
        oldRxn.getCoenzymes(),
        oldRxn.getECNum(),
        oldRxn.getConversionDirection(),
        oldRxn.getPathwayStepDirection(),
        oldRxn.getReactionName(),
        oldRxn.getRxnDetailType()
    );

    findAndIsolateCoenzymesFromReaction(newRxn);
    newRxn = runSpecializedReactionProcessing(newRxn, -1L);

    return Pair.of(oldRxn, newRxn);
  }
}
