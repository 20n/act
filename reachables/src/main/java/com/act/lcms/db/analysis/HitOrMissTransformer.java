package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.lang3.tuple.Pair;

public abstract class HitOrMissTransformer<T> {
  public static final Boolean DO_NOT_THROW_OUT_MOLECULE = true;
  public static final Boolean THROW_OUT_MOLECULE = false;
  public static final Double LOWEST_POSSIBLE_VALUE_FOR_PEAK_STATISTIC = 0.0;
  public static String NIL_PLOT = "NIL_PLOT";
  public static final Integer REPRESENTATIVE_INDEX = 0;

  public abstract Pair<IonAnalysisInterchangeModel.HitOrMiss, Boolean> apply(T replicates);
}
