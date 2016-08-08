package com.act.biointerpretation.sars;

import chemaxon.struc.RxnMolecule;

import java.util.List;

public interface SarFactory {

  Sar buildSar(List<RxnMolecule> reactions);
}
