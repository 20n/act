package com.act.biointerpretation.networkanalysis;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Reaction;
import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ReactionKeywords;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacob.com.NotImplementedException;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a metabolism network, cataloging all possible predicted chemical transformations that could be happening
 * in a given sample.
 */
public class MetabolismNetwork implements ImmutableNetwork {

  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Logger LOGGER = LogManager.getFormatterLogger(MetabolismNetwork.class);

  private static final String ORG_FIELD = ReactionKeywords.ORGANISM$.MODULE$.toString();

  // Map from inchis to nodes.
  // TODO: generalize to case when we no longer exclusively use inchis
  @JsonProperty("nodes")
  List<NetworkNode> nodes;

  @JsonProperty("edges")
  List<NetworkEdge> edges;

  @JsonIgnore
  Map<Integer, NetworkNode> UIDIndex;
  @JsonIgnore
  Map<String, NetworkNode> inchiIndex;

  @JsonCreator
  private MetabolismNetwork(@JsonProperty("nodes") List<NetworkNode> nodes,
                            @JsonProperty("edges") List<NetworkEdge> edges) {
    this();
    nodes.forEach(this::addNode);
    edges.forEach(this::addEdge);
  }

  public MetabolismNetwork() {
    nodes = new ArrayList<>();
    edges = new ArrayList<>();
    UIDIndex = new HashMap<>();
    inchiIndex = new HashMap<>();
  }

  @Override
  public NetworkNode getNodeByUID(Integer uid) {
    NetworkNode result = UIDIndex.get(uid);
    if (result == null) {
      throw new IllegalArgumentException("Node with given UID not found!");
    }
    return result;
  }

  @Override
  public NetworkNode getNodeByInchi(String inchi) {
    NetworkNode result = inchiIndex.get(inchi);
    if (result == null) {
      throw new IllegalArgumentException("Didn't find node with inchi " + inchi);
    }
    return result;
  }

  @Override
  public Optional<NetworkNode> getNodeOptionByInchi(String inchi) {
    return Optional.ofNullable(inchiIndex.get(inchi));
  }

  @Override
  public List<NetworkNode> getNodesByMass(Double mass, Double massTolerance) {
    throw new NotImplementedException("Mass indexing not yet implemented.");
  }

  @JsonIgnore
  @Override
  public Collection<NetworkNode> getNodes() {
    return Collections.unmodifiableCollection(nodes);
  }

  /**
   * Get all edges from the graph.
   *
   * @return An unmodifiable collection of the graph's edges.
   */
  public Collection<NetworkEdge> getEdges() {
    return Collections.unmodifiableCollection(edges);
  }

  @Override
  public Set<NetworkNode> getSubstrates(NetworkEdge edge) {
    return edge.getSubstrates().stream().map(this::getNodeByUID).collect(Collectors.toSet());
  }

  @Override
  public Set<NetworkNode> getProducts(NetworkEdge edge) {
    return edge.getProducts().stream().map(this::getNodeByUID).collect(Collectors.toSet());
  }

  /**
   * Get all nodes that are one step forward from this node. These are predicted products of reactions that have this
   * node as a substrate.
   *
   * @param node The starting node.
   * @return The list of potential product nodes.
   */
  @Override
  public List<NetworkNode> getDerivatives(NetworkNode node) {
    List<NetworkNode> derivatives = new ArrayList<>();
    for (NetworkEdge edge : node.getOutEdges()) {
      edge.getProducts().forEach(p -> derivatives.add(getNodeByUID(p)));
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
  @Override
  public List<NetworkNode> getPrecursors(NetworkNode node) {
    List<NetworkNode> precursors = new ArrayList<>();
    for (NetworkEdge edge : node.getInEdges()) {
      edge.getSubstrates().forEach(s -> precursors.add(getNodeByUID(s)));
    }
    return precursors;
  }

  /**
   * Trace the pathway back from the given startNode for up to numSteps steps, and return the subgraph of all
   * precursors found.  This is intended to supply explanatory pathways for the input node.
   *
   * @param startNode The node to explain.
   * @param numSteps The number of steps back from the node to search.
   * @return A report representing the precursors of the given starting metabolite.
   */
  public PrecursorReport getPrecursorReport(NetworkNode startNode, int numSteps) {
    if (numSteps <= 0) {
      throw new IllegalArgumentException("Precursor graph is only well-defined for numSteps > 0");
    }

    MetabolismNetwork subgraph = new MetabolismNetwork();
    Map<NetworkNode, Integer> levelMap = new HashMap<>();
    Set<NetworkNode> frontier = new HashSet<>();
    frontier.add(startNode);
    levelMap.put(startNode, 0);

    for (MutableInt l = new MutableInt(1); l.toInteger() <= numSteps; l.increment()) {
      // Get edges leading into the derivative frontier
      List<NetworkEdge> edges = frontier.stream().flatMap(n -> n.getInEdges().stream()).collect(Collectors.toList());
      // Add all of the nodes adjacent to the edges, and the edges themselves, to the subgraph
      edges.forEach(e -> this.getSubstrates(e).forEach(subgraph::addNode));
      edges.forEach(e -> this.getProducts(e).forEach(subgraph::addNode));
      edges.forEach(subgraph::addEdge);
      // Calculate new frontier, excluding already-labeled nodes to avoid cycles
      frontier = edges.stream().flatMap(e -> this.getSubstrates(e).stream()).collect(Collectors.toSet());
      frontier.removeIf(levelMap::containsKey);
      // Label remaining nodes with appropriate level.
      frontier.forEach(n -> levelMap.put(n, l.toInteger()));
    }

    return new PrecursorReport(startNode.getMetabolite(), subgraph, levelMap);
  }

  /**
   * Load all reactions from a given DB into the network.
   *
   * @param db The DB.
   */
  public void loadAllEdgesFromDb(MongoDB db) {
    DBIterator iterator = db.getIteratorOverReactions();
    Reaction reaction;
    int count = 0;
    while ((reaction = db.getNextReaction(iterator)) != null) {
      this.addEdgeFromReaction(db, reaction);
      if (count % 1000 == 0) {
        LOGGER.info("Processed %d reactions.", count);
      }
      count++;
    }
  }

  /**
   * Loads an edge from a DB reaction.
   * TODO: optimize number of DB calls made so that this will run faster.
   *
   * @return The added edge if any, or null if the reaction's substrates or products were empty.
   */
  private NetworkEdge addEdgeFromReaction(MongoDB db, Reaction reaction) {
    List<Long> substrateIds = Arrays.asList(reaction.getSubstrates());
    List<String> substrates = new ArrayList<>();
    for (Long s : substrateIds) {
      String inchi = db.getChemicalFromChemicalUUID(s).getInChI();
      for (int i = 0; i < denullCoeff(reaction.getSubstrateCoefficient(s)); i++) {
        substrates.add(inchi);
      }
    }

    List<Long> productIds = Arrays.asList(reaction.getProducts());
    List<String> products = new ArrayList<>();
    for (Long p : productIds) {
      String inchi = db.getChemicalFromChemicalUUID(p).getInChI();
      for (int i = 0; i < denullCoeff(reaction.getProductCoefficient(p)); i++) {
        products.add(inchi);
      }
    }

    if (substrates.isEmpty() || products.isEmpty()) {
      return null;
    }

    NetworkEdge edge = addEdgeFromInchis(substrates, products);
    edge.addReactionId(reaction.getUUID());

    for (JSONObject protein : reaction.getProteinData()) {
      if (protein.has(ORG_FIELD)) {
        edge.addOrg(db.getOrganismNameFromId(protein.getLong(ORG_FIELD)));
      }
    }

    return edge;
  }

  /**
   * Assumes any coefficient which is null should be 1. Null coefficients were given NullPointerExceptions previously.
   *
   * @param coeffOrNull The Integer value directly from the DB.
   * @return The input value if not null; otherwise 1.
   */
  private Integer denullCoeff(Integer coeffOrNull) {
    if (coeffOrNull == null) {
      return 1;
    }
    return coeffOrNull;
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
    List<String> substrates = prediction.getSubstrateInchis();
    List<String> products = prediction.getProductInchis();

    NetworkEdge edge = addEdgeFromInchis(substrates, products);
    edge.addProjectorName(prediction.getProjectorName());
  }

  /**
   * Adds an edge; assumes all nodes pointed to by the edge exist
   */
  public NetworkEdge addEdge(NetworkEdge edge) {
    edge.getSubstrates().forEach(s -> getNodeByUID(s).addOutEdge(edge));
    edge.getProducts().forEach(p -> getNodeByUID(p).addInEdge(edge));
    edges.add(edge);
    return edge;
  }

  /**
   * Adds a given edge to the graph. Creates new nodes from inchis where there aren't already existing nodes.
   * First, adds the substrate and product nodes to the graph, if they don't already exist.
   * Then, checks for an already existing edge with the same substrate and product; if such an edge exists, this edge's
   * auxiliary data is merged into the already existing edge.  If no such edge exists, a new edge is added.
   *
   * @return The added edge.
   */
  public NetworkEdge addEdgeFromInchis(List<String> substrates, List<String> products) {
    List<Integer> sNodes = substrates.stream().map(this::createOrGetNodeFromInchi).map(NetworkNode::getUID)
        .collect(Collectors.toList());
    List<Integer> pNodes = products.stream().map(this::createOrGetNodeFromInchi).map(NetworkNode::getUID)
        .collect(Collectors.toList());

    NetworkEdge edge = new NetworkEdge(sNodes, pNodes);

    List<NetworkEdge> equivalentEdges = getNodeByUID(sNodes.get(0)).getOutEdges().stream()
        .filter(e -> e.hasSameSubstratesAndProducts(edge))
        .collect(Collectors.toList());
    if (equivalentEdges.size() > 1) {
      // Should be at most one edge with a given substrate, product pair
      throw new IllegalStateException("Two edges with same substrates and products found in the same graph");
    }

    if (equivalentEdges.isEmpty()) { // If no equivalent edge exists, add the new edge
      return addEdge(new NetworkEdge(sNodes, pNodes));
    } else { // If there is an equivalent edge, merge the data into that edge.
      return equivalentEdges.get(0).merge(edge);
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
  private NetworkNode createOrGetNodeFromInchi(String inchi) {
    NetworkNode node = inchiIndex.get(inchi);
    if (node == null) {
      return addNode(new NetworkNode(new InchiMetabolite(inchi)));
    }
    return node;
  }

  /**
   * Adds a node to network if its UID is unique. If a node already exists with this UID, returns the existing node
   * without modifying the graph.
   *
   * @param node The node to add.
   * @return The node added, or the existing node.
   */
  public NetworkNode addNode(NetworkNode node) {
    if (UIDIndex.get(node.getUID()) != null) {
      return UIDIndex.get(node.getUID());
    }
    nodes.add(node);
    UIDIndex.put(node.getUID(), node);
    node.getMetabolite().getStructure().ifPresent(s -> inchiIndex.put(s.getInchi(), node));
    return node;
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public static MetabolismNetwork getNetworkFromJsonFile(File inputFile) throws IOException {
    return OBJECT_MAPPER.readValue(inputFile, MetabolismNetwork.class);
  }
}
