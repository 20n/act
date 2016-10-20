package com.act.biointerpretation.networkanalysis.GraphViz;

/**
 * An edge in a GraphViz graph.
 * The edge knows about its source node, sink node, color, and style, and can write out the appropriate DOT language
 * string to add itself to a graph.
 */
public class DotEdge {
  private final String source;
  private final String sink;
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
  public String getDotString() {
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
   * Possible colors of an edge.
   */
  public enum EdgeColor {
    BLACK(),
    RED("red"),
    GREEN("green"),
    YELLOW("yellow");

    private final String stringRep;

    EdgeColor() {
      stringRep = "";
    }

    EdgeColor(String color) {
      this.stringRep = "[color=" + color + "]";
    }
  }

  /**
   * Possible styles of an edge.
   */
  public enum EdgeStyle {
    DEFAULT(),
    DOTTED("dotted");

    private String stringRep;

    EdgeStyle() {
      this.stringRep = "";
    }

    EdgeStyle(String style) {
      this.stringRep = "[style=" + style + "]";
    }
  }
}
