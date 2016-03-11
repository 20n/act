package com.act.lcms.db.io;

import com.act.lcms.MS1;
import com.act.lcms.db.io.parser.TSVParser;
import com.act.lcms.db.model.CuratedStandardMetlinIon;
import com.act.lcms.db.model.StandardIonResult;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

public class LoadStandardIonAnalysisTableIntoDB {
  public static final String OPTION_FILE_PATH = "i";
  public static final String OPTION_AUTHOR = "a";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "TODO: write a help message."
  }, "");

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_FILE_PATH)
        .argName("CSV file path")
        .desc("The file from which data is ingested into the DB")
        .hasArg().required()
    );
    add(Option.builder(OPTION_AUTHOR)
        .argName("commit author")
        .desc("The author of the commit")
        .hasArg().required()
    );
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
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
      HELP_FORMATTER.printHelp(LoadStandardIonAnalysisTableIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadStandardIonAnalysisTableIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File inputFile = new File(cl.getOptionValue(OPTION_FILE_PATH));
    if (!inputFile.exists()) {
      System.err.format("Unable to find input file at %s\n", cl.getOptionValue(OPTION_FILE_PATH));
      new HelpFormatter().printHelp(LoadConstructAnalysisTableIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      db.getConn().setAutoCommit(false);

      TSVParser parser = new TSVParser();
      parser.parse(inputFile);

      for (Map<String, String> val : parser.getResults()) {
        String manualPickOfMetlinIon =
            val.get(ExportStandardIonResultsFromDB.STANDARD_ION_HEADER_FIELDS.MANUAL_PICK.name());
        if (!manualPickOfMetlinIon.equals(ExportStandardIonResultsFromDB.NULL_VALUE)) {
          System.out.format("Manual override has been found, so update the DB\n");
          // A manual entry was created.
          if (!MS1.VALID_MS1_IONS.contains(manualPickOfMetlinIon)) {
            System.err.format("WARNING: found invalid chemical name: %s, skipping ahead\n", manualPickOfMetlinIon);
            continue;
          }

          Integer standardIonResultId = Integer.parseInt(
              val.get(ExportStandardIonResultsFromDB.STANDARD_ION_HEADER_FIELDS.STANDARD_ION_RESULT_ID.name()));
          String comments = val.get(ExportStandardIonResultsFromDB.STANDARD_ION_HEADER_FIELDS.COMMENTS.name());
          CuratedStandardMetlinIon result = CuratedStandardMetlinIon.insertCuratedStandardMetlinIonIntoDB(
              db, new Date(), cl.getOptionValue(OPTION_AUTHOR), manualPickOfMetlinIon, comments, standardIonResultId);

          if (result == null) {
            System.err.format("WARNING: Could not insert curated entry to the curated metlin ion table\n", manualPickOfMetlinIon);
            continue;
          } else {
            if (!StandardIonResult.updateManualOverrideField(db, result.getId(), standardIonResultId)) {
              System.err.format("WARNING: Could not insert manual override id to the standard ion table\n", manualPickOfMetlinIon);
            } else {
              System.out.format("Successfully committed updates to the standard ion table and the curated metlin ion table\n");
            }
          }
        }
      }

      db.getConn().commit();
    }
  }
}
