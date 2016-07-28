package com.act.biointerpretation.sars;

import chemaxon.formats.MolFormatException;
import chemaxon.struc.RxnMolecule;

import java.util.List;

public interface SarBuilder {

  Sar buildSar(List<RxnMolecule> reactions) throws MolFormatException;
}
