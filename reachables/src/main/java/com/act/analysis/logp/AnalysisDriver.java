package com.act.analysis.logp;

import chemaxon.license.LicenseManager;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AnalysisDriver {
  public static final String OPTION_LICENSE_FILE = "l";
  public static final String OPTION_INCHI = "n";
  public static final String OPTION_INPUT_FILE = "i";

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
      LogPAnalysis.performAnalysis(cl.getOptionValue(OPTION_INCHI));
    } else if (cl.hasOption(OPTION_INPUT_FILE)) {
      throw new RuntimeException("Not yet implemented");
    } else {
      throw new RuntimeException("Must specify inchi or input file");
    }
  }
}
