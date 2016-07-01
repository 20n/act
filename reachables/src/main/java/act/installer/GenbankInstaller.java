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


  /**
   * Checks if the value exists in the field. If so, doesn't update the metadata. If it doesn't exist, appends the value
   * to the data.
   * @param field the key referring to the array in the metadata we wish to update
   * @param value the value we wish to add to the array
   * @param data the metadata
   * @return the updated metadata JSONObject
   */
  private JSONObject updateField(String field, String value, JSONObject data) {
    JSONObject metadata = data;

    if (metadata.has(field)) {
      if (value == null || value.isEmpty()) {
        return metadata;
      }

      JSONArray fieldData = (JSONArray) metadata.get(field);
      Boolean valueExists = false;

      for (int i = 0; i < fieldData.length(); i++) {
        if (fieldData.get(i).toString().equals(value)) {
          valueExists = true;
        }
      }

      if (!valueExists) {
        metadata.append(field, value);
      }

    } else if (value != null && !value.isEmpty()) {
        metadata.append(field, value);
    }

    return metadata;
  }


  /**
   * Updates metadata and references field with the information extracted from file
   * @param se an instance of the GenbankSeqEntry class that extracts all the relevant information from a sequence
   *           object
   * @param db reference to the database that should be updated
   */
  private void addSeqEntryToDb(GenbankSeqEntry se, MongoDB db) {
    List<Seq> seqs = se.getSeqs();

    // no prior data on this sequence
    if (seqs.isEmpty()) {
      se.writeToDB(db, Seq.AccDB.genbank);
    }

    // update prior data
    for (Seq seq : seqs) {
      JSONObject metadata = seq.get_metadata();

      if (se.getAccession() != null) {
        metadata = updateField("accession", se.getAccession().get(0), metadata);
      }

      List<String> geneSynonyms = se.getGeneSynonyms();

      if (metadata.get("name") == null) {
        metadata = updateField("name", se.getGeneName(), metadata);
      } else if (!se.getGeneName().equals(metadata.get("name"))) {
        geneSynonyms.add(se.getGeneName());
      }

      for (String geneSynonym : geneSynonyms) {
        if (!geneSynonym.equals(metadata.get("name"))) {
          metadata = updateField("synonyms", geneSynonym, metadata);
        }
      }

      if (se.getProductName() != null) {
        metadata = updateField("product_names", se.getProductName().get(0), metadata);
      }

      if (se.getNucleotideAccession() != null) {
        metadata = updateField("nucleotide_accessions", se.getNucleotideAccession().get(0), metadata);
      }

      if (se.getAccessionSource() != null) {
        metadata = updateField("accession_sources", se.getAccessionSource().get(0), metadata);
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

          for (JSONObject oldRef : oldRefs) {
            if (oldRef.get("src").equals("PMID") && oldRef.get("val").equals(newPmid)) {
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

          for (JSONObject oldRef : oldRefs) {
            if (oldRef.get("src").equals("Patent") && oldRef.get("country_code").equals(countryCode)
                && oldRef.get("patent_number").equals(patentNumber) && oldRef.get("patent_year").equals(patentYear)) {
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
      GenbankInstaller installer = new GenbankInstaller();
      MongoDB db = new MongoDB("localhost", 27017, dbName);

      GenbankInterpreter reader = new GenbankInterpreter(genbankFile, seqType);
      reader.init();
      ArrayList<AbstractSequence> sequences = reader.sequences;

      for (AbstractSequence sequence : sequences) {
        if (seqType.equals("DNA")) {
          List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = sequence.getFeatures();

          for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
            if (feature.getType().equals("CDS") && feature.getQualifiers().containsKey("EC_number")) {
              installer.addSeqEntryToDb(new GenbankSeqEntry(sequence, feature.getQualifiers(), db), db);
            }
          }

        } else if (seqType.equals("Protein")) {
          installer.addSeqEntryToDb(new GenbankSeqEntry(sequence, db), db);
        }
      }
    }

  }
}
