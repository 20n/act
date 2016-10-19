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
public class PrecursorVizBuilder implements JavaRunnable {

  // An organism of interest. Any edge with any organism name which contains this string will be colored red.
  private String orgOfInterest = "$$$$";

  private final File inputFile;
  private final File outputFile;

  public PrecursorVizBuilder(File inputFile, File outputFile) {
    this.inputFile = inputFile;
    this.outputFile = outputFile;
  }

  public void setOrgOfInterest(String org) {
    orgOfInterest = org;
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

    PrecursorReport report = new PrecursorReport();
    report.loadFromJsonFile(inputFile);
    DotGraph graph = buildDotGraph(report);

    graph.writeGraphToStream(new OutputStreamWriter(new FileOutputStream(outputFile)));
  }

  /**
   * Builds DOT graph representation of the precursor report.  The graph is printed out with the target metabolite
   * on the bottom, all its direct precursors one level up, all their precursors two levels up, etc.  Only edges
   * between adjacent levels are drawn, resulting in a BFS tree representation of the precursors.
   *
   * @param report The PrecursorReport.
   * @return The DotGraph.
   */
  private DotGraph buildDotGraph(PrecursorReport report) {
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
    MutableInt level = new MutableInt(0);

    // Move the frontier backword successively. At each level, the next level is found by finding all precursors to the
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
          if (idToLevel.containsKey(substrate) && idToLevel.containsKey(product)) {
            if (idToLevel.get(substrate) == 1 + idToLevel.get(product)) {
              DotEdge.EdgeColor color = matchesOrg(edge) ? DotEdge.EdgeColor.RED : DotEdge.EdgeColor.BLACK;
              DotEdge.EdgeStyle style = edge.getReactionIds().isEmpty() ?
                  DotEdge.EdgeStyle.DOTTED : DotEdge.EdgeStyle.DEFAULT;
              graph.addEdge(new DotEdge(inchiToIdMap.get(substrate), inchiToIdMap.get(product), color, style));
            }
          }
        }
      }
    }
    return graph;
  }

  /**
   * Helper method to test whethe a given edge matches the orgOfInterest string.
   * Returns true if any organism entry in the edge contains orgOfInterest as a substring.
   *
   * @param edge The edge to test.
   * @return True if the edge matches the orgOfInterest.
   */
  private boolean matchesOrg(NetworkEdge edge) {
    return edge.getOrgs().stream().filter(s -> s.contains(orgOfInterest)).count() > 0;
  }
}
