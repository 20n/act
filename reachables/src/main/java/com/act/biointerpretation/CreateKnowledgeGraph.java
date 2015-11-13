package com.act.biointerpretation;

import act.api.NoSQLAPI;
import com.act.biointerpretation.cleanchems.ChemicalCleaner;
import com.act.biointerpretation.step1_reactionmerging.ReactionMerger;

public class CreateKnowledgeGraph {

  private NoSQLAPI api;
  
  CreateKnowledgeGraph() {
    this.api = new NoSQLAPI();
//    this.chemicalCleaner = new ChemicalCleaner();
//    this.reactionCleaner = new ReactionCleaner();
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

//  public void processReactions() {
//    Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
//    while(rxns.hasNext()) {
//      try {
//        reactionCleaner.clean(rxns.next());
//      } catch(Exception err) {
//        //int newid = api.writeToOutKnowlegeGraph(rxn);
//        System.err.println("Error cleaning reaction");
//      }
//    }
//  }

}
