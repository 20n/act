package com.act.analysis.similarity;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class is based on Chris's substructure search from the biointerpretation branch.
 */
public class SubstructureSearch {
  private static final Logger LOGGER = LogManager.getFormatterLogger(SubstructureSearch.class);

  // TODO: are these options sufficient?  Are there others we might want to use?
  /* Chemaxon exposes a very non-uniform means of configuring substructure search.  Hence the mess of lambdas below.
   * Consumer solves the Function<T, void> problem. */
  private static final Map<String, Consumer<MolSearchOptions>> SEARCH_OPTION_ENABLERS =
      Collections.unmodifiableMap(new HashMap<String, Consumer<MolSearchOptions>>() {{
        put("CHARGE_MATCHING_EXACT", (so -> so.setChargeMatching(SearchConstants.CHARGE_MATCHING_EXACT)));
        put("CHARGE_MATCHING_IGNORE", (so -> so.setChargeMatching(SearchConstants.CHARGE_MATCHING_IGNORE)));
        put("IMPLICIT_H_MATCHING_ENABLED", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_ENABLED)));
        put("IMPLICIT_H_MATCHING_DISABLED", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_DISABLED)));
        put("IMPLICIT_H_MATCHING_IGNORE", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_IGNORE)));
        put("STEREO_EXACT", (so -> so.setStereoSearchType(SearchConstants.STEREO_EXACT)));
        put("STEREO_IGNORE", (so -> so.setStereoSearchType(SearchConstants.STEREO_IGNORE)));
        put("STEREO_MODEL_COMPREHENSIVE", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_COMPREHENSIVE)));
        put("STEREO_MODEL_GLOBAL", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_GLOBAL)));
        put("STEREO_MODEL_LOCAL", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL)));
        put("TAUTOMER_SEARCH_ON", (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON)));
        put("TAUTOMER_SEARCH_OFF", (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_OFF)));
        put("TAUTOMER_SEARCH_ON_IGNORE_TAUTOMERSTEREO",
            (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON_IGNORE_TAUTOMERSTEREO)));
        put("VAGUE_BOND_OFF", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_OFF)));
        put("VAGUE_BOND_LEVEL_HALF", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL_HALF)));
        put("VAGUE_BOND_LEVEL1", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL1)));
        put("VAGUE_BOND_LEVEL2", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL2)));
        put("VAGUE_BOND_LEVEL3", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL3)));
        put("VAGUE_BOND_LEVEL4", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4)));
      }});
  public static final List<String> VALID_SEARCH_OPTION_SORTED;
  static {
    List<String> keys = new ArrayList<>(SEARCH_OPTION_ENABLERS.keySet());
    Collections.sort(keys);
    VALID_SEARCH_OPTION_SORTED = Collections.unmodifiableList(keys);
  }

  public static final String OPTION_INPUT_FILE = "f";
  public static final String OPTION_INPUT_DB = "d";
  public static final String OPTION_INPUT_DB_HOST = "s";
  public static final String OPTION_INPUT_DB_PORT = "p";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_QUERY = "q";
  public static final String OPTION_LICENSE_FILE = "l";
  public static final String OPTION_SEARCH_OPTIONS = "x";

  public static final String FIELD_INCHI = "inchi";
  public static final String FIELD_ID = "id";

  public static final String DEFAULT_HOST = "localhost";
  public static final String DEFAULT_PORT = "27017";

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class does substructure matching against an installer DB or a TSV file using a single SMILES query.  ",
      "All matching chemicals are outputted; non-matches are ignored."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILE)
        .argName("tsv file")
        .desc(String.format("The name of the input TSV file to read (must contain an '%s' field)", FIELD_INCHI))
        .hasArg()
        .longOpt("tsv")
    );
    add(Option.builder(OPTION_INPUT_DB)
        .argName("db name")
        .desc("The name of the database to read")
        .hasArg()
        .longOpt("db")
    );
    add(Option.builder(OPTION_INPUT_DB_HOST)
        .argName("db host")
        .desc("The host to which connect when reading from a DB")
        .hasArg()
        .longOpt("host")
    );
    add(Option.builder(OPTION_INPUT_DB_PORT)
        .argName("db port")
        .desc("The port to which connect when reading from a DB")
        .hasArg()
        .longOpt("port")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("file name")
        .desc("The name of the output tsv to write")
        .hasArg().required()
        .longOpt("out")
    );
    add(Option.builder(OPTION_QUERY)
        .argName("query string")
        .desc("The SMILES query string for which to search")
        .hasArg().required()
        .longOpt("query")
    );
    add(Option.builder(OPTION_LICENSE_FILE)
        .argName("license file")
        .desc("A chemaxon license file to use")
        .hasArg()
        .longOpt("license")
    );
    add(Option.builder(OPTION_SEARCH_OPTIONS)
        .argName("search options")
        .desc(String.format("Options to supply to the substructure search.  See " +
            "https://www.chemaxon.com/jchem/doc/dev/java/api/chemaxon/sss/SearchConstants.html " +
            "for explanations.  Valid options are: %s", StringUtils.join(VALID_SEARCH_OPTION_SORTED, ", "))).
        hasArgs().valueSeparator(',').
        longOpt("search-opts")
    );

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  MolSearch ms = new MolSearch();
  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  private final MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  public void init(String smilesQuery, List<String> extraOpts)
      throws IllegalArgumentException, IOException, MolFormatException {
    // Apply all the specified extra search options using the key -> function mapping above.
    for (String opt : extraOpts) {
      if (!SEARCH_OPTION_ENABLERS.containsKey(opt)) {
        throw new IllegalArgumentException(String.format("Unrecognized search option: %s", opt));
      }
      SEARCH_OPTION_ENABLERS.get(opt).accept(searchOptions);
    }

    ms.setSearchOptions(searchOptions);
    ms.setQuery(new MolHandler(smilesQuery, true).getMolecule());
  }

  public boolean matchSubstructure(Molecule target) throws SearchException {
    ms.setTarget(target);
    /* hits are arrays of atom ids in the target that matched the query.  If multiple sites in the target matched,
     * then there should be multiple arrays of atom ids (but we don't care since we're just looking for any match). */
    int[][] hits = ms.findAll();
    if (hits != null) {
      for (int i = 0; i < hits.length; i++) {
        if (hits[i].length > 0) {
          return true;
        }
      }
    }
    return false;
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
      HELP_FORMATTER.printHelp(SubstructureSearch.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(SubstructureSearch.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    if (cl.hasOption(OPTION_LICENSE_FILE)) {
      LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));
    }

    List<String> searchOpts = Collections.emptyList();
    if (cl.hasOption(OPTION_SEARCH_OPTIONS)) {
      searchOpts = Arrays.asList(cl.getOptionValues(OPTION_SEARCH_OPTIONS));
    }

    // Make sure we can initialize correctly before opening any file handles for writing.
    SubstructureSearch matcher = new SubstructureSearch();
    try {
      matcher.init(cl.getOptionValue(OPTION_QUERY), searchOpts);
    } catch (IllegalArgumentException e) {
      System.err.format("Unable to initialize substructure search.  %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(SubstructureSearch.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    } catch (MolFormatException e) {
      System.err.format("Invalid SMILES structure query.  %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(SubstructureSearch.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    Pair<List<String>, Iterator<Map<String, String>>> iterPair = null;
    if (cl.hasOption(OPTION_INPUT_FILE)) {
      File inFile = new File(cl.getOptionValue(OPTION_INPUT_FILE));
      if (!inFile.exists()) {
        System.err.format("File at %s does not exist", inFile.getAbsolutePath());
        HELP_FORMATTER.printHelp(SubstructureSearch.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        System.exit(1);
      }
      iterPair = iterateOverTSV(inFile);
    } else if (cl.hasOption(OPTION_INPUT_DB)) {
      iterPair = iterateOverDB(
          cl.getOptionValue(OPTION_INPUT_DB_HOST, DEFAULT_HOST),
          Integer.parseInt(cl.getOptionValue(OPTION_INPUT_DB_HOST, DEFAULT_PORT)),
          cl.getOptionValue(OPTION_INPUT_DB)
      );
    } else {
      System.err.format("Must specify either input TSV file or input DB from which to read.\n");
      HELP_FORMATTER.printHelp(SubstructureSearch.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    TSVWriter<String, String> writer = new TSVWriter<>(iterPair.getLeft());
    writer.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));

    LOGGER.info("Seaching for substructure '%s'", cl.getOptionValue(OPTION_QUERY));

    try {
      int rowNum = 0;
      while (iterPair.getRight().hasNext()) {
        Map<String, String> row = iterPair.getRight().next();
        rowNum++;
        try {
          String inchi = row.get(FIELD_INCHI);
          Molecule target = null;
          try {
            target = MolImporter.importMol(inchi);
          } catch (Exception e) {
            LOGGER.warn("Skipping molecule %d due to exception: %s\n", rowNum, e.getMessage());
            continue;
          }
          if (matcher.matchSubstructure(target)) {
            writer.append(row);
            writer.flush();
          } else {
            // Don't output if not a match.
            LOGGER.debug("Found non-matching molecule: %s", inchi);
          }
        } catch (SearchException e) {
          LOGGER.error("Exception on input line %d: %s\n", rowNum, e.getMessage());
          throw e;
        }
      }
    } finally {
      writer.close();
    }
    LOGGER.info("Done with substructure search");
  }

  public static Pair<List<String>, Iterator<Map<String, String>>> iterateOverTSV(File inputFile) throws Exception {
    TSVParser parser = new TSVParser();
    parser.parse(inputFile);
    List<String> header = parser.getHeader();

    Iterator<Map<String, String>> chemsIter = parser.getResults().iterator();

    return Pair.of(header, chemsIter);
  }

  public static Pair<List<String>, Iterator<Map<String, String>>> iterateOverDB(
      String host, Integer port, String dbName) throws Exception {
    MongoDB db = new MongoDB(host, port, dbName);

    final DBIterator iter = db.getIteratorOverChemicals();
    Iterator<Map<String, String>> chemsIter = new Iterator<Map<String, String>>() {
      @Override
      public boolean hasNext() {
        return iter.hasNext();
      }

      @Override
      public Map<String, String> next() {
        Chemical c = db.getNextChemical(iter);
        return new HashMap<String, String>() {{
          put(FIELD_ID, c.getUuid().toString());
          put(FIELD_INCHI, c.getInChI());
        }};
      }
    };

    return Pair.of(Arrays.asList(FIELD_ID, FIELD_INCHI), chemsIter);
  }
}
