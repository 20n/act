package com.act.biointerpretation.l2expansion;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class L2ExpansionToProducts {
  private static final Logger LOGGER = LogManager.getFormatterLogger(L2ExpansionToProducts.class);
  private static final String OPTION_VAL = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "FILL_OUT ",
      "FILL_OUT"}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_VAL)
          .argName("FILL_OUT")
          .desc("FILL_OUT")
          .hasArg()
          .longOpt("FILL_OUT")
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
      HELP_FORMATTER.printHelp(L2ExpansionToProducts.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(L2ExpansionToProducts.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File folder = new File("/Volumes/shared-data/Vijay/perlstein_analysis/l2_human_projections/");
    File[] listOfFiles = folder.listFiles();

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File("/Volumes/shared-data/Vijay/perlstein_analysis/l4n1.txt")))) {
      for (File file : listOfFiles) {
        L2PredictionCorpus corpus = L2PredictionCorpus.readPredictionsFromJsonFile(file);
        for (String inchi : corpus.getUniqueProductInchis()) {
          predictionWriter.write(inchi);
          predictionWriter.newLine();
        }
      }
    }
  }
}
