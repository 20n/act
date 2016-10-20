package com.act.biointerpretation.networkanalysis.GraphViz;

import com.act.biointerpretation.networkanalysis.MetabolismNetwork;
import com.act.biointerpretation.networkanalysis.NetworkEdge;
import com.act.biointerpretation.networkanalysis.NetworkNode;
import com.act.biointerpretation.networkanalysis.PrecursorReport;
import com.act.jobs.FileChecker;
import com.act.jobs.JavaRunnable;
import org.apache.commons.lang.mutable.MutableInt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class for turning a PrecursorReport into a DotGraph that can be visualized.
 * Implements JavaRunnable for workflow incorporation.
 */
public class PrecursorReportVisualizer {

  // A map from organisms of interest to their respective colors.
  // Any edge with any organism name which contains one of these strings will be colored.
  // An edge which matches multiple keys of this map will be assigned the color of one of those keys;
  // no guarantees are made as to which one it will be assigned.
  private final Map<String, DotEdge.EdgeColor> orgToColor;

  public void addOrgOfInterest(String org, DotEdge.EdgeColor color) {
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
    MetabolismNetwork network = report.getNetwork();
    String target = report.getTarget().getInchi();

    // Assign every inchi in the graph an ID, so we can label graph nodes by ID rather than inchi.
    // TODO: record this map somewhere so we can figure out which nodes are which chemicals.
    Map<String, String> inchiToIdMap = new HashMap<>();
    Integer id = 0;
    for (NetworkNode node : network.getNodes()) {
      inchiToIdMap.put(node.getMetabolite().getInchi(), id.toString());
      id++;
    }

    DotGraph graph = new DotGraph();
    List<NetworkNode> frontier = Arrays.asList(network.getNode(target));
    Map<String, Integer> idToLevel = new HashMap<>();
    // MutableInt allows variable to be "effectively constant" so we can use it in a lambda
    MutableInt level = new MutableInt(0);

    // Move the frontier backward successively. At each level, the next level is found by finding all precursors to the
    // current frontier, then removing all those precursors which have been seen previously.
    while (!frontier.isEmpty()) {
      frontier.forEach(n -> idToLevel.put(n.getMetabolite().getInchi(), level.intValue()));
      frontier = frontier.stream().
          flatMap(n -> network.getPrecursors(n).stream()).collect(Collectors.toList());
      frontier.removeIf(n -> idToLevel.containsKey(n.getMetabolite().getInchi()));
      level.increment();
    }

    // Add edges to the graph.  A DotEdge is added for each (substrate, product) pair where the substrate is one
    // level farther back in the tree than the product.
    for (NetworkEdge edge : network.getEdges()) {
      for (String substrate : edge.getSubstrates()) {
        for (String product : edge.getProducts()) {
          if (edgeInBfsTree(substrate, product, idToLevel)) {
            graph.addEdge(buildDotEdge(edge, inchiToIdMap.get(substrate), inchiToIdMap.get(product)));
          }
        }
      }
    }
    return graph;
  }

  /**
   * Returns true if the substrate is exactly one level deeper in the precursor tree than the product.
   */
  private boolean edgeInBfsTree(String substrate, String product, Map<String, Integer> levelMap) {
    if (!levelMap.containsKey(substrate) || !levelMap.containsKey(product)) {
      return false;
    }
    return levelMap.get(substrate) == 1 + levelMap.get(product);
  }

  /**
   * Helper method to build a DotEdge given a NetworkEdge, and a particular (substrate, product) pair.
   * The edge is colored according to the orgToColor map, and formatted as a solid line if it has a DB
   * reaction associated, or a dotted line otherwise.
   */
  private DotEdge buildDotEdge(NetworkEdge edge, String substrateId, String productId) {
    DotEdge.EdgeColor color = DotEdge.EdgeColor.BLACK;
    for (String orgOfInterest : orgToColor.keySet()) {
      if (matchesOrg(edge, orgOfInterest)) {
        color = orgToColor.get(orgOfInterest);
      }
    }
    DotEdge.EdgeStyle style = edge.getReactionIds().isEmpty() ?
        DotEdge.EdgeStyle.DOTTED : DotEdge.EdgeStyle.DEFAULT;
    return new DotEdge(substrateId, productId, color, style);
  }

  /**
   * Helper method to test whethe a given edge matches the orgOfInterest string.
   * Returns true if any organism entry in the edge contains orgOfInterest as a substring.
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

      DotGraph graph = buildDotGraph(report);

      graph.writeGraphToStream(new OutputStreamWriter(new FileOutputStream(outputFile)));
    }
  }
}
