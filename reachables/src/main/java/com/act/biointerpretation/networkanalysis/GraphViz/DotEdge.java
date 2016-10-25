package com.act.biointerpretation.networkanalysis.GraphViz;

/**
 * An edge in a GraphViz graph.
 * The edge knows about its source node, sink node, color, and style, and can write out the appropriate DOT language
 * string to add itself to a graph.
 */
public class DotEdge {

  private static final String EDGE_SYMBOL = "->";
  private static final String END_LINE = ";\n";

  private final String source;
  private final String sink;
  private DotColor color;
  private EdgeStyle style;

  public DotEdge(String source, String sink) {
    this.source = source;
    this.sink = sink;
    this.color = DotColor.DEFAULT_BLACK;
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
        .append(EDGE_SYMBOL)
        .append(sink)
        .append(color.getColorString())
        .append(style.stringRep)
        .append(END_LINE).toString();
  }

  /**
   * @return The DotEdge to allow chaining.
   */
  public DotEdge setColor(DotColor color) {
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
