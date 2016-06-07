package com.act.biointerpretation.l2expansion;

import chemaxon.reaction.Reactor;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;


public class L2RoCorpus {

  //All ROs which uniquely and perfectly match reactions in the PABA clade
  private static final Set<Integer> RoIds = new HashSet<Integer>(Arrays.asList(358, 33, 75, 342, 357));

  private Map<Ero, Reactor> corpus = new HashMap<>();

  public static void main(String[] args) throws java.io.IOException {
    L2RoCorpus roCorpus = new L2RoCorpus();
    roCorpus.buildCorpus();
  }

  /*
   * Add the reaction operators to the corpus according to the RoIds array
   * */
  public void buildCorpus() throws FileNotFoundException, IOException {
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

  public Map<Ero,Reactor> getCorpus() {
    return corpus;
  }

}
