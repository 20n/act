package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IonAnalysisParser {

  public static final String OPTION_INPUT_FILE = "i";
  public static final String OPTION_SECOND_INPUT_FILE = "t";
  public static final String OPTION_OUTPUT_FILE = "o";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILE)
        .argName("input file")
        .desc("The input file containing molecular hit results")
        .hasArg().required()
        .longOpt("input-file")
    );
    add(Option.builder(OPTION_SECOND_INPUT_FILE)
        .argName("second input file")
        .desc("The input file containing molecular hit results")
        .hasArg().required()
        .longOpt("second-input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output file")
        .desc("The output file to write to")
        .hasArg().required()
        .longOpt("output-file")
    );
  }};

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
      System.exit(1);
    }

    Set<String> inchis = IonAnalysisInterchangeModel.getAllMoleculeHitsFromTwoGeneratedFiles(
        cl.getOptionValue(OPTION_INPUT_FILE), cl.getOptionValue(OPTION_SECOND_INPUT_FILE), 1000.0, 10000.0, 15.0);

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File(cl.getOptionValue(OPTION_OUTPUT_FILE))))) {
      for (String inchi : inchis) {
        predictionWriter.append(inchi);
        predictionWriter.newLine();
      }
    }
  }
}
