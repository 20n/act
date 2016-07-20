package com.act.biointerpretation.l2expansion;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.Sar;

import java.util.List;

/**
 * This class bundles together the necessary components from which reaction predictions can be made.
 */
public class PredictionSeed {

  private final List<Molecule> substrates;
  private final Ero ro;
  private final Sar sar;

  public PredictionSeed(List<Molecule> substrates, Ero ro, Sar sar) {
    this.substrates = substrates;
    this.ro = ro;
    this.sar = sar;
  }

  public List<Molecule> getSubstrates() {
    return substrates;
  }

  public Ero getRo() {
    return ro;
  }

  public Sar getSar() {
    return sar;
  }
}
