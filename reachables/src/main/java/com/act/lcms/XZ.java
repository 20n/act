package com.act.lcms;

import java.io.Serializable;

public class XZ implements Serializable {
  private static final long serialVersionUID = -5116293669998344905L;
  private Double time;
  private Double intensity;

  public XZ(Double t, Double i) {
    this.time = t;
    this.intensity = i;
  }

  public Double getTime() {
        return time;
    }

  public Double getIntensity() {
        return intensity;
    }
}
