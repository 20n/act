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
import com.act.biointerpretation.desalting.ReactionDesalter;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class is based on Chris's substructure search from the biointerpretation branch.  The list of substructures
 * is hard-coded to find molecules that might contribute to umami-enhancement behavior.
 */
public class SubstructureSearch {
  private static final Logger LOGGER = LogManager.getFormatterLogger(SubstructureSearch.class);

  public static final String OPTION_INPUT_FILE = "f";
  public static final String OPTION_INPUT_DB = "d";
  public static final String OPTION_INPUT_DB_HOST = "s";
  public static final String OPTION_INPUT_DB_PORT = "p";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_QUERY = "q";
  public static final String OPTION_LICENSE_FILE = "l";

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

    // Everybody needs a little help from their friends.
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  // TODO: consider adding options for search options like VAGUE_BOND_LEVEL4, STEREO_MODEL_LOCAL, and STEREO_EXACT.

  MolSearch ms = null;

  public void init(String smilesQuery) throws IOException, MolFormatException {
    ms = new MolSearch();
    ms.setSearchOptions(SEARCH_OPTIONS);
    ms.setQuery(new MolHandler(smilesQuery, true).getMolecule());
  }

  public boolean matchVague(Molecule target) throws SearchException {
    ms.setTarget(target);
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
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionDesalter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    if (cl.hasOption(OPTION_LICENSE_FILE)) {
      LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));
    }

    Pair<List<String>, Iterator<Map<String, String>>> iterPair = null;
    if (cl.hasOption(OPTION_INPUT_FILE)) {
      File inFile = new File(cl.getOptionValue(OPTION_INPUT_FILE));
      if (!inFile.exists()) {
        System.err.format("File at %s does not exist", inFile.getAbsolutePath());
        HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    TSVWriter<String, String> writer = new TSVWriter<>(iterPair.getLeft());
    writer.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));

    LOGGER.info("Seaching for substructure '%s'", cl.getOptionValue(OPTION_QUERY));

    try {
      SubstructureSearch matcher = new SubstructureSearch();
      matcher.init(cl.getOptionValue(OPTION_QUERY));
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
          // Don't output if not a match.
          if (!matcher.matchVague(target)) {
            LOGGER.debug("Found non-matching molecule: %s", inchi);
            continue;
          }
          writer.append(row);
          writer.flush();
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
