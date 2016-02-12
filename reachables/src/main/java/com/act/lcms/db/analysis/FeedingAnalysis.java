package com.act.lcms.db.analysis;

import com.act.lcms.MS1;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.model.CuratedChemical;
import com.act.lcms.db.model.FeedingLCMSWell;
import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.ScanFile;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeedingAnalysis {
  public static final String DEFAULT_ION = "M+H";

  public static final String OPTION_DIRECTORY = "d";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_FEEDING_STRAIN_OR_CONSTRUCT = "c";
  public static final String OPTION_FEEDING_EXTRACT = "e";
  public static final String OPTION_FEEDING_FED_CHEMICAL = "f";
  public static final String OPTION_ION_NAME = "i";
  public static final String OPTION_PLATE_BARCODE = "b";
  public static final String OPTION_SEARCH_MZ = "m";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class applies the MS1 LCMS analysis to a strain/construct fed ",
      "different concentrations of a precursors. It takes in a feeding specification ",
      "plate, strain or construct (MSID or internal name), extract, ion name, mz, ",
      "and fed chemical and generates a comparative graph of ",
      "intensity vs. concentration (using max peak inferred from traces) ",
      "and an overlaid graph of the spectra in the various concentrations."
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
    add(Option.builder(OPTION_OUTPUT_PREFIX)
            .argName("output prefix")
            .desc("A prefix for the output data/pdf files")
            .hasArg().required()
            .longOpt("output-prefix")
    );

    add(Option.builder(OPTION_FEEDING_STRAIN_OR_CONSTRUCT)
            .desc("Perform a feeding analysis for this strain/construct")
            .hasArg().required()
            .longOpt("construct-or-strain")
    );
    add(Option.builder(OPTION_FEEDING_EXTRACT)
            .desc("Specify the extract for which to perform a feeding analysis")
            .hasArg().required()
            .longOpt("extract")
    );
    add(Option.builder(OPTION_FEEDING_FED_CHEMICAL)
            .desc("Specify the fed chemical group for which to perform the feeding analysis")
            .hasArg().required()
            .longOpt("fed-chemical")
    );
    add(Option.builder(OPTION_ION_NAME)
            .desc(String.format("An ion of the chemical target for which to compute feeding curves (default is %s)",
                DEFAULT_ION))
            .hasArg()
            .longOpt("ion")
    );
    add(Option.builder(OPTION_PLATE_BARCODE)
            .desc("The barcode of the plate from which to extract feeding data")
            .hasArg().required()
            .longOpt("plate-barcode")
    );
    add(Option.builder(OPTION_SEARCH_MZ)
            .desc("A m/z value or chemical name to search for in the feeding data; default is the construct's target")
            .hasArg()
            .longOpt("search-chem")
    );
  }};
  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  private static void performFeedingAnalysis(DB db, String lcmsDir,
                                             String searchIon, String searchMassStr, String plateBarcode,
                                             String strainOrConstruct, String extract, String feedingCondition,
                                             String outPrefix, String fmt)
      throws SQLException, Exception {
    Plate p = Plate.getPlateByBarcode(db, plateBarcode);
    if (p == null) {
      throw new RuntimeException(String.format("Unable to find plate with barcode %s", plateBarcode));
    }
    if (p.getContentType() != Plate.CONTENT_TYPE.FEEDING_LCMS) {
      throw new RuntimeException(String.format("Plate with barcode %s is not a feeding plate (%s)",
          plateBarcode, p.getContentType()));
    }
    List<FeedingLCMSWell> allPlateWells = FeedingLCMSWell.getInstance().getFeedingLCMSWellByPlateId(db, p.getId());
    if (allPlateWells == null || allPlateWells.size() == 0) {
      throw new RuntimeException(String.format("No feeding LCMS wells available for plate %s", p.getBarcode()));
    }

    List<FeedingLCMSWell> relevantWells = new ArrayList<>();
    for (FeedingLCMSWell well : allPlateWells) {
      if (!well.getMsid().equals(strainOrConstruct) && !well.getComposition().equals(strainOrConstruct)) {
        // Ignore wells that don't have the right strain/construct (though we assume the whole plate shares one).
        continue;
      }

      if (!well.getExtract().equals(extract)) {
        // Filter by extract.
        continue;
      }

      if (!well.getChemical().equals(feedingCondition)) {
        // Filter by fed chemical.
        continue;
      }

      relevantWells.add(well);
    }

    Collections.sort(relevantWells, new Comparator<FeedingLCMSWell>() {
      @Override
      public int compare(FeedingLCMSWell o1, FeedingLCMSWell o2) {
        // Assume concentration is never null.
        return o1.getConcentration().compareTo(o2.getConcentration());
      }
    });

    Map<FeedingLCMSWell, ScanFile> wellsToScanFiles = new HashMap<>();
    Set<String> constructs = new HashSet<>(1);
    for (FeedingLCMSWell well : relevantWells) {
      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      if (scanFiles == null || scanFiles.size() == 0) {
        System.err.format("WARNING: no scan files for well at %s %s\n", p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      if (scanFiles.size() > 1) {
        System.err.format("WARNING: found multiple scan files for %s %s, using first\n",
            p.getBarcode(), well.getCoordinatesString());
      }
      while (scanFiles.size() > 0 && scanFiles.get(0).getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
        scanFiles.remove(0);
      }
      if (scanFiles.size() == 0) {
        System.err.format("WARNING: no scan files with valid format for %s %s\n",
            p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      // All of the extracted wells should be unique, so there should be no collisions here.
      wellsToScanFiles.put(well, scanFiles.get(0));
      constructs.add(well.getComposition());
    }

    Pair<String, Double> searchMass = null;
    if (searchMassStr != null) {
      searchMass = Utils.extractMassFromString(db, searchMassStr);
    }
    if (searchMass == null) {
      if (constructs.size() != 1) {
        throw new RuntimeException(String.format(
            "Found multiple growth targets for feeding analysis when no mass specified: %s",
            StringUtils.join(constructs, ", ")));
      }
      String constructName = constructs.iterator().next();
      CuratedChemical cc = Utils.extractTargetForConstruct(db, constructName);
      if (cc == null) {
        throw new RuntimeException(String.format("Unable to find curated chemical for construct %s", constructName));
      }
      System.out.format("Using target %s of construct %s as search mass (%f)\n",
          cc.getName(), constructName, cc.getMass());
      searchMass = Pair.of(cc.getName(), cc.getMass());
    }

    MS1 c = new MS1();
    // TODO: use configurable or scan-file derived ion mode. Do it the way its done in:
    // https://github.com/20n/act/blob/d997e84f0f44a5c88a94ef935829cb47e0ca8d1a/reachables/src/main/java/com/act/lcms/db/analysis/AnalysisHelper.java#L79
    MS1.IonMode mode = MS1.IonMode.valueOf("POS");
    Map<String, Double> metlinMasses = c.getIonMasses(searchMass.getValue(), mode);

    if (searchIon == null || searchIon.isEmpty()) {
      System.err.format("No search ion defined, defaulting to M+H\n");
      searchIon = DEFAULT_ION;
    }

    List<Pair<Double, MS1ScanForWellAndMassCharge>> rampUp = new ArrayList<>();
    for (FeedingLCMSWell well : relevantWells) {
      ScanFile scanFile = wellsToScanFiles.get(well);
      if (scanFile == null) {
        System.err.format("WARNING: no scan file available for %s %s", p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      File localScanFile = new File(lcmsDir, scanFile.getFilename());
      if (!localScanFile.exists() && localScanFile.isFile()) {
        System.err.format("WARNING: could not find regular file at expected path: %s\n",
            localScanFile.getAbsolutePath());
        continue;
      }
      System.out.format("Processing scan data at %s\n", localScanFile.getAbsolutePath());

      MS1ScanForWellAndMassCharge ms1ScanCache = new MS1ScanForWellAndMassCharge();
      MS1ScanForWellAndMassCharge ms1ScanResults =
          ms1ScanCache.getByPlateIdPlateRowPlateColIonMzUseSnrScanFile(db, p, well, searchMass.getValue(), true, localScanFile.getAbsolutePath(), metlinMasses);

      Double concentration = well.getConcentration();
      rampUp.add(Pair.of(concentration, ms1ScanResults));
    }

    c.plotFeedings(rampUp, searchIon, outPrefix, fmt, outPrefix + ".gnuplot");
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
      System.out.format("Loading/updating LCMS scan files into DB\n");
      ScanFile.insertOrUpdateScanFilesInDirectory(db, lcmsDir);

      System.out.format("Running feeding analysis\n");
      performFeedingAnalysis(db, cl.getOptionValue(OPTION_DIRECTORY),
          cl.getOptionValue(OPTION_ION_NAME),cl.getOptionValue(OPTION_SEARCH_MZ),
          cl.getOptionValue(OPTION_PLATE_BARCODE), cl.getOptionValue(OPTION_FEEDING_STRAIN_OR_CONSTRUCT),
          cl.getOptionValue(OPTION_FEEDING_EXTRACT), cl.getOptionValue(OPTION_FEEDING_FED_CHEMICAL),
          cl.getOptionValue(OPTION_OUTPUT_PREFIX), "pdf");
    }
  }
}
