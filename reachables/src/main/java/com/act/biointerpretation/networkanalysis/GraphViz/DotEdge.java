package com.act.biointerpretation.networkanalysis.GraphViz;

/**
 * A class to represent an edge in a GraphViz graph.
 * The edge knows about its source node, sink node, color, and style, and can write out the appropriate DOT language
 * string to add itself to a graph.
 * TODO: Add support for variable node formatting.
 */
public class DotEdge {
  private final String source;
  private String sink;
  private EdgeColor color;
  private EdgeStyle style;

  public DotEdge(String source, String sink, EdgeColor color, EdgeStyle style) {
    this.source = source;
    this.sink = sink;
    this.color = color;
    this.style = style;
  }

  /**
   * Gets the string that should be written to a DOT file to add this edge to a graph.
   *
   * @return The string.
   */
  public String getDotLine() {
    return new StringBuilder()
        .append(source)
        .append("->")
        .append(sink)
        .append(color.stringRep)
        .append(style.stringRep)
        .append(";\n").toString();
  }

  public DotEdge setColor(EdgeColor color) {
    this.color = color;
    return this;
  }

  public DotEdge setStyle(EdgeStyle style) {
    this.style = style;
    return this;
  }

  /**
   * Represents the possible colors of an edge.
   */
  public enum EdgeColor {
    BLACK(""),
    RED("[color=red]");

    private String stringRep;

    EdgeColor(String color) {
      this.stringRep = color;
    }
  }

  /**
   * Represents possible styles of an edge.
   */
  public enum EdgeStyle {
    DEFAULT(""),
    DOTTED("[style=dotted]");

    private String stringRep;

    EdgeStyle(String style) {
      this.stringRep = style;
    }
  }
}