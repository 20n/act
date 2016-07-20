package com.act.biointerpretation.l2expansion;

import chemaxon.reaction.ReactionException;

import java.io.IOException;
import java.util.List;

public interface PredictionGenerator {

  List<L2Prediction> getPredictions(PredictionSeed seed) throws IOException, ReactionException;
}
