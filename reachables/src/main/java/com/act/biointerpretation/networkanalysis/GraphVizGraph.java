package com.act.biointerpretation.networkanalysis;

import com.jogamp.common.util.ArrayHashSet;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Class for building a GraphViz visualization
 */
public class GraphVizGraph {
  List<Pair<String, String>> edges;

  public GraphVizGraph() {
    edges = new ArrayList<>();
  }

  public void addEdge(String source, String sink) {
    edges.add(new ImmutablePair<>(source, sink));
  }

  public void writeDotToStream(OutputStreamWriter stream) throws IOException {
    stream.write("digraph G {\n");
    for (Pair<String, String> edge : edges) {
      stream.write(edge.getLeft() + "->" + edge.getRight() + ";\n");
    }
    stream.write("}\n");
    stream.close();
  }
}
