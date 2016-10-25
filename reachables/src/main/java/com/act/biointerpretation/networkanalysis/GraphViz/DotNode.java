package com.act.biointerpretation.networkanalysis.GraphViz;

/**
 * Represents a node in a dot graph.
 */
public class DotNode {

  private static final String END_LINE = ";\n";

  private final String label;
  private DotColor color;

  public DotNode(String label) {
    this.label = label;
    this.color = DotColor.DEFAULT_BLACK;
  }

  public void setColor(DotColor color) {
    this.color = color;
  }

  public String getDotString() {
    return new StringBuilder()
        .append(label)
        .append(color.getColorString())
        .append(END_LINE)
        .toString();
  }
}
