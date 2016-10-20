package com.act.analysis.similarity;

import com.act.lcms.*;
import com.act.lcms.Peak;
import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.model.ScanFile;
import com.act.utils.TSVParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.didion.jwnl.data.Exc;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ManisaFormatting {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ManisaFormatting.class);
  private static final String OPTION_VAL = "p";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "FILL_OUT ",
      "FILL_OUT"}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_VAL)
          .argName("FILL_OUT")
          .desc("FILL_OUT")
          .hasArg()
          .longOpt("FILL_OUT")
      );
    }
  };

  static {
    // Add DB connection options.
    OPTION_BUILDERS.addAll(DB.DB_OPTION_BUILDERS);
  }


  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
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
      LOGGER.error("Argument parsing failed: %s", e.getMessage());
      HELP_FORMATTER.printHelp(ManisaFormatting.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ManisaFormatting.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    try (DB db = DB.openDBFromCLI(cl)) {
      TSVParser parser = new TSVParser();
      parser.parse(new File("/mnt/shared-data/Vijay/manisa/manisa-filtered.txt"));

      Set<String> includeIons = new HashSet<>();
      includeIons.add("M+H");
      includeIons.add("M+2Na-H");
      includeIons.add("M+ACN+H");
      includeIons.add("M+Na");
      includeIons.add("M+H-H2O");

      for (Map<String, String> row : parser.getResults()) {
        String inchi = row.get("inchi");
        Double retentionTime = Double.parseDouble(row.get("retention"));
        Integer plateId = Integer.parseInt(row.get("plate_id"));
        Integer plateRow = Integer.parseInt(row.get("row"));
        Integer plateCol = Integer.parseInt(row.get("col"));

        Map<String, Double> allMasses = MS1.getIonMasses(MassCalculator.calculateMass(inchi), MS1.IonMode.POS);
        Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, includeIons, null);

        for (Map.Entry<String, Double> metlinMass : metlinMasses.entrySet()) {
          List<VizRepresentation.VizPeak> peaks = new ArrayList<>();
          VizRepresentation.VizPeak peak = new VizRepresentation.VizPeak(-1.0, -1.0, -1.0, -1.0, retentionTime, 0.02, metlinMass.getValue(), 5.0);
          peaks.add(peak);

          List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(db, plateId, plateRow, plateCol);
          String scanFile = scanFiles.get(0).getFilename().split("/")[scanFiles.get(0).getFilename().split("/").length - 1];
          VizRepresentation.VizScanFiles.FileName fileName = new VizRepresentation.VizScanFiles.FileName(scanFile);

          List<VizRepresentation.VizScanFiles.FileName> fileNames = new ArrayList<>();
          fileNames.add(fileName);

          List<String> scanFilesForViz = new ArrayList<>();
          scanFilesForViz.add(scanFile);

          VizRepresentation.VizLayout layout = new VizRepresentation.VizLayout(1, 0);

          VizRepresentation representation = new VizRepresentation(fileNames, peaks, layout);

          String path = "/mnt/shared-data/Vijay/manisa/";
          try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(path + row.get("Molecule") + "_" + metlinMass.getKey() + ".json"))) {
            OBJECT_MAPPER.writeValue(predictionWriter, representation);
          }
        }
      }
    }
  }
}
