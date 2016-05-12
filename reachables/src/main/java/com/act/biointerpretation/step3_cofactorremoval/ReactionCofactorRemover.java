package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.step2_desalting.ReactionDesalter;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ReactionCofactorRemover {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionCofactorRemover.class);

  public static final String OPTION_READ_DB = "d";
  public static final String OPTION_RXN_ID = "r";
  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class removes cofactors from a single reaction and prints the results to stdout. ",
      "To remove cofactors from an entire installer DB, use BiointerpretationDriver."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_READ_DB)
        .argName("read db name")
        .desc("The name of the read DB to use")
        .hasArg().required()
        .longOpt("db")
    );
    add(Option.builder(OPTION_RXN_ID)
        .argName("id")
        .desc("The id of the reaction from which to remove cofactors")
        .hasArg().required()
        .longOpt("id")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
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
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionDesalter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    NoSQLAPI api = new NoSQLAPI(cl.getOptionValue(OPTION_READ_DB), cl.getOptionValue(OPTION_READ_DB));

    CofactorRemover cofactorRemover = new CofactorRemover(api);
    cofactorRemover.init();

    Pair<Reaction, Reaction> results = cofactorRemover.removeCofactorsFromOneReaction(
        Long.parseLong(cl.getOptionValue(OPTION_RXN_ID)));

    System.out.format("Reaction before processing:\n");
    printReport(results.getLeft());
    System.out.println();
    System.out.format("Reaction after processing:\n");
    printReport(results.getRight());
    System.out.println();
  }

  private static void printReport(Reaction rxn) {
    System.out.format("Reaction before processing:\n");
    System.out.format("  Substrates:      %s\n", StringUtils.join(rxn.getSubstrates(), ", "));
    System.out.format("  Products:        %s\n", StringUtils.join(rxn.getProducts(), ", "));
    System.out.format("  Sub. cofactors:  %s\n", StringUtils.join(rxn.getSubstrates(), ", "));
    System.out.format("  Prod. cofactors: %s\n", StringUtils.join(rxn.getProducts(), ", "));
    System.out.format("  Coenzymes:       %s\n", StringUtils.join(rxn.getProducts(), ", "));
  }
}
