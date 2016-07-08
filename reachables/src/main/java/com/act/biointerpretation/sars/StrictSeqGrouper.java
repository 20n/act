package com.act.biointerpretation.sars;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Seq;
import com.mongodb.DBObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A sequence grouper that iterates over the seq DB and groups only seq entries that have exactly same sequence.
 */
public class StrictSeqGrouper implements Iterable<SeqGroup> {

  final Integer limit;
  final Iterator<Seq> seqIterator;

  /**
   * Builds a grouper for the given iterator.
   *
   * @param seqIterator The Seq entries to group.
   */
  public StrictSeqGrouper(Iterator<Seq> seqIterator) {
    this.seqIterator = seqIterator;
    this.limit = Integer.MAX_VALUE;
  }

  /**
   * Builds a grouper for the given iterator.
   *
   * @param seqIterator The Seq entries to group.
   * @param limit The maximum number of entries to process. This can be used to limit memory.
   */
  public StrictSeqGrouper(Iterator<Seq> seqIterator, Integer limit) {
    this.seqIterator = seqIterator;
    this.limit = limit;
  }

  @Override
  public Iterator<SeqGroup> iterator() {
    Map<String, SeqGroup> sequenceToSeqGroupMap = getSequenceToSeqGroupMap(seqIterator);
    return sequenceToSeqGroupMap.values().iterator();
  }

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
