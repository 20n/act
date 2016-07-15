package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;

public class SerializableReactor {
  private static String SERIALIZATION_FORMAT = "smarts";

  @JsonProperty("ro_id")
  private Integer roId;

  private Reactor reactor;

  @JsonProperty("seq_id")
  private Integer seqId;

  /**
   * For json.
   */
  private SerializableReactor() {
  }

  public SerializableReactor(SerializableReactor template) {
    this.roId = template.roId;
    this.reactor = template.reactor;
    this.seqId = template.seqId;
  }

  public SerializableReactor(Reactor reactor, Integer roId, Integer seqId) {
    this.reactor = reactor;
    this.roId = roId;
    this.seqId = seqId;
  }

  @JsonProperty("reactor_smarts")
  public String getReactorSmarts() throws IOException {
    return MolExporter.exportToFormat(reactor.getReaction(), SERIALIZATION_FORMAT);
  }

  @JsonProperty("reactor_smarts")
  private void setReactorSmarts(String smarts) throws IOException, ReactionException {
    reactor = new Reactor();
    reactor.setReactionString(smarts);
  }

  @JsonIgnore
  public Reactor getReactor() throws IOException {
    return reactor;
  }

  public Integer getRoId() {
    return roId;
  }

  public Integer getSeqId() {
    return seqId;
  }
}
