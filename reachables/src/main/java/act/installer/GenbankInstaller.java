package act.installer;

import act.installer.sequence.GenbankSeqEntry;
import act.server.MongoDB;
import act.shared.Seq;
import com.act.utils.parser.GenbankInterpreter;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GenbankInstaller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInterpreter.class);
  public static final String OPTION_GENBANK_PATH = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is the driver to write sequence data from a Genbank file to our database. It can be used on the command line with a file path as a parameter."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_GENBANK_PATH)
        .argName("genbank file")
        .desc("genbank file containing sequence and annotations")
        .hasArg()
        .longOpt("genbank")
        .required()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Example of usage: -p filepath.gb")
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
        HELP_FORMATTER.printHelp(GenbankInterpreter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        return;
      }
    }

    File genbankFile = new File(cl.getOptionValue(OPTION_GENBANK_PATH));
    if (!genbankFile.exists()) {
      String msg = String.format("Genbank file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      GenbankInterpreter reader = new GenbankInterpreter(genbankFile);
      reader.init();

      MongoDB db = new MongoDB("localhost", 27017, "marvin");

      ArrayList<ProteinSequence> sequences = reader.sequences;
      for (ProteinSequence sequence : sequences) {
        GenbankSeqEntry se = new GenbankSeqEntry(sequence);

        if (se.ec == null)
          continue;

        long startTime = System.nanoTime();
        List<Seq> seqs = se.getSeqs(db);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
//        System.out.println(duration);

        if (seqs.isEmpty()) {
          // write data to database
          int id = se.writeToDB(db, Seq.AccDB.genbank);
          continue;
        }

        for (Seq seq : seqs) {
          // get all the data from the se object about proteins
          // add that data to each of these seq objects

//          System.out.println(seq.get_gene_name());
//        System.out.println(seq.get_sequence());
//        System.out.println(seq.get_org_name());
//        System.out.println(seq.get_ec());
//          System.out.println(seq.getUUID());
//        System.out.println(seq.get_uniprot_accession());
        }
      }
    }
  }
}