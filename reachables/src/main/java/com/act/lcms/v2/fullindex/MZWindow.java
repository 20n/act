package com.act.lcms.v2.fullindex;

import java.io.Serializable;

// TODO: unify this with the MZWindow from TraceIndexExtractor, or better yet replace TraceIndexExtractor altogether.
public class MZWindow implements Serializable {
  private static final long serialVersionUID = -3326765598920871504L;

  int index;
  Double targetMZ;
  double min;
  double max;

  public MZWindow(int index, Double targetMZ) {
    this.index = index;
    this.targetMZ = targetMZ;
    this.min = targetMZ - Builder.WINDOW_WIDTH_FROM_CENTER;
    this.max = targetMZ + Builder.WINDOW_WIDTH_FROM_CENTER;
  }

  public int getIndex() {
    return index;
  }

  public Double getTargetMZ() {
    return targetMZ;
  }

  public double getMin() {
    return min;
  }

  public double getMax() {
    return max;
  }
}
