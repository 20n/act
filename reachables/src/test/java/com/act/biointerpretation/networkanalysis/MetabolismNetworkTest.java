package com.act.biointerpretation.networkanalysis;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for class MetabolismNetwork
 */
public class MetabolismNetworkTest {

  private static final String METABOLITE_1 = "A";
  private static final String METABOLITE_2 = "B";
  private static final String METABOLITE_3 = "C";
  private static final String METABOLITE_4 = "D";
  private static final String METABOLITE_5 = "E";
  private static final String METABOLITE_6 = "F";

  private static final Integer RXN_1 = 100;
  private static final Integer RXN_2 = 102;
  private static final String PROJECTOR = "RO";

  @Test
  public void testAddNewEdge() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();

    // Act
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2)));

    // Assert
    assertEquals("Graph should have one edge", 1, network.getEdges().size());
    assertEquals("Product should have one in edge.", 1, network.getNode(METABOLITE_2).getInEdges().size());
    assertEquals("Substrate should have one out edge.", 1, network.getNode(METABOLITE_1).getOutEdges().size());

    NetworkEdge edge = network.getEdges().iterator().next();
    assertEquals("Edge should have one substrate", 1, edge.getSubstrates().size());
    ;
    assertEquals("Edge's substrate should be METABOLITE_1", METABOLITE_1, edge.getSubstrates().get(0));
    assertEquals("Edge should have one product", 1, edge.getProducts().size());
    assertEquals("Edge's product should be METABOLITE_2", METABOLITE_2, edge.getProducts().get(0));
    assertTrue("Edge's reaction info should be empty", edge.getReactionIds().isEmpty());
    assertTrue("Edge's RO info should be empty", edge.getProjectorNames().isEmpty());
  }

  /**
   * Tests adding two edges with same substrates and products. Verifies that the edge is not duplicated,
   * but the reaction and projection info from the edges is merged.
   */
  @Test
  public void testAddNewRedundantEdge() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    NetworkEdge e1 = new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2));
    e1.addReactionId(RXN_1);
    NetworkEdge e2 = new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2));
    e2.addReactionId(RXN_2);
    e2.addProjectorName(PROJECTOR);

    // Act
    network.addEdge(e1);
    network.addEdge(e2);

    // Assert
    assertEquals("Graph should have one edge", 1, network.getEdges().size());
    assertEquals("Product should have one in edge.", 1, network.getNode(METABOLITE_2).getInEdges().size());
    assertEquals("Substrate should have one out edge.", 1, network.getNode(METABOLITE_1).getOutEdges().size());

    NetworkEdge edge = network.getEdges().iterator().next();
    assertEquals("Edge should have one substrate", 1, edge.getSubstrates().size());
    assertEquals("Edge's substrate should be METABOLITE_1", METABOLITE_1, edge.getSubstrates().get(0));
    assertEquals("Edge should have one product", 1, edge.getProducts().size());
    assertEquals("Edge's product should be METABOLITE_2", METABOLITE_2, edge.getProducts().get(0));
    assertEquals("Edge's reaction info should contain 2 reactions", 2, edge.getReactionIds().size());
    assertTrue("Edge's reaction info should contain the right reactions",
        edge.getReactionIds().containsAll(Arrays.asList(RXN_1, RXN_2)));
    assertEquals("Edge's RO info should contain one RO", 1, edge.getProjectorNames().size());
    assertTrue("Edge's RO info should contain the right RO", edge.getProjectorNames().contains(PROJECTOR));
  }

  @Test
  public void testGetDerivatives() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_3, METABOLITE_4)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_5), Arrays.asList(METABOLITE_3, METABOLITE_1)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_5), Arrays.asList(METABOLITE_3, METABOLITE_2)));

    // Act
    List<NetworkNode> derivatives = network.getDerivatives(network.getNode(METABOLITE_1));

    // Assert
    List<String> expectedInchis = Arrays.asList(METABOLITE_2, METABOLITE_3, METABOLITE_4);
    List<String> inchis = derivatives.stream().map(n -> n.getMetabolite().getInchi()).collect(Collectors.toList());
    assertEquals("Should be 3 derivatives.", 3, inchis.size());
    assertTrue("Derivatives should be metabolites 2,3,4", inchis.containsAll(expectedInchis));
  }

  @Test
  public void testGetPrecursors() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_2), Arrays.asList(METABOLITE_1)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_1)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_1, METABOLITE_3), Arrays.asList(METABOLITE_5)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_2, METABOLITE_3), Arrays.asList(METABOLITE_5)));

    // Act
    List<NetworkNode> precursors = network.getPrecursors(network.getNode(METABOLITE_1));

    // Assert
    List<String> expectedInchis = Arrays.asList(METABOLITE_2, METABOLITE_3, METABOLITE_4);
    List<String> inchis = precursors.stream().map(n -> n.getMetabolite().getInchi()).collect(Collectors.toList());
    assertEquals("Should be 3 precursors.", 3, inchis.size());
    assertTrue("Precursors should be metabolites 2,3,4", inchis.containsAll(expectedInchis));
  }

  /**
   * Test precursor report of 1 level.
   * Adds one relevant inedge, one irrelevant outedge of the precursor, and two level 2 precursors.
   * Test verifies that only the substrates of the first inedge are included in the subgraph.
   */
  @Test
  public void testPrecursorSubgraphN1() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_5)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_3), Arrays.asList(METABOLITE_1, METABOLITE_2)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_3)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_2), Arrays.asList(METABOLITE_3)));
    NetworkNode e = network.getNode(METABOLITE_5);

    // Act
    PrecursorReport report = network.getPrecursorReport(e, 1);

    // Assert
    assertEquals("Report has correct target", e.getMetabolite(), report.getTarget());

    MetabolismNetwork precursorNetwork = report.getNetwork();
    assertEquals("Subgraph should contain three nodes", 3, precursorNetwork.getNodes().size());
    assertEquals("Subgraph should contain one edge", 1, precursorNetwork.getEdges().size());

    assertTrue("Subgraph should contain query node", precursorNetwork.getNodeOption(METABOLITE_5).isPresent());
    assertTrue("Subgraph should contain first precursor", precursorNetwork.getNodeOption(METABOLITE_3).isPresent());
    assertTrue("Subgraph should contain second precursor", precursorNetwork.getNodeOption(METABOLITE_4).isPresent());
  }

  /**
   * Test precursor report of 2 levels.
   * Adds one relevant inedge, one irrelevant outedge of a precursor, and two level 2 precursors.
   * Test verifies that every metabolite except METABOLITE 6 is reported.
   */
  @Test
  public void testPrecursorSubgraphN2() {

    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_5)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_6)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_3)));
    network.addEdge(new NetworkEdge(Arrays.asList(METABOLITE_2), Arrays.asList(METABOLITE_3)));
    NetworkNode e = network.getNode(METABOLITE_5);

    // Act
    PrecursorReport report = network.getPrecursorReport(e, 2);

    // Assert
    MetabolismNetwork precursorNetwork = report.getNetwork();
    assertEquals("Subgraph should contain five nodes", 5, precursorNetwork.getNodes().size());
    assertEquals("Subgraph should contain three edges", 3, precursorNetwork.getEdges().size());

    assertTrue("Subgraph should contain query node", precursorNetwork.getNodeOption(METABOLITE_5).isPresent());
    assertTrue("Subgraph should contain first n1 precursor", precursorNetwork.getNodeOption(METABOLITE_3).isPresent());
    assertTrue("Subgraph should contain second n1 precursor", precursorNetwork.getNodeOption(METABOLITE_4).isPresent());
    assertTrue("Subgraph should contain second n2 precursor", precursorNetwork.getNodeOption(METABOLITE_1).isPresent());
    assertTrue("Subgraph should contain second n2 precursor", precursorNetwork.getNodeOption(METABOLITE_2).isPresent());
  }

}