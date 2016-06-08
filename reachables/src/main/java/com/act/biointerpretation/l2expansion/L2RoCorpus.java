package com.act.biointerpretation.l2expansion;

import chemaxon.reaction.Reactor;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the set of ROs to be used in an L2 expansion run
 */
public class L2RoCorpus {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  private Set<Integer> RoIds;

  private Map<Ero, Reactor> corpus = new HashMap<>();

  public L2RoCorpus(Set<Integer> roIds) {
    RoIds = roIds;
  }

  /**
   * Add the reaction operators to the corpus according to the RoIds array
   */
  public void buildCorpus() throws IOException {
    ErosCorpus erosCorpus = new ErosCorpus();
    erosCorpus.loadCorpus();
    for (Ero ero : erosCorpus.getRos()) {
      if (RoIds.contains(ero.getId())) {
        try {
          Reactor reactor = new Reactor();
          reactor.setReactionString(ero.getRo());
          corpus.put(ero, reactor);
        } catch (ReactionException e) {
          LOGGER.error("Reaction exception on RO: " + ero.getId());
        }
      }
    }
  }

  public Map<Ero, Reactor> getCorpus() {
    return corpus;
  }

}
