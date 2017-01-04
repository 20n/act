package act.installer.reachablesexplorer;

import com.act.utils.CLIUtil;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class outputs a TSV file that can be consumed by various web services that support the reachables wiki.
 */
public class WikiWebServicesExporter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(WikiWebServicesExporter.class);

  private static final List<String> HEADER = Arrays.asList("inchi", "inchi_key", "display_name", "image_name");

  private static final String OPTION_INPUT_DB = "d";
  private static final String OPTION_INPUT_DB_HOST = "H";
  private static final String OPTION_INPUT_DB_PORT = "p";
  private static final String OPTION_INPUT_DB_COLLECTION = "c";
  private static final String OPTION_OUTPUT_FILE = "o";
  private static final String OPTION_EXPORT_SOME = "m";

  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_PORT = "27017";
  private static final String DEFAULT_DB = "wiki_reachables";
  private static final String DEFAULT_COLLECTION = "reachablesv7";
  private static final String DEFAULT_SEQUENCES_COLLECTION = "sequencesv7";
  private static final String DEFAULT_RENDERING_CACHE = "/tmp";

  private static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_DB)
        .argName("db name")
        .desc(String.format("The name of the reachables database to read (default: %s)", DEFAULT_DB))
        .hasArg()
        .longOpt("db")
    );
    add(Option.builder(OPTION_INPUT_DB_HOST)
        .argName("db host")
        .desc(String.format("The host to which connect when reading from a DB (default: %s)", DEFAULT_HOST))
        .hasArg()
        .longOpt("host")
    );
    add(Option.builder(OPTION_INPUT_DB_PORT)
        .argName("db port")
        .desc(String.format("The port to which connect when reading from a DB (default: %s)", DEFAULT_PORT))
        .hasArg()
        .longOpt("port")
    );
    add(Option.builder(OPTION_INPUT_DB_COLLECTION)
        .argName("collection")
        .desc(String.format("The collection from which to read reachables documents (default: %s)", DEFAULT_COLLECTION))
        .hasArg()
        .longOpt("collection")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("file name")
        .desc("The name of the output tsv to write")
        .hasArg().required()
        .longOpt("out")
    );
    add(Option.builder(OPTION_EXPORT_SOME)
        .argName("ids")
        .desc("Only export molecules with the specified ids")
        .hasArgs().valueSeparator(',')
        .longOpt("only-export")
    );
  }};

  private static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class write the contents of a Reachables collection as a TSV that can be consumed by the substructure ",
      "search service"
  }, "");
  private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(WikiWebServicesExporter.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    String host = cl.getOptionValue(OPTION_INPUT_DB_HOST, DEFAULT_HOST);
    Integer port = Integer.parseInt(cl.getOptionValue(OPTION_INPUT_DB_PORT, DEFAULT_PORT));
    String dbName = cl.getOptionValue(OPTION_INPUT_DB, DEFAULT_DB);
    String collection = cl.getOptionValue(OPTION_INPUT_DB_COLLECTION, DEFAULT_COLLECTION);

    LOGGER.info("Attempting to connect to DB %s:%d/%s, collection %s", host, port, dbName, collection);
    Loader loader = new Loader(host, port, dbName, collection, DEFAULT_SEQUENCES_COLLECTION, DEFAULT_RENDERING_CACHE);

    JacksonDBCollection<Reachable, String> reachables = loader.getJacksonReachablesCollection();

    LOGGER.info("Connected to DB, reading reachables");

    List<Long> exportIds = !cl.hasOption(OPTION_EXPORT_SOME) ?
        Collections.emptyList() :
        Arrays.stream(cl.getOptionValues(OPTION_EXPORT_SOME))
            .map(Long::valueOf)
            .collect(Collectors.toList());

    TSVWriter<String, String> tsvWriter = new TSVWriter<>(HEADER);
    tsvWriter.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));
    try {
      DBCursor<Reachable> cursor = exportIds.isEmpty() ? reachables.find() :
          reachables.find(DBQuery.in("_id", exportIds));
      int written = 0;
      while (cursor.hasNext()) {
        final Reachable r = cursor.next();

        Map<String, String> row = new HashMap<String, String>() {{
          put("inchi", r.getInchi());
          put("inchi_key", r.getInchiKey());
          put("display_name", r.getPageName());
          put("image_name", r.getStructureFilename());
        }};
        tsvWriter.append(row);
        tsvWriter.flush();
        written++;
      }
      LOGGER.info("Wrote %d reachables to output TSV", written);
    } finally {
      tsvWriter.close();
    }
  }
}
