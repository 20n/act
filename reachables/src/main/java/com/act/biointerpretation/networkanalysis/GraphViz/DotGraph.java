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
