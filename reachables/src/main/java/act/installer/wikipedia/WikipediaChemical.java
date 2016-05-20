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

public class WikipediaChemical {
  // private static String XML_DUMP_FILENAME = "/Users/tom/Documents/enwiki-latest-pages-articles1.xml-p000000010p000030302";
  private static String XML_DUMP_FILENAME = "/mnt/data-level1/data/enwiki-20160501-pages-articles.xml";

  private static ObjectMapper mapper = new ObjectMapper();


  private static final String[] EXCLUDE_TITLES_WITH_WORDS_LIST =
      new String[] {"Identifier", "Wikipedia", "InChI"};
  private static final Set<String> EXCLUDE_TITLES_WITH_WORDS = new HashSet<>(
      Arrays.asList(EXCLUDE_TITLES_WITH_WORDS_LIST));


  private static final Pattern TITLE_PATTERN = Pattern.compile(".*<title>([^<>]+)</title>.*");
  private static final Pattern INCHI_PATTERN =
      Pattern.compile(".*(?i)([Std]?InChI[0-9]?=1S?/[0-9A-Za-z+\\-\\(\\)/.,\\?;\\*]+).*");

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
    System.out.println(inchi);
    try {
      MolImporter.importMol(inchi);
    } catch (MolFormatException e) {
      return false;
    }
    return true;
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
        if (line.contains("InChI")) {
          System.out.println("Detected InChI");
          Matcher inchiMatcher = INCHI_PATTERN.matcher(line);
          if (inchiMatcher.matches()) {
            counter++;
            String inchi = inchiMatcher.group(1);
            boolean isChemaxonValidInchi = isChemaxonValidInchi(inchi);
            boolean isStandardInchi = inchi.startsWith("InChI=1S");
            processedWikipediaChemicals.add(
                new ProcessedWikipediaChemical(inchi, lastTitle, isStandardInchi, isChemaxonValidInchi));
            System.out.println("# " + counter + " #");

          }
        }
      }
    }
  }

  public static void main(final String[] args) throws IOException {
    WikipediaChemical wikipediaChemical = new WikipediaChemical();
    wikipediaChemical.counter = 0;

    try (BufferedReader br = new BufferedReader(new FileReader(XML_DUMP_FILENAME))) {
      String line;
      while ((line = br.readLine()) != null) {
        wikipediaChemical.processLine(line);
      }

      File file = new File("src/wikipediaChemical.json");
      mapper.writeValue(file, wikipediaChemical.processedWikipediaChemicals);
    }
  }
}
