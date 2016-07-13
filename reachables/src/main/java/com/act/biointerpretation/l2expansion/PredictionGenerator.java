package com.act.biointerpretation.l2expansion;

import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.Sar;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface PredictionGenerator {

  public List<L2Prediction> getPredictions(List<Molecule> substrates, Ero ro, Optional<Sar> sar)
      throws IOException, ReactionException;
}
