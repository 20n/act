/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
    this(source, sink, DotColor.DEFAULT_BLACK, EdgeStyle.DEFAULT_SOLID);
  }

  public DotEdge(String source, String sink, DotColor color, EdgeStyle style) {
    this.source = source;
    this.sink = sink;
    this.color = color;
    this.style = style;
  }

  /**
   * Gets the string that should be written to a DOT file to add this edge to a graph.
   * See user manual for dot format here: http://graphviz.org/Documentation/dotguide.pdf
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
