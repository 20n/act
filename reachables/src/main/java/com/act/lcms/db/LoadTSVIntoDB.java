package com.act.lcms.db;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.List;

public class LoadTSVIntoDB {
  public enum TSV_TYPE {
    CHEMICAL,
    CONSTRUCT
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    opts.addOption(Option.builder("t")
            .argName("type")
            .desc("The type of TSV data to read, options are: " + StringUtils.join(TSV_TYPE.values(), ", "))
            .hasArg().required()
            .longOpt("table-type")
            .build()
    );
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
      fmt.printHelp(LoadTSVIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      new HelpFormatter().printHelp(LoadTSVIntoDB.class.getCanonicalName(), opts, true);
      return;
    }

    System.out.format("Option list is: %s\n", StringUtils.join(cl.getOptions(), ", "));


    File inputFile = new File(cl.getOptionValue("input-file"));
    if (!inputFile.exists()) {
      System.err.format("Unable to find input file at %s\n", cl.getOptionValue("input-file"));
      new HelpFormatter().printHelp(LoadTSVIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    TSV_TYPE contentType = null;
    try {
      contentType = TSV_TYPE.valueOf(cl.getOptionValue("table-type"));
    } catch (IllegalArgumentException e) {
      System.err.format("Unrecognized TSV type '%s'\n", cl.getOptionValue("table-type"));
      new HelpFormatter().printHelp(LoadTSVIntoDB.class.getCanonicalName(), opts, true);
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

      TSVParser parser = new TSVParser();
      parser.parse(inputFile);

      List<Pair<Integer, DB.OPERATION_PERFORMED>> results;
      switch (contentType) {
        case CHEMICAL:
          results = CuratedChemical.insertOrUpdateCuratedChemicalsFromTSV(db, parser);
          for (Pair<Integer, DB.OPERATION_PERFORMED> r : results) {
            System.out.format("%d: %s\n", r.getLeft(), r.getRight());
          }
          break;
        case CONSTRUCT:
          results = ConstructMapEntry.insertOrUpdateCompositionMapEntrysFromTSV(db, parser);
          for (Pair<Integer, DB.OPERATION_PERFORMED> r : results) {
            System.out.format("%d: %s\n", r.getLeft(), r.getRight());
          }
          break;
        default:
          throw new RuntimeException(String.format("Unsupported TSV type: %s", contentType));
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
