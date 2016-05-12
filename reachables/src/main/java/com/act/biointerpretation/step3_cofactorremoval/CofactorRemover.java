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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
  private Set<Long> knownCofactorOldIds;

  private BlacklistedInchisCorpus blacklistedInchisCorpus;

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  public CofactorRemover(NoSQLAPI api) {
    super(api);
    knownCofactorOldIds = new HashSet<>();
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
      Long newId = mapOldChemIdToNewId(oldId);
      if (newId == null) {
        throw new RuntimeException(String.format("Unable to map chemical %d for reaction %d to new id",
            oldId, reaction.getUUID()));
      }
      newCofactorIds.add(newId);
    }

    List<Long> oldNonCofactorIds =
        partitionedIds.containsKey(false) ? partitionedIds.get(false) : Collections.EMPTY_LIST;
    List<Pair<Long, Long>> oldToNewNonCofactorIds = new ArrayList<>(oldNonCofactorIds.size());
    for (Long oldId : oldNonCofactorIds) {
      Long newId = mapOldChemIdToNewId(oldId);
      if (newId == null) {
        throw new RuntimeException(String.format("Unable to map chemical %d for reaction %d to new id",
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
}
