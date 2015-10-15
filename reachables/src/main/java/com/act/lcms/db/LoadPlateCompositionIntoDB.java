package com.act.lcms.db;

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

  public static final String[] PLATE_TYPES = {
      "sample",
      "standard",
      "amyris_strain",
      "induction",
      "pregrowth"
  };

  // TODO: add argument parser and/or usage message.
  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    opts.addOption(Option.builder("t")
            .argName("type")
            .desc("The type of plate composition in this file, valid options are: " +
                StringUtils.join(Arrays.asList(PLATE_TYPES), ", "))
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

    System.out.format("Option list is: %s\n", StringUtils.join(cl.getOptions(), ", "));


    File inputFile = new File(cl.getOptionValue("input-file"));
    if (!inputFile.exists()) {
      System.err.format("Unable to find input file at %s\n", cl.getOptionValue("input-file"));
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    PlateCompositionParser parser = new PlateCompositionParser();
    parser.processFile(inputFile);

    try (DB db = new DB().connectToDB("jdbc:postgresql://localhost:10000/lcms?user=mdaly")) {

      Plate p = Plate.getOrInsertFromPlateComposition(db, parser);

      switch (cl.getOptionValue("plate-type")) {
        case "sample":
          List<SampleWell> sampleWells = SampleWell.insertFromPlateComposition(db, parser, p);
          for (SampleWell sampleWell : sampleWells) {
            System.out.format("%d: %d x %d  %s  %s\n", sampleWell.getId(),
                sampleWell.getPlateColumn(), sampleWell.getPlateRow(), sampleWell.getMsid(), sampleWell.getComposition());
          }
          break;
        case "standard":
          List<StandardWell> standardWells = StandardWell.insertFromPlateComposition(db, parser, p);
          for (StandardWell standardWell : standardWells) {
            System.out.format("%d: %d x %d  %s\n", standardWell.getId(),
                standardWell.getPlateColumn(), standardWell.getPlateRow(), standardWell.getChemical());
          }
          break;
        case "amyris_strain":
          List<DeliveredStrainWell> deliveredStrainWells =
              DeliveredStrainWell.insertFromPlateComposition(db, parser, p);
          for (DeliveredStrainWell deliveredStrainWell : deliveredStrainWells) {
            System.out.format("%d: %d x %d (%s) %s %s \n", deliveredStrainWell.getId(),
                deliveredStrainWell.getPlateColumn(), deliveredStrainWell.getPlateRow(), deliveredStrainWell.getWell(),
                deliveredStrainWell.getMsid(), deliveredStrainWell.getComposition());
          }
          break;
        case "induction":
          List<InductionWell> inductionWells =
              InductionWell.insertFromPlateComposition(db, parser, p);
          for (InductionWell inductionWell : inductionWells) {
            System.out.format("%d: %d x %d %s %s %s %d\n", inductionWell.getId(),
                inductionWell.getPlateColumn(), inductionWell.getPlateRow(),
                inductionWell.getMsid(), inductionWell.getComposition(),
                inductionWell.getChemical(), inductionWell.getGrowth());
          }
          break;
        case "pregrowth":
          List<PregrowthWell> pregrowthWells =
              PregrowthWell.insertFromPlateComposition(db, parser, p);
          for (PregrowthWell pregrowthWell : pregrowthWells) {
            System.out.format("%d: %d x %d (%s @ %s) %s %s %d\n", pregrowthWell.getId(),
                pregrowthWell.getPlateColumn(), pregrowthWell.getPlateRow(),
                pregrowthWell.getSourcePlate(), pregrowthWell.getSourceWell(),
                pregrowthWell.getMsid(), pregrowthWell.getComposition(), pregrowthWell.getGrowth());
          }
        default:
          System.err.format("Unrecognized data type '%s'\n", args[0]);
          break;
      }
    }
  }
}
