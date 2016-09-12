package act.installer.pubchem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class PubchemSynonymFinder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemSynonymFinder.class);
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_PUBCHEM_COMPOUND_ID = "c";
  public static final String OPTION_IDS_FILE = "f";
  public static final String OPTION_OUTPUT = "o";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class finds and prints Pubchem synonym data from a RocksDB index created from Pubchem RDF lcms. ",
      "Specify one or more Pubchem compound ids to find."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index will be stored; must not already exist")
        .hasArg().required()
        .longOpt("index")
    );
    add(Option.builder(OPTION_PUBCHEM_COMPOUND_ID)
        .argName("compound id")
        .desc("Lookup one compound ID in the database")
        .hasArg()
        .longOpt("pc-cid")
    );
    add(Option.builder(OPTION_IDS_FILE)
        .argName("compound ids file")
        .desc("Lookup a list of compound ids and print them as one large JSON document; comments (#) will be ignored")
        .hasArg()
        .longOpt("pc-cids-file")
    );
    add(Option.builder(OPTION_OUTPUT)
        .argName("output file")
        .desc("Write output to a file; default is stdout")
        .hasArg()
        .longOpt("output")
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

  private static final Pattern PC_CID_PATTERN = Pattern.compile("^CID\\d+$");

  public static void main(String[] args) throws Exception {
    org.apache.commons.cli.Options opts = new org.apache.commons.cli.Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(PubchemSynonymFinder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(PubchemSynonymFinder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (!rocksDBFile.isDirectory()) {
      System.err.format("Index directory does not exist or is not a directory at '%s'", rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(PubchemSynonymFinder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    List<String> compoundIds = null;
    if (cl.hasOption(OPTION_PUBCHEM_COMPOUND_ID)) {
      compoundIds = Collections.singletonList(cl.getOptionValue(OPTION_PUBCHEM_COMPOUND_ID));
    } else if (cl.hasOption(OPTION_IDS_FILE)) {
      File idsFile = new File(cl.getOptionValue(OPTION_IDS_FILE));
      if (!idsFile.exists()) {
        System.err.format("Cannot find Pubchem CIDs file at %s", idsFile.getAbsolutePath());
        HELP_FORMATTER.printHelp(PubchemSynonymFinder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        System.exit(1);
      }

      compoundIds = getCIDsFromFile(idsFile);

      if (compoundIds.size() == 0) {
        System.err.format("Found zero Pubchem CIDs to process in file at '%s', exiting", idsFile.getAbsolutePath());
        HELP_FORMATTER.printHelp(PubchemSynonymFinder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        System.exit(1);
      }
    } else {
      System.err.format("Must specify one of '%s' or '%s'; index is too big to print all synonyms.",
          OPTION_PUBCHEM_COMPOUND_ID, OPTION_IDS_FILE);
      HELP_FORMATTER.printHelp(PubchemSynonymFinder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Run a quick check to warn users of malformed ids.
    compoundIds.forEach(x -> {
      if (!PC_CID_PATTERN.matcher(x).matches()) { // Use matches() for complete matching.
        LOGGER.warn("Specified compound id does not match expected format: %s", x);
      }
    });

    LOGGER.info("Opening DB and searching for %d Pubchem CIDs", compoundIds.size());
    Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles = null;
    Map<String, PubchemSynonyms> results = new LinkedHashMap<>(compoundIds.size());
    try {
      dbAndHandles = PubchemTTLMerger.openExistingRocksDB(rocksDBFile);
      RocksDB db = dbAndHandles.getLeft();
      ColumnFamilyHandle cidToSynonymsCfh =
          dbAndHandles.getRight().get(PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_SYNONYMS);

      for (String cid : compoundIds) {
        PubchemSynonyms synonyms = null;
        byte[] val = db.get(cidToSynonymsCfh, cid.getBytes(UTF8));
        if (val != null) {
          ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(val));
          // We're relying on our use of a one-value-type per index model here so we can skip the instanceof check.
          synonyms = (PubchemSynonyms) oi.readObject();
        } else {
          LOGGER.warn("No synonyms available for compound id '%s'", cid);
        }
        results.put(cid, synonyms);
      }
    } finally {
      if (dbAndHandles != null) {
        dbAndHandles.getLeft().close();
      }
    }

    try (OutputStream outputStream =
             cl.hasOption(OPTION_OUTPUT) ? new FileOutputStream(cl.getOptionValue(OPTION_OUTPUT)) : System.out) {
      OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputStream, results);
      new OutputStreamWriter(outputStream).append('\n');
    }
    LOGGER.info("Done searching for Pubchem synonyms");
  }

  private static List<String> getCIDsFromFile(File idsFile) throws IOException {
    List<String> compoundIds = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(idsFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("#")) { // skip comments
          continue;
        }
        compoundIds.add(line);
      }
    }
    return compoundIds;
  }
}
