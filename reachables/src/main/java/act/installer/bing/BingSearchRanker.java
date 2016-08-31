package act.installer.bing;

import act.server.MongoDB;
import com.act.utils.TSVWriter;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This module provide a command line interface to update and export Bing Search results and ranks from the Installer
 * database. It supports two types of input: raw list of InChI and TSV file with an InChI header.
 * Usage (raw input):
 *       sbt 'runMain act.installer.bing.BingSearchRanker
 *                -i /mnt/shared-data/Thomas/bing_ranker/l2chemicalsProductFiltered.txt
 *                -o /mnt/shared-data/Thomas/bing_ranker/l2chemicalsProductFiltered_BingSearchRanker_results.tsv'
 * Usage (TSV input):
 *       sbt 'runMain act.installer.bing.BingSearchRanker
 *                -i /mnt/shared-data/Thomas/bing_ranker/benzene_search_results_wikipedia_20160617T1723.txt.hits
 *                -o /mnt/shared-data/Thomas/bing_ranker/benzene_search_results_wikipedia_BingSearchRanker_results.tsv'
 *                -t
 * Usage (TSV input & all extra options, including force update):
 *       sbt 'runMain act.installer.bing.BingSearchRanker
 *                -i /mnt/shared-data/Thomas/bing_ranker/benzene_search_results_wikipedia_20160617T1723.txt.hits
 *                -o /mnt/shared-data/Thomas/bing_ranker/benzene_search_results_wikipedia_BingSearchRanker_results.tsv'
 *                -t -c -w -u -f
 */

public class BingSearchRanker {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearchRanker.class);
  private static final String EMPTY_STRING = "";

  // Default configuration for the Installer database
  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 27017;
  public static final String INSTALLER_DATABASE = "actv01";

  // Configuration for usage explorer UI
  public static final String HOST_USAGE_EXPLORER = "usage-explorer";
  public static final int PORT_USAGE_EXPLORER = 8080;

  // Define options for CLI
  public static final String OPTION_INPUT_FILEPATH = "i";
  public static final String OPTION_OUTPUT_FILEPATH = "o";
  public static final String OPTION_TSV_INPUT = "t";
  public static final String OPTION_FORCE_UPDATE = "f";
  public static final String OPTION_INCLUDE_CHEBI_APPLICATIONS = "c";
  public static final String OPTION_INCLUDE_WIKIPEDIA_URL = "w";
  public static final String OPTION_INCLUDE_USAGE_EXPLORER_URL = "u";

  // Other static variables
  public static final Integer DEFAULT_COUNT = 0;
  private static final Integer INCHI_CHUNK_SIZE = 10000;

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class adds Bing Search results for a list of molecules in the Installer (actv01) database",
      "and exports the results in a TSV format for easy import in Google spreadsheets.",
      "It supports two different input formats: raw list of InChI strings and TSV file with an InChI column.",
      "Default input format (with only options -i and -o) is raw list of InChI."
  }, " ");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILEPATH)
        .argName("INPUT_FILEPATH")
        .desc("The full path to the input file")
        .hasArg()
        .required()
        .longOpt("input_filepath")
        .type(String.class)
    );
    add(Option.builder(OPTION_OUTPUT_FILEPATH)
        .argName("OUTPUT_PATH")
        .desc("The full path where to write the output.")
        .hasArg()
        .required()
        .longOpt("output_path")
        .type(String.class)
    );
    add(Option.builder(OPTION_TSV_INPUT)
        .argName("TSV_INPUT")
        .desc("Whether the input is a TSV file with an InChI column.")
        .longOpt("tsv")
        .type(boolean.class)
    );
    add(Option.builder(OPTION_FORCE_UPDATE)
        .argName("FORCE_UPDATE")
        .desc("Whether exisitng BING cross-references in the Installer database should be overwritten.")
        .longOpt("force_update")
        .type(boolean.class)
    );
    add(Option.builder(OPTION_INCLUDE_CHEBI_APPLICATIONS)
        .argName("INCLUDE_CHEBI_APPLICATIONS")
        .desc("Whether to include (when applicable) ChEBI applications in the output file.")
        .longOpt("include_chebi")
        .type(boolean.class)
    );
    add(Option.builder(OPTION_INCLUDE_WIKIPEDIA_URL)
        .argName("INCLUDE_WIKIPEDIA_URL")
        .desc("Whether to include (when applicable) the Wikipedia URL in the output file.")
        .longOpt("include_wikipedia")
        .type(boolean.class)
    );
    add(Option.builder(OPTION_INCLUDE_USAGE_EXPLORER_URL)
        .argName("INCLUDE_USAGE_EXPLORER_URL")
        .desc("Whether to include (when applicable) the usage explorer UI URL in the output file.")
        .longOpt("include_usage")
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

  public enum BingRankerHeaderFields {
    INCHI,
    BEST_NAME,
    TOTAL_COUNT_SEARCH_RESULTS,
    ALL_NAMES,
    WIKIPEDIA_URL,
    CHEBI_MAIN_APPLICATIONS,
    CHEBI_DIRECT_APPLICATIONS,
    USAGE_EXPLORER_URL
  }

  public enum ConditionalReachabilityHeaderFields {
    DEPTH,
    ROOT_MOLECULE_BEST_NAME,
    ROOT_INCHI,
    TOTAL_COUNT_SEARCH_RESULTS_ROOT
  }

  // Instance variables
  private MongoDB mongoDB;
  private BingSearcher bingSearcher;
  private Boolean includeChebiApplications;
  private Boolean includeWikipediaUrl;
  private Boolean includeUsageExplorerUrl;
  private Boolean forceUpdate;

  public BingSearchRanker() {
    mongoDB = new MongoDB(DEFAULT_HOST, DEFAULT_PORT, INSTALLER_DATABASE);
    bingSearcher = new BingSearcher();
    includeChebiApplications = false;
    includeWikipediaUrl = false;
    includeUsageExplorerUrl = false;
    forceUpdate = false;
  }

  public BingSearchRanker(Boolean includeChebiApplications,
                          Boolean includeWikipediaUrl,
                          Boolean includeUsageExplorerUrl,
                          Boolean forceUpdate) {
    this.mongoDB = new MongoDB(DEFAULT_HOST, DEFAULT_PORT, INSTALLER_DATABASE);
    this.bingSearcher = new BingSearcher();
    this.includeChebiApplications = includeChebiApplications;
    this.includeWikipediaUrl = includeWikipediaUrl;
    this.includeUsageExplorerUrl = includeUsageExplorerUrl;
    this.forceUpdate = forceUpdate;
  }

  public static void main(final String[] args) throws Exception {

    // Parse the command line options
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
      HELP_FORMATTER.printHelp(BingSearchRanker.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(BingSearchRanker.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String inputPath = cl.getOptionValue(OPTION_INPUT_FILEPATH);
    String outputPath = cl.getOptionValue(OPTION_OUTPUT_FILEPATH);
    Boolean isTSVInput = cl.hasOption(OPTION_TSV_INPUT);

    // Read the molecule corpus
    LOGGER.info("Reading the input molecule corpus");
    MoleculeCorpus moleculeCorpus = new MoleculeCorpus();
    if (isTSVInput) {
      LOGGER.info("Input format is TSV");
      moleculeCorpus.buildCorpusFromTSVFile(inputPath);
    } else {
      LOGGER.info("Input format is raw InChIs");
      moleculeCorpus.buildCorpusFromRawInchis(inputPath);
    }

    // Get the inchi set
    Set<String> inchis = moleculeCorpus.getMolecules();
    LOGGER.info("Found %d molecules in the input corpus", inchis.size());

    // Update the Bing Search results in the Installer database
    BingSearchRanker bingSearchRanker = new BingSearchRanker(
        cl.hasOption(OPTION_INCLUDE_CHEBI_APPLICATIONS),
        cl.hasOption(OPTION_INCLUDE_WIKIPEDIA_URL),
        cl.hasOption(OPTION_INCLUDE_USAGE_EXPLORER_URL),
        cl.hasOption(OPTION_FORCE_UPDATE));
    LOGGER.info("Updating the Bing Search results in the Installer database");
    bingSearchRanker.addBingSearchResults(inchis);
    LOGGER.info("Done updating the Bing Search results");

    // Write the results in a TSV file
    LOGGER.info("Writing results to output file");
    bingSearchRanker.writeBingSearchRanksAsTSV(inchis, outputPath);
    LOGGER.info("Bing Search ranker is done. \"I'm tired, boss.\"");
  }

  /**
   * This function constructs the Usage Explorer URL for TSV export
   * @param inchi the InChI string representation of the molecule
   * @return a String with the link to access the Usage Explorer app.
   */
  public String getUsageExplorerURLStringFromInchi(String inchi) {
    try {
      URI uri = new URIBuilder()
          .setScheme("http")
          .setHost(HOST_USAGE_EXPLORER)
          .setPort(PORT_USAGE_EXPLORER)
          .setParameter("inchi", inchi)
          .build();
      return uri.toString();
    } catch (URISyntaxException e) {
      LOGGER.error("An error occurred when trying to build the Usage Explorer URI", e);
    }
    return null;
  }

  /**
   * This function add the Bing Search results to the installer database from a set of InChI strings
   * @param inchis set of InChI string representations
   */
  public void addBingSearchResults(Set<String> inchis) throws IOException {
    bingSearcher.addBingSearchResultsForInchiSet(mongoDB, inchis, forceUpdate);
  }

  /**
   * Add InChI, names and usage information related headers to a list of header fields.
   * @param headerFields List of headers to be populated
   */
  private void addChemicalHeaders(List<String> headerFields) {
    headerFields.add(BingRankerHeaderFields.INCHI.name());
    headerFields.add(BingRankerHeaderFields.BEST_NAME.name());
    headerFields.add(BingRankerHeaderFields.TOTAL_COUNT_SEARCH_RESULTS.name());
    headerFields.add(BingRankerHeaderFields.ALL_NAMES.name());
    if (includeChebiApplications) {
      headerFields.add(BingRankerHeaderFields.CHEBI_MAIN_APPLICATIONS.name());
      headerFields.add(BingRankerHeaderFields.CHEBI_DIRECT_APPLICATIONS.name());
    }
    if (includeWikipediaUrl) {
      headerFields.add(BingRankerHeaderFields.WIKIPEDIA_URL.name());
    }
    if (includeUsageExplorerUrl) {
      headerFields.add(BingRankerHeaderFields.USAGE_EXPLORER_URL.name());
    }
  }

  /**
   * Updates a TSV row (actually a Map from header to value) with InChI, names and usage information.
   * @param o BasicDBObject containing InChI, and xrefs.{BING, CHEBI, WIKIPEDIA} info
   * @param row TSV row (map from TSV header to value) to be updated
   */
  private void updateRowWithChemicalInformation(BasicDBObject o, Map<String, String> row) {
    String inchi = o.get("InChI").toString();
    row.put(BingRankerHeaderFields.INCHI.name(), inchi);
    BasicDBObject xref = (BasicDBObject) o.get("xref");
    BasicDBObject bing = (BasicDBObject) xref.get("BING");
    BasicDBObject bingMetadata = (BasicDBObject) bing.get("metadata");
    row.put(BingRankerHeaderFields.BEST_NAME.name(), bingMetadata.get("best_name").toString());
    row.put(BingRankerHeaderFields.TOTAL_COUNT_SEARCH_RESULTS.name(),
        bingMetadata.get("total_count_search_results").toString());
    NamesOfMolecule namesOfMolecule = mongoDB.getNamesFromBasicDBObject(o);
    Set<String> names = namesOfMolecule.getAllNames();
    row.put(BingRankerHeaderFields.ALL_NAMES.name(), names.toString());
    if (includeChebiApplications) {
      BasicDBObject chebi = (BasicDBObject) xref.get("CHEBI");
      if (chebi != null) {
        BasicDBObject chebiMetadata = (BasicDBObject) chebi.get("metadata");
        BasicDBObject chebiApplications = (BasicDBObject) chebiMetadata.get("applications");
        if (chebiApplications != null) {
          row.put(BingRankerHeaderFields.CHEBI_MAIN_APPLICATIONS.name(),
              chebiApplications.get("main_applications").toString());
          row.put(BingRankerHeaderFields.CHEBI_DIRECT_APPLICATIONS.name(),
              chebiApplications.get("direct_applications").toString());
        } else {
          LOGGER.debug("ChEBI cross-reference found, but no ChEBI applications for %s", inchi);
          row.put(BingRankerHeaderFields.CHEBI_MAIN_APPLICATIONS.name(), EMPTY_STRING);
          row.put(BingRankerHeaderFields.CHEBI_DIRECT_APPLICATIONS.name(), EMPTY_STRING);
        }
      } else {
        LOGGER.debug("No ChEBI cross-reference found for %s", inchi);
      }
    }
    if (includeWikipediaUrl) {
      BasicDBObject wikipedia = (BasicDBObject) xref.get("WIKIPEDIA");
      if (wikipedia != null) {
        row.put(BingRankerHeaderFields.WIKIPEDIA_URL.name(), wikipedia.get("dbid").toString());
      } else {
        LOGGER.debug("No Wikipedia cross-reference found for %s", inchi);
        row.put(BingRankerHeaderFields.WIKIPEDIA_URL.name(), EMPTY_STRING);
      }
    }
    if (includeUsageExplorerUrl) {
     row.put(BingRankerHeaderFields.USAGE_EXPLORER_URL.name(), getUsageExplorerURLStringFromInchi(inchi));
    }
  }

  /**
   * Divide a large set of Strings into a list of smaller sets (chunks) of size `chunkSize`
   * @param inchis set of String (possibly representing InChIs)
   * @param chunkSize (Integer) the size of resulting chunks
   * @return inchiChunks: a list of "chunks", smaller sets of strings
   */
  private List<Set<String>> getInchiChunks(Set<String> inchis, Integer chunkSize) {
    List<Set<String>> inchiChunks = new ArrayList<>();
    Set<String> inchiChunk = new HashSet<>();
    for (String inchi: inchis) {
      inchiChunk.add(inchi);
      if (inchiChunk.size() == chunkSize) {
        inchiChunks.add(inchiChunk);
        inchiChunk = new HashSet<>();
      }
    }
    if (inchiChunk.size() > 0) {
      inchiChunks.add(inchiChunk);
    }
    return inchiChunks;
  }

  /**
   * This function writes the Bing Search ranks for a chunk of inchis in a TSV file, append only option.
   * @param inchis (Set<String>) set of InChI string representations
   * @param outputPath (String) path indicating the output file
   * @param appendOutput (Boolean) whether to append the results to the output file
   * @throws IOException
   */
  private void writeBingSearchRanksAsTSVForInchiChunk(Set<String> inchis, String outputPath, Boolean appendOutput)
      throws IOException {

    // Define headers
    List<String> bingRankerHeaderFields = new ArrayList<>();
    addChemicalHeaders(bingRankerHeaderFields);

    // Open TSV writer
    try(TSVWriter<String, String> tsvWriter = new TSVWriter<>(bingRankerHeaderFields)) {
      tsvWriter.open(new File(outputPath), appendOutput);

      int counter = 0;
      DBCursor cursor = mongoDB.fetchNamesAndUsageForInchis(inchis);

      // Iterate through the target chemicals
      while (cursor.hasNext()) {
        counter++;
        BasicDBObject o = (BasicDBObject) cursor.next();
        Map<String, String> row = new HashMap<>();
        updateRowWithChemicalInformation(o, row);
        tsvWriter.append(row);
        tsvWriter.flush();
      }
      LOGGER.info("Wrote %d Bing Search results to %s", counter, outputPath);
    }
  }

  /**
   * This function writes the Bing Search ranks for a specific set of inchis in a TSV file.
   * @param inchis set of InChI string representations
   * @param outputPath path indicating the output file
   * @throws IOException
   */
  public void writeBingSearchRanksAsTSV(Set<String> inchis, String outputPath) throws IOException {

    List<Set<String>> inchiChunks = getInchiChunks(inchis, INCHI_CHUNK_SIZE);
    LOGGER.info("%d chunks of maximum size %d were found!", inchiChunks.size(), INCHI_CHUNK_SIZE);
    if (inchiChunks.size() == 0) {
      LOGGER.info("No chunks found. Exiting!");
      System.exit(1);
    }
    writeBingSearchRanksAsTSVForInchiChunk(inchiChunks.get(0), outputPath, false);
    for (int chunkIndex = 1; chunkIndex < inchiChunks.size(); chunkIndex++) {
      writeBingSearchRanksAsTSVForInchiChunk(inchiChunks.get(chunkIndex), outputPath, true);
    }
  }

  /**
   * This function is used to write out the conditional reachability results with data on target chemical, root chemical,
   * depth of steps from root to target chemical, the bing search results, all the other names associated with the target
   * and inchi of the target in a tsv file. This function is not scalable since it has to have an in-memory representation
   * of the target and root molecule's bing results to input the data into the TSV file.
   * @param descendantInchiToRootInchi mapping of chemical to its root chemical in the conditional reachability tree
   * @param depthOfPathFromRootToMolecule Since a chemical can be associated with only one root, there is a unique mapping between
   *                        the chemical and it's depth from the root. This structure holds that information.
   * @param outputPath The output path of the tsv file.
   * @throws IOException
   */
  public void writeBingSearchRanksAsTSVUsingConditionalReachabilityFormat(
      Set<String> inchisToProcess,
      Map<String, String> descendantInchiToRootInchi,
      Map<String, Integer> depthOfPathFromRootToMolecule,
      String outputPath) throws IOException {

    // Define headers
    List<String> bingRankerHeaderFields = new ArrayList<>();
    addChemicalHeaders(bingRankerHeaderFields);
    bingRankerHeaderFields.add(ConditionalReachabilityHeaderFields.DEPTH.name());
    bingRankerHeaderFields.add(ConditionalReachabilityHeaderFields.ROOT_MOLECULE_BEST_NAME.name());
    bingRankerHeaderFields.add(ConditionalReachabilityHeaderFields.TOTAL_COUNT_SEARCH_RESULTS_ROOT.name());
    bingRankerHeaderFields.add(ConditionalReachabilityHeaderFields.ROOT_INCHI.name());

    LOGGER.info("The total number of inchis are: %d", inchisToProcess.size());

    LOGGER.info("Creating mappings between inchi and it's DB object");
    DBCursor cursor = mongoDB.fetchNamesAndUsageForInchis(inchisToProcess);

    // TODO: We have to do an in-memory calculation of all the inchis since we need to pair up the descendant and root
    // db objects. This can take up a lot of memory.
    Map<String, BasicDBObject> inchiToDBObject = new HashMap<>();

    int cursorCounter = 0;
    while (cursor.hasNext()) {
      cursorCounter++;
      BasicDBObject o = (BasicDBObject) cursor.next();
      String inchi = o.get("InChI").toString();

      if (inchi == null) {
        LOGGER.error("Inchi could not be parsed.");
        continue;
      }

      inchiToDBObject.put(inchi, o);
    }

    LOGGER.info("The total number of inchis found in the db is: %d", cursorCounter);

    LOGGER.info("Going to write to TSV file.");
    try (TSVWriter<String, String> tsvWriter = new TSVWriter<>(bingRankerHeaderFields)) {
      tsvWriter.open(new File(outputPath));

      int counter = 0;

      for (String descendantInchi : descendantInchiToRootInchi.keySet()) {
        // Add all the descendant field results
        BasicDBObject descendentDBObject = inchiToDBObject.get(descendantInchi);
        if (descendentDBObject == null) {
          LOGGER.info("Could not find info on inchi %s", descendantInchi);
          continue;
        }

        // Add all descendant molecule fields
        Map<String, String> row = new HashMap<>();
        updateRowWithChemicalInformation(descendentDBObject, row);

        // Add all the root molecule fields
        String rootInchi = descendantInchiToRootInchi.get(descendantInchi);
        row.put(ConditionalReachabilityHeaderFields.ROOT_INCHI.name(), rootInchi);
        BasicDBObject rootDBObject = inchiToDBObject.get(rootInchi);
        if (rootDBObject != null) {
          BasicDBObject rootXref = (BasicDBObject) rootDBObject.get("xref");
          BasicDBObject rootBing = (BasicDBObject) rootXref.get("BING");
          BasicDBObject rootMetadata = (BasicDBObject) rootBing.get("metadata");

          String bestNameForRootMolecule = rootMetadata.get("best_name").toString();
          row.put(ConditionalReachabilityHeaderFields.ROOT_MOLECULE_BEST_NAME.name(),
              bestNameForRootMolecule.equals("") ? rootInchi : bestNameForRootMolecule);

          row.put(ConditionalReachabilityHeaderFields.TOTAL_COUNT_SEARCH_RESULTS_ROOT.name(),
              rootMetadata.get("total_count_search_results").toString());
        } else {
          row.put(ConditionalReachabilityHeaderFields.ROOT_MOLECULE_BEST_NAME.name(), rootInchi);
          row.put(ConditionalReachabilityHeaderFields.TOTAL_COUNT_SEARCH_RESULTS_ROOT.name(), DEFAULT_COUNT.toString());
        }
        row.put(ConditionalReachabilityHeaderFields.DEPTH.name(),
            depthOfPathFromRootToMolecule.get(descendantInchi).toString());

        tsvWriter.append(row);
        tsvWriter.flush();
        counter++;
      }

      LOGGER.info("Wrote %d rows to %s", counter, outputPath);
    }
  }
}
