package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup {

  private static final String SMILES = "smiles";

  @JsonProperty("seq_group")
  private SeqGroup group;

  @JsonProperty("sars")
  private List<Sar> sars;

  @JsonProperty("reactor")
  private SerializableReactor reactor;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }

  public CharacterizedGroup(SeqGroup group, List<Sar> sars, SerializableReactor reactor) {
    this.group = group;
    this.sars = sars;
    this.reactor = reactor;
  }

  public List<Sar> getSars() {
    return sars;
  }

  public SeqGroup getGroup() {
    return group;
  }

  public SerializableReactor getReactor() {
    return reactor;
  }

  private void setReactor(SerializableReactor reactor) {
    this.reactor = reactor;
  }
}
