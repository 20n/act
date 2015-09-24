package com.act.lcms;

import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.List;

public class LCMSSpectrum implements Serializable {
  private static final long serialVersionUID = -1329555801774532940L;

  private Integer index;
  private Double timeVal;
  private String timeUnit;
  private List<Pair<Double, Double>> intensities;
  private Double basePeakMZ;
  private Double basePeakIntensity;
  private Integer function;
  private Integer scan;
  private Double totalIntensity;

  public LCMSSpectrum(Integer index, Double timeVal, String timeUnit, List<Pair<Double, Double>> intensities,
                      Double basePeakMZ, Double basePeakIntensity,
                      Integer function, Integer scan, Double totalIntensity) {
    this.index = index;
    this.timeVal = timeVal;
    this.timeUnit = timeUnit;
    this.intensities = intensities;
    this.basePeakMZ = basePeakMZ;
    this.basePeakIntensity = basePeakIntensity;
    this.function = function;
    this.scan = scan;
    this.totalIntensity = totalIntensity;
  }

  public Integer getIndex() {
    return index;
  }

  public Double getTimeVal() {
    return timeVal;
  }

  public String getTimeUnit() {
    return timeUnit;
  }

  public List<Pair<Double, Double>> getIntensities() {
    return intensities;
  }

  public Double getBasePeakMZ() {
    return basePeakMZ;
  }

  public Double getBasePeakIntensity() {
    return basePeakIntensity;
  }

  public Integer getFunction() {
    return function;
  }

  public Integer getScan() {
    return scan;
  }

  public Double getTotalIntensity() {
    return totalIntensity;
  }
}
