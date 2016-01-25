package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

public class CreateKnowledgeGraph {

  private NoSQLAPI api;
  private ChemicalCleaner chemicalCleaner;
  private ReactionCleaner reactionCleaner;

  CreateKnowledgeGraph() {
    this.api = new NoSQLAPI();
    this.chemicalCleaner = new ChemicalCleaner();
    this.reactionCleaner = new ReactionCleaner();
  }

  public static void main(String[] args) {
    CreateKnowledgeGraph kg = new CreateKnowledgeGraph();
    kg.create();
  }

  public void create() {
    ReactionMerger merger1 = new ReactionMerger();
    merger1.run();
    merger1 = null;

    ChemicalCleaner cclean = new ChemicalCleaner();
    cclean.run();
    cclean = null;

     //processReactions();
  }

  public void processReactions() {
    Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
    while(rxns.hasNext()) {
      try {
        reactionCleaner.clean(rxns.next());
      } catch(Exception err) {
        //int newid = api.writeToOutKnowlegeGraph(rxn);
        System.err.println("Error cleaning reaction");
      }
    }
  }
}
