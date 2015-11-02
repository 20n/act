package com.act.lcms.db.io;

import com.act.lcms.db.io.parser.ConstructAnalysisFileParser;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.List;

public class LoadConstructAnalysisTableIntoDB {
  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    opts.addOption(Option.builder("i")
            .argName("path")
            .desc("The TSV file to read")
            .hasArg().required()
            .longOpt("input-file")
            .build()
    );

    // DB connection options.
    opts.addOption(Option.builder()
            .argName("database url")
            .desc("The url to use when connecting to the LCMS db")
            .hasArg()
            .longOpt("db-url")
            .build()
    );
    opts.addOption(Option.builder("u")
            .argName("database user")
            .desc("The LCMS DB user")
            .hasArg()
            .longOpt("db-user")
            .build()
    );
    opts.addOption(Option.builder("p")
            .argName("database password")
            .desc("The LCMS DB password")
            .hasArg()
            .longOpt("db-pass")
            .build()
    );
    opts.addOption(Option.builder("H")
            .argName("database host")
            .desc(String.format("The LCMS DB host (default = %s)", DB.DEFAULT_HOST))
            .hasArg()
            .longOpt("db-host")
            .build()
    );
    opts.addOption(Option.builder("P")
            .argName("database port")
            .desc(String.format("The LCMS DB port (default = %d)", DB.DEFAULT_PORT))
            .hasArg()
            .longOpt("db-port")
            .build()
    );
    opts.addOption(Option.builder("N")
            .argName("database name")
            .desc(String.format("The LCMS DB name (default = %s)", DB.DEFAULT_DB_NAME))
            .hasArg()
            .longOpt("db-name")
            .build()
    );

    // Everybody needs a little help from their friends.
    opts.addOption(Option.builder("h")
            .argName("help")
            .desc("Prints this help message")
            .longOpt("help")
            .build()
    );

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HelpFormatter fmt = new HelpFormatter();
      fmt.printHelp(LoadConstructAnalysisTableIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      new HelpFormatter().printHelp(LoadConstructAnalysisTableIntoDB.class.getCanonicalName(), opts, true);
      return;
    }

    File inputFile = new File(cl.getOptionValue("input-file"));
    if (!inputFile.exists()) {
      System.err.format("Unable to find input file at %s\n", cl.getOptionValue("input-file"));
      new HelpFormatter().printHelp(LoadConstructAnalysisTableIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    DB db;

    if (cl.hasOption("db-url")) {
      db = new DB().connectToDB(cl.getOptionValue("db-url"));
    } else {
      Integer port = null;
      if (cl.getOptionValue("P") != null) {
        port = Integer.parseInt(cl.getOptionValue("P"));
      }
      db = new DB().connectToDB(cl.getOptionValue("H"), port, cl.getOptionValue("N"),
          cl.getOptionValue("u"), cl.getOptionValue("p"));
    }

    try {
      db.getConn().setAutoCommit(false);

      ConstructAnalysisFileParser parser = new ConstructAnalysisFileParser();
      parser.parse(inputFile);

      List<Pair<Integer, DB.OPERATION_PERFORMED>> results =
          ChemicalAssociatedWithPathway.insertOrUpdateChemicalsAssociatedWithPathwayFromParser(db, parser);
      if (results != null) {
        for (Pair<Integer, DB.OPERATION_PERFORMED> r : results) {
          System.out.format("%d: %s\n", r.getLeft(), r.getRight());
        }
      }
      // If we didn't encounter an exception, commit the transaction.
      db.getConn().commit();
    } catch (Exception e) {
      System.err.format("Caught exception when trying to load plate composition, rolling back. %s\n", e.getMessage());
      db.getConn().rollback();
      throw (e);
    } finally {
      db.getConn().close();
    }

  }
}
