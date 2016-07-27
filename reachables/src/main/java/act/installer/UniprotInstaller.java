package act.installer;

import act.installer.sequence.UniprotSeqEntry;
import act.server.MongoDB;
import act.shared.Seq;
import com.act.utils.parser.UniprotInterpreter;
import com.mongodb.util.JSON;
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
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
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
  private static final String OPTION_UNIPROT_PATH = "p";
  private static final String OPTION_DB_NAME = "d";
  private static final String NAME = "name";
  private static final String ACCESSION = "accession";
  private static final String SYNONYMS = "synonyms";
  private static final String PRODUCT_NAMES = "product_names";
  private static final String ACCESSION_SOURCES = "accession_sources";
  private static final String VAL = "val";
  private static final String SRC = "src";
  private static final String PMID = "PMID";
  private static final String CATALYTIC_ACTIVITY = "catalytic_activity";

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

  public void init() throws IOException, SAXException, ParserConfigurationException, CompoundNotFoundException {
    UniprotInterpreter uniprotInterpreter = new UniprotInterpreter(uniprotFile);
    uniprotInterpreter.init();
    addSeqEntryToDb(new UniprotSeqEntry(uniprotInterpreter.getXmlDocument(), db), db);

  }

  private JSONObject updateAccessions(JSONObject newAccessionObject, JSONObject metadata) {
    if (!metadata.has(ACCESSION)) {
      return metadata.put(ACCESSION, newAccessionObject);
    }

    JSONObject oldAccessionObject = metadata.getJSONObject(ACCESSION);

    if (newAccessionObject.has(Seq.AccType.genbank_protein.toString())) {
      JSONArray newProteinAccessions = newAccessionObject.getJSONArray(Seq.AccType.genbank_protein.toString());

      for (int i = 0; i < newProteinAccessions.length(); i++) {
        oldAccessionObject =
            updateArrayField(Seq.AccType.genbank_protein.toString(), newProteinAccessions.getString(i), oldAccessionObject);
      }

    }

    if (newAccessionObject.has(Seq.AccType.genbank_nucleotide.toString())) {
      JSONArray newNucleotideAccessions = newAccessionObject.getJSONArray(Seq.AccType.genbank_nucleotide.toString());

      for (int i = 0; i < newNucleotideAccessions.length(); i++) {
        oldAccessionObject =
            updateArrayField(Seq.AccType.genbank_nucleotide.toString(), newNucleotideAccessions.getString(i), oldAccessionObject);
      }

    }

    if (newAccessionObject.has(Seq.AccType.uniprot.toString())) {
      JSONArray newUniprotAccessions = newAccessionObject.getJSONArray(Seq.AccType.uniprot.toString());

      for (int i = 0; i < newUniprotAccessions.length(); i++) {
        oldAccessionObject =
            updateArrayField(Seq.AccType.uniprot.toString(), newUniprotAccessions.getString(i), oldAccessionObject);
      }
    }

    return metadata.put(ACCESSION, oldAccessionObject);
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
      se.writeToDB(db, Seq.AccDB.uniprot);
      return;
    }

    // update prior data
    for (Seq seq : seqs) {
      JSONObject metadata = seq.get_metadata();

      JSONObject accessions = se.getAccession();

      // TODO: change accession update to fit new data model
      // currently a little inefficiently coded, but will change with data model update anyways
      if (accessions != null && accessions != new JSONObject()) {
        metadata = updateAccessions(accessions, metadata);
      }

      List<String> geneSynonyms = se.getGeneSynonyms();

      if (se.getGeneName() != null) {
        if (!metadata.has(NAME) || metadata.get(NAME) == null) {
          metadata.put(NAME, se.getGeneName());
        } else if (!se.getGeneName().equals(metadata.get(NAME))) {
          geneSynonyms.add(se.getGeneName());
        }
      }

      for (String geneSynonym : geneSynonyms) {
        if (!geneSynonym.equals(metadata.get(NAME))) {
          metadata = updateArrayField(SYNONYMS, geneSynonym, metadata);
        }
      }

      if (se.getProductName() != null && !se.getProductName().isEmpty()) {
        metadata = updateArrayField(PRODUCT_NAMES, se.getProductName().get(0), metadata);
      }

      if (se.getCatalyticActivity() != null) {
        metadata.put(CATALYTIC_ACTIVITY, se.getCatalyticActivity());
      }

      seq.set_metadata(metadata);

      db.updateMetadata(seq);

      List<JSONObject> oldRefs = seq.get_references();
      List<JSONObject> newPmidRefs = se.getRefs();

      if (!oldRefs.isEmpty()) {
        for (JSONObject newPmidRef : newPmidRefs) {
          Boolean pmidExists = false;
          String newPmid = (String) newPmidRef.get(VAL);

          for (JSONObject newRef : oldRefs) {
            if (newRef.get(SRC).equals(PMID) && newRef.get(VAL).equals(newPmid)) {
              pmidExists = true;
            }
          }

          if (!pmidExists) {
            oldRefs.add(newPmidRef);
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

  public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException, CompoundNotFoundException {
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
