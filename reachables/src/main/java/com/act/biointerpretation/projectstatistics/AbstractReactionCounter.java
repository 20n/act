package com.act.biointerpretation.projectstatistics;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import com.mongodb.DBObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AbstractReactionCounter {

  private static enum Characterization {
    CONCRETE,
    ABSTRACT,
    FAKE
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

  public void writeAbstractReactionsToFile(File outputFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
      for (Integer reactionId : reactionMap.keySet()) {
        if (reactionMap.get(reactionId).equals(Characterization.ABSTRACT)) {
          writer.write(reactionId.toString());
          writer.newLine();
        }
      }
    }
  }

  public void printSummary() {

    int abstractCounter = 0;
    int concreteCounter = 0;
    int fakeCounter = 0;

    for (Integer reactionId : reactionMap.keySet()) {
      Characterization type = reactionMap.get(reactionId);
      switch (type) {
        case CONCRETE:
          concreteCounter++;
          break;
        case ABSTRACT:
          abstractCounter++;
          break;
        case FAKE:
          fakeCounter++;
          break;
      }
    }


    LOGGER.info("There are %d concrete reactions.", concreteCounter);
    LOGGER.info("There are %d abstract reactions.", abstractCounter);
    LOGGER.info("There are %d fake reactions.", fakeCounter);

    abstractCounter = 0;
    concreteCounter = 0;
    fakeCounter = 0;

    for (Long chemicalId : chemicalMap.keySet()) {
      Characterization type = chemicalMap.get(chemicalId);
      switch (type) {
        case CONCRETE:
          concreteCounter++;
          break;
        case ABSTRACT:
          abstractCounter++;
          break;
        case FAKE:
          fakeCounter++;
          break;
      }
    }

    LOGGER.info("There are %d concrete chemicals which participate in reactions.", concreteCounter);
    LOGGER.info("There are %d abstract chemicals which participate in reactions.", abstractCounter);
    LOGGER.info("There are %d fake chemicals which participate in reactions.", fakeCounter);
  }

  public void buildReactionMap() {

    Iterator<Reaction> reactionIterator = readReactionsFromDB();

    while (reactionIterator.hasNext()) {

      Reaction reaction = reactionIterator.next();
      if (reaction.getUUID() % 10000 == 0) {
        LOGGER.info("On reaction id %d.", reaction.getUUID());
      }

      List<Long> substrates = Arrays.asList(reaction.getSubstrates());
      List<Long> products = Arrays.asList(reaction.getProducts());

      reactionMap.put(reaction.getUUID(), getType(substrates, products));
    }
  }

  private Characterization getType(List<Long> substrates, List<Long> products) {
    Boolean existsAbstract = false;

    for (Long chemicalId : substrates) {
      Characterization characterization = getType(chemicalId);
      switch (characterization) {
        case FAKE:
          return Characterization.FAKE;
        case ABSTRACT:
          existsAbstract = true;
          break;
      }
    }

    for (Long chemicalId : substrates) {
      Characterization characterization = getType(chemicalId);
      switch (characterization) {
        case FAKE:
          return Characterization.FAKE;
        case ABSTRACT:
          existsAbstract = true;
          break;
      }
    }

    if (existsAbstract) {
      return Characterization.ABSTRACT;
    }

    return Characterization.CONCRETE;
  }

  private Characterization getType(Long chemicalId) {
    Characterization type = chemicalMap.get(chemicalId);
    if (type != null) {
      return type;
    }

    Chemical chemical = mongoDB.getChemicalFromChemicalUUID(chemicalId);
    type = getType(chemical.getInChI());
    chemicalMap.put(chemicalId, type);
    return type;
  }

  private Characterization getType(String inchi) {
    if (inchi.contains("FAKE")) {
      return Characterization.FAKE;
    }
    if (inchi.contains("R")) {
      return Characterization.ABSTRACT;
    }
    return Characterization.CONCRETE;
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
        DBObject o = iter.next();
        return mongoDB.convertDBObjectToReaction(o);
      }
    };
  }

  public static void main(String[] args) throws IOException {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");

    AbstractReactionCounter counter = new AbstractReactionCounter(mongoDB);

    counter.buildReactionMap();
    counter.printSummary();
    counter.writeAbstractReactionsToFile(new File("/mnt/shared-data/Gil/abstract_reactions.txt"));
  }

}
