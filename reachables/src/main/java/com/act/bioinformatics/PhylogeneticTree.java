package com.act.bioinformatics;

import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.apache.commons.lang3.tuple.Pair;
import org.forester.phylogeny.Phylogeny;
import org.forester.phylogeny.PhylogenyNode;
import org.forester.io.parsers.PhylogenyParser;
import org.forester.io.parsers.nhx.NHXParser;
import org.forester.phylogeny.iterators.LevelOrderTreeIterator;

public class PhylogeneticTree {

  public Pair<String, String> runClustal(String fastaFile) {
    String distF = fastaFile + ".dist";
    String phylF = fastaFile + ".ph";
    String algnF = fastaFile + ".align";
    String clstF = fastaFile + ".cluster";

    String[] createMultipleSeqAlignment = new String[] { "clustalo",
      "-i", fastaFile,
      "--clustering-out=" + clstF,
      "--distmat-out=" + distF, // all pairs pc similarity
      "--percent-id", // output percentages instead of a metric
      "--full", // get all pairs comparison
      "-o", algnF
    };

    String[] createPhylogeneticTree = new String[] { "clustalw",
      "-infile=" + algnF,
      "-tree",
      "-outputtree=phylip",
      "-clustering=Neighbour-joining"
    };

    String[] rmMSAIntermediateFile = new String[] { "rm", algnF };

    exec(createMultipleSeqAlignment);
    exec(createPhylogeneticTree);
    exec(rmMSAIntermediateFile);

    return Pair.of(phylF, distF);
  }

  private void exec(String[] cmd) {

    Process proc = null;
    try {
      proc = Runtime.getRuntime().exec(cmd);

      // read its input stream in case the process reports something
      Scanner procSays = new Scanner(proc.getInputStream());
      while (procSays.hasNextLine()) {
        System.out.println(procSays.nextLine());
      }
      procSays.close();

      // read the error stream in case the plotting failed
      procSays = new Scanner(proc.getErrorStream());
      while (procSays.hasNextLine()) {
        System.err.println("E: " + procSays.nextLine());
      }
      procSays.close();

      // wait for process to finish
      proc.waitFor();

    } catch (IOException e) {
      System.err.println("ERROR: Cannot locate executable for " + cmd[0]);
      System.err.println("ERROR: Rerun after installing: ");
      System.err.println("\tFor clustalo, install from http://www.clustal.org/omega/");
      System.err.println("\tFor clustalw, install from http://www.clustal.org/clustal2/");
      System.err.println("ERROR: ABORT!\n");
      throw new RuntimeException("Required " + cmd[0] + " not in path");
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      if (proc != null) {
        proc.destroy();
      }
    }
  }

  private List<String> readLines(String f, Integer maxLns) throws IOException {
    List<String> lines = new ArrayList<>();
    BufferedReader br = new BufferedReader(new FileReader(f));
    String line;
    int readLns = 0;
    // readline && (maxLns != null => readLns++ < maxLns)
    while((line = br.readLine()) != null && (maxLns == null || readLns++ < maxLns))
      lines.add(line);
    br.close();
    return lines;
  }

  public Map<Pair<String, String>, Double> readPcSimilarityFile(String distFile) throws IOException {
    // format:
    // 1st line: num entries
    // 2nd onwards: Name and multispace separated percentages
    List<String> distData = readLines(distFile, null);
    
    Map<String, List<Double>> matrixRows = new HashMap<>();
    List<String> names = new ArrayList<>();
    for (int i = 1; i < distData.size(); i++) {
      String[] row = distData.get(i).split("\\s+");
      names.add(row[0]);
      List<Double> pcs = new ArrayList<>();
      for (int j = 1; j < row.length; j++) {
        pcs.add(Double.parseDouble(row[j]));
      }
      matrixRows.put(row[0], pcs);
    }

    // now remap the matrix hashmap from String -> List<Double> to (String, String) -> Double
    Map<Pair<String, String>, Double> pairwise = new HashMap<>();
    for (Map.Entry<String, List<Double>> mapEntry : matrixRows.entrySet()) {
      String repA = mapEntry.getKey();
      List<Double> row = mapEntry.getValue();
      for (int col = 0; col < row.size(); col++) {
        String repB = names.get(col);
        pairwise.put(Pair.of(repA, repB), row.get(col));
      }
    }
    return pairwise;
  }

  private List<String> extractOriginalSeedNames(String fastaFile, int numSeeds) throws IOException {
    // seeds are supposed to be the first few lines of the file
    // extract `numSeeds * 2` lines and look for the first `>NAME `
    // numSeeds * 2 because there is a comment line and sequence line
    // in the fasta file
    List<String> seedNames = new ArrayList<>();
    List<String> lines = readLines(fastaFile, numSeeds * 2);
    for (int i = 0; i < numSeeds * 2; i += 2) {
      String[] indent_name = lines.get(i).split("\t");
      if (indent_name[0].charAt(0) != '>')
        throw new RuntimeException("Expecting seq description in fasta. Found: " + indent_name);
      seedNames.add(indent_name[0].substring(1));
    }
    return seedNames;
  }

  public Phylogeny readPhylipFile(String phylipFile) throws Exception {
    // phylip files are in https://en.wikipedia.org/wiki/Newick_format
    // forester is a library that provides capabilities for reading phylogeny formats
    // we get access to it through maven: 
    //    "org.biojava.thirdparty"  % "forester" % "1.005" in build.sbt
    // Reference URLs:
    //    https://sites.google.com/site/cmzmasek/home/software/forester
    //    https://github.com/cmzmasek/forester
    // Looking through the github repo, it looks like some of it is LGPL
    // TODO: check license.


    PhylogenyParser parser = new NHXParser();
    parser.setSource(new File(phylipFile));
    Phylogeny[] phylos = parser.parse();
    if (phylos.length != 1) {
      throw new RuntimeException(".ph file expected to have exactly one phylogenetic tree. Found: " + phylos.length);
    }
    return phylos[0];
  }

  private final double distanceToRoot(PhylogenyNode n) {
    double d = 0.0;
    while ( n.getParent() != null ) {
      if ( n.getDistanceToParent() > 0.0 ) {
          d += n.getDistanceToParent();
      }
      n = n.getParent();
    }
    return d;
  }

  private final int stepsToRoot(PhylogenyNode n) {
    int s = 0;
    while ( n.getParent() != null ) {
      s++;
      n = n.getParent();
    }
    return s;
  }

  class NodeInTree {
    PhylogenyNode node;
    Double distToRoot;
    Integer depth;
    NodeInTree(PhylogenyNode n, Double d2r, int depth) {
      this.node = n;
      this.distToRoot = d2r;
      this.depth = depth;
    }
  }

  public Pair<List<PhylogenyNode>, Map<PhylogenyNode, List<PhylogenyNode>>> identifyRepresentatives(int numRepsDesired, Phylogeny phyloTree) {
    LevelOrderTreeIterator nodesIt = new LevelOrderTreeIterator(phyloTree);

    List<NodeInTree> nodes = new ArrayList<>();
    int maxDepth = 0;
    while (nodesIt.hasNext()) {
      PhylogenyNode treeNode = nodesIt.next();
      int steps2Root = stepsToRoot(treeNode);
      double dist2Root = distanceToRoot(treeNode);
      nodes.add(new NodeInTree(treeNode, dist2Root, steps2Root));
      
      if (maxDepth < steps2Root)
        maxDepth = steps2Root;
    }
    Collections.sort(nodes, new Comparator<NodeInTree>() {
      public int compare(NodeInTree a, NodeInTree b) {
        int sortByStepsFromRoot = a.depth.compareTo(b.depth);
        int sortByDistance = a.distToRoot.compareTo(b.distToRoot);
        // first sort by steps from root, and if that is equal then by distance
        return sortByStepsFromRoot != 0 ? sortByStepsFromRoot : sortByDistance;
      }
    });

    // find out how many nodes would be included if the depth cutoff is 
    // set to X. To evaluate that, lets compute for each X what the num
    // of nodes is: num *at depth* X + num external with depth < X
    Map<Integer, Integer> depthToNumNodes = new HashMap<>();
    // init to 0
    for (int d = 0; d <= maxDepth; d++) 
      depthToNumNodes.put(d, 0);
    for (NodeInTree n : nodes) {
      if (n.node.isExternal()) {
        // if node is external then it gets added to everything that
        // depth at or below its depth
        for (int d = n.depth; d <= maxDepth; d++) {
          depthToNumNodes.put(d, depthToNumNodes.get(d) + 1);
        }
      } else {
        // if node is internal, it only adds to the depth count
        // at that level
        depthToNumNodes.put(n.depth, depthToNumNodes.get(n.depth) + 1);
      }
    }

    // now find the depth at which "included nodes" close to `numRepsDesired`
    int cutDepth = 0;
    for (int d = 0; d <= maxDepth; d++) {
      int num = depthToNumNodes.get(d);
      System.out.format("Depth: %d Num in cut: %d\n", d, num);
      if (num <= numRepsDesired) { cutDepth = d; }
      if (num > numRepsDesired) { break; }
    }
    System.out.format("Depth picked for cut: %d\n", cutDepth);

    // narrow down to a slice in the tree at a depth which gives us
    // the right number of representatives; at this stage the nodes
    // identified might be external (those from the FASTA file), or
    // may be internal (hypothetical ancestors, that are inferred from
    // the clustering). Later we will replace the internal nodes with
    // their closest external descendant
    List<PhylogenyNode> reps = new ArrayList<>();
    for (NodeInTree n : nodes) {
      PhylogenyNode node = n.node;
      Double d = n.distToRoot;
      int depth = n.depth;

      if (depth < cutDepth) {
        // strictly less than cutDepth, so only add those that are
        // external. If internal there will be a descendant that 
        // superceeds this node later
        if (node.isExternal())
          reps.add(node);
      } else if (depth == cutDepth) {
        // add this node to the reps list, no matter if its external
        // or internal. The external ones obviously here
        reps.add(node);
      }
    }

    // replace internal nodes (the hypothetical ancestors) with their
    // closest external descendant
    List<PhylogenyNode> externalReps = new ArrayList<>();
    // alongside, build the map of (representative -> subtree represented)
    Map<PhylogenyNode, List<PhylogenyNode>> represented  = new HashMap<>();

    for (PhylogenyNode rep : reps) {
      PhylogenyNode representative = rep;

      // if rep is not external, overwrite with closest descendent
      if (!rep.isExternal()) {
        // overwrite internal node with the `optimal` external descendent
        representative = pickOptimalExternalDescendent(rep);
      }

      // log who this `representative` stands for (rep.descendents)
      represented.put(representative, rep.getAllExternalDescendants());
      
      // add this rep to the list of reps at this depth
      externalReps.add(representative);
    }

    return Pair.of(externalReps, represented);
  }

  private PhylogenyNode pickOptimalExternalDescendent(PhylogenyNode internal) {
    // the rep is an internal node, so we need to find a descendent,
    // that is a good indicator of the set of nodes under it

    List<NodeInTree> subtree = new ArrayList<>();
    
    for (PhylogenyNode d : internal.getAllExternalDescendants()) {
      // only consider external reps
      if (!d.isExternal())
        continue;

      Double dist = distanceToRoot(d);
      Integer steps = stepsToRoot(d);
      subtree.add(new NodeInTree(d, dist, steps));
    }

    Collections.sort(subtree, new Comparator<NodeInTree>() {
      public int compare(NodeInTree a, NodeInTree b) {
        int sortByDistance = a.distToRoot.compareTo(b.distToRoot);
        return sortByDistance;
      }
    });
    
    return subtree.get(0).node;                  // closest
    // return subtree.get(subtree.size() - 1).node; // farthest
    // return subtree.get(subtree.size() / 2).node; // median
  }

  Double aggregate(List<Double> ds) {
    Double aggr = 0.0;
    for (Double d : ds)
      aggr += d;
    return aggr / ds.size();
  }

  private void ensureInvariantsOnReps(List<PhylogenyNode> reps, Map<PhylogenyNode, List<PhylogenyNode>> represented, Map<Pair<String, String>, Double> pairwise, List<String> originalSeeds) {

    for (PhylogenyNode rep : reps) {
      String repName = rep.getName();

      // invariant 1: representative, truly "represent" their cluster,
      //              i.e., aggregate similarity between rep and every other
      //              node in cluster is high
      List<Double> simi = new ArrayList<>();
      for (PhylogenyNode represent : represented.get(rep)) {
        String underName = represent.getName();
        Double s = pairwise.get(Pair.of(repName, underName));
        simi.add(s);
      }
      Double inv1 = aggregate(simi);

      // invariant 2: representatives represent clusters that are diverse
      //              i.e., aggregate similarity between any two reps is low
      simi = new ArrayList<>();
      for (PhylogenyNode otherRep : reps) {
        if (otherRep.equals(rep))
          continue;
        Double s = pairwise.get(Pair.of(repName, otherRep.getName()));
        simi.add(s);
      }
      Double inv2 = aggregate(simi);

      // invariant 3: representatives should be well spread in distance from the seeds
      List<Double> distFromSeeds = new ArrayList<>();
      for (String seed : originalSeeds) {
        Double distFromS = pairwise.get(Pair.of(repName, seed));
        distFromSeeds.add(distFromS);
      }
      Double inv3 = aggregate(distFromSeeds);

      System.out.format("%s: represents %d nodes. Invariants: (%5.2f, %5.2f, %5.2f)\n", repName, represented.get(rep).size(), inv1, inv2, inv3);
    }
  }

  private boolean fastaFile(String f) {
    return f.endsWith(".fa") || f.endsWith(".fasta");
  }

  private boolean phylipFile(String f) {
    return f.endsWith(".ph");
  }

  private void process(String fasta, int numRepsDesired, int numOrigSeeds) throws Exception {
    // run clustalo and clustalw to create the distance matrix in .dist
    // and phylogenetic clustering tree in .ph
    Pair<String, String> files = runClustal(fasta);
    String phylipFile = files.getLeft();
    String distMatrixFile = files.getRight();

    Phylogeny phlyoTree = readPhylipFile(phylipFile);
    Pair<List<PhylogenyNode>, Map<PhylogenyNode, List<PhylogenyNode>>> reps = identifyRepresentatives(numRepsDesired, phlyoTree);
    List<PhylogenyNode> repSeqs = reps.getLeft();
    Map<PhylogenyNode, List<PhylogenyNode>> subtrees = reps.getRight();
    
    Map<Pair<String, String>, Double> pairwiseDist = readPcSimilarityFile(distMatrixFile);
    List<String> originalSeeds = extractOriginalSeedNames(fasta, numOrigSeeds);
    // do sanity check to ensure reps are galaxy centers, and galaxies
    // are sufficiently distinct and far away from each other
    ensureInvariantsOnReps(repSeqs, subtrees, pairwiseDist, originalSeeds);

  }

  public static void main(String[] args) throws Exception {
    PhylogeneticTree phyl = new PhylogeneticTree();

    if (args.length < 3 || !phyl.fastaFile(args[0])) {
      throw new RuntimeException("Needs:\n" +
          "(1) FASTA file (.fa or .fasta)\n" +
          "(2) Num sequences desired as representatives\n" +
          "(3) Num original seeds from which FASTA file constructed\n"
          );
    }

    String inFile = args[0];
    Integer numRepsDesired = Integer.parseInt(args[1]);
    Integer numOriginalSeeds = Integer.parseInt(args[2]);
    phyl.process(inFile, numRepsDesired, numOriginalSeeds);
  }

}
