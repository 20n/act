package com.act.biointerpretation.l2expansion;

import java.util.List;

/**
 * An interface for a PredictionFilter, intended to act on an L2PredictionCorpus by
 * being applied to every L2Prediction in the corpus.
 */
public interface PredictionFilter {

  public List<L2Prediction> applyFilter(L2Prediction prediction);
}
