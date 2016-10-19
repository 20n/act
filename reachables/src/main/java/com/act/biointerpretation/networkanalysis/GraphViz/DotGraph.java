package com.act.biointerpretation.networkanalysis.GraphViz;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GraphViz graph.
 */
public class DotGraph {

  List<DotEdge> edges;

  public DotGraph() {
    edges = new ArrayList<>();
  }

  public void addEdge(DotEdge edge) {
    edges.add(edge);
  }

  /**
   * Writes this graph to a given stream in DOT format, so it can be processed by GraphViz.
   *
   * @param stream The stream to write to.
   * @throws IOException
   */
  public void writeGraphToStream(OutputStreamWriter stream) throws IOException {
    stream.write("digraph G {\n");
    for (DotEdge edge : edges) {
      stream.write(edge.getDotLine());
    }
    stream.write("}\n");
    stream.close();
  }
}
