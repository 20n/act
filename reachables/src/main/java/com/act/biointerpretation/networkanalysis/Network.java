package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import act.shared.Reaction;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a metabolism network, cataloging all possible predicted chemical transformations that could be happening
 * in a given sample.  Currently all edges encode one-substrate-one-product transformations.
 * TODO: generalize the network to encapsulate multiple-substrate, multiple-product dependencies.
 */
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

  @JsonIgnore
  public Collection<NetworkNode> getNodes() {
    return nodes.values();
  }

  /**
   * Get all edges from the graph. Assumes each edge has one substrate; otherwise this may return duplicates.
   * TODO: generalize this if we generalize the graph for multiple substrate edges.
   *
   * @return A collection of the graph's edges.
   */
  public Collection<NetworkEdge> getEdges() {
    return nodes.values().stream().flatMap(node -> node.getOutEdges().stream()).collect(Collectors.toList());
  }

  /**
   * Get all nodes that are one step forward from this node. These are predicted products of reactions that have this
   * node as a substrate.
   *
   * @param node The starting node.
   * @return The list of potential product nodes.
   */
  public List<NetworkNode> getChildren(NetworkNode node) {
    return node.getOutEdges().stream().map(edge -> getNode(edge.getProduct())).collect(Collectors.toList());
  }

  /**
   * Get all nodes that are one step before this node. These are substrates of reactions that are predicted to produce
   * this node as a product.
   *
   * @param node The starting node.
   * @return The list of potential substrate nodes.
   */
  public List<NetworkNode> getParents(NetworkNode node) {
    return node.getInEdges().stream().map(edge -> getNode(edge.getSubstrate())).collect(Collectors.toList());
  }

  /**
   * Load an edge into the network from a reaction in our reactions DB.
   * Only works on single-substrate reactions. For a reaction with multiple products, one edge is loaded into the
   * network for each product.  i.e. the reaction A -> (B,C) creates two edges: (A->B) and (A->C).
   *
   * @param db    The DB to look in.
   * @param rxnId The reaction ID.
   */
  public void loadEdgeFromReaction(MongoDB db, long rxnId) {
    Reaction reaction = db.getReactionFromUUID(rxnId);
    if (reaction.getSubstrates().length != 1) {
      LOGGER.warn("Can only load edge from reaction with one substrate. Refusing to load.");
      return;
    }

    Long substrateId = reaction.getSubstrates()[0];
    String substrateInchi = db.getChemicalFromChemicalUUID(substrateId).getInChI();
    createNodeIfNoneExists(substrateInchi);

    for (Long productId : reaction.getProducts()) {
      String productInchi = db.getChemicalFromChemicalUUID(productId).getInChI();
      createNodeIfNoneExists(productInchi);
      NetworkEdge edge = new NetworkEdge(substrateInchi, productInchi);
      edge.setReactionId(reaction.getUUID());
      linkEdge(edge);
    }
  }

  /**
   * Loads all predictions from a prediction corpus into the network as edges.
   * Only works on single-substrate predictions.  For a prediction with multiple products, one edge is loaded into the
   * network for each product. i.e. the prediction A -> (B,C) creates two edges: (A->B), (A->C).
   *
   * @param predictionCorpus
   */
  public void loadSingleSubstratePredictions(L2PredictionCorpus predictionCorpus) {
    for (L2Prediction prediction : predictionCorpus.getCorpus()) {
      if (prediction.getSubstrateInchis().size() != 1) {
        LOGGER.warn("Can only load edge from prediction with one substrate. Refusing to load.");
        return;
      }

      String substrateInchi = prediction.getSubstrateInchis().get(0);
      createNodeIfNoneExists(substrateInchi);

      for (String productInchi : prediction.getProductInchis()) {
        createNodeIfNoneExists(productInchi);
        NetworkEdge edge = new NetworkEdge(substrateInchi, productInchi);
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
    nodes.putIfAbsent(inchi, new NetworkNode(inchi));
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
