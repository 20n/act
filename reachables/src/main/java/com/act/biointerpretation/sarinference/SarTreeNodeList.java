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

package com.act.biointerpretation.sarinference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A simple list of SarTreeNodes that can be serialized for curation and deserialized for later use.
 */
public class SarTreeNodeList {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("sar_tree_nodes")
  List<SarTreeNode> sarTreeNodes;

  public SarTreeNodeList() {
    sarTreeNodes = new ArrayList<>();
  }

  public SarTreeNodeList(List<SarTreeNode> sarTreeNodes) {
    this.sarTreeNodes = sarTreeNodes;
  }

  public void addNode(SarTreeNode node) {
    sarTreeNodes.add(node);
  }

  public void addAll(Collection<SarTreeNode> nodes) {
    sarTreeNodes.addAll(nodes);
  }

  public void sortBy(SarTreeNode.ScoringFunctions function) {
    sarTreeNodes.forEach(node -> node.setRankingScore(function));
    sortByRankingScores();
  }

  public void sortByRankingScores() {
    sarTreeNodes.sort((a, b) -> -Double.compare(a.getRankingScore(), b.getRankingScore()));
  }

  public SarTreeNodeList loadFromFile(File file) throws IOException {
    SarTreeNodeList fromFile = OBJECT_MAPPER.readValue(file, SarTreeNodeList.class);
    this.setSarTreeNodes(fromFile.getSarTreeNodes());
    return this;
  }

  public void writeToFile(File file) throws IOException {
    OBJECT_MAPPER.writeValue(file, this);
  }

  public Integer size() {
    return sarTreeNodes.size();
  }

  public List<SarTreeNode> getSarTreeNodes() {
    return sarTreeNodes;
  }

  public void setSarTreeNodes(List<SarTreeNode> sarTreeNodes) {
    this.sarTreeNodes = sarTreeNodes;
  }
}
