package act.installer.wikipedia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;


public class ImportantChemicalsWikipedia {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ImportantChemicalsWikipedia.class);

  public static final String OPTION_WIKIPEDIA_DUMP_FULL_PATH = "i";
  public static final String OPTION_OUTPUT_PATH = "o";
  public static final String OPTION_TSV_OUTPUT = "t";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class parses Wikipedia data dumps to extract important chemicals."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_WIKIPEDIA_DUMP_FULL_PATH)
        .argName("WIKIPEDIA_DUMP_PATH")
        .desc("The full path to the Wikipedia XML dump to parse. It should be located on the NAS " +
            "(/mnt/data-level1/data/enwiki-20160501-pages-articles.xml) but can also be obtained from " +
            "https://dumps.wikimedia.org/enwiki/")
        .hasArg().required()
        .longOpt("wikipedia_dump_path")
        .type(String.class)
    );
    add(Option.builder(OPTION_OUTPUT_PATH)
        .argName("OUTPUT_PATH")
        .desc("The full path to write the output data.")
        .hasArg().required()
        .longOpt("output_path")
        .type(String.class)
    );
    add(Option.builder(OPTION_TSV_OUTPUT)
        .argName("TSV_OUTPUT")
        .desc("Whether the output should be written in JSON format.")
        .longOpt("tsv")
        .type(boolean.class)
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

  private static String DATABASE_TYPE = "WIKIPEDIA";

  private static ObjectMapper mapper = new ObjectMapper();


  // Some Wikipedia pages contains InChI strings but are not about a specific Chemical.
  // A good heuristic to exclude them is to list words that appear in the titles.
  // A title is considered "valid" if it does not include any of these strings.
  private static final String[] EXCLUDE_TITLES_WITH_WORDS_LIST =
      new String[] {"Identifier", "Wikipedia", "InChI", "Template", "testcase"};
  private static final Set<String> EXCLUDE_TITLES_WITH_WORDS = new HashSet<>(
      Arrays.asList(EXCLUDE_TITLES_WITH_WORDS_LIST));

  // Some InChI cause fatal Java errors when trying to validate them through Chemaxon's library. Ignore them.
  private static final String[] EXCLUDE_INCHIS_LIST =
      new String[] {"InChI = 1/C12H10AsCl/c14/h1-10H"};
  private static final Set<String> EXCLUDE_INCHIS = new HashSet<>(
      Arrays.asList(EXCLUDE_INCHIS_LIST));

  private static final String TSV_SEPARATOR = "\t";

  // These patterns allow to identify Wikipedia titles and InChIs.
  private static final Pattern TITLE_PATTERN = Pattern.compile(".*<title>([^<>]+)</title>.*");
  private static final Pattern INCHI_PATTERN =
      Pattern.compile(".*(?i)(InChI[0-9]?\\p{Space}*=\\p{Space}*1S?/[\\p{Space}0-9a-z+\\-\\(\\)/.,\\?;\\*]+).*");

  private String lastTitle;
  private boolean isValidTitle;
  private static HashSet<ImportantChemical> importantChemicalsWikipedia = new HashSet<>();

  public ImportantChemicalsWikipedia() {}

  public class ImportantChemical implements Serializable {

    @JsonProperty("type")
    private String type;

    @JsonProperty("dbid")
    private String dbid;

    @JsonProperty("inchi")
    private String inchi;

    @JsonProperty("metadata")
    private WikipediaMetadata metadata;

    public ImportantChemical(String type, String dbid, String inchi, WikipediaMetadata metadata) {
      this.type = type;
      this.dbid = dbid;
      this.inchi = inchi;
      this.metadata = metadata;
    }

    public String getType() {
      return type;
    }

    public String getDbid() {
      return dbid;
    }

    public String getInchi() {
      return inchi;
    }

    public WikipediaMetadata getMetadata() {
      return metadata;
    }
  }

  public class WikipediaMetadata {

    @JsonProperty("article")
    private String article;

    @JsonProperty("std_inchi")
    private boolean stdInChI;

    public WikipediaMetadata(String article, boolean stdInChI) {
      this.article = article;
      this.stdInChI = stdInChI;
    }
  }

  /**
   * This function extracts an InChI string from a candidate line.
   * @param line a String from the raw XML data source file
   * @return a String representing the molecule's InChI
   */
  public static String extractInchiFromLine(String line) {
    Matcher inchiMatcher = INCHI_PATTERN.matcher(line);
    if (inchiMatcher.matches()) {
      return inchiMatcher.group(1);
    }
    return null;
  }

  /**
   * This function formats a matched InChI to make it canonical.
   * @param inchi a String representing the molecule's InChI
   * @return a formatted string representing the corresponding canonical InChI
   */
  public static String formatInchiString(String inchi) {
    // Remove all whitespaces
    String tmpInchi = inchi.replaceAll("\\s+","");

    // Some InChIs start with "InChI1" or "InChI2". We need to remove the suffix ("1", "2") to allow Chemaxon validation
    String formattedInchi = tmpInchi.replaceAll("InChI[0-9]?", "InChI");

    return formattedInchi;
  }

  /**
   * This function tries to import a molecule in Chemaxon and returns a boolean indicating whether or not it succeeded.
   * @param inchi a string representing the molecule's canonical InChI
   * @return a boolean indicating success or failure to import the molecule in Chemaxon
   */
  public static boolean isChemaxonValidInchi(String inchi) {
    try {
      MolImporter.importMol(inchi);
    } catch (MolFormatException e) {
      return false;
    }
    return true;
  }


  /**
   * This function processes a line found to contain a candidate InChI and adds potential candidate molecules to the
   * important chemicals set.
   * @param line a String from the raw XML data source file
   */
  public void processInchiLine(String line) throws IOException {

    String inchi;

    // Extract a potential Inchi from the line. Check if null.
    if ((inchi = extractInchiFromLine(line)) != null) {
      if (!EXCLUDE_INCHIS.contains(inchi)) {

        // InChI formatting
        String formattedInchi = formatInchiString(inchi);
        LOGGER.trace(formattedInchi);

        // InChI validation through Chemaxon library
        boolean isChemaxonValidInchi = isChemaxonValidInchi(formattedInchi);
        if (!isChemaxonValidInchi) {
          LOGGER.info("~~~~~~~~~~~~~~~~~~~~~~~~~");
          LOGGER.info("Chemaxon validation failed");
          LOGGER.info("Last title      : " + lastTitle);
          LOGGER.info("Extracted line  : " + line);
          LOGGER.info("Matched InChI   : " + inchi);
          LOGGER.info("Formatted InChI : " + formattedInchi);
        } else {
          boolean isStandardInchi = formattedInchi.startsWith("InChI=1S");
          String wikipediaURL = "https://en.wikipedia.org/wiki/" +
              lastTitle.replace(" ", "_");

          WikipediaMetadata metadata = new WikipediaMetadata(lastTitle, isStandardInchi);

          ImportantChemical importantChemical = new ImportantChemical(
              DATABASE_TYPE , wikipediaURL, formattedInchi, metadata);
          importantChemicalsWikipedia.add(importantChemical);
        }
      }
    }
  }

  /**
   * This function processes a line from the data source to find titles or InChIs
   * @param line a String from the raw XML data source file
   */
  public void processLine(String line) throws IOException {
    Matcher titleMatcher = TITLE_PATTERN.matcher(line);

    if (titleMatcher.matches()) {
      lastTitle = titleMatcher.group(1);
      isValidTitle = true;
      for (String excludedWord : EXCLUDE_TITLES_WITH_WORDS) {
        if (lastTitle.contains(excludedWord)) {
          isValidTitle = false;
        }
      }
    } else {
      if (isValidTitle) {
        if (line.contains("InChI") && !line.contains("InChIKey") && !line.contains("InChI_Ref")) {
          processInchiLine(line);
        }
      }
    }
  }

  /**
   * This function writes the important chemicals set to a TSV file.
   * @param outputPath a String indicating where the file should be written (including its name)
   */
  public static void writeToTSV(String outputPath) throws IOException {

    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(outputPath), "UTF-8"));
    for (ImportantChemical wikipediaChemical : importantChemicalsWikipedia) {
      StringBuffer oneLine = new StringBuffer();
      oneLine.append(wikipediaChemical.getType());
      oneLine.append(TSV_SEPARATOR);
      oneLine.append(wikipediaChemical.getDbid());
      oneLine.append(TSV_SEPARATOR);
      oneLine.append(wikipediaChemical.getInchi());
      oneLine.append(TSV_SEPARATOR);
      String metadataAsString = mapper.writeValueAsString(wikipediaChemical.getMetadata());
      oneLine.append(metadataAsString);
      bw.write(oneLine.toString());
      bw.newLine();
    }
    bw.flush();
    bw.close();
  }

  /**
   * This function writes the important chemicals set to a JSON file.
   * @param outputPath a String indicating where the file should be written (including its name)
   */
  public static void writeToJSON(String outputPath) throws IOException {
    File file = new File(outputPath);
    mapper.writeValue(file, importantChemicalsWikipedia);
  }


  public static void main(final String[] args) throws IOException {

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
      HELP_FORMATTER.printHelp(ImportantChemicalsWikipedia.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ImportantChemicalsWikipedia.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String inputPath = cl.getOptionValue(OPTION_WIKIPEDIA_DUMP_FULL_PATH, "1000");
    String outputPath = cl.getOptionValue(OPTION_OUTPUT_PATH, "1000");
    Boolean outputTSV = cl.hasOption(OPTION_TSV_OUTPUT);

    ImportantChemicalsWikipedia wikipediaChemical = new ImportantChemicalsWikipedia();

    try (BufferedReader br = new BufferedReader(new FileReader(inputPath))) {
      String line;
      while ((line = br.readLine()) != null) {
        wikipediaChemical.processLine(line);
      }
    }
    catch (IOException e) {
      LOGGER.error(e);
    }

    if (outputTSV) {
      writeToTSV(outputPath);
    } else {
      writeToJSON(outputPath);
    }
  }
}
