package act.installer.wikipedia;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.Set;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WikipediaChemical {
  private static String XML_DUMP_FILENAME = "/Users/tom/Documents/enwiki-latest-pages-articles1.xml-p000000010p000030302";
  /// private static String XML_DUMP_FILENAME = "/mnt/data-level1/data/enwiki-20160501-pages-articles.xml";

  private static final Logger LOGGER = LogManager.getFormatterLogger(WikipediaChemical.class);
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

  // These patterns allow to identify Wikipedia titles and InChIs.
  private static final Pattern TITLE_PATTERN = Pattern.compile(".*<title>([^<>]+)</title>.*");
  private static final Pattern INCHI_PATTERN =
      Pattern.compile(".*(?i)(InChI[0-9]?\\p{Space}?=\\p{Space}?1S?/[\\p{Space}0-9a-z+\\-\\(\\)/.,\\?;\\*]+).*");

  private String lastTitle;
  private boolean isValidTitle;
  private Integer counter;
  private HashSet<ProcessedWikipediaChemical> processedWikipediaChemicals = new HashSet<>();

  public WikipediaChemical() {}

  public class ProcessedWikipediaChemical {

    @JsonProperty("inchi")
    private String inchi;

    @JsonProperty("wikipedia_title")
    private String wikipediaTitle;

    @JsonProperty("is_standard_inchi")
    private boolean isStandardInchi;

    @JsonProperty("is_chemaxon_valid_inchi")
    private boolean isChemaxonValidInchi;

    public ProcessedWikipediaChemical(
        String inchi, String wikipediaTitle, boolean isStandardInchi, boolean isChemaxonValidInchi) {
      this.inchi = inchi;
      this.wikipediaTitle = wikipediaTitle;
      this.isStandardInchi = isStandardInchi;
      this.isChemaxonValidInchi = isChemaxonValidInchi;
    }
  }

  public static boolean isChemaxonValidInchi(String inchi) {
    try {
      MolImporter.importMol(inchi);
    } catch (MolFormatException e) {
      return false;
    }
    return true;
  }

  public static String formatInchiString(String inchi) {
    // Remove all whitespaces
    String tmpInchi = inchi.replaceAll("\\s+","");

    // Some InChIs start with "InChI1" or "InChI2". We need to remove the suffix ("1", "2") to allow Chemaxon validation
    String formattedInchi = tmpInchi.replaceAll("InChI[0-9]?", "InChI");

    return formattedInchi;
  }

  public static String extractInchiFromLine(String line) {
    Matcher inchiMatcher = INCHI_PATTERN.matcher(line);
    if (inchiMatcher.matches()) {
      return inchiMatcher.group(1);
    }
    return null;
  }

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
          LOGGER.info("Extracted line: " + line);
          LOGGER.info("Matched InChI: " + inchi);
          LOGGER.info("Formatted InChI: " + formattedInchi);
        }

        boolean isStandardInchi = formattedInchi.startsWith("InChI=1S");
        ProcessedWikipediaChemical processedWikipediaChemical = new ProcessedWikipediaChemical(
            formattedInchi, lastTitle, isStandardInchi, isChemaxonValidInchi);
        processedWikipediaChemicals.add(processedWikipediaChemical);
      }
    }
  }

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

  public static void main(final String[] args) throws IOException {

    WikipediaChemical wikipediaChemical = new WikipediaChemical();

    try (BufferedReader br = new BufferedReader(new FileReader(XML_DUMP_FILENAME))) {
      String line;
      while ((line = br.readLine()) != null) {
        wikipediaChemical.processLine(line);
      }

      File file = new File("src/wikipediaChemical3.json");
      mapper.writeValue(file, wikipediaChemical.processedWikipediaChemicals);
    }
  }
}
