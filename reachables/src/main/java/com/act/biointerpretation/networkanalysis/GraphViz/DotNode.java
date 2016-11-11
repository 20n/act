package com.act.biointerpretation.networkanalysis.GraphViz;

/**
 * Represents a node in a dot graph.
 */
public class DotNode {

  private static final String END_LINE = ";\n";

  private final String label;
  private DotColor color;

  public DotNode(String label) {
    this(label, DotColor.DEFAULT_BLACK);
  }

  public DotNode(String label, DotColor color) {
    this.label = label;
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
