package com.act.lcms.db.io;

import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;
import com.act.lcms.db.model.StandardWell;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PrintConstructInfo {
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_CONSTRUCT = "c";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "Prints information about a construct's composition and plate location."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DIRECTORY)
            .argName("directory")
            .desc("The directory where LCMS analysis results live")
            .hasArg().required()
            .longOpt("data-dir")
    );
    add(Option.builder(OPTION_CONSTRUCT)
            .argName("construct id")
            .desc("A construct whose data to search for")
            .hasArg().required()
            .longOpt("construct")
    );

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
            .argName("help")
            .desc("Prints this help message")
            .longOpt("help")
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
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File lcmsDir = new File(cl.getOptionValue(OPTION_DIRECTORY));
    if (!lcmsDir.isDirectory()) {
      System.err.format("File at %s is not a directory\n", lcmsDir.getAbsolutePath());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      System.out.print("Loading/updating LCMS scan files into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      String construct = cl.getOptionValue(OPTION_CONSTRUCT);
      List<LCMSWell> lcmsWells = LCMSWell.getInstance().getByConstructID(db, construct);
      Collections.sort(lcmsWells, new Comparator<LCMSWell>() {
        @Override
        public int compare(LCMSWell o1, LCMSWell o2) {
          return o1.getId().compareTo(o2.getId());
        }
      });

      Set<String> uniqueMSIDs = new HashSet<>();
      Map<Integer, Plate> platesById = new HashMap<>();

      System.out.format("\n\n-- Construct %s --\n\n", construct);

      List<ChemicalAssociatedWithPathway> pathwayChems =
          ChemicalAssociatedWithPathway.getInstance().getChemicalsAssociatedWithPathwayByConstructId(db, construct);
      System.out.print("Chemicals associated with pathway:\n");
      System.out.format("  %-8s%-15s%-45s\n", "index", "kind", "chemical");
      for (ChemicalAssociatedWithPathway chem : pathwayChems) {
        System.out.format("  %-8d%-15s%-45s\n", chem.getIndex(), chem.getKind(), chem.getChemical());
      }

      System.out.print("\nLCMS wells:\n");
      System.out.format("  %-15s%-6s%-15s%-15s%-15s\n", "barcode", "well", "msid", "fed", "lcms_count");
      for (LCMSWell well : lcmsWells) {
        uniqueMSIDs.add(well.getMsid());

        Plate p = platesById.get(well.getPlateId());
        if (p == null) {
          // TODO: migrate Plate to be a subclass of BaseDBModel.
          p = Plate.getPlateById(db, well.getPlateId());
          platesById.put(p.getId(), p);
        }

        String chem = well.getChemical();
        List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
            db, p.getId(), well.getPlateRow(), well.getPlateColumn());

        System.out.format("  %-15s%-6s%-15s%-15s%-15d\n", p.getBarcode(),
            well.getCoordinatesString(), well.getMsid(),
            chem == null || chem.isEmpty() ? "--" : chem, scanFiles.size());
        System.out.flush();
      }

      List<Integer> plateIds = Arrays.asList(platesById.keySet().toArray(new Integer[platesById.size()]));
      Collections.sort(plateIds);
      System.out.print("\nAppears in plates:\n");
      for (Integer id : plateIds) {
        Plate p = platesById.get(id);
        System.out.format("  %s: %s\n", p.getBarcode(), p.getName());
      }

      List<String> msids = Arrays.asList(uniqueMSIDs.toArray(new String[uniqueMSIDs.size()]));
      Collections.sort(msids);
      System.out.format("\nMSIDS: %s\n", StringUtils.join(msids, ", "));

      Set<String> availableNegativeControls = new HashSet<>();
      for (Map.Entry<Integer, Plate> entry : platesById.entrySet()) {
        List<LCMSWell> wells = LCMSWell.getInstance().getByPlateId(db, entry.getKey());
        for (LCMSWell well : wells) {
          if (!construct.equals(well.getComposition())) {
            availableNegativeControls.add(well.getComposition());
          }
        }
      }

      // Print available standards for each step w/ plate barcodes and coordinates.
      System.out.format("\nAvailable Standards:\n");
      Map<Integer, Plate> plateCache = new HashMap<>();
      for (ChemicalAssociatedWithPathway chem : pathwayChems) {
        List<StandardWell> matchingWells =
            StandardWell.getInstance().getStandardWellsByChemical(db, chem.getChemical());
        for (StandardWell well : matchingWells){
          if (!plateCache.containsKey(well.getPlateId())) {
            Plate p = Plate.getPlateById(db, well.getPlateId());
            plateCache.put(p.getId(), p);
          }
        }
        Map<Integer, List<StandardWell>> standardWellsByPlateId = new HashMap<>();
        for (StandardWell well : matchingWells) {
          List<StandardWell> plateWells = standardWellsByPlateId.get(well.getPlateId());
          if (plateWells == null) {
            plateWells = new ArrayList<>();
            standardWellsByPlateId.put(well.getPlateId(), plateWells);
          }
          plateWells.add(well);
        }
        List<Pair<String, Integer>> plateBarcodes = new ArrayList<>(plateCache.size());
        for (Plate p : plateCache.values()) {
          if (p.getBarcode() == null) {
            plateBarcodes.add(Pair.of("(no barcode)", p.getId()));
          } else {
            plateBarcodes.add(Pair.of(p.getBarcode(), p.getId()));
          }
        }
        Collections.sort(plateBarcodes);
        System.out.format("  %s:\n", chem.getChemical());
        for (Pair<String, Integer> barcodePair : plateBarcodes) {
          // TODO: hoist this whole sorting/translation step into a utility class.
          List<StandardWell> wells = standardWellsByPlateId.get(barcodePair.getRight());
          if (wells == null) {
            // Don't print plates that don't apply to this chemical, which can happen because we're caching the plates.
            continue;
          }
          Collections.sort(wells, new Comparator<StandardWell>() {
            @Override
            public int compare(StandardWell o1, StandardWell o2) {
              int c = o1.getPlateRow().compareTo(o2.getPlateRow());
              if (c != 0) return c;
              return o1.getPlateColumn().compareTo(o2.getPlateColumn());
            }
          });
          List<String> descriptions = new ArrayList<>(wells.size());
          for (StandardWell well : wells) {
            descriptions.add(String.format("%s in %s%s", well.getCoordinatesString(), well.getMedia(),
                well.getConcentration() == null ? "" : String.format(" c. %f", well.getConcentration())));
          }
          System.out.format("    %s: %s\n", barcodePair.getLeft(), StringUtils.join(descriptions, ", "));
        }
      }

      List<String> negativeControlStrains =
          Arrays.asList(availableNegativeControls.toArray(new String[availableNegativeControls.size()]));
      Collections.sort(negativeControlStrains);
      System.out.format("\nAvailable negative controls: %s\n", StringUtils.join(negativeControlStrains, ","));
      System.out.print("\n----------\n");
      System.out.print("\n\n");
    }
  }
}
