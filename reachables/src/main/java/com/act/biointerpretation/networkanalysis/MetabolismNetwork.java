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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents a metabolism network, cataloging all possible predicted chemical transformations that could be happening
 * in a given sample.  Currently all edges encode one-substrate-one-product transformations.
 * TODO: generalize the network to encapsulate multiple-substrate, multiple-product dependencies.
 */
public class MetabolismNetwork {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(MetabolismNetwork.class);

  // Map from inchis to nodes.
  // TODO: generalize to case when we no longer exclusively use inchis
  @JsonProperty("nodes")
  Map<String, NetworkNode> nodes;

  @JsonProperty("edges")
  Collection<NetworkEdge> edges;

  public MetabolismNetwork() {
    nodes = new ConcurrentHashMap<>();
    edges = new ArrayList<>();
  }

  public NetworkNode getNode(String inchi) {
    NetworkNode node = nodes.get(inchi);
    if (node != null) {
      return node;
    }
    LOGGER.warn("Couldn't get node with inchi %s", inchi);
    return null;
  }

  @JsonIgnore
  public Collection<NetworkNode> getNodes() {
    return Collections.unmodifiableCollection(nodes.values());
  }

  /**
   * Get all edges from the graph.
   *
   * @return An unmodifiable collection of the graph's edges.
   */
  public Collection<NetworkEdge> getEdges() {
    return Collections.unmodifiableCollection(edges);
  }

  /**
   * Get all nodes that are one step forward from this node. These are predicted products of reactions that have this
   * node as a substrate.
   *
   * @param node The starting node.
   * @return The list of potential product nodes.
   */
  public List<NetworkNode> getDerivatives(NetworkNode node) {
    return node.getOutEdges().stream()
      .flatMap(edge -> edge.getProducts().stream()
        .map(product -> getNode(product)))
      .collect(Collectors.toList());
  }

  /**
   * Get all nodes that are one step before this node. These are substrates of reactions that are predicted to produce
   * this node as a product.
   *
   * @param node The starting node.
   * @return The list of potential substrate nodes.
   */
  public List<NetworkNode> getPrecursors(NetworkNode node) {
    return node.getInEdges().stream()
      .flatMap(edge -> edge.getSubstrates().stream()
        .map(substrate -> getNode(substrate)))
      .collect(Collectors.toList());
  }

  /**
   * Load an edge into the network from a reaction in our reactions DB.
   *
   * @param db    The DB to look in.
   * @param rxnId The reaction ID.
   */
  public void loadEdgeFromReaction(MongoDB db, long rxnId) {
    Reaction reaction = db.getReactionFromUUID(rxnId);
    List<Long> substrateIds = Arrays.asList(reaction.getSubstrates());
    List<String> substrates = substrateIds.stream().map(id -> db.getChemicalFromChemicalUUID(id).getInChI())
      .collect(Collectors.toList());

    List<Long> productIds = Arrays.asList(reaction.getProducts());
    List<String> products = productIds.stream().map(id -> db.getChemicalFromChemicalUUID(id).getInChI())
      .collect(Collectors.toList());

    NetworkEdge edge = new NetworkEdge(substrates, products);
    edge.addReactionId(reaction.getUUID());
    addEdge(edge);
  }

  /**
   * Loads all predictions from a prediction corpus into the network as edges.
   *
   * @param predictionCorpus
   */
  public void loadPredictions(L2PredictionCorpus predictionCorpus) {
    predictionCorpus.getCorpus().forEach(prediction -> loadEdgeFromPrediction(prediction));
  }

  /**
   * Loads a single prediction into the graph as an edge or edges.
   *
   * @param prediction The prediction to load.
   */
  public void loadEdgeFromPrediction(L2Prediction prediction) {
    List<String> substrates= prediction.getSubstrateInchis();
    List<String> products = prediction.getProductInchis();

    NetworkEdge edge = new NetworkEdge(substrates, products);
    edge.addProjectorName(prediction.getProjectorName());
    addEdge(edge);
  }

  /**
   * Adds a given edge to the graph.
   * First, adds the substrate and product nodes to the graph, if they don't already exist.
   * Then, checks for an already existing edge with the same substrate and product; if such an edge exists, this edge's
   * auxiliary data is merged into the already existing edge.  If no such edge exists, a new edge is added.
   *
   * @param edge The edge to add.
   */
  public void addEdge(NetworkEdge edge) {

    edge.getSubstrates().forEach(s -> createNodeIfNoneExists(s));
    edge.getProducts().forEach(p -> createNodeIfNoneExists(p));

    NetworkNode substrateNode = getNode(edge.getSubstrates().get(0));
    List<NetworkEdge> equivalentEdges = substrateNode.getOutEdges().stream()
        .filter(e -> e.hasSameChemicals(edge))
        .collect(Collectors.toList());
    assert (equivalentEdges.size() <= 1); // Should be at most one edge with a given substrate, product pair

    if (equivalentEdges.isEmpty()) { // If no equivalent edge exists, add the new edge
      edge.getProducts().forEach(product -> getNode(product).addInEdge(edge));
      edge.getSubstrates().forEach(substrate -> getNode(substrate).addInEdge(edge));
      edges.add(edge);
    } else { // If there is an equivalent edge, merge the data into that edge.
      equivalentEdges.get(0).addDataFrom(edge);
    }
  }

  /**
   * Checks if a node with a given inchi is already in the map.  If so, returns the node. If not, creates a new node
   * with that inchi and returns it.
   * TODO: generalize this to handle metabolites rather than just inchis
   *
   * @param inchi The inchi.
   * @return The node.
   */
  private NetworkNode createNodeIfNoneExists(String inchi) {
    NetworkNode node = nodes.get(inchi);
    if (node == null) {
      node = nodes.put(inchi, new NetworkNode(new Metabolite(inchi)));
    }
    return node;
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public void loadFromJsonFile(File inputFile) throws IOException {
    MetabolismNetwork networkFromFile = OBJECT_MAPPER.readValue(inputFile, MetabolismNetwork.class);

    this.nodes = networkFromFile.nodes;
    edges.forEach(e -> addEdge(e));
  }
}
