package com.act.biointerpretation.networkanalysis.GraphViz;

import java.awt.Color;

/**
 * Represents the color of an edge or node in a dot graph.
 */
public class DotColor {
  public static final DotColor DEFAULT_BLACK = new DotColor();
  public static final DotColor RED = new DotColor("red");

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
   * http://graphviz.org/Documentation/dotguide.pdf. This is prefered to the java.awt.color
   * constructor, where the fine-grained control is not needed, so as to produce an easier-to-read dot graph.
   *
   * @param color
   */
  public DotColor(String color) {
    this.stringRep = wrapColorName(color);
  }

  /**
   * Create an edge from a java.awt.Color object. This allows choice of specific RGB values.
   *
   * @param color
   */
  public DotColor(Color color) {
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