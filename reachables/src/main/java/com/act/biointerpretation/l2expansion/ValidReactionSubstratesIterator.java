package com.act.biointerpretation.l2expansion;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.Utils.LRUCache;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class iterates over all reactions in a MongoDB that contain only valid InChIs as substrates or products,
 * returning the substrates of each such reaction.  This should limit the set of returned reactions to only those that
 * are eligible for mechanistic validation.
 *
 * TODO: generalize this to iterate over reactions in addition to just substrates.
 */
public class ValidReactionSubstratesIterator implements Iterator<List<String>> {
  private static final int DEFAULT_CACHE_SIZE = 10000;

  private MongoDB db;
  private DBIterator dbIter;
  private LRUCache<Long, String> validInchiCache;
  private LRUCache<Long, String> invalidInchiCache;

  private Reaction nextValidReaction;

  public ValidReactionSubstratesIterator(MongoDB db) {
    this.db = db;
    dbIter = db.getIteratorOverReactions();
    validInchiCache = new LRUCache<>(DEFAULT_CACHE_SIZE, true);
    invalidInchiCache = new LRUCache<>(DEFAULT_CACHE_SIZE, true);
  }

  /* This iterator opportunistically loads a reaction when hasNext() is called, as it must inspect one more more
   * reactions in order to determine whether any more valid reactions exist in the DB.
   *
   * Once hasNext() has primed the iterator, next() simply extracts the reaction's substrates and fetches their
   * InChIs, which should already have been cached in this iterator.
   */

  @Override
  public boolean hasNext() {
    if (nextValidReaction == null && !dbIter.hasNext()) {
      return false;
    }

    if (nextValidReaction != null) {
      return true; // hasNext should be safely callable any number of times.
    }

    boolean foundValidReaction = false;
    Reaction r = db.getNextReaction(dbIter);
    do {
      if (reactionChemicalsAreValid(r)) {
        foundValidReaction = true;
      } else {
        if (dbIter.hasNext()) {
          r = db.getNextReaction(dbIter);
        } else {
          r = null;
        }
      }
    } while (!foundValidReaction);

    nextValidReaction = r;
    if (nextValidReaction == null) {
      return false;
    }
    return true;
  }

  @Override
  public List<String> next() {
    if (nextValidReaction != null) {
      Reaction r = nextValidReaction;
      nextValidReaction = null; // Invalidate reaction to avoid accidental double next() calls.

      List<String> substrateInchis = new ArrayList<>(r.getSubstrates().length);
      for (Long id : r.getSubstrates()) {
        Pair<String, Boolean> lookupResults = getInChI(id);
        assert (lookupResults.getRight()); // We should always hit the cache here since we looked up to valide.

        Integer coefficient = r.getSubstrateCoefficient(id);
        if (coefficient == null) {
          coefficient = 1; // Default to one if we can't find a coefficient for this substrate.
        }
        // Add the inchi once per coefficient count.
        for (int i = 0; i < coefficient; i++) {
          substrateInchis.add(lookupResults.getLeft());
        }
      }
      return substrateInchis;
    } else {
      throw new RuntimeException("next() called without calling hasNext() or on an exhausted iterator");
    }
  }

  /**
   * Returns true iff all substrates/products of a reaction have valid InChIs.
   * @param r The reaction to test.
   * @return True if the reactions substrates/products have valid InChIs; false otherwise.
   */
  private boolean reactionChemicalsAreValid(Reaction r) {
    if (r.getSubstrates() == null || r.getSubstrates().length == 0) {
      return false;
    }

    for (Long id : r.getSubstrates()) {
      if (!validateChemicalForId(id)) {
        return false;
      }
    }

    if (r.getProducts() != null) {
      for (Long id : r.getProducts()) {
        if (!validateChemicalForId(id)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Validates and caches the InChI for a given chemical id.  InChIs are partitioned into different caches depending
   * on whether they're valid or not to reduce the incidence of invalid InChIs forcing valid ones out of the cache, but
   * still enjoying the performance benefit of caching for chemicals with invalid InChIs.
   * @param id The chemical id whose InChI to fetch.
   * @return True if the chemical has a valid InChI, false otherwise.
   */
  private boolean validateChemicalForId(Long id) {
    if (invalidInchiCache.containsKey(id)) {
      return false;
    }

    if (validInchiCache.containsKey(id)) {
      return true;
    }

    Chemical c = db.getChemicalFromChemicalUUID(id);
    String inchi = c.getInChI();
    if (inchi.contains("FAKE")) {
      invalidInchiCache.put(id, inchi);
    }

    // TODO: can we skip this step and let the SPARK nodes do it?
    try {
      MoleculeImporter.importMolecule(inchi, MoleculeFormat.inchi());
    } catch (MolFormatException e) {
      invalidInchiCache.put(id, inchi);
      return false;
    }

    validInchiCache.put(id, inchi);
    return true;
  }

  /**
   * Tries to fetch a chemical's InChI from the cache; falls back to the DB on a miss.  Does not update the cache
   * itself, as validation and cache partitioning is done elsewhere.
   * @param chemicalId The id of the chemical to look up.
   * @return A pair of the chemical's InChI and a boolean indicating whether the chemical was found in the valid cache.
   */
  private Pair<String, Boolean> getInChI(Long chemicalId) {
    String inchi = validInchiCache.get(chemicalId);
    if (inchi != null) {
      return Pair.of(inchi, true);
    }

    Chemical c = db.getChemicalFromChemicalUUID(chemicalId);
    return Pair.of(c.getInChI(), false);
  }
}
