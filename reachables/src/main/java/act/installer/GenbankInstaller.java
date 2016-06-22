package act.installer;

import act.installer.sequence.GenbankSeqEntry;
import act.server.MongoDB;
import act.shared.Seq;
import com.act.utils.parser.GenbankProteinSeqInterpreter;
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
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GenbankInstaller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInstaller.class);
  public static final String OPTION_GENBANK_PATH = "p";
  public static final String OPTION_DB_NAME = "d";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is the driver to write sequence data from a Genbank file to our database. It can be used on the " +
          "command line with a file path as a parameter."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_GENBANK_PATH)
        .argName("genbank file")
        .desc("genbank file containing sequence and annotations")
        .hasArg()
        .longOpt("genbank")
        .required()
    );
    add(Option.builder(OPTION_DB_NAME)
        .argName("db name")
        .desc("name of the database to be queried")
        .hasArg()
        .longOpt("database")
        .required()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Example of usage: -p filepath.gb -d marvin")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
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
      if (cl.hasOption("help")) {
        HELP_FORMATTER.printHelp(GenbankInstaller.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        return;
      }
    }

    File genbankFile = new File(cl.getOptionValue(OPTION_GENBANK_PATH));
    String db_name = cl.getOptionValue(OPTION_DB_NAME);

    if (!genbankFile.exists()) {
      String msg = String.format("Genbank file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      GenbankProteinSeqInterpreter reader = new GenbankProteinSeqInterpreter(genbankFile);
      reader.init();

      MongoDB db = new MongoDB("localhost", 27017, db_name);

      ArrayList<ProteinSequence> sequences = reader.sequences;
      for (ProteinSequence sequence : sequences) {
        GenbankSeqEntry se = new GenbankSeqEntry(sequence, db);

        // not a protein
        if (se.getEc() == null)
          continue;

        List<Seq> seqs = se.getSeqs();

        // no prior data on this sequence
        if (seqs.isEmpty()) {
          int id = se.writeToDB(db, Seq.AccDB.genbank);
          continue;
        }

        // update prior data
        for (Seq seq : seqs) {
          // not 100% if metadata for all of these database entries will be the same, so I modify each entry independently
          JSONObject metadata = seq.get_metadata();

          if (se.getGeneName().equals(metadata.get("name")) || metadata.get("name") == null) {
            metadata.append("synonyms", se.getGeneSynonyms());
          } else {
            metadata.append("synonyms", se.getGeneSynonyms().add(se.getGeneName()));
          }

          metadata.append("product_name", se.getProductName());

          seq.set_metadata(metadata);

          db.updateMetadata(seq);
        }
      }
    }
  }
}
