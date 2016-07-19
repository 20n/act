package act.installer;

import act.installer.sequence.UniprotSeqEntry;
import act.server.MongoDB;
import com.act.utils.parser.UniprotInterpreter;
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
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UniprotInstaller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(UniprotInstaller.class);
  public static final String OPTION_UNIPROT_PATH = "p";
  public static final String OPTION_DB_NAME = "d";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is the driver to write sequence data from a Uniprot file to our database. It can be used on the ",
      "command line with a file path as a parameter."}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_UNIPROT_PATH)
        .argName("uniprot file")
        .desc("uniprot file containing sequence and annotations")
        .hasArg()
        .longOpt("uniprot")
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

  File uniprotFile;
  MongoDB db;

  public UniprotInstaller (File uniprotFile, MongoDB db) {
    this.uniprotFile = uniprotFile;
    this.db = db;
  }

  public void init() throws IOException, SAXException, ParserConfigurationException {
    UniprotInterpreter uniprotInterpreter = new UniprotInterpreter(uniprotFile);
    uniprotInterpreter.init();
    UniprotSeqEntry seqEntry = new UniprotSeqEntry(uniprotInterpreter.getXmlDocument(), db);

  }

  public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
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
        HELP_FORMATTER.printHelp(UniprotInstaller.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        return;
      }
    }

    File uniprotFile = new File(cl.getOptionValue(OPTION_UNIPROT_PATH));
    String dbName = cl.getOptionValue(OPTION_DB_NAME);

    if (!uniprotFile.exists()) {
      String msg = String.format("Uniprot file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      MongoDB db = new MongoDB("localhost", 27017, dbName);

      UniprotInstaller installer = new UniprotInstaller(uniprotFile, db);
      installer.init();
    }
  }

}
