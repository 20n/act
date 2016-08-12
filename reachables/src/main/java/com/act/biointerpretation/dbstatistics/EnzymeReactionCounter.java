package com.act.biointerpretation.dbstatistics;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Seq;
import com.mongodb.DBObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class EnzymeReactionCounter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(EnzymeReactionCounter.class);
  MongoDB mongoDB;
  Map<String, Set<Long>> sequenceToReactions;

  public EnzymeReactionCounter(MongoDB mongoDb) {
    this.mongoDB = mongoDb;
    sequenceToReactions = new HashMap<>();
  }

  public void writeSarEnzymesToFile(File outputFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
      for (String seq : sequenceToReactions.keySet()) {
        Set<Long> reactionSet = sequenceToReactions.get(seq);
        if (reactionSet.size() > 1) {
          writeLine(writer, reactionSet);
          writer.newLine();
        }
      }
    }
  }

  private void writeLine(BufferedWriter writer, Set<Long> set) throws IOException {
    for (Long id : set) {
      writer.write(id.toString());
      writer.write(" ");
    }
  }

  public void printSummary() {
    Map<Integer, Integer> countHistogram = new HashMap<>();

    for (Set<Long> reactionSet : sequenceToReactions.values()) {
      Integer setSize = reactionSet.size();

      if (!countHistogram.containsKey(setSize)) {
        countHistogram.put(setSize, 0);
      }
      Integer previousVal = countHistogram.get(setSize);
      countHistogram.put(setSize, previousVal + 1);
    }

    Integer numSeqs = sequenceToReactions.keySet().size();
    LOGGER.info("Total sequences: %d", numSeqs);

    for (Integer count : countHistogram.keySet()) {
      Double percent = 100 * new Double(countHistogram.get(count)) / new Double(numSeqs);
      LOGGER.info("Sequences with %d reactions: %f percent.", count, percent);
    }
  }

  public void buildSequenceToReactionMap() {

    Iterator<Seq> seqIterator = mongoDB.getSeqIterator();
    int counter = 0;

    while (seqIterator.hasNext()) {

      Seq seq = seqIterator.next();

      if (counter % 100000 == 0) {
        LOGGER.info("On seq %d", counter);
      }

      if (counter > 3000000) { // fails out of memory after this
        break;
      }

      String sequence = seq.get_sequence();

      if (!sequenceToReactions.containsKey(sequence)) {
        sequenceToReactions.put(sequence, new HashSet<Long>());
      }

      sequenceToReactions.get(sequence).addAll(seq.getReactionsCatalyzed());
      counter++;
    }
  }

  public static void main(String[] args) throws IOException {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");

    EnzymeReactionCounter counter = new EnzymeReactionCounter(mongoDB);

    counter.buildSequenceToReactionMap();
    counter.printSummary();
    counter.writeSarEnzymesToFile(new File("/mnt/shared-data/Gil/sar_enzymes.txt"));
  }
}
