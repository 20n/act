package com.act.biointerpretation.l2expansion;

import java.util.List;

/**
 * An interface intended to act on each L2Prediction in an L2PredictionCorpus,
 * in order to create a filtered corpus.
 */
public interface PredictionFilter {

  public List<L2Prediction> applyFilter(L2Prediction prediction);
}
