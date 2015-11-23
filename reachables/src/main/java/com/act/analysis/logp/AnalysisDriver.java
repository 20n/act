package com.act.analysis.logp;

import chemaxon.license.LicenseManager;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.io.parser.TSVParser;
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
import java.util.List;
import java.util.Map;

public class AnalysisDriver {
  public static final String OPTION_LICENSE_FILE = "l";
  public static final String OPTION_INCHI = "n";
  public static final String OPTION_INPUT_FILE = "i";
  public static final String OPTION_DISPLAY = "d";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "TODO: write help message"
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

    LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));
    if (cl.hasOption(OPTION_INCHI)) {
      LogPAnalysis.performAnalysis(cl.getOptionValue(OPTION_INCHI), cl.hasOption(OPTION_DISPLAY));
    } else if (cl.hasOption(OPTION_INPUT_FILE)) {
      TSVParser parser = new TSVParser();
      parser.parse(new File(cl.getOptionValue(OPTION_INPUT_FILE)));
      int i = 0;
      for (Map<String, String> row : parser.getResults()) {
        i++;
        if (!row.containsKey("name") || !row.containsKey("inchi")) {
          System.err.format("WARNING: TSV rows must contain at least name and inchi, skipping row %d\n", i);
          continue;
        }
        System.out.format("Analysis for chemical %s\n", row.get("name"));
        LogPAnalysis.performAnalysis(row.get("inchi"), false);
      }
    } else {
      throw new RuntimeException("Must specify inchi or input file");
    }

  }
}
