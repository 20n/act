package com.act.analysis.similarity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.FilenameFilter;
import java.util.List;

public class VizRepresentation {

  static class VizPeak {

    @JsonProperty("matching_inchis")
    private Double matchingInchis;

    @JsonProperty("matching_formulae")
    private Double matchingFormulae;

    @JsonProperty("moleculeMass")
    private Double moleculeMass;

    @JsonProperty("rank_metric")
    private Double rankMetric;

    @JsonProperty("rt")
    private Double retentionTime;

    @JsonProperty("mz_band")
    private Double mzBand;

    @JsonProperty("mz")
    private Double massCharge;

    @JsonProperty("rt_band")
    private Double rtBand;

    public Double getMatchingInchis() {
      return matchingInchis;
    }

    public void setMatchingInchis(Double matchingInchis) {
      this.matchingInchis = matchingInchis;
    }

    public Double getMatchingFormulae() {
      return matchingFormulae;
    }

    public void setMatchingFormulae(Double matchingFormulae) {
      this.matchingFormulae = matchingFormulae;
    }

    public Double getMoleculeMass() {
      return moleculeMass;
    }

    public void setMoleculeMass(Double moleculeMass) {
      this.moleculeMass = moleculeMass;
    }

    public Double getRankMetric() {
      return rankMetric;
    }

    public void setRankMetric(Double rankMetric) {
      this.rankMetric = rankMetric;
    }

    public Double getRetentionTime() {
      return retentionTime;
    }

    public void setRetentionTime(Double retentionTime) {
      this.retentionTime = retentionTime;
    }

    public Double getMzBand() {
      return mzBand;
    }

    public void setMzBand(Double mzBand) {
      this.mzBand = mzBand;
    }

    public Double getMassCharge() {
      return massCharge;
    }

    public void setMassCharge(Double massCharge) {
      this.massCharge = massCharge;
    }

    public Double getRtBand() {
      return rtBand;
    }

    public void setRtBand(Double rtBand) {
      this.rtBand = rtBand;
    }

    public VizPeak(Double matchingInchis, Double matchingFormulae, Double moleculeMass, Double rankMetric,
                Double retentionTime, Double mzBand, Double massCharge, Double rtBand) {
      this.matchingInchis = matchingInchis;
      this.matchingFormulae = matchingFormulae;
      this.moleculeMass = moleculeMass;
      this.rankMetric = rankMetric;
      this.mzBand = mzBand;
      this.retentionTime = retentionTime;
      this.massCharge = massCharge;
      this.rtBand = rtBand;
    }
  }

  static class VizLayout {

    @JsonProperty("nrow")
    private Integer numberOfRows;

    @JsonProperty("ncol")
    private Integer numberOfColumns;

    public Integer getNumberOfRows() {
      return numberOfRows;
    }

    public void setNumberOfRows(Integer numberOfRows) {
      this.numberOfRows = numberOfRows;
    }

    public Integer getNumberOfColumns() {
      return numberOfColumns;
    }

    public void setNumberOfColumns(Integer numberOfColumns) {
      this.numberOfColumns = numberOfColumns;
    }

    public VizLayout(Integer numberOfRows, Integer numberOfColumns) {
      this.numberOfColumns = numberOfColumns;
      this.numberOfRows = numberOfRows;
    }
  }

  static class VizScanFiles {

    static class FileName {
      @JsonProperty("filename")
      private String fileName;

      public FileName(String fileName) {
        this.fileName = fileName;
      }
    }

    @JsonProperty("scanfiles")
    private List<FileName> scanFiles;

    public VizScanFiles(List<FileName> fileNames) {
      this.scanFiles = fileNames;
    }
  }


  @JsonProperty("scanfiles")
  private VizScanFiles scanFiles;

  @JsonProperty("peaks")
  private List<VizPeak> peaks;

  @JsonProperty("layout")
  private VizLayout layout;

  public VizScanFiles getScanFiles() {
    return scanFiles;
  }

  public void setScanFiles(VizScanFiles scanFiles) {
    this.scanFiles = scanFiles;
  }

  public List<VizPeak> getPeaks() {
    return peaks;
  }

  public void setPeaks(List<VizPeak> peaks) {
    this.peaks = peaks;
  }

  public VizLayout getLayout() {
    return layout;
  }

  public void setLayout(VizLayout layout) {
    this.layout = layout;
  }

  public VizRepresentation(VizScanFiles scanFiles, List<VizPeak> peaks, VizLayout layout) {
    this.scanFiles = scanFiles;
    this.peaks = peaks;
    this.layout = layout;
  }
}
