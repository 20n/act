package com.act.lcms.db;

import java.io.File;
import java.util.List;

public class LoadPlateCompositionIntoDB {

  // TODO: add argument parser and/or usage message.
  public static void main(String[] args) throws Exception {
    PlateCompositionParser parser = new PlateCompositionParser();
    parser.processFile(new File(args[1]));

    try (DB db = new DB().connectToDB("jdbc:postgresql://localhost:10000/lcms?user=mdaly")) {

      Plate p = Plate.getOrInsertFromPlateComposition(db, parser);

      switch (args[0]) {
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
        default:
          System.err.format("Unrecognized data type '%s'\n", args[0]);
          break;
      }
    }
  }
}
