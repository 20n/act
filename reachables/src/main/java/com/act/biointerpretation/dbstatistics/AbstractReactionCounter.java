package com.act.biointerpretation.dbstatistics;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AbstractReactionCounter {

  private static final Logger LOGGER = LogManager.getFormatterLogger(AbstractReactionCounter.class);

  private enum Characterization {
    INCHI_IMPORTABLE(2),
    SMILES_IMPORTABLE(1),
    NOT_IMPORTABLE(0);

    private int ranking;

    Characterization(int i) {
      ranking = i;
    }

    public int getRanking() {
      return ranking;
    }
  }

  MongoDB mongoDB;
  Map<Long, Characterization> chemicalMap;
  Map<Integer, Characterization> reactionMap;

  public AbstractReactionCounter(MongoDB mongoDb) {
    this.mongoDB = mongoDb;
    chemicalMap = new HashMap<>();
    reactionMap = new HashMap<>();
  }

  /**
   * Iterates over reactions in the DB, characterizing reactions and chemicals seen as inchi importable,
   * smiles importable, or not importable. Prints summary counts at the end.
   *
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");

    AbstractReactionCounter counter = new AbstractReactionCounter(mongoDB);
    counter.buildChemicalAndReactionMaps();

    counter.printSummary();
  }

  /**
   * Iterates over the reactions in the DB, and populates the mapping from chemical and reaction IDs
   * to Characterizations.
   */
  public void buildChemicalAndReactionMaps() {

    Iterator<Reaction> reactionIterator = readReactionsFromDB();

    while (reactionIterator.hasNext()) {

      Reaction reaction = reactionIterator.next();

      if (reaction.getUUID() % 1000 == 0) {
        LOGGER.info("On reaction id %d.", reaction.getUUID());
      }

      reactionMap.put(reaction.getUUID(), getType(reaction));
    }
  }

  /**
   * Get an iterator over all reactions in the DB.
   *
   * @return The iterator.
   */
  private Iterator<Reaction> readReactionsFromDB() {
    final DBIterator iter = mongoDB.getIteratorOverReactions(false);

    return new Iterator<Reaction>() {
      @Override
      public boolean hasNext() {
        boolean hasNext = iter.hasNext();
        if (!hasNext)
          iter.close();
        return hasNext;
      }

      @Override
      public Reaction next() {
        return mongoDB.getNextReaction(iter);
      }
    };
  }

  /**
   * Gets the type of the reaction. Returns INCHI_IMPORTABLE if all chemicals are also INCHI_IMPORTABLE. Returns
   * SMILES_IMPORTABLE if at least one chemical is not INCHI_IMPORTABLE, but all are SMILES_IMPORTABLE. Otherwise,
   * if at least one chemical involved is NOT_IMPORTABLE, so is the reaction.
   *
   * @param reaction The reaction.
   * @return Its characterization.
   */
  private Characterization getType(Reaction reaction) {

    List<Long> substrates = Arrays.asList(reaction.getSubstrates());
    List<Long> products = Arrays.asList(reaction.getProducts());

    Characterization tracker = Characterization.INCHI_IMPORTABLE;

    for (Long chemicalId : substrates) {
      tracker = getWorseCharacterization(tracker, getType(chemicalId));
    }

    for (Long chemicalId : products) {
      tracker = getWorseCharacterization(tracker, getType(chemicalId));
    }

    return tracker;
  }

  /**
   * Gets the worse of two characterization values.
   *
   * @param first One characterization.
   * @param second The other characterization.
   * @return The worse of the two, where the best->worst ordering is INCHI_IMPORTABLE, SMILES_IMPORTABLE, NOT_IMPORTABLE.
   */
  private Characterization getWorseCharacterization(Characterization first, Characterization second) {
    return first.getRanking() < second.getRanking() ? first : second;
  }

  /**
   * Characterizes a chemical ID by looking in the chemical map if it's already cached, or explicitly calculating the
   * characterization if not.
   *
   * @param chemicalId The id of the chemical.
   * @return The characterization.
   */
  private Characterization getType(Long chemicalId) {
    Characterization type = chemicalMap.get(chemicalId);
    if (type != null) {
      return type;
    }

    Chemical chemical = mongoDB.getChemicalFromChemicalUUID(chemicalId);
    type = getType(chemical);
    chemicalMap.put(chemicalId, type);
    return type;
  }

  /**
   * Characterizes a chemical by trying to import it first by inchi, then by smiles.
   *
   * @param chemical The chemical.
   * @return The characterization.
   */
  private Characterization getType(Chemical chemical) {
    if (!chemical.getInChI().contains("FAKE") && !chemical.getInChI().contains("R")) {
      try {
        MolImporter.importMol(chemical.getInChI(), "inchi");
        return Characterization.INCHI_IMPORTABLE;
      } catch (MolFormatException e) {
      }
    }

    if (chemical.getSmiles() != null) {
      try {
        MolImporter.importMol(chemical.getSmiles(), "smarts");
        return Characterization.SMILES_IMPORTABLE;
      } catch (MolFormatException e) {
      }
    }

    return Characterization.NOT_IMPORTABLE;
  }

  /**
   * Print counts of chemicals and reactions that are inchi importable, smiles importable, and not importable.
   */
  public void printSummary() {

    List<Characterization> reactionCharacterizations =
        reactionMap.keySet().stream().map(id -> reactionMap.get(id)).collect(Collectors.toList());
    printCounts(reactionCharacterizations, "reactions");

    List<Characterization> chemicalCharacterizations =
        chemicalMap.keySet().stream().map(id -> chemicalMap.get(id)).collect(Collectors.toList());
    printCounts(chemicalCharacterizations, "chemicals");
  }

  /**
   * Helper method to print the three characterization counts regarding either chemicals or reactions.
   *
   * @param characterizations The list of characterizations.
   * @param typeOfThing A string to identify the type of thing being characterized.
   */
  private void printCounts(List<Characterization> characterizations, String typeOfThing) {
    LOGGER.info("There are %d inchi importable %s.",
        Collections.frequency(characterizations, Characterization.INCHI_IMPORTABLE),
        typeOfThing);
    LOGGER.info("There are %d smiles importable %s.",
        Collections.frequency(characterizations, Characterization.SMILES_IMPORTABLE),
        typeOfThing);
    LOGGER.info("There are %d not importable %s.",
        Collections.frequency(characterizations, Characterization.NOT_IMPORTABLE),
        typeOfThing);
  }
}
