package com.act.biointerpretation.sars;

import act.server.MongoDB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A sequence grouper that iterates over the seq DB and groups only seq entries that have exactly same sequence.
 */
public class StrictSeqGrouper implements Iterable<SeqGroup> {

  MongoDB db;

  public StrictSeqGrouper(MongoDB db) {
    this.db = db;
  }

  public List<SeqGroup> getAllEnzymeGroups() {
    return new ArrayList<>();
  }

  @Override
  public Iterator<SeqGroup> iterator() {
    return getAllEnzymeGroups().iterator();
  }

}
