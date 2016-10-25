package com.act.biointerpretation.networkanalysis.GraphViz;

import com.act.utils.TSVWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a GraphViz graph.
 * See user manual for format here: http://graphviz.org/Documentation/dotguide.pdf
 */
public class DotGraph {

  // The node label refers to the node's label as drawn in the graph
  private static final String LABEL_HEADER = "node_label";
  // The node name is a more meaningful identifier, i.e., the InChI of the molecule the nodde represents.
  private static final String NAME_HEADER = "node_name";

  List<DotEdge> edges;
  List<DotNode> nodes;
  Map<String, String> nodeLabelToName;

  public DotGraph() {
    edges = new ArrayList<>();
    nodes = new ArrayList<>();
    nodeLabelToName = new HashMap<>();
  }

  public void addEdge(DotEdge edge) {
    edges.add(edge);
  }

  public void addNode(DotNode node) { nodes.add(node); }

  public void setNodeName(String nodeLabel, String nodeName) {
    nodeLabelToName.put(nodeLabel, nodeName);
  }

  /**
   * @param outputFile
   */
  public void writeNodeNamesToFile(File outputFile) throws IOException {
    try (TSVWriter<String, String> writer = new TSVWriter<>(Arrays.asList(LABEL_HEADER, NAME_HEADER))) {
      writer.open(outputFile);

      for (Map.Entry<String, String> entry : nodeLabelToName.entrySet()) {
        writer.append(new HashMap<String, String>() {{
          put(LABEL_HEADER, entry.getKey());
          put(NAME_HEADER, entry.getValue());
        }});
      }
    }
  }

  /**
   * Writes this graph to a given stream in DOT format, so it can be processed by GraphViz.
   *
   * @param outputFile The file to write to.
   * @throws IOException
   */
  public void writeGraphToFile(File outputFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
      writer.write("digraph G {\n");
      for (DotEdge edge : edges) {
        writer.write(edge.getDotString());
      }
      for (DotNode node : nodes) {
        writer.write(node.getDotString());
      }
      writer.write("}\n");
    }
  }
}
