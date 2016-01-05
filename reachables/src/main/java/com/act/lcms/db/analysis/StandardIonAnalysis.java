package com.act.lcms.db.analysis;

import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.ConstructEntry;
import com.act.lcms.db.model.Plate;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StandardIonAnalysis {
  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_CONSTRUCT = "c";
  public static final String OPTION_NEGATIVE_CONSTRUCTS = "C";
  public static final String OPTION_OUTPUT_PREFIX = "o";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "TODO: write a help message."
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
    /*
    add(Option.builder(OPTION_OUTPUT_PREFIX)
            .argName("output prefix")
            .desc("A prefix for the output data/pdf files")
            .hasArg().required()
            .longOpt("output-prefix")
    );
    */
    add(Option.builder(OPTION_CONSTRUCT)
            .argName("construct")
            .desc("The construct whose pathway chemicals should be analyzed")
            .hasArg().required()
            .longOpt("construct")
    );
  }};
  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public Pair<ConstructEntry, List<ChemicalAssociatedWithPathway>> getChemicalsForConstruct(DB db, String constructId)
    throws SQLException {
    ConstructEntry construct =
        ConstructEntry.getInstance().getCompositionMapEntryByCompositionId(db, constructId);
    if (construct == null) {
      throw new RuntimeException(String.format("Unable to find construct '%s'", constructId));
    }

    List<ChemicalAssociatedWithPathway> products =
        ChemicalAssociatedWithPathway.getInstance().getChemicalsAssociatedWithPathwayByConstructId(db, constructId);

    return Pair.of(construct, products);
  }

  public List<StandardWell> getStandardWellsForChemical(DB db, ChemicalAssociatedWithPathway pathwayChem)
      throws SQLException {
    return StandardWell.getInstance().getStandardWellsByChemical(db, pathwayChem.getChemical());
  }

  public List<StandardWell> getViableNegativeControlsForStandardWell(DB db, StandardWell baseStandard)
      throws SQLException {
    List<StandardWell> wellsFromSamePlate = StandardWell.getInstance().getByPlateId(db, baseStandard.getPlateId());

    // TODO: take availability of scan files into account here?
    List<StandardWell> candidates = new ArrayList<>();
    for (StandardWell well : wellsFromSamePlate) {
      if (well.getChemical().equals(baseStandard.getChemical())) {
        continue; // Skip wells with the same chemical.
      }

      if (baseStandard.getConcentration() != null && well.getConcentration() != null &&
          !baseStandard.getConcentration().equals(well.getConcentration())) {
        continue; // Skip non-matching concentrations if both wells define concentration.
      }

      if (baseStandard.getMedia() != null && well.getMedia() != null &&
          !baseStandard.getMedia().equals(well.getMedia())) {
        continue; // Skip non-matching media if both wells define media type.
      }
      candidates.add(well);
    }

    return candidates;
  }

  public Map<StandardWell, List<ScanFile>> getViableScanFilesForStandardWells(
      DB db, StandardWell posStandard, List<StandardWell> negativeCandidates) throws SQLException {
    Map<StandardWell, List<ScanFile>> wellToFilesMap = new HashMap<>();
    List<ScanFile> posScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
        db, posStandard.getPlateId(), posStandard.getPlateRow(), posStandard.getPlateColumn());
    wellToFilesMap.put(posStandard, posScanFiles);

    Set<ScanFile.SCAN_MODE> viableScanModes = new HashSet<>();
    for (ScanFile file : posScanFiles) {
      viableScanModes.add(file.getMode());
    }

    for (StandardWell well : negativeCandidates) {
      List<ScanFile> allScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      List<ScanFile> viableScanFiles = new ArrayList<>();
      for (ScanFile file : allScanFiles) {
        if (viableScanModes.contains(file.getMode())) {
          viableScanFiles.add(file);
        }
      }
      wellToFilesMap.put(well, viableScanFiles);
    }

    return wellToFilesMap;
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

      StandardIonAnalysis analysis = new StandardIonAnalysis();

      Pair<ConstructEntry, List<ChemicalAssociatedWithPathway>> constructAndPathwayChems =
          analysis.getChemicalsForConstruct(db, cl.getOptionValue(OPTION_CONSTRUCT));
      System.out.format("Construct: %s\n", constructAndPathwayChems.getLeft().getCompositionId());
      Map<Integer, Plate> plateCache = new HashMap<>();
      for (ChemicalAssociatedWithPathway pathwayChem : constructAndPathwayChems.getRight()) {
        System.out.format("  Pathway chem %s\n", pathwayChem.getChemical());
        List<StandardWell> standardWells = analysis.getStandardWellsForChemical(db, pathwayChem);
        for (StandardWell well : standardWells) {
          List<StandardWell> negatives = analysis.getViableNegativeControlsForStandardWell(db, well);
          Map<StandardWell, List<ScanFile>> scanFiles =
              analysis.getViableScanFilesForStandardWells(db, well, negatives);
          List<String> posScanFileNames = new ArrayList<>();
          for (ScanFile scanFile : scanFiles.get(well)) {
            posScanFileNames.add(scanFile.getFilename());
          }
          Plate plate = plateCache.get(well.getPlateId());
          if (plate == null) {
            plate = Plate.getPlateById(db, well.getPlateId());
            plateCache.put(plate.getId(), plate);
          }

          System.out.format("    Standard well: %s @ %s, '%s'%s%s\n", plate.getBarcode(), well.getCoordinatesString(),
              well.getChemical(),
              well.getMedia() == null ? "" : String.format(" in %s", well.getMedia()),
              well.getConcentration() == null ? "" : String.format(" @ %s", well.getConcentration()));
          System.out.format("      Scan files: %s\n", StringUtils.join(posScanFileNames, ", "));

          for (StandardWell negWell : negatives) {
            plate = plateCache.get(negWell.getPlateId());
            if (plate == null) {
              plate = Plate.getPlateById(db, negWell.getPlateId());
              plateCache.put(plate.getId(), plate);
            }
            List<String> negScanFileNames = new ArrayList<>();
            for (ScanFile scanFile : scanFiles.get(negWell)) {
              negScanFileNames.add(scanFile.getFilename());
            }

            System.out.format("      Viable negative: %s @ %s, '%s'%s%s\n", plate.getBarcode(),
                negWell.getCoordinatesString(),
                negWell.getChemical(),
                negWell.getMedia() == null ? "" : String.format(" in %s", negWell.getMedia()),
                negWell.getConcentration() == null ? "" : String.format(" @ %s", negWell.getConcentration()));
            System.out.format("        Scan files: %s\n", StringUtils.join(negScanFileNames, ", "));
          }
        }
      }
    }
  }
}
