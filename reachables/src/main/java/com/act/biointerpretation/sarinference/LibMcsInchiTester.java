package com.act.biointerpretation.sarinference;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import com.act.biointerpretation.l2expansion.L2FilteringDriver;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class was designed to test how LibMCS handles inchis. Specifically, the process of using LibMCS looks like:
 * Inchi (A) -> molecule (B) -> LibMCS node (C)
 * I wanted to see the difference between the initial inchis, A, and the corresponding inchis when exported after step
 * B, or after step C.  Step A->B indicates what happens when we simply import and export a molecule from chemaxon, while
 * B->C indicates changes that LibMCS itself makes to the inchis.
 *
 * The conclusion was that LibMCS strips away both the charge and stereochemical layers from molecules, and thus that
 * we should not rely on comparing inchis from before LibMCS analysis to after.
 *
 * This investigation also indicated that LibMCS does not change the masses of the molecules, and that chemaxon
 * Molecule.getExactMass() is almost exactly the same as our MassCalculator.calculatorMass(), and should probably
 * replace it.
 */
public class LibMcsInchiTester {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsInchiTester.class);

  private static final String OPTION_SUBSTRATE_INCHIS = "s";
  private static final String OPTION_PRE_CLUSTER_OUTPUT = "B";
  private static final String OPTION_POST_CLUSTER_OUTPUT = "A";
  private static final String OPTION_WRITE_MASSES = "M";
  private static final String OPTION_HELP = "h";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_SUBSTRATE_INCHIS)
          .argName("substrate inchis file")
          .desc("The path to a file of inchis to cluster.")
          .hasArg()
          .longOpt("input-inchis")
          .required()
      );
      add(Option.builder(OPTION_PRE_CLUSTER_OUTPUT)
          .argName("before output path")
          .desc("The path to which to write the inchis before clustering.")
          .hasArg()
          .longOpt("before-output-path")
          .required()
      );
      add(Option.builder(OPTION_POST_CLUSTER_OUTPUT)
          .argName("after otput path")
          .desc("The path to which to write the inchis after clustering.")
          .hasArg()
          .longOpt("after-output-path")
          .required()
      );
      add(Option.builder(OPTION_WRITE_MASSES)
          .argName("write masses")
          .desc("Write masses, sorted in descending order, instead of inchis.")
          .longOpt("write-masses")
          .required()
      );
      add(Option.builder(OPTION_HELP)
          .argName("help")
          .desc("Prints this help message.")
          .longOpt("help")
      );
    }
  };

  public static final String HELP_MESSAGE =
      "This class is used to cluster a list of given substrates.";

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws Exception {

    // Build command line parser.
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }
    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      LOGGER.error("Argument parsing failed: %s", e.getMessage());
      exitWithHelp(opts);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File substratesFile = new File(cl.getOptionValue(OPTION_SUBSTRATE_INCHIS));
    File preClusterOutput = new File(cl.getOptionValue(OPTION_PRE_CLUSTER_OUTPUT));
    File postClusterOutput = new File(cl.getOptionValue(OPTION_POST_CLUSTER_OUTPUT));

    L2InchiCorpus substrates = new L2InchiCorpus();
    substrates.loadCorpus(substratesFile);
    substrates.filterByMass(950);

    LOGGER.info("Building SAR tree with LibraryMCS.");
    LibraryMCS libMcs = new LibraryMCS();
    SarTree sarTree = new SarTree();

    sarTree.buildByClustering(libMcs, substrates.getMolecules());

    LOGGER.info("Getting leaf sars.");
    Collection<SarTreeNode> sars = sarTree.getNodes();
    sars.removeIf(sar -> sarTree.getChildren(sar).size() != 0);
    List<String> outputInchis = sars.stream().map(sar -> {
      try {
        return sar.getSubstructureInchi();
      } catch (IOException e) {
        throw new RuntimeException(new MolFormatException("Couldn't export inchi."));
      }
    }).collect(Collectors.toList());

    L2InchiCorpus outputCorpus = new L2InchiCorpus(outputInchis);

    if (cl.hasOption(OPTION_WRITE_MASSES)) {
      substrates.writeMasses(preClusterOutput);
      outputCorpus.writeMasses(postClusterOutput);
    } else {
      substrates.writeToFile(preClusterOutput);
      outputCorpus.writeToFile(postClusterOutput);
    }

    LOGGER.info("Complete!.");
  }

  private static void exitWithHelp(Options opts) {
    HELP_FORMATTER.printHelp(L2FilteringDriver.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
    System.exit(1);
  }
}
