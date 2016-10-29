package com.act.biointerpretation.retentiontime;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.MajorMicrospeciesPlugin;
import chemaxon.marvin.calculations.logDPlugin;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.struc.Molecule;
import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.lcms.db.analysis.Utils;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeatureGenerator {
  private static final Logger LOGGER = LogManager.getFormatterLogger(FeatureGenerator.class);
  private static final String OPTION_INPUT_FILE = "i";
  private static final String OPTION_SMILES_REPRESENTATION = "s";
  private static final String OPTION_INCHI_REPRESENTATION = "t";
  private static final String OPTION_OUTPUT_FILE = "o";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "FILL_OUT ",
      "FILL_OUT"}, "");

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
      add(Option.builder(OPTION_OUTPUT_FILE)
          .argName("output file")
          .desc("this option is for the input chemical being in inchi representation")
          .longOpt("output-file")
          .hasArg()
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
      HELP_FORMATTER.printHelp(FeatureGenerator.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(FeatureGenerator.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    logPPlugin plugin = new logPPlugin();
    MajorMicrospeciesPlugin microspeciesPlugin = new MajorMicrospeciesPlugin();

    String chemicalFormat = cl.hasOption(OPTION_SMILES_REPRESENTATION) ? "smiles" : "inchi";

    BufferedReader reader = new BufferedReader(new FileReader(cl.getOptionValue(OPTION_INPUT_FILE)));

    Set<String> OUTPUT_TSV_HEADER_FIELDS = new HashSet<>();
    OUTPUT_TSV_HEADER_FIELDS.add("Chemical");
    OUTPUT_TSV_HEADER_FIELDS.add("Mass");
    OUTPUT_TSV_HEADER_FIELDS.add("LogP");

    List<String> header = new ArrayList<>();
    header.addAll(OUTPUT_TSV_HEADER_FIELDS);

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));

    String chemical;
    while ((chemical = reader.readLine()) != null) {
      Molecule molecule = MolImporter.importMol(chemical, chemicalFormat);

      Cleaner.clean(molecule, 3);
      plugin.standardize(molecule);
      microspeciesPlugin.setpH(2.7);
      microspeciesPlugin.setMolecule(molecule);
      microspeciesPlugin.run();

      Molecule phMol = microspeciesPlugin.getMajorMicrospecies();
      plugin.setlogPMethod(LogPMethod.CONSENSUS);
      plugin.setUserTypes("logPTrue,logPMicro,logPNonionic");
      plugin.setMolecule(phMol);
      plugin.run();

      Double mass = molecule.getMass();
      Double logP = plugin.getlogPTrue();

      Map<String, String> row = new HashMap<>();
      row.put("Chemical", MolExporter.exportToFormat(molecule, "smiles"));
      row.put("Mass", mass.toString());
      row.put("LogP", logP.toString());

      writer.append(row);
      writer.flush();
    }

    writer.close();
  }
}
