package com.act.lcms.regression;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.lcms.db.analysis.Utils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InchiOrSmilesToMassCharge {
  private static final Logger LOGGER = LogManager.getFormatterLogger(InchiOrSmilesToMassCharge.class);
  private static final String OPTION_OUTPUT_MASS_CHARGE_FILE = "o";
  private static final String OPTION_INCLUDE_IONS = "n";
  private static final String OPTION_INPUT_FILE = "i";
  private static final String OPTION_SMILES_REPRESENTATION = "s";
  private static final String OPTION_INCHI_REPRESENTATION = "t";

  public static final String HELP_MESSAGE = "This class is used to convert a list of inchis to mass charges";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_INPUT_FILE)
          .argName("input file")
          .desc("file containing a chemical per line")
          .hasArg()
          .longOpt("input-file")
          .required()
      );
      add(Option.builder(OPTION_SMILES_REPRESENTATION)
          .argName("smiles representation")
          .desc("this option is for the input chemical being in smiles representation")
          .longOpt("smiles-representation")
      );
      add(Option.builder(OPTION_INCHI_REPRESENTATION)
          .argName("inchi representation")
          .desc("this option is for the input chemical being in inchi representation")
          .longOpt("inchi-representation")
      );
      add(Option.builder(OPTION_INCLUDE_IONS)
          .argName("ion list")
          .desc("A comma-separated list of ions to include in the search (ions not in this list will be ignored)")
          .hasArgs().valueSeparator(',')
          .longOpt("include-ions")
      );
      add(Option.builder(OPTION_OUTPUT_MASS_CHARGE_FILE)
          .argName("output mass charge file")
          .desc("output file of mass charges")
          .hasArg()
          .longOpt("output-mass-charge-file")
          .required()
      );
    }
  };

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws Exception {
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
      HELP_FORMATTER.printHelp(InchiOrSmilesToMassCharge.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(InchiOrSmilesToMassCharge.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    String chemicalFormat = cl.hasOption(OPTION_SMILES_REPRESENTATION) ? "smiles" : "inchi";

    BufferedReader reader = new BufferedReader(new FileReader(cl.getOptionValue(OPTION_INPUT_FILE)));
    Set<String> includeIons = new HashSet<>(Arrays.asList(cl.getOptionValues(OPTION_INCLUDE_IONS)));

    try (BufferedWriter predictionWriter =
             new BufferedWriter(new FileWriter(new File(cl.getOptionValue(OPTION_OUTPUT_MASS_CHARGE_FILE))))) {

      predictionWriter.write("MZ");
      predictionWriter.newLine();

      String chemical;
      while ((chemical = reader.readLine()) != null) {
        try {
          Molecule molecule = MolImporter.importMol(chemical, chemicalFormat);
          String inchi = MolExporter.exportToFormat(molecule, "inchi:AuxNone,Woff");
          Double mass = MassCalculator.calculateMass(inchi);

          if (includeIons.size() == 0) {
            // Assume the ion modes are all positive!
            Map<String, Double> allMasses = MS1.getIonMasses(mass, MS1.IonMode.POS);
            Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, includeIons, null);

            for (Map.Entry<String, Double> entry : metlinMasses.entrySet()) {
              predictionWriter.write(entry.getValue().toString());
              predictionWriter.newLine();
            }
          } else {
            predictionWriter.write(mass.toString());
            predictionWriter.newLine();
          }
        } catch (Exception e) {
          LOGGER.error("Caught exception when trying to import %s", chemical);
        }
      }
    }
  }
}
