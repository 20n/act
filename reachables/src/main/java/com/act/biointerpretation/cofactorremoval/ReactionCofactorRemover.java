/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.desalting.ReactionDesalter;
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
    System.out.format("  Sub. cofactors:  %s\n", StringUtils.join(rxn.getSubstrateCofactors(), ", "));
    System.out.format("  Prod. cofactors: %s\n", StringUtils.join(rxn.getProductCofactors(), ", "));
    System.out.format("  Coenzymes:       %s\n", StringUtils.join(rxn.getCoenzymes(), ", "));
  }
}
