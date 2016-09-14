package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.Serializable;

public class SerializableReactor implements Serializable {
  private static String SERIALIZATION_FORMAT = "smarts";

  @JsonProperty("ro_id")
  private Integer roId;

  @JsonIgnore
  private Reactor reactor;

  /**
   * For json.
   */
  private SerializableReactor() {
  }

  public SerializableReactor(SerializableReactor template) {
    this.roId = template.roId;
    this.reactor = template.reactor;
  }

  public SerializableReactor(Reactor reactor, Integer roId) {
    this.reactor = reactor;
    this.roId = roId;
  }

  @JsonProperty("reactor_smarts")
  public String getReactorSmarts() throws IOException {
    return MolExporter.exportToFormat(reactor.getReaction(), SERIALIZATION_FORMAT);
  }

  @JsonProperty("reactor_smarts")
  private void setReactorSmarts(String smarts) throws ReactionException {
    reactor = new Reactor();
    reactor.setReactionString(smarts);
  }

  @JsonIgnore
  public Reactor getReactor() {
    return reactor;
  }

  public Integer getRoId() {
    return roId;
  }
}
