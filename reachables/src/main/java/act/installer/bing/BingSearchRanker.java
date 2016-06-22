package act.installer.bing;

import act.installer.brenda.BrendaChebiOntology;
import act.server.MongoDB;
import com.act.utils.TSVWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This module provide a command line interface to update and export Bing Search results and ranks from the Installer
 * database. It supports two types of input: raw list of InChI and TSV file with an InchI header.
 */

public class BingSearchRanker {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearchRanker.class);

  // Default configuration for the Installer database
  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 27017;
  public static final String INSTALLER_DATABASE = "actv01";

  // Define options for CLI
  public static final String OPTION_INPUT_FILEPATH = "i";
  public static final String OPTION_OUTPUT_FILEPATH = "o";
  public static final String OPTION_TSV_INPUT = "t";
  public static final String OPTION_TSV_INPUT_HEADER_NAME = "n";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class adds Bing Search results for a list of molecules in the Installer (actv01) database",
      "and exports the results in a TSV format for easy import in Google spreadsheets."
  }, " ");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILEPATH)
        .argName("INPUT_FILEPATH")
        .desc("The full path to the input file")
        .hasArg().required()
        .longOpt("input_filepath")
        .type(String.class)
    );
    add(Option.builder(OPTION_OUTPUT_FILEPATH)
        .argName("OUTPUT_PATH")
        .desc("The full path where to write the output.")
        .hasArg().required()
        .longOpt("output_path")
        .type(String.class)
    );
    add(Option.builder(OPTION_TSV_INPUT)
        .argName("TSV_INPUT")
        .desc("Whether the input is a TSV file with an InChI column.")
        .longOpt("tsv")
        .type(boolean.class)
    );
    add(Option.builder(OPTION_TSV_INPUT_HEADER_NAME)
        .argName("TSV_INPUT_HEADER_NAME")
        .desc("Header name in case of TSV input.")
        .longOpt("inchi_header_name")
        .type(String.class)
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

  private static ObjectMapper mapper = new ObjectMapper();

  // Instance variables
  private MongoDB mongoDB;
  private BingSearcher bingSearcher;

  public BingSearchRanker() {
    mongoDB = new MongoDB(DEFAULT_HOST, DEFAULT_PORT, INSTALLER_DATABASE);
    bingSearcher = new BingSearcher();
  }

  public static void main(final String[] args) throws Exception {

    BingSearchRanker bingSearchRanker = new BingSearchRanker();

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
      String inchiHeaderName = cl.getOptionValue(OPTION_TSV_INPUT_HEADER_NAME);
      moleculeCorpus.buildCorpusFromTSVFile(inputPath, inchiHeaderName);
    } else {
      moleculeCorpus.buildCorpusFromRawInchis(inputPath);
    }

    // Get the inchi set
    Set<String> inchis = moleculeCorpus.getMolecules();
    LOGGER.info("Found %d molecules in the input corpus", inchis.size());

    // Update the Bing Search results in the Installer database
    LOGGER.info("Updating the Bing Search results in the Installer database");
    bingSearchRanker.addBingSearchResults(inchis);
    LOGGER.info("Done updating the Bing Search results");

    // Write the results in a TSV file
    LOGGER.info("Writing results to output file");
    bingSearchRanker.writeBingSearchRanksAsTSV(inchis, outputPath);
    LOGGER.info("Results have been written to: %s", outputPath);
  }


  /**
   * This function parses the InChI from a BasicDBObject
   * @param c BasicDBObject extracted from the Installer database
   * @return InChI string
   */
  public String parseInchi(BasicDBObject c) {
    String inchi = (String) c.get("InChI");
    return inchi;
  }

  /**
   * This function parses the Bing Search results count from a BasicDBObject representing Bing metadata
   * @param c BasicDBObject representing Bing metadata
   * @return the Bing Search results count
   */
  public Long parseCountFromBingMetadata(BasicDBObject c) {
    Long totalCountSearchResults = (Long) c.get("total_count_search_results");
    return totalCountSearchResults;
  }

  /**
   * This function parses the best name from a BasicDBObject representing Bing metadata
   * @param c BasicDBObject representing Bing metadata
   * @return the best name
   */
  public String parseNameFromBingMetadata(BasicDBObject c) {
    String bestName = (String) c.get("best_name");
    return bestName;
  }

  /**
   * This function add the Bing Search results to the installer database from a set of InChI strings
   * @param inchis set of InChI string representations
   */
  public void addBingSearchResults(Set<String> inchis) throws IOException {
    bingSearcher.addBingSearchResultsForInchiSet(mongoDB, inchis);
  }

  /**
   * This function writes the Bing Search ranks for a specific set of inchis in a TSV file.
   * @param inchis set of InChI string representations
   * @param outputPath path indicating the output file
   * @throws IOException
   */
  public void writeBingSearchRanksAsTSV(Set<String> inchis, String outputPath) throws IOException {

    DBCursor cursor = mongoDB.fetchNamesAndBingInformationForInchis(inchis);

    // Define headers
    List<String> headers = new ArrayList<>();
    headers.add("inchi");
    headers.add("best_name");
    headers.add("total_count_search_results");
    headers.add("names_list");

    // Open TSV writer
    TSVWriter tsvWriter = new TSVWriter(headers);
    tsvWriter.open(new File(outputPath));

    // Iterate through the target chemicals
    while (cursor.hasNext()) {
      BasicDBObject o = (BasicDBObject) cursor.next();
      String inchi = parseInchi(o);
      Map<String, String> row = new HashMap<>();
      row.put("inchi", inchi);
      BasicDBObject xref = (BasicDBObject) o.get("xref");
      BasicDBObject bing = (BasicDBObject) xref.get("BING");
      BasicDBObject metadata = (BasicDBObject) bing.get("metadata");
      row.put("best_name", parseNameFromBingMetadata(metadata));
      row.put("total_count_search_results", parseCountFromBingMetadata(metadata).toString());
      row.put("names_list", mapper.writeValueAsString(mongoDB.getNamesFromBasicDBObject(o, inchi)));
      tsvWriter.append(row);
    }
    tsvWriter.flush();
    tsvWriter.close();
  }
}
