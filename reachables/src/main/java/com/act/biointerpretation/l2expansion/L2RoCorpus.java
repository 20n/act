package com.act.biointerpretation.l2expansion;

import chemaxon.reaction.Reactor;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;

import java.io.IOException;
import java.util.*;

public class L2RoCorpus {

  //All ROs which uniquely and perfectly match reactions in the PABA clade
  private Set<Integer> RoIds;

  private Map<Ero, Reactor> corpus = new HashMap<>();

  public L2RoCorpus(Set<Integer> roIds) {
    RoIds = roIds;
  }

  /*
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
          System.out.println("Reaction exception on RO: " + ero.getId());
        }
      }
    }
  }

  public Map<Ero, Reactor> getCorpus() {
    return corpus;
  }

}
