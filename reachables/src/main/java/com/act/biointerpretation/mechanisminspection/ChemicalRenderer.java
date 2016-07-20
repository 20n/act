package com.act.biointerpretation.mechanisminspection;

import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ChemicalRenderer {

  public static final String OPTION_INCHI_LIST = "l";
  public static final String OPTION_OUTPUT_DIRECTORY = "d";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_INCHI_LIST)
          .argName("read db name")
          .desc("The name of the read DB to use")
          .hasArg().required()
          .longOpt("db")
      );
      add(Option.builder(OPTION_OUTPUT_DIRECTORY)
          .argName("id")
          .desc("The id of the reaction to validate")
          .hasArg().required()
          .longOpt("id")
      );
    }
  };
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class renders representations of a reaction."
  }, "");

  public static void main(String[] args) throws IOException {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(ChemicalRenderer.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ChemicalRenderer.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String dirPath = cl.getOptionValue(OPTION_OUTPUT_DIRECTORY);

    ReactionRenderer renderer = new ReactionRenderer();
    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(cl.getOptionValue(OPTION_INCHI_LIST))));
    String line;
    Integer counter = 0;
    while ((line = in.readLine()) != null) {
      line = line.replace("\n", "");
      Molecule mol = MolImporter.importMol(line);
      String fullPath = StringUtils.join(new String[]{dirPath, counter.toString(), ".", "png"});
      renderer.drawMolecule(mol, new File(fullPath));
      counter++;
    }
  }
}