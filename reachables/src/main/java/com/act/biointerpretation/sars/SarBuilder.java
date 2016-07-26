package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;

import java.util.List;

public interface SarBuilder {

  Sar buildSar(List<Reaction> reactions) throws MolFormatException;
}
