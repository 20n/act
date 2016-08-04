package com.act.biointerpretation.sars.sartrees;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple list of SarTreeNodes that can be serialized for curation and deserialized for later use.
 */
public class SarTreeNodeCorpus {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty("sar_tree_nodes")
  List<SarTreeNode> sarTreeNodes;

  public SarTreeNodeCorpus() {
    sarTreeNodes = new ArrayList<>();
  }

  public SarTreeNodeCorpus(List<SarTreeNode> sarTreeNodes) {
    this.sarTreeNodes = sarTreeNodes;
  }


  public void loadFromFile(File file) throws IOException {
    SarTreeNodeCorpus fromFile = OBJECT_MAPPER.readValue(file, SarTreeNodeCorpus.class);
    this.setSarTreeNodes(fromFile.getSarTreeNodes());
  }

  public void writeToFile(File file) throws IOException {
    OBJECT_MAPPER.writeValue(file, this);
  }

  public List<SarTreeNode> getSarTreeNodes() {
    return sarTreeNodes;
  }

  public void setSarTreeNodes(List<SarTreeNode> sarTreeNodes) {
    this.sarTreeNodes = sarTreeNodes;
  }
}
