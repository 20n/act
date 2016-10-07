package com.act.lcms;

import com.act.utils.CLIUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

// Usage sbt "runMain com.act.lcms.SmallFormulaeCorpusBuilder"


public class SmallFormulaeCorpusBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SmallFormulaeCorpusBuilder.class);

  private static final String DEFAULT_INPUT_FILE = "/mnt/shared-data/Saurabh/PR466/small-formulae-enumeration.tsv";

  private static final String OPTION_INPUT_FILE = "i";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILE)
        .argName("input file")
        .desc("Input file containing mass to formula enumeration")
        .hasArg()
        .longOpt("input")
    );
  }};
  private static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "Test whether we can fill all the small formulae in a mapp"
  }, "");

  NavigableMap<Float, String> massToFormulaMap;

  public NavigableMap<Float, String> getMassToFormulaMap() {
    return massToFormulaMap;
  }

  public SmallFormulaeCorpusBuilder() {
    massToFormulaMap = new TreeMap<>();
  }

  public void populateMapFromFile(File inputFile) throws IOException {

    Float mass;
    String formula;
    String line;
    String[] splitLine;

    try (BufferedReader formulaeReader = getFormulaeReader(inputFile)) {

      formulaeReader.readLine();
      int i = 0;
      while (formulaeReader.ready()) {

        if (i % 1000000 == 0) {
          LOGGER.debug("Formulae processed so far: %d", i);
          LOGGER.debug("Heap space used: %d out of %d max",
              Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory());
        }

        line = formulaeReader.readLine();
        splitLine = line.split("\t");
        assert splitLine.length == 2;

        mass = new Float(splitLine[0]);
        formula = splitLine[1];

        massToFormulaMap.put(mass, formula);
        i++;
      }
    }

  }

  private BufferedReader getFormulaeReader(File inchiFile) throws FileNotFoundException {
    FileInputStream formulaeInputStream = new FileInputStream(inchiFile);
    return new BufferedReader(new InputStreamReader(formulaeInputStream));
  }


  public static void main(String[] args) throws Exception {

    CLIUtil cliUtil = new CLIUtil(SmallFormulaeCorpusBuilder.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    String inputFile = cl.getOptionValue(OPTION_INPUT_FILE, DEFAULT_INPUT_FILE);

    SmallFormulaeCorpusBuilder builder = new SmallFormulaeCorpusBuilder();
    builder.populateMapFromFile(new File(inputFile));

    NavigableMap<Float, String> massToFormulaMap = builder.getMassToFormulaMap();
    LOGGER.info("Constructed map of size %d. Printing its first elements...", massToFormulaMap.size());

    // Assert some values are present
    assert massToFormulaMap.get(172.868180F).equals("CHS5");
    assert massToFormulaMap.get(972.169955F).equals("CHS30");
    assert massToFormulaMap.get(976.826451F).equals("C30H100N30O4S");

    // Assert we can find acetaminophen
    // 151.163
    LOGGER.info("Ceiling entry for key 151.162: (%f,%s)", massToFormulaMap.ceilingEntry(151.162F).getKey(), massToFormulaMap.ceilingEntry(151.16F).getValue());
    LOGGER.info("Floor entry for key 151.173: (%f,%s)", massToFormulaMap.floorEntry(151.163F).getKey(), massToFormulaMap.ceilingEntry(151.17F).getValue());
  }
}
