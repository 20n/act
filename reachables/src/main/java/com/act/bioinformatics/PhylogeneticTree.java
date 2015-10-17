package com.act.bioinformatics;

import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;

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

  public Object readPhylipFile(String phylipFile) throws Exception {
    String treeStr = readLines(phylipFile);
    return null;
  }


  public List<String> identifyRepresentatives(int numRepsDesired, Object phyloTree) {
    List<String> reps = new ArrayList<>();
    return reps;
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

    Object phlyoTree = readPhylipFile(phylipFile);
    List<String> representativeSeqs = identifyRepresentatives(numRepsDesired, phlyoTree);
    
    for (String rep : representativeSeqs) {
      System.out.println("Representative nodes: " + rep);
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
