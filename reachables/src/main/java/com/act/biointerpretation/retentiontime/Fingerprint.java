package com.act.biointerpretation.retentiontime;

import chemaxon.descriptors.CFParameters;
import chemaxon.descriptors.GenerateMD;
import chemaxon.descriptors.MDParameters;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Fingerprint {

  public static final String OPTION_INPUT_INCHIS = "i";
  public static final String OPTION_OUTPUT_FINGERPRINT = "o";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_INPUT_INCHIS)
          .argName("input inchis")
          .desc("input inchis")
          .hasArg().required()
          .longOpt("input inchis")
      );
      add(Option.builder(OPTION_OUTPUT_FINGERPRINT)
          .argName("output fingerprint")
          .desc("output fingerprint")
          .hasArg().required()
          .longOpt("output fingerprint")
      );
    }
  };

  public static void generate(String inputFile, String outputFile) throws Exception {
    GenerateMD generator = new GenerateMD(1);
    MDParameters cfpConfig = new CFParameters( new File("/mnt/shared-data/Vijay/ret_time_prediction/config/cfp.xml"));
    generator.setInput(inputFile);
    generator.setDescriptor(0, outputFile, "CF", cfpConfig, "");
    generator.setBinaryOutput(true);
    generator.init();
    generator.run();
    generator.close();
  }

  public static void compare(String input) throws Exception {
    BufferedReader reader = new BufferedReader(new FileReader(input));

    String line = null;
    List<List<String>> binaryValues = new ArrayList<>();
    while ((line = reader.readLine()) != null) {
      binaryValues.add(Arrays.asList(line.split("|")));
    }

    // Compare
    int differenceInBits = 0;
    int total = 0;
    for (int i = 0; i < binaryValues.get(0).size(); i++) {
      String firstBinary = binaryValues.get(0).get(i);
      String secondBinary = binaryValues.get(1).get(i);
      for (int j = 0; j < firstBinary.length(); j++) {
        total++;
        if (firstBinary.charAt(j) != secondBinary.charAt(j)) {
          differenceInBits++;
        }
      }
    }

    System.out.println(differenceInBits);
    System.out.println(total);
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
      System.exit(1);
    }

    generate(cl.getOptionValue(OPTION_INPUT_INCHIS), cl.getOptionValue(OPTION_OUTPUT_FINGERPRINT));
    compare(cl.getOptionValue(OPTION_OUTPUT_FINGERPRINT));
  }
}
