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

public class IonAnalysisParser {

  public static final String OPTION_INPUT_FILE = "i";

   public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
      add(Option.builder(OPTION_INPUT_FILE)
               .argName("input file")
               .desc("The directory where LCMS analysis results live")
               .hasArg().required()
               .longOpt("input-file")
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

    IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
    model.loadCorpusFromFile(new File(cl.getOptionValue(OPTION_INPUT_FILE)));

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File("output_inchis.txt")))) {
      for (String inchi : model.getAllMoleculeHits()) {
        predictionWriter.append(inchi);
        predictionWriter.newLine();
      }
    }
  }
}
