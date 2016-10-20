package com.act.analysis.similarity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class VizRepresentation {

  @JsonProperty("scanfiles")
  private List<String> scanFiles;

  @JsonProperty("peaks")
  private List<Peak> peaks;

  @JsonProperty("layout")
  private VizLayout layout;

  public List<String> getScanFiles() {
    return scanFiles;
  }

  public void setScanFiles(List<String> scanFiles) {
    this.scanFiles = scanFiles;
  }

  public List<Peak> getPeaks() {
    return peaks;
  }

  public void setPeaks(List<Peak> peaks) {
    this.peaks = peaks;
  }

  public VizLayout getLayout() {
    return layout;
  }

  public void setLayout(VizLayout layout) {
    this.layout = layout;
  }

  public VizRepresentation(List<String> scanFiles, List<Peak> peaks, VizLayout layout) {
    this.scanFiles = scanFiles;
    this.peaks = peaks;
    this.layout = layout;
  }
}
