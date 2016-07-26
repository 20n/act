package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;

import java.util.List;

public class SubstructureSarBuilder implements SarBuilder {

  private final DbAPI dbApi;
  private final McsCalculator mcsCalculator;

  public SubstructureSarBuilder(DbAPI dbApi, McsCalculator mcsCalculator) {
    this.dbApi = dbApi;
    this.mcsCalculator = mcsCalculator;
  }

  @Override
  public Sar buildSar(List<Reaction> reactions) throws MolFormatException {
    List<Molecule> substrates = dbApi.getFirstSubstratesAsMolecules(reactions);
    Molecule sarMcs = mcsCalculator.getMCS(substrates);
    return new OneSubstrateSubstructureSar(sarMcs);
  }
}
