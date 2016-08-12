package com.act.biointerpretation.dbstatistics;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import com.mongodb.DBObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AbstractReactionCounter {

  private static enum Characterization {
    INCHI_IMPORTABLE,
    SMILES_IMPORTABLE,
    NOT_IMPORTABLE
  }

  private static final Logger LOGGER = LogManager.getFormatterLogger(AbstractReactionCounter.class);
  MongoDB mongoDB;
  Map<Long, Characterization> chemicalMap;
  Map<Integer, Characterization> reactionMap;

  public AbstractReactionCounter(MongoDB mongoDb) {
    this.mongoDB = mongoDb;
    chemicalMap = new HashMap<>();
    reactionMap = new HashMap<>();
  }

  /**
   * Finds all reactions characterized by a certain Characterization, and writes their reaciton Ids to files.
   *
   * @param outputFile Where to write the output,
   * @param characterization
   * @throws IOException
   */
  public void writeReactionsToFile(File outputFile, Characterization characterization) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
      for (Integer reactionId : reactionMap.keySet()) {
        if (reactionMap.get(reactionId).equals(characterization)) {
          writer.write(reactionId.toString());
          writer.newLine();
        }
      }
    }
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

  public void buildReactionMap() {

    Iterator<Reaction> reactionIterator = readReactionsFromDB();

    while (reactionIterator.hasNext()) {

      Reaction reaction = reactionIterator.next();

      if (reaction.getUUID() % 1000 == 0) {
        LOGGER.info("On reaction id %d.", reaction.getUUID());
      }

      List<Long> substrates = Arrays.asList(reaction.getSubstrates());
      List<Long> products = Arrays.asList(reaction.getProducts());

      reactionMap.put(reaction.getUUID(), getType(substrates, products));
    }
  }


  private Characterization getType(List<Long> substrates, List<Long> products) {
    Characterization tracker = Characterization.INCHI_IMPORTABLE;

    for (Long chemicalId : substrates) {
      tracker = getWorseCharacterization(tracker, getType(chemicalId));
    }

    for (Long chemicalId : products) {
      tracker = getWorseCharacterization(tracker, getType(chemicalId));
    }

    return tracker;
  }

  private Characterization getWorseCharacterization(Characterization starting, Characterization other) {
    if (starting.equals(Characterization.NOT_IMPORTABLE) || other.equals(Characterization.NOT_IMPORTABLE)) {
      return Characterization.NOT_IMPORTABLE;
    }
    if (starting.equals(Characterization.SMILES_IMPORTABLE) || other.equals(Characterization.SMILES_IMPORTABLE)) {
      return Characterization.SMILES_IMPORTABLE;
    }
    return Characterization.INCHI_IMPORTABLE;
  }

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

  public static void main(String[] args) throws IOException {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");

    AbstractReactionCounter counter = new AbstractReactionCounter(mongoDB);

    counter.buildReactionMap();
    counter.printSummary();

    counter.writeReactionsToFile(new File("/mnt/shared-data/Gil/inchi_reactions.txt"), Characterization.INCHI_IMPORTABLE);
    counter.writeReactionsToFile(new File("/mnt/shared-data/Gil/smiles_reactions.txt"), Characterization.SMILES_IMPORTABLE);
    counter.writeReactionsToFile(new File("/mnt/shared-data/Gil/bad_reactions.txt"), Characterization.NOT_IMPORTABLE);
  }
}
