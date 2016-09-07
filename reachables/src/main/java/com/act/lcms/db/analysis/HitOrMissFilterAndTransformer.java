package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.lang3.tuple.Pair;

public abstract class HitOrMissFilterAndTransformer<T> {
  public static final Boolean DO_NOT_THROW_OUT_MOLECULE = true;
  public static final Boolean THROW_OUT_MOLECULE = false;

  public abstract Pair<IonAnalysisInterchangeModel.HitOrMiss, Boolean> apply(T oneOrMoreReplicates);
}
