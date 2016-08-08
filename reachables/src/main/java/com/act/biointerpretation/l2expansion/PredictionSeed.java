package com.act.biointerpretation.l2expansion;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SerializableReactor;

import java.util.ArrayList;
import java.util.List;

/**
 * This class bundles together the necessary components from which reaction predictions can be made.
 */
public class PredictionSeed {

  private final String projectorName;
  private final List<Molecule> substrates;
  private final SerializableReactor ro;
  private final List<Sar> sars;

  public PredictionSeed(String projectorName, List<Molecule> substrates, SerializableReactor ro, List<Sar> sars) {
    this.projectorName = projectorName;
    this.substrates = substrates;
    this.ro = ro;
    this.sars = sars;
  }

  public String getProjectorName() {
    return projectorName;
  }

  public List<Molecule> getSubstrates() {
    return substrates;
  }

  public SerializableReactor getRo() {
    return ro;
  }

  public List<Sar> getSars() {
    return new ArrayList<Sar>(sars);
  }
}
