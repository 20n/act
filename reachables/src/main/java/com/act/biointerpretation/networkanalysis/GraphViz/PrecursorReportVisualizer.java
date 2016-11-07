package com.act.biointerpretation.networkanalysis.GraphViz;

import com.act.biointerpretation.networkanalysis.ImmutableNetwork;
import com.act.biointerpretation.networkanalysis.NetworkEdge;
import com.act.biointerpretation.networkanalysis.NetworkNode;
import com.act.biointerpretation.networkanalysis.PrecursorReport;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class for turning a PrecursorReport into a DotGraph that can be visualized.
 * Implements JavaRunnable for workflow incorporation.
 */
public class PrecursorReportVisualizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PrecursorReportVisualizer.class);

  // A map from organisms of interest to their respective colors.
  // Any edge with any organism name which contains one of these strings will be colored.
  // An edge which matches multiple keys of this map will be given one edge per distinct color specified by its
  // org matches.
  private final Map<String, DotColor> orgToColor;

  public void addOrgOfInterest(String org, DotColor color) {
    orgToColor.put(org, color);
  }

  public PrecursorReportVisualizer() {
    orgToColor = new HashMap<>();
  }

  public Runner getRunner(File inputNetwork, File outputFile) {
    return new Runner(inputNetwork, outputFile);
  }

  /**
   * Builds DOT graph representation of the precursor report.  The graph is printed out with the target metabolite
   * on the bottom, all its direct precursors one level up, all their precursors two levels up, etc.  Only edges
   * between adjacent levels are drawn, resulting in a reverse BFS tree representation of the precursors.
   *
   * @param report The PrecursorReport.
   * @return The DotGraph.
   */
  public DotGraph buildDotGraph(PrecursorReport report) {
    ImmutableNetwork network = report.getNetwork();
    DotGraph graph = new DotGraph();

    for (NetworkNode node : network.getNodes()) {
      if (report.getLcmsConfidence(node) != null && report.getLcmsConfidence(node) > .75) {
        graph.addNode(new DotNode(node.getUID().toString()).setColor(DotColor.RED));
      }
    }

    // Add edges to the graph.  One or more DotEdges are added for each (substrate, product) pair where the substrate
    // is one level farther back in the tree than the product.
    for (NetworkEdge edge : network.getEdges()) {
      for (Integer substrate : edge.getSubstrates()) {
        for (Integer product : edge.getProducts()) {
          if (report.edgeInBfsTree(network.getNodeByUID(substrate), network.getNodeByUID(product))) {
            addEdgesToGraph(edge, substrate.toString(), product.toString(), graph);
          }
        }
      }
    }
    return graph;
  }

  /**
   * Helper method to build DotEdges given a NetworkEdge, and a particular (substrate, product) pair.
   * Each edge is formatted as a solid line if it has a DB reaction associated, or a dotted line otherwise.
   * The color of the edge is determined by whether it matches an organism of interest.
   * If the NetworkEdge matches multiple orgs of interest, one edge is drawn for each one it matches,
   * in the appropriate color.
   */
  private void addEdgesToGraph(NetworkEdge edge, String substrateId, String productId, DotGraph graph) {
    DotEdge.EdgeStyle style = edge.getReactionIds().isEmpty() ?
        DotEdge.EdgeStyle.DOTTED : DotEdge.EdgeStyle.DEFAULT_SOLID;

    Set<DotColor> colors = new HashSet<>();
    for (String orgOfInterest : orgToColor.keySet()) {
      if (matchesOrg(edge, orgOfInterest)) {
        colors.add(orgToColor.get(orgOfInterest));
      }
    }
    if (colors.isEmpty()) {
      colors.add(DotColor.DEFAULT_BLACK);
    }

    colors.stream().map(c -> new DotEdge(substrateId, productId).setColor(c).setStyle(style)).forEach(graph::addEdge);
  }

  /**
   * Helper method to test whether a given edge matches the orgOfInterest string.
   * Returns true if any organism entry in the edge contains orgOfInterest as a substring.
   * For example, we can put in "homo sapiens" if we only want to match to the specific genus and species,
   * but we can also put in only "homo" and this method will return true on any organism which has "homo"
   * in its name.
   */
  private boolean matchesOrg(NetworkEdge edge, String orgOfInterest) {
    return edge.getOrgs().stream().filter(s -> s.contains(orgOfInterest)).count() > 0;
  }

  /**
   * Workflow-compatible component for graph visualization.
   */
  public class Runner implements JavaRunnable {

    private final File inputFile;
    private final File outputFile;

    public Runner(File inputFile, File outputFile) {
      this.inputFile = inputFile;
      this.outputFile = outputFile;
    }

    /**
     * Loads in a precursorReport from file, builds a DotGraph from it, and writes it to file.
     * The graph can be visualized with an online GraphViz viewer like http://www.webgraphviz.com/.
     *
     * @throws IOException
     */
    @Override
    public void run() throws IOException {
      FileChecker.verifyInputFile(inputFile);
      FileChecker.verifyAndCreateOutputFile(outputFile);

      PrecursorReport report = PrecursorReport.readFromJsonFile(inputFile);

      LOGGER.info("Handled input files. Building dot graph.");
      DotGraph graph = buildDotGraph(report);

      LOGGER.info("Build graph. Writing out graph.");
      graph.writeGraphToFile(outputFile);
      LOGGER.info("Graph written to %s. Workflow complete!", outputFile.getAbsolutePath());
    }
  }
}
