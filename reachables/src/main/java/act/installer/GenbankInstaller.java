package act.installer;

import act.installer.sequence.GenbankSeqEntry;
import act.server.MongoDB;
import act.shared.Seq;
import com.act.utils.parser.GenbankInterpreter;
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
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GenbankInstaller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInstaller.class);
  public static final String OPTION_GENBANK_PATH = "p";
  public static final String OPTION_DB_NAME = "d";
  public static final String OPTION_SEQ_TYPE = "s";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is the driver to write sequence data from a Genbank file to our database. It can be used on the ",
      "command line with a file path as a parameter."}, "");

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
    add(Option.builder(OPTION_SEQ_TYPE)
        .argName("sequence type")
        .desc("declares whether the sequence type is DNA or Protein")
        .hasArg()
        .longOpt("sequence")
        .required()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Example of usage: -p filepath.gb -d marvin -s DNA")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  File genbankFile;
  String seqType;
  MongoDB db;

  public GenbankInstaller (File genbankFile, String seqType, MongoDB db) {
    this.genbankFile = genbankFile;
    this.seqType = seqType;
    this.db = db;
  }

  public void init() throws Exception {
    GenbankInterpreter reader = new GenbankInterpreter(genbankFile, seqType);
    reader.init();
    List<AbstractSequence> sequences = reader.getSequences();

    int sequenceCount = 0;

    for (AbstractSequence sequence : sequences) {
      if (seqType.equals("DNA")) {
        for (FeatureInterface<AbstractSequence<Compound>, Compound> feature :
            (List<FeatureInterface<AbstractSequence<Compound>, Compound>>) sequence.getFeatures()) {
          if (feature.getType().equals("CDS") && feature.getQualifiers().containsKey("protein_id")) {
            addSeqEntryToDb(new GenbankSeqEntry(sequence, feature.getQualifiers(), db), db);
            sequenceCount++;
          }
        }

      } else if (seqType.equals("Protein")) {
        addSeqEntryToDb(new GenbankSeqEntry(sequence, db), db);
        sequenceCount++;
      }
    }

    LOGGER.info("%s sequences installed in the db", sequenceCount);
  }


  /**
   * Checks if the new value already exists in the field. If so, doesn't update the metadata. If it doesn't exist,
   * appends the new value to the data.
   * @param field the key referring to the array in the metadata we wish to update
   * @param value the value we wish to add to the array
   * @param data the metadata
   * @return the updated metadata JSONObject
   */
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

  private JSONObject updateAccessions(JSONObject newAccessionObject, JSONObject metadata) {
    JSONObject oldAccessionObject = (JSONObject) metadata.get("accession");

    if (newAccessionObject.has("genbank-protein")) {
      String newProteinAccession = (String) ((JSONArray) newAccessionObject.get("genbank-protein")).get(0);
      oldAccessionObject = updateArrayField("genbank-protein", newProteinAccession, oldAccessionObject);
    }

    if (newAccessionObject.has("genbank-nucleotide")) {
      String newNucleotideAccession = (String) ((JSONArray) newAccessionObject.get("genbank-nucleotide")).get(0);
      oldAccessionObject = updateArrayField("genbank-nucleotide", newNucleotideAccession, oldAccessionObject);
    }

    metadata.remove("accession");
    metadata.put("accession", oldAccessionObject);

    return metadata;
  }


  /**
   * Updates metadata and references field with the information extracted from file
   * @param se an instance of the GenbankSeqEntry class that extracts all the relevant information from a sequence
   *           object
   * @param db reference to the database that should be updated
   */
  private void addSeqEntryToDb(GenbankSeqEntry se, MongoDB db) {
    List<Seq> seqs = se.getSeqs(db);

    // no prior data on this sequence
    if (seqs.isEmpty()) {
      se.writeToDB(db, Seq.AccDB.genbank);
      return;
    }

    // update prior data
    for (Seq seq : seqs) {
      JSONObject metadata = seq.get_metadata();

      if (se.getAccession() != null && se.getAccession() != new JSONObject()) {
        metadata = updateAccessions(se.getAccession(), metadata);
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
    String dbName = cl.getOptionValue(OPTION_DB_NAME);
    String seqType = cl.getOptionValue(OPTION_SEQ_TYPE);

    if (!genbankFile.exists()) {
      String msg = String.format("Genbank file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      MongoDB db = new MongoDB("localhost", 27017, dbName);

      GenbankInstaller installer = new GenbankInstaller(genbankFile, seqType, db);
      installer.init();
    }

  }
}
