package com.act.biointerpretation.mechanisminspection;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LabelledReactionsCorpus {
  private static final String VALIDATED_REACTIONS_FILE_PATH = "validated_reactions.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private NoSQLAPI api;

  @JsonProperty("labelled_reactions")
  private List<LabelledReaction> labelledReactions;

  public List<LabelledReaction> getLabelledReactions() {
    return labelledReactions;
  }

  public LabelledReactionsCorpus(NoSQLAPI api) {
    this.api = api;
  }

  // This default constructor is needed to jackson deserialization of the corpus.
  public LabelledReactionsCorpus() {}

  public void setLabelledReactions(List<LabelledReaction> labelledReactions) {
    this.labelledReactions = labelledReactions;
  }

  public void loadCorpus() throws IOException {
    InputStream validatedReactionsStream = INSTANCE_CLASS_LOADER.getResourceAsStream(VALIDATED_REACTIONS_FILE_PATH);
    LabelledReactionsCorpus labelledReactionsCorpus = OBJECT_MAPPER.readValue(validatedReactionsStream, LabelledReactionsCorpus.class);
    this.setLabelledReactions(labelledReactionsCorpus.getLabelledReactions());
  }

  public boolean checkIfReactionIsLabelled(Reaction reaction) {
    Set<String> rxnSubstrates = new HashSet<>();
    for (Long id : reaction.getSubstrates()) {
      rxnSubstrates.add(this.api.readChemicalFromInKnowledgeGraph(id).getInChI());
    }

    Set<String> rxnProducts = new HashSet<>();
    for (Long id : reaction.getProducts()) {
      rxnProducts.add(this.api.readChemicalFromInKnowledgeGraph(id).getInChI());
    }

    for (LabelledReaction labelledReaction : this.labelledReactions) {
      if (labelledReaction.getEcnum().equals(reaction.getECNum()) &&
          labelledReaction.getEasy_desc().equals(reaction.getReactionName()) &&
          labelledReaction.getProducts().equals(rxnProducts) &&
          labelledReaction.getSubstrates().equals(rxnSubstrates)) {
        return true;
      }
    }

    return false;
  }
}
