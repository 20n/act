package com.act.lcms;

public class XZ {
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
