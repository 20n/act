package com.act.biointerpretation.sars;

import act.shared.Seq;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A sequence grouper that iterates over the seq DB and groups only seq entries that have exactly same sequence.
 */
public class StrictSeqGrouper {

  final Integer limit;
  final Iterator<Seq> seqIterator;

  /**
   * Builds a StricSeqGrouper for the given Seq entries.
   *
   * @param seqIterator The Seq entries to group.
   */
  public StrictSeqGrouper(Iterator<Seq> seqIterator) {
    this.seqIterator = seqIterator;
    this.limit = Integer.MAX_VALUE;
  }

  /**
   * Builds a StrictSeqGrouper for the given Seq entries.
   *
   * @param seqIterator The Seq entries to group.
   * @param limit The maximum number of entries to process. This can be used to limit memory and time.
   */
  public StrictSeqGrouper(Iterator<Seq> seqIterator, Integer limit) {
    this.seqIterator = seqIterator;
    this.limit = limit;
  }

  /**
   * Returns the collection of SeqGroups produced by running this grouper on the Seq entries from the DB.
   * TODO: Implement this in a way that doesn't store the whole map in memory at the same time.
   *
   * @return The collection of produced SeqGroups.
   */
  public Collection<SeqGroup> getSeqGroups() {
    Map<String, SeqGroup> sequenceToSeqGroupMap = getSequenceToSeqGroupMap(seqIterator);
    return sequenceToSeqGroupMap.values();
  }

  /**
   * Iterates over seq entries and builds a map from unique sequences to SeqGroup objects that list their
   * corresponding Seq entry ids and Reaction ids.
   *
   * @param seqIterator
   * @return
   */
  private Map<String, SeqGroup> getSequenceToSeqGroupMap(Iterator<Seq> seqIterator) {
    Map<String, SeqGroup> sequenceToSeqGroupMap = new HashMap<>();

    Integer counter = 0;
    while (seqIterator.hasNext()) {
      if (counter >= limit) {
        break;
      }

      Seq seq = seqIterator.next();
      String sequence = seq.get_sequence();

      if (!sequenceToSeqGroupMap.containsKey(sequence)) {
        sequenceToSeqGroupMap.put(sequence, new SeqGroup(sequence));
      }

      SeqGroup group = sequenceToSeqGroupMap.get(sequence);
      group.addSeqId(seq.getUUID());
      for (Long reactionId : seq.getReactionsCatalyzed()) {
        group.addReactionId(reactionId);
      }
      counter++;
    }

    return sequenceToSeqGroupMap;
  }
}
