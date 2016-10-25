package com.act.biointerpretation.networkanalysis.GraphViz;

import java.awt.*;

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

  public DotEdge(String source, String sink) {
    this.source = source;
    this.sink = sink;
    this.color = EdgeColor.DEFAULT_BLACK;
    this.style = EdgeStyle.DEFAULT_SOLID;
  }

  /**
   * Gets the string that should be written to a DOT file to add this edge to a graph.
   * See user manual for dot format here: http://graphviz.org/Documentation/dotguide.pdf
   *
   * @return The string.
   */
  public String getDotString() {
    return new StringBuilder()
        .append(source)
        .append("->")
        .append(sink)
        .append(color.getColorString())
        .append(style.stringRep)
        .append(";\n").toString();
  }

  /**
   * @return The DotEdge to allow chaining.
   */
  public DotEdge setColor(EdgeColor color) {
    this.color = color;
    return this;
  }

  /**
   * @return The DotEdge to allow chaining.
   */
  public DotEdge setStyle(EdgeStyle style) {
    this.style = style;
    return this;
  }

  /**
   * Represents the color of an edge.
   */
  public static class EdgeColor {
    public static final EdgeColor DEFAULT_BLACK = new EdgeColor();
    public static final EdgeColor RED = new EdgeColor("red");

    private final String stringRep;

    /**
     * Create an edge with default color of black. This should be used where possible to avoid
     * cluttering the GraphViz output.
     */
    public EdgeColor() {
      stringRep = "";
    }

    /**
     * Create an edge color from the list of dot graph colors found in appendix G:
     * http://graphviz.org/Documentation/dotguide.pdf. This is prefered to the java.awt.color
     * constructor, where the fine-grained control is not needed, so as to produce an easier-to-read dot graph.
     *
     * @param color
     */
    public EdgeColor(String color) {
      this.stringRep = wrapColorName(color);
    }

    /**
     * Create an edge from a java.awt.Color object. This allows choice of specific RGB values.
     *
     * @param color
     */
    public EdgeColor(Color color) {
      this.stringRep = wrapColorName(getColorName(color));
    }

    public String getColorString() {
      return stringRep;
    }

    private static String wrapColorName(String colorName) {
      return new StringBuilder()
          .append("[color=")
          .append(colorName)
          .append("]").toString();
    }

    private static String getColorName(Color color) {
      return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
  }

  /**
   * Possible styles of an edge.
   */
  public enum EdgeStyle {
    DEFAULT_SOLID(),
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
