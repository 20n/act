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

  public String runClustal(String fastaFile) {
    String[] createMultipleSeqAlignment = new String[] { "clustalo",
      "-i", fastaFile,
      "-o", fastaFile + ".align"
    };

    String[] createPhylogeneticTree = new String[] { "clustalw",
      "-infile=" + fastaFile + ".align",
      "-tree",
      "-outputtree=phylip",
      "-clustering=Neighbour-joining"
    };

    String[] rmMSAIntermediateFile = new String[] { "rm",
      fastaFile + ".align"
    };

    exec(createMultipleSeqAlignment);
    exec(createPhylogeneticTree);
    exec(rmMSAIntermediateFile);

    return fastaFile + ".ph";
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
      // mark fill out appropriate instructions for installing clustal{o,w}
      System.err.println("If clustal{o,w}, install using XXXXXXXXXX"); 
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

  private String readLines(String f) throws IOException {
    StringBuffer lines = new StringBuffer();
    BufferedReader br = new BufferedReader(new FileReader(f));
    String line;
    while((line = br.readLine()) != null)
      lines.append(line);
    br.close();
    return lines.toString();
  }

  public Phylogeny readPhylipFile(String phylipFile) throws Exception {
    String treeStr = readLines(phylipFile);
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

  public List<PhylogenyNode> identifyRepresentatives(int numRepsDesired, Phylogeny phyloTree) {
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
      if (num > numRepsDesired) {
        // this depth exceeds out limit; the previous depth good enough
        cutDepth = d - 1; 
        break;
      }
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
      // System.out.format("%d\t%s\t%f\n", depth, node.getName(), d);

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

    List<PhylogenyNode> externalReps = new ArrayList<>();
    // replace internal nodes (the hypothetical ancestors) with their
    // closest external descendant
    for (PhylogenyNode rep : reps) {
      PhylogenyNode representative = rep;

      // if rep is not external, overwrite with closest descendent
      if (!rep.isExternal()) {
        // the rep is an internal node, so we need to find a descendent,
        // and one that is closest to the root, i.e., closest to this
        // internal node
        Double closestDescendantDist = null; 
        PhylogenyNode closestDescendant = null;
        for (PhylogenyNode d : rep.getAllExternalDescendants()) {
          // only consider external reps
          if (!d.isExternal())
            continue;

          Double dist = distanceToRoot(d);
          if (closestDescendant == null || dist < closestDescendantDist) {
            closestDescendantDist = dist;
            closestDescendant = d;
          }
        }
        representative = closestDescendant;
      }
      
      // add this rep to the list of reps at this depth
      externalReps.add(representative);
    }

    return externalReps;
  }

  private boolean fastaFile(String f) {
    return f.endsWith(".fa") || f.endsWith(".fasta");
  }

  private boolean phylipFile(String f) {
    return f.endsWith(".ph");
  }

  private void process(String inFile, Integer numRepsDesired) throws Exception {
    String phylipFile = null;
    if (fastaFile(inFile)) {
      phylipFile = runClustal(inFile);
    } else if (phylipFile(inFile)) {
      phylipFile = inFile;
    }

    Phylogeny phlyoTree = readPhylipFile(phylipFile);
    List<PhylogenyNode> representativeSeqs = identifyRepresentatives(numRepsDesired, phlyoTree);
    
    for (PhylogenyNode rep : representativeSeqs) {
      System.out.println("Representative nodes: " + rep.getName());
    }
  }

  public static void main(String[] args) throws Exception {
    PhylogeneticTree phyl = new PhylogeneticTree();

    if (args.length < 2 || (!phyl.fastaFile(args[0]) && !phyl.phylipFile(args[0]))) {
      throw new RuntimeException("Needs:\n" +
          "(1) FASTA file (.fa or .fasta) or Phylip phylogenetic tree file\n" +
          "(2) Num sequences desired as representatives"
          );
    }

    Integer numRepsDesired = Integer.parseInt(args[1]);
    String inFile = args[0];
    phyl.process(inFile, numRepsDesired);
  }

}
