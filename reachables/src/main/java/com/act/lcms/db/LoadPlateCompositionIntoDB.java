package com.act.lcms.db;

import com.act.lcms.db.model.DeliveredStrainWell;
import com.act.lcms.db.model.InductionWell;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PregrowthWell;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class LoadPlateCompositionIntoDB {

  // TODO: add argument parser and/or usage message.
  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    opts.addOption(Option.builder("t")
            .argName("type")
            .desc("The type of plate composition in this file, valid options are: " +
                StringUtils.join(Arrays.asList(Plate.CONTENT_TYPE.values()), ", "))
            .hasArg()
            .longOpt("plate-type")
            .required()
            .build()
    );
    opts.addOption(Option.builder("i")
            .argName("path")
            .desc("The plate composition file to read")
            .hasArg()
            .longOpt("input-file")
            .required()
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
      fmt.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      return;
    }

    File inputFile = new File(cl.getOptionValue("input-file"));
    if (!inputFile.exists()) {
      System.err.format("Unable to find input file at %s\n", cl.getOptionValue("input-file"));
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    PlateCompositionParser parser = new PlateCompositionParser();
    parser.processFile(inputFile);

    Plate.CONTENT_TYPE contentType = null;
    try {
      contentType = Plate.CONTENT_TYPE.valueOf(cl.getOptionValue("plate-type"));
    } catch (IllegalArgumentException e) {
      System.err.format("Unrecognized plate type '%s'\n", cl.getOptionValue("plate-type"));
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
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

      Plate p = Plate.getOrInsertFromPlateComposition(db, parser, contentType);

      switch (contentType) {
        case LCMS:
          List<LCMSWell> LCMSWells = LCMSWell.getInstance().insertFromPlateComposition(db, parser, p);
          for (LCMSWell LCMSWell : LCMSWells) {
            System.out.format("%d: %d x %d  %s  %s\n", LCMSWell.getId(),
                LCMSWell.getPlateColumn(), LCMSWell.getPlateRow(), LCMSWell.getMsid(), LCMSWell.getComposition());
          }
          break;
        case STANDARD:
          List<StandardWell> standardWells = StandardWell.getInstance().insertFromPlateComposition(db, parser, p);
          for (StandardWell standardWell : standardWells) {
            System.out.format("%d: %d x %d  %s\n", standardWell.getId(),
                standardWell.getPlateColumn(), standardWell.getPlateRow(), standardWell.getChemical());
          }
          break;
        case DELIVERED_STRAIN:
          List<DeliveredStrainWell> deliveredStrainWells =
              DeliveredStrainWell.getInstance().insertFromPlateComposition(db, parser, p);
          for (DeliveredStrainWell deliveredStrainWell : deliveredStrainWells) {
            System.out.format("%d: %d x %d (%s) %s %s \n", deliveredStrainWell.getId(),
                deliveredStrainWell.getPlateColumn(), deliveredStrainWell.getPlateRow(), deliveredStrainWell.getWell(),
                deliveredStrainWell.getMsid(), deliveredStrainWell.getComposition());
          }
          break;
        case INDUCTION:
          List<InductionWell> inductionWells = InductionWell.getInstance().insertFromPlateComposition(db, parser, p);
          for (InductionWell inductionWell : inductionWells) {
            System.out.format("%d: %d x %d %s %s %s %d\n", inductionWell.getId(),
                inductionWell.getPlateColumn(), inductionWell.getPlateRow(),
                inductionWell.getMsid(), inductionWell.getComposition(),
                inductionWell.getChemical(), inductionWell.getGrowth());
          }
          break;
        case PREGROWTH:
          List<PregrowthWell> pregrowthWells = PregrowthWell.getInstance().insertFromPlateComposition(db, parser, p);
          for (PregrowthWell pregrowthWell : pregrowthWells) {
            System.out.format("%d: %d x %d (%s @ %s) %s %s %d\n", pregrowthWell.getId(),
                pregrowthWell.getPlateColumn(), pregrowthWell.getPlateRow(),
                pregrowthWell.getSourcePlate(), pregrowthWell.getSourceWell(),
                pregrowthWell.getMsid(), pregrowthWell.getComposition(), pregrowthWell.getGrowth());
          }
          break;
        default:
          System.err.format("Unrecognized/unimplemented data type '%s'\n", contentType);
          break;
      }
      // If we didn't encounter an exception, commit the transaction.
      db.getConn().commit();
    } catch (Exception e) {
      System.err.format("Caught exception when trying to load plate composition, rolling back. %s\n", e.getMessage());
      db.getConn().rollback();
      throw(e);
    } finally {
      db.getConn().close();
    }

  }
}
