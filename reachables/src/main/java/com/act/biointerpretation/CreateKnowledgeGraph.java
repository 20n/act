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

  public void create() {
    processOrganisms();
    Map<Long, Long> validChems = processChemicals();
    Map<Integer, Integer> validRxns = processReactions(validChems);
  }

  public static void main(String[] args) {
    CreateKnowledgeGraph drknow = new CreateKnowledgeGraph();
    drknow.create();
  }

  public void processOrganisms() {
    // TODO: read and process through organisms collections
  }

  public Map<Long, Long> processChemicals() {
    Map<Long, Long> old2new = new HashMap<Long, Long>();

    Iterator<Chemical> chems = api.readChemsFromInKnowledgeGraph();
    while(chems.hasNext()) {
      Chemical c = chemicalCleaner.clean(chems.next());

      // when c==null, chemical is malformed
      // inchi was bad, so some other error,
      // omit from installing in new knowledge
      // graph
      if (c == null)
        continue;

      long newid = api.writeToOutKnowlegeGraph(c);
      old2new.put(c.getUuid(), newid);
    }

    return old2new;
  }

  public Map<Integer, Integer> processReactions(Map<Long, Long> old2newChemMap) {
    Map<Integer, Integer> old2newRxnMap = new HashMap<Integer, Integer>();

    // the reaction cleaner needs to know which
    // chemical ids are valid. pass that through...
    reactionCleaner.setValidChems(old2newChemMap);

    Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
    while(rxns.hasNext()) {
      Reaction rxn = reactionCleaner.clean(rxns.next());

      // when rxn==null, reaction is malformed
      // various reasons, described in ReactionCleaner
      if (rxn == null)
        continue;

      int newid = api.writeToOutKnowlegeGraph(rxn);
      old2newRxnMap.put(rxn.getUUID(), newid);
    }

    return old2newRxnMap;
  }

}
