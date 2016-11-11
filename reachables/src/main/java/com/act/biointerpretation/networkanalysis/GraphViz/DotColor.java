package com.act.biointerpretation.networkanalysis.GraphViz;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents the color of an edge or node in a dot graph.
 */
public class DotColor {
  public static final DotColor DEFAULT_BLACK = new DotColor();
  public static final DotColor RED = new DotColor("red");

  // This list is incomplete; add to it if you want any of the more nuanced colors. You can find options here:
  // http://www.graphviz.org/doc/info/colors.html.
  private static final Set<String> VALID_COLORS = new HashSet<String>() {{
    addAll(Arrays.asList("red", "green", "blue", "yellow", "purple", "pink", "orange",
        "black", "white", "violet", "turquoise", "silver", "beige"));
  }};

  private final String stringRep;

  /**
   * Create an edge with default color of black. This should be used where possible to avoid
   * cluttering the GraphViz output.
   */
  public DotColor() {
    stringRep = "";
  }

  /**
   * Create an edge color from the list of dot graph colors found in appendix G:
   * http://graphviz.org/Documentation/dotguide.pdf. This is preferred to the java.awt.color
   * constructor, where the fine-grained control is not needed, so as to produce an easier-to-read dot graph.
   * @param color
   */
  public DotColor(String color) {
    if (!VALID_COLORS.contains(color)) {
      throw new IllegalArgumentException(String.format("Color %s not in list DotColor.VALID_COLORS " +
          "of valid GraphViz colors.", color));
    }
    this.stringRep = wrapColorName(color);
  }

  /**
   * Create an edge from a java.awt.Color object. This allows choice of specific RGB values.
   * @param color
   */
  public DotColor(Color color) {
    this.stringRep = wrapColor(color);
  }

  public String getColorString() {
    return stringRep;
  }

  private static String wrapColor(Color color) {
    return wrapColorName(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
  }

  private static String wrapColorName(String colorName) {
    return new StringBuilder()
        .append("[color=")
        .append(colorName)
        .append("]").toString();
  }
}
