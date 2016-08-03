package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * This trivial class represents a SAR with no constraints.
 *
 * Warning: this class has been structured to play nicely with Jackson.  While making it its own top-level class may
 * seem excessive and its single `substructure` field pointless, this gives Jackson a means of dispatching its
 * serializers/deserializers that keeps the library happy.  This is particularly important when using the L2Expander
 * with Spark: Spark jobs will crash if Jackson doesn't know how to process the expander's default NO_SAR value.
 */
public class NoSar implements Sar, Serializable {
  private static final long serialVersionUID = -7309106064265051106L;

  @JsonProperty("substructure")
  Molecule substructure = null;

  @Override
  public boolean test(List<Molecule> substrates) {
    return true;
  }

  Molecule getSubstructure() {
    return substructure;
  }

  void setSubstructure(Molecule substructure) {
    this.substructure = substructure;
  }
}
