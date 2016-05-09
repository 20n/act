package com.act.biointerpretation.step2_desalting;

import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class ChemicalDesalter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ChemicalDesalter.class);

  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_INCHI_INPUT_LIST = "i";
  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class desalts a list of InChIs and outputs the results to a file.  To desalt an entire installer DB, ",
      "use BiointerpretationDriver."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg().required()
        .longOpt("output-prefix")
    );
    add(Option.builder(OPTION_INCHI_INPUT_LIST)
        .argName("inchi list file")
        .desc("A file containing a list of InChIs to desalt")
        .hasArg().required()
        .longOpt("input-inchis")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private Desalter desalter = new Desalter();

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
      HELP_FORMATTER.printHelp(ReactionDesalter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    ChemicalDesalter runner = new ChemicalDesalter();
    String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX);
    if (cl.hasOption(OPTION_INCHI_INPUT_LIST)) {
      File inputFile = new File(cl.getOptionValue(OPTION_INCHI_INPUT_LIST));
      if (!inputFile.exists()) {
        System.err.format("Cannot find input file at %s\n", inputFile.getAbsolutePath());
        HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        System.exit(1);
      }
      runner.exampleChemicalsList(outAnalysis, inputFile);
    }
  }

  public ChemicalDesalter() {

  }

  public void exampleChemicalsList(String outputPrefix, File inputFile)
      throws IOException, LicenseProcessingException, ReactionException {
    desalter.initReactors();
    List<String> inchis = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
      String line;
      // Slurp in the list of InChIs from the input file.
      while ((line = reader.readLine()) != null) {
        inchis.add(line.trim());
      }
    }
    generateAnalysisOfDesaltingSaltyChemicals(inchis, outputPrefix);
  }

  /**
   * This function bins each reaction into modified, unchanged, errors and complex files based on
   * processing them through the desalter module.
   *
   * @param salties      A list of reactions
   * @param outputPrefix The output prefix for the generated files
   */
  private void generateAnalysisOfDesaltingSaltyChemicals(List<String> salties, String outputPrefix) {
    try (
        BufferedWriter substrateModifiedFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_modified.txt")));
        BufferedWriter substrateUnchangedFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_unchanged.txt")));
        BufferedWriter substrateErrorsFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_errors.txt")));
        BufferedWriter substrateComplexFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_complex.txt")))
    ) {
      for (int i = 0; i < salties.size(); i++) {
        String salty = salties.get(i);
        String saltySmile = null;
        try {
          saltySmile = desalter.InchiToSmiles(salty);
        } catch (Exception err) {
          LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", salty,
              err.getMessage()));
          substrateErrorsFileWriter.write("InchiToSmiles1\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        Map<String, Integer> results = null;
        try {
          results = desalter.desaltMolecule(salty);
        } catch (Exception err) {
          LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", salty,
              err.getMessage()));
          substrateErrorsFileWriter.append("cleaned\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        //Not sure results can be size zero or null, but check anyway
        if (results == null) {
          LOGGER.error(String.format("Clean results are null for chemical: %s\n", salty));
          substrateErrorsFileWriter.append("clean results are null:\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }
        if (results.isEmpty()) {
          LOGGER.error(String.format("Clean results are empty for chemical: %s\n", salty));
          substrateErrorsFileWriter.append("clean results are empty:\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        //If cleaning resulted in a single organic product
        if (results.size() == 1) {
          String cleaned = results.keySet().iterator().next();
          String cleanSmile = null;
          try {
            cleanSmile = desalter.InchiToSmiles(cleaned);
          } catch (Exception err) {
            LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", cleaned,
                err.getMessage()));
            substrateErrorsFileWriter.append("InchiToSmiles2\t" + salty);
            substrateErrorsFileWriter.newLine();
          }

          if (!salty.equals(cleaned)) {
            String[] stringElements = {salty, cleaned, saltySmile, cleanSmile};
            substrateModifiedFileWriter.append(StringUtils.join(Arrays.asList(stringElements), "\t"));
            substrateModifiedFileWriter.newLine();
          } else {
            String[] stringElements = {salty, saltySmile};
            substrateUnchangedFileWriter.append(StringUtils.join(Arrays.asList(stringElements), "\t"));
            substrateUnchangedFileWriter.newLine();
          }
        } else {
          //Otherwise there were multiple organic products
          substrateComplexFileWriter.append(">>\t" + salty + "\t" + saltySmile);
          substrateComplexFileWriter.newLine();
          for (String inchi : results.keySet()) {
            substrateComplexFileWriter.append("\t" + inchi + "\t" + desalter.InchiToSmiles(inchi));
            substrateComplexFileWriter.newLine();
          }
        }
      }
    } catch (IOException exception) {
      LOGGER.error(String.format("IOException: %s", exception.getMessage()));
    }
  }
}
