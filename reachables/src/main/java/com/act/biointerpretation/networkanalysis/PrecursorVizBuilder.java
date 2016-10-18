package com.act.biointerpretation.networkanalysis;

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

public class PrecursorVizBuilder implements JavaRunnable {

  private final File inputFile;
  private final File outputFile;

  public PrecursorVizBuilder(File inputFile, File outputFile) {
    this.inputFile = inputFile;
    this.outputFile = outputFile;
  }


  @Override
  public void run() throws IOException {
    FileChecker.verifyInputFile(inputFile);
    FileChecker.verifyAndCreateOutputFile(outputFile);

    PrecursorReport report = new PrecursorReport();
    report.loadFromJsonFile(inputFile);
    GraphVizGraph graph = buildPrecursorGraph(report);

    graph.writeDotToStream(new OutputStreamWriter(new FileOutputStream(outputFile)));
  }

  private GraphVizGraph buildPrecursorGraph(PrecursorReport report) {
    MetabolismNetwork network = report.getNetwork();
    String target = report.getTarget().getInchi();

    Map<String, String> inchiToIdMap = new HashMap<>();
    Integer id = 0;
    for (NetworkNode node : network.getNodes()) {
      inchiToIdMap.put(node.getMetabolite().getInchi(), id.toString());
      id++;
    }

    GraphVizGraph graph = new GraphVizGraph();
    List<NetworkNode> currentLevel = Arrays.asList(network.getNode(target));
    Map<String, Integer> levelMap = new HashMap<>();
    MutableInt level = new MutableInt(0);
    while (!currentLevel.isEmpty()) {
      currentLevel.forEach(n -> System.out.print(n.getMetabolite().getInchi() + " "));
      System.out.println();
      currentLevel.forEach(n -> levelMap.put(n.getMetabolite().getInchi(), level.intValue()));
      currentLevel = currentLevel.stream().
          flatMap(n -> network.getPrecursors(n).stream()).collect(Collectors.toList());
      currentLevel.removeIf(n -> levelMap.containsKey(n.getMetabolite().getInchi()));
      level.increment();
    }

    for (NetworkEdge edge : network.getEdges()) {
      for (String substrate : edge.getSubstrates()) {
        for (String product : edge.getProducts()) {
          if (levelMap.containsKey(substrate) && levelMap.containsKey(product)) {
            if (levelMap.get(substrate) == 1 + levelMap.get(product)) {
              graph.addEdge(inchiToIdMap.get(substrate), inchiToIdMap.get(product));
            }
          }
        }
      }
    }

    return graph;
  }
}
