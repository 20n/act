package com.act.biointerpretation.networkanalysis.GraphViz;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GraphViz graph.
 * See user manual for format here: http://graphviz.org/Documentation/dotguide.pdf
 */
public class DotGraph {

  List<DotEdge> edges;
  List<DotNode> nodes;

  public DotGraph() {
    edges = new ArrayList<>();
    nodes = new ArrayList<>();
  }

  public void addEdge(DotEdge edge) {
    edges.add(edge);
  }

  public void addNode(DotNode node) {
    nodes.add(node);
  }

  /**
   * Writes this graph to a given stream in DOT format, so it can be processed by GraphViz.
   * @param outputFile The file to write to.
   * @throws IOException
   */
  public void writeGraphToFile(File outputFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
      writer.write("digraph G {\n");
      for (DotNode node : nodes) {
        writer.write(node.getDotString());
      }
      for (DotEdge edge : edges) {
        writer.write(edge.getDotString());
      }
      writer.write("}\n");
    }
  }
}
