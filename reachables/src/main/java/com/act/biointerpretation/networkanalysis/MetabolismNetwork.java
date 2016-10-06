package com.act.biointerpretation.networkanalysis;

import act.server.MongoDB;
import act.shared.Reaction;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents a metabolism network, cataloging all possible predicted chemical transformations that could be happening
 * in a given sample.
 */
public class MetabolismNetwork {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Logger LOGGER = LogManager.getFormatterLogger(MetabolismNetwork.class);

  // Map from inchis to nodes.
  // TODO: generalize to case when we no longer exclusively use inchis
  @JsonProperty("nodes")
  Map<String, NetworkNode> nodes;

  @JsonProperty("edges")
  List<NetworkEdge> edges;

  public MetabolismNetwork() {
    nodes = new ConcurrentHashMap<>();
    edges = new ArrayList<>();
  }

  public Optional<NetworkNode> getNodeOption(String inchi) {
    NetworkNode node = nodes.get(inchi);
    if (node != null) {
      return Optional.of(node);
    }
    return Optional.empty();
  }

  public NetworkNode getNode(String inchi) {
    NetworkNode node = nodes.get(inchi);
    if (node != null) {
      return node;
    }
    throw new NullPointerException("Node not found! If you aren't sure if the node is there, use getNodeOption()");
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
    List<NetworkNode> derivatives = new ArrayList<>();
    for (NetworkEdge edge : node.getOutEdges()) {
      edge.getProducts().forEach(p -> derivatives.add(getNode(p)));
    }
    return derivatives;
  }

  /**
   * Get all nodes that are one step before this node. These are substrates of reactions that are predicted to produce
   * this node as a product.
   *
   * @param node The starting node.
   * @return The list of potential substrate nodes.
   */
  public List<NetworkNode> getPrecursors(NetworkNode node) {
    List<NetworkNode> precursors = new ArrayList<>();
    for (NetworkEdge edge : node.getInEdges()) {
      edge.getSubstrates().forEach(s -> precursors.add(getNode(s)));
    }
    return precursors;
  }

  /**
   * Trace the pathway back from the given startNode for up to numSteps steps, and return the subgraph of all
   * precursors found.  This is intended to supply explanatory pathways for the input node.
   *
   * @param startNode The node to explain.
   * @param numSteps The number of steps back from the node to search.
   * @return A subnetwork representing the precursors of the given starting metabolite.
   */
  public MetabolismNetwork getPrecursorSubgraph(NetworkNode startNode, int numSteps) {
    if (numSteps <= 0) {
      throw new IllegalArgumentException("Precursor graph is only well-defined for numSteps > 0");
    }

    MetabolismNetwork subgraph = new MetabolismNetwork();
    Set<NetworkNode> frontier = new HashSet<>();
    frontier.add(startNode);

    for (int n = 0; n < numSteps; n++) {
      // Move frontier back, then add all new edges. Edge adding will add substrate and product nodes as necessary.
      frontier = frontier.stream().flatMap(node -> getPrecursors(node).stream()).collect(Collectors.toSet());
      frontier.forEach(node -> node.getOutEdges().forEach(e -> subgraph.addEdge(e)));
    }

    return subgraph;
  }

  /**
   * Load an edge into the network from a reaction in our reactions DB.
   *
   * @param db The DB to look in.
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
    if (equivalentEdges.size() > 1) {
      // Should be at most one edge with a given substrate, product pair
      throw new IllegalStateException("Two edges with same substrates and products found in the same graph");
    }

    if (equivalentEdges.isEmpty()) { // If no equivalent edge exists, add the new edge
      edge.getProducts().forEach(product -> getNode(product).addInEdge(edge));
      edge.getSubstrates().forEach(substrate -> getNode(substrate).addInEdge(edge));
      edges.add(edge);
    } else { // If there is an equivalent edge, merge the data into that edge.
      equivalentEdges.get(0).merge(edge);
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
    networkFromFile.edges.forEach(e -> this.addEdge(e));
  }
}
