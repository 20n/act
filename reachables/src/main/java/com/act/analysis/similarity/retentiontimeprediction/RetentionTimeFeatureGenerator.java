package com.act.analysis.similarity.retentiontimeprediction;

import chemaxon.formats.MolImporter;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.struc.Molecule;
import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.lcms.db.analysis.Utils;
import com.act.utils.TSVParser;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RetentionTimeFeatureGenerator {
  private static final Logger LOGGER = LogManager.getFormatterLogger(RetentionTimeFeatureGenerator.class);
  private static final String OPTION_INPUT_INCHI_FILE = "i";
  private static final String OPTION_OUTPUT_FEATURE_FILE = "o";
  private static final String RETENTION_TIME_HEADER = "RetentionTime";
  private static final String MASS_HEADER = "Mass";
  private static final String LOGP_HEADER = "LogP";
  private static final String INCHI_HEADER = "Inchi";

  public static final List<String> OUTPUT_TSV_HEADER_FIELDS = new ArrayList<String>() {{
    add(RETENTION_TIME_HEADER);
    add(MASS_HEADER);
    add(LOGP_HEADER);
    add(INCHI_HEADER);
  }};

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This module is used to generate the two features needed for retention time prediction: Mass of molecule and LogP.",
      "This file generates these values for each input molecule and writes them to a TSV file."},
      "Issue 467 gives a thorough analysis on why these two features are being used.");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
      add(Option.builder(OPTION_INPUT_INCHI_FILE)
          .argName("input inchi file")
          .desc("input list of inchis")
          .hasArg()
          .longOpt("input inchi file")
          .required()
      );
    add(Option.builder(OPTION_OUTPUT_FEATURE_FILE)
        .argName("output feature file")
        .desc("output feature file")
        .hasArg()
        .longOpt("output feature file")
        .required()
    );
  }};

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
      HELP_FORMATTER.printHelp(RetentionTimeFeatureGenerator.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(RetentionTimeFeatureGenerator.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    List<String> header = new ArrayList<>();
    header.addAll(OUTPUT_TSV_HEADER_FIELDS);

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(cl.getOptionValue(OPTION_OUTPUT_FEATURE_FILE)));

    TSVParser parser = new TSVParser();
    parser.parse(new File(cl.getOptionValue(OPTION_INPUT_INCHI_FILE)));

    logPPlugin plugin = new logPPlugin();

    for (Map<String, String> row : parser.getResults()) {
      String inchi = row.get("Inchi");
      Double mass = MassCalculator.calculateMass(inchi);
      plugin.setMolecule(MolImporter.importMol(inchi, "inchi"));
      plugin.run();

      Map<String, String> outputRow = new HashMap<>();
      outputRow.put(INCHI_HEADER, inchi);
      outputRow.put(LOGP_HEADER, String.format("%f", plugin.getlogPTrue()));
      outputRow.put(MASS_HEADER, mass.toString());

      writer.append(outputRow);
      writer.flush();
    }

    writer.close();
  }
}
