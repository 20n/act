package act.installer;

import act.installer.sequence.UniprotSeqEntry;
import act.server.MongoDB;
import act.shared.Seq;
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
import org.json.JSONArray;
import org.json.JSONObject;
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
    addSeqEntryToDb(new UniprotSeqEntry(uniprotInterpreter.getXmlDocument(), db), db);

  }

  private JSONObject updateArrayField(String field, String value, JSONObject data) {
    if (data.has(field)) {
      if (value == null || value.isEmpty()) {
        return data;
      }

      JSONArray fieldData = (JSONArray) data.get(field);

      for (int i = 0; i < fieldData.length(); i++) {
        if (fieldData.get(i).toString().equals(value)) {
          return data;
        }
      }

      data.append(field, value);

    } else if (value != null && !value.isEmpty()) {
      data.append(field, value);
    }

    return data;
  }

  private void addSeqEntryToDb(UniprotSeqEntry se, MongoDB db) {
    List<Seq> seqs = se.getSeqs(db);

    // no prior data on this sequence
    if (seqs.isEmpty()) {
      se.writeToDB(db, Seq.AccDB.genbank);
      return;
    }

    // update prior data
    for (Seq seq : seqs) {
      JSONObject metadata = seq.get_metadata();

      if (se.getAccession() != null && !se.getAccession().isEmpty()) {
        metadata = updateArrayField("accession", se.getAccession().get(0), metadata);
      }

      List<String> geneSynonyms = se.getGeneSynonyms();

      if (se.getGeneName() != null) {
        if (!metadata.has("name") || metadata.get("name") == null) {
          metadata.put("name", se.getGeneName());
        } else if (!se.getGeneName().equals(metadata.get("name"))) {
          geneSynonyms.add(se.getGeneName());
        }
      }

      for (String geneSynonym : geneSynonyms) {
        if (!geneSynonym.equals(metadata.get("name"))) {
          metadata = updateArrayField("synonyms", geneSynonym, metadata);
        }
      }

      if (se.getProductName() != null) {
        metadata = updateArrayField("product_names", se.getProductName().get(0), metadata);
      }

      if (se.getAccessionSource() != null) {
        metadata = updateArrayField("accession_sources", se.getAccessionSource().get(0), metadata);
      }

      seq.set_metadata(metadata);

      db.updateMetadata(seq);

      List<JSONObject> oldRefs = seq.get_references();
      List<JSONObject> newPmidRefs = se.getPmids();
      List<JSONObject> newPatentRefs = se.getPatents();

      if (!oldRefs.isEmpty()) {
        for (JSONObject newPmidRef : newPmidRefs) {
          Boolean pmidExists = false;
          String newPmid = (String) newPmidRef.get("val");

          for (JSONObject newRef : oldRefs) {
            if (newRef.get("src").equals("PMID") && newRef.get("val").equals(newPmid)) {
              pmidExists = true;
            }
          }

          if (!pmidExists) {
            oldRefs.add(newPmidRef);
          }
        }

        for (JSONObject newPatentRef : newPatentRefs) {
          Boolean patentExists = false;
          String countryCode = (String) newPatentRef.get("country_code");
          String patentNumber = (String) newPatentRef.get("patent_number");
          String patentYear = (String) newPatentRef.get("patent_year");

          for (JSONObject newRef : oldRefs) {
            if (newRef.get("src").equals("Patent") && newRef.get("country_code").equals(countryCode)
                && newRef.get("patent_number").equals(patentNumber) && newRef.get("patent_year").equals(patentYear)) {
              patentExists = true;
            }
          }

          if (!patentExists) {
            oldRefs.add(newPatentRef);
          }
        }

        seq.set_references(oldRefs);

      } else {
        seq.set_references(se.getRefs());
      }

      if (seq.get_references() != null) {
        db.updateReferences(seq);
      }
    }
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
