package com.act.lcms;

import chemaxon.formats.ImportOptions;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.struc.Molecule;
import com.act.utils.CLIUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class MassCalculator2 {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MassCalculator2.class);
  private static final String OPTION_INPUT_FILE = "i";
  private static final String OPTION_OUTPUT_FILE = "o";
  private static final String OPTION_LICENSE_FILE = "l";

  private static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class calculates the monoisotopic mass of one or more molecules.  Feed it InChIs and it will output ",
      "the masses and net charges for those molecules.  This class can accept a file full of InChIs or just a bunch ",
      "of InChIs on the command line."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILE)
        .argName("input-file")
        .desc("An input file containing just InChIs")
        .hasArg()
        .longOpt("input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output-file")
        .desc("An output file to which to write masses and charges (default is stdout)")
        .hasArg()
        .longOpt("output-file")
    );
  }};

  private static final CLIUtil CLI_UTIL = new CLIUtil(MassCalculator2.class, HELP_MESSAGE, OPTION_BUILDERS);

  protected static Pair<Double, Integer> calculateMassAndCharge(String inchi) throws MolFormatException {
    Molecule mol = MolImporter.importMol(inchi);
    LOGGER.info("Exact mass vs. just mass for %s: %.6f %.6f", inchi, mol.getExactMass(), mol.getMass());
    return Pair.of(mol.getExactMass(), mol.getTotalCharge());
  }

  public static void main(String[] args) throws Exception {
    CommandLine cl = CLI_UTIL.parseCommandLine(args);

    if (cl.hasOption(OPTION_LICENSE_FILE)) {
      LOGGER.info("Using license file at %s", cl.getOptionValue(OPTION_LICENSE_FILE));
      LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));
    }

    List<String> inchis = new ArrayList<>();

    if (cl.hasOption(OPTION_INPUT_FILE)) {
      try (BufferedReader reader = new BufferedReader(new FileReader(cl.getOptionValue(OPTION_INPUT_FILE)))) {
        String line;
        while ((line = reader.readLine()) != null) {
          inchis.add(line);
        }
      }
    }

    if (cl.getArgList().size() > 0) {
      LOGGER.info("Reading %d InChIs from the command line", cl.getArgList().size());
      inchis.addAll(cl.getArgList());
    }

    try (PrintWriter writer = new PrintWriter(cl.hasOption(OPTION_OUTPUT_FILE) ?
        new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE)) :
        new OutputStreamWriter(System.out))) {
      writer.format("InChI\tMass\tCharge\n");

      for (String inchi : inchis) {
        try {
          Pair<Double, Integer> massAndCharge = calculateMassAndCharge(inchi);
          writer.format("%s\t%.6f\t%3d\n", inchi, massAndCharge.getLeft(), massAndCharge.getRight());
        } catch (MolFormatException e) {
          LOGGER.error("Unable to compute mass for %s: %s", inchi, e.getMessage());
        }
      }
    }
  }
}
