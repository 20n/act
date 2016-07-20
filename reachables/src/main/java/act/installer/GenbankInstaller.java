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
   * @param field - the key referring to the array in the metadata we wish to update
   * @param value - the value we wish to add to the array
   * @param data - the metadata
   * @return
   */
  private JSONObject updateField(String field, String value, JSONObject data) {
    JSONObject metadata = data;

    if (metadata.has(field)) {
      if (value.isEmpty() || value == null)
        return metadata;

      JSONArray fieldData = (JSONArray) metadata.get(field);
      Boolean valueExists = false;

      for (int i = 0; i < fieldData.length(); i++) {
        if (fieldData.get(i).toString().equals(value))
          valueExists = true;
      }

      if (!valueExists)
        metadata.append(field, value);

    } else if (value != null && !value.isEmpty()){
        metadata.append(field, value);
    }

    return metadata;
  }


  /**
   * Updates metadata field with the information extracted from file
   * @param se - an instance of the GenbankSeqEntry class that extracts all the relevant information from a sequence
   *           object
   * @param db - reference to the database that should be updated
   */
  private void addSeqEntryToDb(GenbankSeqEntry se, MongoDB db) {
    List<Seq> seqs = se.getSeqs();

    System.out.println("Number of matches: " + seqs.size());

    // no prior data on this sequence
    if (seqs.isEmpty())
      se.writeToDB(db, Seq.AccDB.genbank);

    // update prior data
    for (Seq seq : seqs) {
      JSONObject metadata = seq.get_metadata();

      metadata = updateField("accession", se.getAccession().get(0), metadata);

      List<String> geneSynonyms = se.getGeneSynonyms();

      if (metadata.get("name") == null)
        metadata = updateField("name", se.getGeneName(), metadata);
      else if (!se.getGeneName().equals(metadata.get("name")))
        geneSynonyms.add(se.getGeneName());

      for (String geneSynonym : geneSynonyms) {
        if (!geneSynonym.equals(metadata.get("name")))
          metadata = updateField("synonyms", geneSynonym, metadata);
      }

      if (se.getProductName() != null)
        metadata = updateField("product_names", se.getProductName().get(0), metadata);

      if (se.getNucleotideAccession() != null)
        metadata = updateField("nucleotide_accessions", se.getNucleotideAccession().get(0), metadata);

      if (se.getAccessionSource() != null)
        metadata = updateField("accession_sources", se.getAccessionSource().get(0), metadata);

      seq.set_metadata(metadata);

      db.updateMetadata(seq);
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
    String db_name = cl.getOptionValue(OPTION_DB_NAME);
    String seq_type = cl.getOptionValue(OPTION_SEQ_TYPE);

    if (!genbankFile.exists()) {
      String msg = String.format("Genbank file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      GenbankInstaller installer = new GenbankInstaller();
      MongoDB db = new MongoDB("localhost", 27017, db_name);

      GenbankInterpreter reader = new GenbankInterpreter(genbankFile, seq_type);
      reader.init();
      ArrayList<AbstractSequence> sequences = reader.sequences;

      for (AbstractSequence sequence : sequences) {
        if (seq_type.equals("DNA")) {
          List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = sequence.getFeatures();

          for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
            if (feature.getType().equals("CDS") && feature.getQualifiers().containsKey("EC_number"))
              installer.addSeqEntryToDb(new GenbankSeqEntry(sequence, feature.getQualifiers(), db), db);
          }

        } else if (seq_type.equals("Protein")) {
          installer.addSeqEntryToDb(new GenbankSeqEntry(sequence, db), db);
        }
      }
    }

  }
}
