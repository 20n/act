package com.act.biointerpretation.sars;

import chemaxon.struc.Molecule;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * This trivial class represents a SAR with no constraints.
 *
 * Note that while this is an class with no fields, Jackson should still be able to process it thanks to the use of
 * `@JsonSubTypes` in Sar.java.
 */
public class NoSar implements Sar, Serializable {
  private static final long serialVersionUID = -7309106064265051106L;

  @Override
  public boolean test(List<Molecule> substrates) {
    return true;
  }
}
