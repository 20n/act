package com.act.lcms.db.io;

import com.act.lcms.XZ;
import com.act.lcms.db.analysis.StandardIonAnalysis;
import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.StandardIonResult;
import com.act.lcms.db.model.StandardWell;
import com.act.utils.TSVWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExportStandardIonResultsFromDB {
  public static final String TSV_FORMAT = "tsv";
  public static final String OPTION_CONSTRUCT = "C";
  public static final String OPTION_CHEMICAL = "c";
  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String NULL_VALUE = "NULL";
  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This module outputs a TSV file of standard ion results for a given input of chemicals"
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  private static final String DEFAULT_ION = "M+H";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CONSTRUCT)
        .argName("construct")
        .desc("The construct to get results from")
        .hasArg()
    );
    add(Option.builder(OPTION_CHEMICAL)
        .argName("chemical name")
        .desc("The chemical to get results from")
        .hasArg()
    );
  }};

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }

  public enum STANDARD_ION_HEADER_FIELDS {
    CHEMICAL,
    PLATE_METADATA,
    BEST_ION_FROM_ALGO,
    SNR_TIME,
    MANUAL_PICK,
    DIAGNOSTIC_PLOTS,
    COMMENTS,
    STANDARD_ION_RESULT_ID,
  };

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
      HELP_FORMATTER.printHelp(ExportStandardIonResultsFromDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ExportStandardIonResultsFromDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      List<String> chemicalNames = new ArrayList<>();
      if (cl.hasOption(OPTION_CONSTRUCT)) {
        // Extract the chemicals in the pathway and their product masses, then look up info on those chemicals
        List<Pair<ChemicalAssociatedWithPathway, Double>> productMasses =
            Utils.extractMassesForChemicalsAssociatedWithConstruct(db, cl.getOptionValue(OPTION_CONSTRUCT));

        for (Pair<ChemicalAssociatedWithPathway, Double> pair : productMasses) {
          chemicalNames.add(pair.getLeft().getChemical());
        }
      }

      if (cl.hasOption(OPTION_CHEMICAL)) {
        String chemicalName = cl.getOptionValue(OPTION_CHEMICAL);
        List<StandardWell> standardWells = StandardIonAnalysis.getStandardWellsForChemical(db, chemicalName);
        if (standardWells.size() > 0) {
          chemicalNames.add(chemicalName);
        }
      }

      if (chemicalNames.size() == 0) {
        System.err.format("No chemicals can be found from the input query.\n");
      } else {
        List<String> standardIonHeaderFields = new ArrayList<>();
        for (STANDARD_ION_HEADER_FIELDS field : STANDARD_ION_HEADER_FIELDS.values()) {
          standardIonHeaderFields.add(field.name());
        }

        String outAnalysis;
        if (cl.hasOption(OPTION_OUTPUT_PREFIX)) {
          outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX) + "." + TSV_FORMAT;
        } else {
          outAnalysis = String.join("-", chemicalNames) + "." + TSV_FORMAT;
        }

        TSVWriter<String, String> resultsWriter = new TSVWriter<>(standardIonHeaderFields);
        resultsWriter.open(new File(outAnalysis));

        List<StandardIonResult> ionResults = new ArrayList<>();
        for (String chemicalName : chemicalNames) {
          List<StandardIonResult> getResultByChemicalName = StandardIonResult.getByChemicalName(db, chemicalName);
          if (getResultByChemicalName != null) {
            ionResults.addAll(getResultByChemicalName);
          }
        }

        for (StandardIonResult ionResult : ionResults) {
          StandardWell well = StandardWell.getInstance().getById(db, ionResult.getStandardWellId());
          Plate plateForWellToAnalyze = Plate.getPlateById(db, well.getPlateId());
          String plateMetadata = plateForWellToAnalyze.getBarcode() + " " + well.getCoordinatesString() + " " +
              well.getMedia() + " " + well.getConcentration();

          String bestIon = ionResult.getBestMetlinIon();
          XZ intensityAndTimeOfBestIon = ionResult.getAnalysisResults().get(bestIon);
          String snrAndTime = String.format("%.2f SNR at %.2fs", intensityAndTimeOfBestIon.getIntensity(),
              intensityAndTimeOfBestIon.getTime());

          Map<String, String> diagnosticPlots = new HashMap<>();
          diagnosticPlots.put(bestIon, ionResult.getPlottingResultFilePaths().get(bestIon));
          diagnosticPlots.put(DEFAULT_ION, ionResult.getPlottingResultFilePaths().get(DEFAULT_ION));
          String diagnosticPlotsString = OBJECT_MAPPER.writeValueAsString(diagnosticPlots);

          Map<String, String> row = new HashMap<>();
          row.put(STANDARD_ION_HEADER_FIELDS.CHEMICAL.name(), ionResult.getChemical());
          row.put(STANDARD_ION_HEADER_FIELDS.PLATE_METADATA.name(), plateMetadata);
          row.put(STANDARD_ION_HEADER_FIELDS.BEST_ION_FROM_ALGO.name(), bestIon);
          row.put(STANDARD_ION_HEADER_FIELDS.SNR_TIME.name(), snrAndTime);
          row.put(STANDARD_ION_HEADER_FIELDS.MANUAL_PICK.name(), NULL_VALUE);
          row.put(STANDARD_ION_HEADER_FIELDS.DIAGNOSTIC_PLOTS.name(), diagnosticPlotsString);
          row.put(STANDARD_ION_HEADER_FIELDS.COMMENTS.name(), "");
          row.put(STANDARD_ION_HEADER_FIELDS.STANDARD_ION_RESULT_ID.name(), Integer.toString(ionResult.getId()));

          resultsWriter.append(row);
          resultsWriter.flush();
        }

        try {
          resultsWriter.flush();
          resultsWriter.close();
        } catch (IOException e) {
          System.err.println("Error while flushing/closing csv writer.");
          e.printStackTrace();
        }
      }
    }
  }
}
