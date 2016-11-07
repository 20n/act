package com.act.biointerpretation.networkanalysis;

import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.MolecularStructure;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private static final String METABOLITE_7 = "G";

  private static final Double MASS_1 = 0.1;
  private static final Double MASS_2 = 0.2;
  private static final Double MASS_3 = 0.4;
  private static final Double MASS_4 = 0.6;
  private static final Double MASS_5 = 0.7;
  private static final Double MASS_6 = 0.9;
  private static final Double MASS_7 = 1.0;

  private static List<NetworkNode> nodes = new ArrayList<>();
  private static Map<String, Integer> inchiToID = new HashMap<>();

  private static final Integer RXN_1 = 100;
  private static final Integer RXN_2 = 102;
  private static final String PROJECTOR = "RO";

  @Before
  public void createMockNodes() {
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_1, MASS_1)));
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_2, MASS_2)));
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_3, MASS_3)));
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_4, MASS_4)));
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_5, MASS_5)));
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_6, MASS_6)));
    nodes.add(new NetworkNode(getMockMetabolite(METABOLITE_7, MASS_7)));

    nodes.forEach(n -> inchiToID.put(n.getMetabolite().getStructure().get().getInchi(), n.getUID()));
  }

  private Metabolite getMockMetabolite(String s, double mass) {
    MolecularStructure structure = Mockito.mock(MolecularStructure.class);
    Mockito.when(structure.getInchi()).thenReturn(s);
    Mockito.when(structure.getMonoIsotopicMass()).thenReturn(mass);

    Metabolite m = Mockito.mock(Metabolite.class);
    Mockito.when(m.getStructure()).thenReturn(Optional.of(structure));
    Mockito.when(m.getMonoIsotopicMass()).thenReturn(mass);
    return m;
  }

  @Test
  public void testAddNewEdge() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    nodes.forEach(network::addNode);

    // Act
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2));

    // Assert
    assertEquals("Graph should have one edge", 1, network.getEdges().size());
    assertEquals("Product should have one in edge.", 1, network.getNodeByInchi(METABOLITE_2).getInEdges().size());
    assertEquals("Product should have no out edges.", 0, network.getNodeByInchi(METABOLITE_2).getOutEdges().size());
    assertEquals("Substrate should have one out edge.", 1, network.getNodeByInchi(METABOLITE_1).getOutEdges().size());
    assertEquals("Substrate should have no in edge.", 0, network.getNodeByInchi(METABOLITE_1).getInEdges().size());

    NetworkEdge edge = network.getEdges().iterator().next();
    assertEquals("Edge should have one substrate", 1, edge.getSubstrates().size());
    ;
    assertEquals("Edge's substrate should be METABOLITE_1", inchiToID.get(METABOLITE_1), edge.getSubstrates().get(0));
    assertEquals("Edge should have one product", 1, edge.getProducts().size());
    assertEquals("Edge's product should be METABOLITE_2", inchiToID.get(METABOLITE_2), edge.getProducts().get(0));
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
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2)).addReactionId(RXN_1);

    // Act
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2)).addReactionId(RXN_2).addProjectorName(PROJECTOR);

    // Assert
    assertEquals("Graph should have one edge", 1, network.getEdges().size());
    assertEquals("Product should have one in edge.", 1, network.getNodeByInchi(METABOLITE_2).getInEdges().size());
    assertEquals("Substrate should have one out edge.", 1, network.getNodeByInchi(METABOLITE_1).getOutEdges().size());

    NetworkEdge edge = network.getEdges().iterator().next();
    assertEquals("Edge should have one substrate", 1, edge.getSubstrates().size());
    assertEquals("Edge's substrate should be METABOLITE_1", inchiToID.get(METABOLITE_1), edge.getSubstrates().get(0));
    assertEquals("Edge should have one product", 1, edge.getProducts().size());
    assertEquals("Edge's product should be METABOLITE_2", inchiToID.get(METABOLITE_2), edge.getProducts().get(0));
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
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_2));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_3, METABOLITE_4));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_5), Arrays.asList(METABOLITE_3, METABOLITE_1));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_5), Arrays.asList(METABOLITE_3, METABOLITE_2));

    // Act
    List<NetworkNode> derivatives = network.getDerivatives(network.getNodeByInchi(METABOLITE_1));

    // Assert
    List<String> expectedInchis = Arrays.asList(METABOLITE_2, METABOLITE_3, METABOLITE_4);
    List<String> inchis = derivatives.stream().map(n -> n.getMetabolite().getStructure().get().getInchi()).collect(Collectors.toList());
    assertEquals("Should be 3 derivatives.", 3, inchis.size());
    assertTrue("Derivatives should be metabolites 2,3,4", inchis.containsAll(expectedInchis));
  }

  @Test
  public void testGetPrecursors() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_2), Arrays.asList(METABOLITE_1));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_1));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1, METABOLITE_3), Arrays.asList(METABOLITE_5));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_2, METABOLITE_3), Arrays.asList(METABOLITE_5));

    // Act
    List<NetworkNode> precursors = network.getPrecursors(network.getNodeByInchi(METABOLITE_1));

    // Assert
    List<String> expectedInchis = Arrays.asList(METABOLITE_2, METABOLITE_3, METABOLITE_4);
    List<String> inchis = precursors.stream().map(n -> n.getMetabolite().getStructure().get().getInchi()).collect(Collectors.toList());
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
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_5));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_3), Arrays.asList(METABOLITE_1, METABOLITE_2));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_3));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_2), Arrays.asList(METABOLITE_3));
    NetworkNode e = network.getNodeByInchi(METABOLITE_5);

    // Act
    PrecursorReport report = network.getPrecursorReport(e, 1);

    // Assert
    assertEquals("Report has correct target", e.getMetabolite(), report.getTarget());

    ImmutableNetwork precursorNetwork = report.getNetwork();
    assertEquals("Subgraph should contain three nodes", 3, precursorNetwork.getNodes().size());
    assertEquals("Subgraph should contain one edge", 1, precursorNetwork.getEdges().size());

    assertTrue("Subgraph should contain query node", precursorNetwork.getNodeOptionByInchi(METABOLITE_5).isPresent());
    assertTrue("Subgraph should contain first precursor", precursorNetwork.getNodeOptionByInchi(METABOLITE_3).isPresent());
    assertTrue("Subgraph should contain second precursor", precursorNetwork.getNodeOptionByInchi(METABOLITE_4).isPresent());
  }

  /**
   * Test precursor report of 2 levels.
   * Adds one relevant inedge, one irrelevant outedge of a precursor, and two level 2 precursors.
   * Test verifies that every metabolite except METABOLITE 6 is reported. Metabolite 6 should not be reported since
   * it is the derivative of a precursor of the target metabolite 5. Metabolites 1-5 are either direct percursors,
   * or precursors of precursors, and so they should all be reported. Metabolite 7 is a second product of an in-edge
   * of the target, so it should also be in the subgraph, but not the level map.
   */
  @Test
  public void testPrecursorSubgraphN2() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_5, METABOLITE_7));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_3, METABOLITE_4), Arrays.asList(METABOLITE_6));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_1), Arrays.asList(METABOLITE_3));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_2), Arrays.asList(METABOLITE_3));
    NetworkNode e = network.getNodeByInchi(METABOLITE_5);

    // Act
    PrecursorReport report = network.getPrecursorReport(e, 2);

    // Assert
    ImmutableNetwork precursorNetwork = report.getNetwork();
    assertEquals("Subgraph should contain six nodes", 6, precursorNetwork.getNodes().size());
    assertEquals("Subgraph should contain three edges", 3, precursorNetwork.getEdges().size());

    assertTrue("Subgraph should contain query node", precursorNetwork.getNodeOptionByInchi(METABOLITE_5).isPresent());
    assertTrue("Subgraph should contain first n1 precursor", precursorNetwork.getNodeOptionByInchi(METABOLITE_3).isPresent());
    assertTrue("Subgraph should contain second n1 precursor", precursorNetwork.getNodeOptionByInchi(METABOLITE_4).isPresent());
    assertTrue("Subgraph should contain second product of target's in-edge1", precursorNetwork.getNodeOptionByInchi(METABOLITE_7).isPresent());
    assertTrue("Subgraph should contain second n2 precursor", precursorNetwork.getNodeOptionByInchi(METABOLITE_1).isPresent());
    assertTrue("Subgraph should contain second n2 precursor", precursorNetwork.getNodeOptionByInchi(METABOLITE_2).isPresent());

    assertEquals("Level of target is 0", 0, (int) report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_5)));
    assertEquals("Level of 1st precursor is 1", 1, (int) report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_3)));
    assertEquals("Level of 2nd precursor is 2", 2, (int) report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_1)));
    assertEquals("Level of non-precursor is null", null, report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_7)));
  }


  /**
   * Test precursor report of 2 levels to see how it handles cycles.  The key test is that the target, METABOLITE_5,
   * should have level 0, despite the fact that it is also its own second-level precursor.
   */
  @Test
  public void testPrecursorSubgraphCycles() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_4), Arrays.asList(METABOLITE_5));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_3), Arrays.asList(METABOLITE_4));
    network.addEdgeFromInchis(Arrays.asList(METABOLITE_5), Arrays.asList(METABOLITE_4));
    NetworkNode e = network.getNodeByInchi(METABOLITE_5);

    // Act
    PrecursorReport report = network.getPrecursorReport(e, 2);

    // Assert
    ImmutableNetwork precursorNetwork = report.getNetwork();
    assertEquals("Level of target is 0", 0, (int) report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_5)));
    assertEquals("Level of 1st precursor is 1", 1, (int) report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_4)));
    assertEquals("Level of 2nd precursor is 2", 2, (int) report.getLevel(precursorNetwork.getNodeByInchi(METABOLITE_3)));
  }
}
