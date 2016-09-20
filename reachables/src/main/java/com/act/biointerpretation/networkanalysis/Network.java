package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import act.shared.Reaction;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// Represents a metabolism network, cataloging all possible predicted chemical transformations that could be happening
// in a given sample.
public class Network {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(Network.class);

  @JsonProperty("nodes")
  Map<String, NetworkNode> nodes;

  public Network() {
    nodes = new HashMap<>();
  }

  public NetworkNode getNode(String inchi) {
    return nodes.get(inchi);
  }

  public void loadEdgeFromReaction(MongoDB db, long rxnId) {
    Reaction reaction = db.getReactionFromUUID(rxnId);
    if (reaction.getSubstrates().length != 1) {
      LOGGER.warn("Can only load edge from reaction with one substrate. Refusing to load.");
      return;
    }

    Long substrate = reaction.getSubstrates()[0];
    String substrateInchi = db.getChemicalFromChemicalUUID(substrate).getInChI();
    createNodeIfNoneExists(substrateInchi);

    for (Long product : reaction.getProducts()) {
      String productInchi = db.getChemicalFromChemicalUUID(product).getInChI();
      createNodeIfNoneExists(productInchi);
      NetworkEdge edge = new NetworkEdge(substrateInchi, productInchi);
      edge.setReactionId(reaction.getUUID());
      linkEdge(edge);
    }
  }

  public void loadSingleSubstratePredictions(L2PredictionCorpus predictionCorpus) {
    for (L2Prediction prediction : predictionCorpus.getCorpus()) {
      if (prediction.getSubstrateInchis().size() != 1) {
        LOGGER.warn("Can only load edge from prediction with one substrate. Refusing to load.");
        return;
      }

      String substrate = prediction.getSubstrateInchis().get(0);
      createNodeIfNoneExists(substrate);

      for (String product : prediction.getProductInchis()) {
        createNodeIfNoneExists(product);
        NetworkEdge edge = new NetworkEdge(substrate, product);
        edge.setProjectorName(prediction.getProjectorName());
        linkEdge(edge);
      }
    }
  }


  /**
   * Links an edge with its product and substrate by pointing the corresponding nodes to the edge.
   *
   * @param edge The edge to link.
   */
  private void linkEdge(NetworkEdge edge) {
    getNode(edge.getProduct()).addInEdge(edge);
    getNode(edge.getSubstrate()).addOutEdge(edge);
  }

  private void createNodeIfNoneExists(String inchi) {
    if (nodes.get(inchi) == null) {
      nodes.put(inchi, new NetworkNode(inchi));
    }
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public void loadFromJsonFile(File inputFile) throws IOException {
    Network fromFile = OBJECT_MAPPER.readValue(inputFile, Network.class);
    this.nodes = fromFile.nodes;
  }
}
