package com.act.analysis.similarity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VizLayout {

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
