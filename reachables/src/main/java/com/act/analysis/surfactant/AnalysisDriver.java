package com.act.analysis.surfactant;

import chemaxon.license.LicenseManager;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisDriver {
  public static final String OPTION_LICENSE_FILE = "l";
  public static final String OPTION_INCHI = "n";
  public static final String OPTION_INPUT_FILE = "i";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_DISPLAY = "d";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This is a driver for the SurfactantAnalysis class.  Given a list of input molecules or a single InChI, ",
      "it will apply the SurfactantAnalysis's structural metrics to the molecule(s) and write them to an output TSV ",
      "if a file is specified.  Visualization can also be enabled if a single InChI is provided."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_LICENSE_FILE)
            .argName("path")
            .desc("The Chemaxon license file to load")
            .hasArg().required()
            .longOpt("license")
    );
    add(Option.builder(OPTION_INCHI)
            .argName("inchi")
            .desc("A single inchi to analyze")
            .hasArg()
            .longOpt("inchi")
    );
    add(Option.builder(OPTION_INPUT_FILE)
            .argName("input file")
            .desc("An input TSV of chemicals to analyze")
            .hasArg()
            .longOpt("input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
            .argName("output file")
            .desc("An output TSV in which to write features")
            .hasArg()
            .longOpt("output-file")
    );
    add(Option.builder(OPTION_DISPLAY)
            .desc(String.format("Display the specified molecule (only works with -%s)", OPTION_INCHI))
            .longOpt("display")
    );
  }};

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
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    Set<String> seenOutputIds = new HashSet<>();


    TSVWriter<String, String> tsvWriter = null;
    if (cl.hasOption(OPTION_OUTPUT_FILE)) {
      File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_FILE));
      List<Map<String, String>> oldResults = null;
      if (outputFile.exists()) {
        System.err.format("Output file already exists, reading old results and skipping processed molecules.\n");
        TSVParser outputParser = new TSVParser();
        outputParser.parse(outputFile);
        oldResults = outputParser.getResults();
        for (Map<String, String> row : oldResults) {
          // TODO: verify that the last row was written cleanly/completely.
          seenOutputIds.add(row.get("id"));
        }
      }

      List<String> header = new ArrayList<>();
      header.add("name");
      header.add("id");
      header.add("inchi");
      header.add("label");
      for (SurfactantAnalysis.FEATURES f : SurfactantAnalysis.FEATURES.values()) {
        header.add(f.toString());
      }
      // TODO: make this API more auto-closable friendly.
      tsvWriter = new TSVWriter<>(header);
      tsvWriter.open(outputFile);
      if (oldResults != null) {
        System.out.format("Re-writing %d existing result rows\n", oldResults.size());
        tsvWriter.append(oldResults);
      }
    }

    try {
      Map<SurfactantAnalysis.FEATURES, Double> analysisFeatures;

      LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));
      if (cl.hasOption(OPTION_INCHI)) {
        analysisFeatures =
            SurfactantAnalysis.performAnalysis(cl.getOptionValue(OPTION_INCHI), cl.hasOption(OPTION_DISPLAY));
        Map<String, String> tsvFeatures = new HashMap<>();
        // Convert features to strings to avoid some weird formatting issues.  It's ugly, but it works.
        for (Map.Entry<SurfactantAnalysis.FEATURES, Double> entry : analysisFeatures.entrySet()) {
          tsvFeatures.put(entry.getKey().toString(), String.format("%.6f", entry.getValue()));
        }
        tsvFeatures.put("name", "direct-inchi-input");
        if (tsvWriter != null) {
          tsvWriter.append(tsvFeatures);
        }
      } else if (cl.hasOption(OPTION_INPUT_FILE)) {
        TSVParser parser = new TSVParser();
        parser.parse(new File(cl.getOptionValue(OPTION_INPUT_FILE)));
        int i = 0;
        List<Map<String, String>> inputRows = parser.getResults();

        for (Map<String, String> row : inputRows) {
          i++; // Just for warning messages.
          if (!row.containsKey("name") || !row.containsKey("id") || !row.containsKey("inchi")) {
            System.err.format("WARNING: TSV rows must contain at least name, id, and inchi, skipping row %d\n", i);
            continue;
          }
          if (seenOutputIds.contains(row.get("id"))) {
            System.out.format("Skipping input row with id already in output: %s\n", row.get("id"));
            continue;
          }

          System.out.format("Analysis for chemical %s\n", row.get("name"));
          try {
            analysisFeatures = SurfactantAnalysis.performAnalysis(row.get("inchi"), false);
          } catch (Exception e) {
            // Ignore exceptions for now.  Sometimes the regression analysis or Chemaxon processing chokes unexpectedly.
            System.err.format("ERROR caught exception while processing '%s':\n", row.get("name"));
            System.err.format("%s\n", e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("Skipping...");
            continue;
          }
          System.out.format("--- Done analysis for chemical %s\n", row.get("name"));

          // This is a duplicate of the OPTION_INCHI block code, but it's inside of a tight loop, so...
          Map<String, String> tsvFeatures = new HashMap<>();
          for (Map.Entry<SurfactantAnalysis.FEATURES, Double> entry : analysisFeatures.entrySet()) {
            tsvFeatures.put(entry.getKey().toString(), String.format("%.6f", entry.getValue()));
          }
          tsvFeatures.put("name", row.get("name"));
          tsvFeatures.put("id", row.get("id"));
          tsvFeatures.put("inchi", row.get("inchi"));
          tsvFeatures.put("label", row.containsKey("label") ? row.get("label") : "?");
          if (tsvWriter != null) {
            tsvWriter.append(tsvFeatures);
            // Flush every time in case we crash or get interrupted.  The features must flow!
            tsvWriter.flush();
          }
        }
      } else {
        throw new RuntimeException("Must specify inchi or input file");
      }
    } finally {
      if (tsvWriter != null) {
        tsvWriter.close();
      }
    }
  }
}
