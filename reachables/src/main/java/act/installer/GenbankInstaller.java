package act.installer;

import act.installer.sequence.GenbankSeqEntry;
import act.installer.sequence.GenbankSeqEntryFactory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class GenbankInstaller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInstaller.class);
  private static final String OPTION_GENBANK_PATH = "p";
  private static final String OPTION_DB_NAME = "d";
  private static final String OPTION_SEQ_TYPE = "s";
  private static final String ACCESSION = "accession";
  private static final String NAME = "name";
  private static final String COUNTRY_CODE = "country_code";
  private static final String PATENT_NUMBER = "patent_number";
  private static final String PATENT_YEAR = "patent_year";
  private static final String SYNONYMS = "synonyms";
  private static final String PRODUCT_NAMES = "product_names";
  private static final String DNA = "DNA";
  private static final String CDS = "CDS";
  private static final String PROTEIN_ID = "protein_id";
  private static final String PROTEIN = "Protein";
  private static final String VAL = "val";
  private static final String SRC = "src";
  private static final String PMID = "PMID";
  private static final String PATENT = "Patent";

  //  http://www.ncbi.nlm.nih.gov/Sequin/acc.html
  private static final Pattern PROTEIN_ACCESSION_PATTERN = Pattern.compile("\\w{3}\\d{5}");
  private static final Pattern NUCLEOTIDE_ACCESSION_PATTERN = Pattern.compile("\\w\\d{5}|\\w{2}\\d{6}");

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

    GenbankSeqEntry seqEntry;

    for (AbstractSequence sequence : sequences) {
      if (seqType.equals(DNA)) {
        for (FeatureInterface<AbstractSequence<Compound>, Compound> feature :
            (List<FeatureInterface<AbstractSequence<Compound>, Compound>>) sequence.getFeatures()) {
          if (feature.getType().equals(CDS) && feature.getQualifiers().containsKey(PROTEIN_ID)) {
            seqEntry = new GenbankSeqEntryFactory().createFromDNASequenceReference(sequence,
                feature.getQualifiers(), db);
            addSeqEntryToDb(seqEntry, db);
            sequenceCount++;
          }
        }

      } else if (seqType.equals(PROTEIN)) {
        seqEntry = new GenbankSeqEntryFactory().createFromProteinSequenceReference(sequence, db);
        addSeqEntryToDb(seqEntry, db);
        sequenceCount++;
      }
    }

    LOGGER.info("%s sequences installed in the db", sequenceCount);
  }

  /**
   * Verifies the accession string according to the standard Genbank/Uniprot accession qualifications
   * @param proteinAccession the accession string to be validated
   * @param accessionPattern the pattern that the accession string should match
   * @return
   */
  private boolean verifyAccession(String proteinAccession, Pattern accessionPattern) {
    return accessionPattern.matcher(proteinAccession).find();
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
    if (value == null || value.isEmpty()) {
      return data;
    }

    if (data.has(field)) {
      JSONArray fieldData = (JSONArray) data.get(field);

      for (int i = 0; i < fieldData.length(); i++) {
        if (fieldData.get(i).toString().equals(value)) {
          return data;
        }
      }
    }

    return data.append(field, value);
  }

  /**
   * Updates the accession JSONObject for the given accessions type
   * @param newAccessionObject the new accession object to load in the new accessions of the given type
   * @param metadata contains the accession object to be updated
   * @param accType the type of accessions to update
   * @param accessionPattern the accession pattern to validate the accession string according to Genbank/Uniprot standards
   * @return the metadata containing the updated accession mapping
   */
  private JSONObject updateAccessions(JSONObject newAccessionObject, JSONObject metadata, Seq.AccType accType,
                                      Pattern accessionPattern) {
    JSONObject oldAccessionObject = metadata.getJSONObject(ACCESSION);

    if (newAccessionObject.has(accType.toString())) {
      JSONArray newAccTypeAccessions = newAccessionObject.getJSONArray(accType.toString());

      for (int i = 0; i < newAccTypeAccessions.length(); i++) {
        if (!verifyAccession(newAccTypeAccessions.getString(i), accessionPattern)) {
          continue;
        }

        oldAccessionObject = updateArrayField(accType.toString(), newAccTypeAccessions.getString(i),
            oldAccessionObject);
      }

    }

    return metadata.put(ACCESSION, oldAccessionObject);
  }

  /**
   * Updates metadata and reference fields with the information extracted from file
   * @param se an instance of the GenbankSeqEntry class that extracts all the relevant information from a sequence
   *           object
   * @param db reference to the database that should be queried and updated
   */
  private void addSeqEntryToDb(GenbankSeqEntry se, MongoDB db) {
    List<Seq> seqs = se.getMatchingSeqs();

    // no prior data on this sequence
    if (seqs.isEmpty()) {
      se.writeToDB(db, Seq.AccDB.genbank);
      return;
    }

    // update prior data
    for (Seq seq : seqs) {
      JSONObject metadata = seq.get_metadata();

      JSONObject accessions = se.getAccession();

      if (!metadata.has(ACCESSION)) {
        metadata.put(ACCESSION, accessions);
      } else {
        metadata = updateAccessions(accessions, metadata, Seq.AccType.genbank_nucleotide,
            NUCLEOTIDE_ACCESSION_PATTERN);
        metadata = updateAccessions(accessions, metadata, Seq.AccType.genbank_protein, PROTEIN_ACCESSION_PATTERN);
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

      if (se.getProductName() != null) {
        metadata = updateArrayField(PRODUCT_NAMES, se.getProductName().get(0), metadata);
      }

      seq.set_metadata(metadata);

      db.updateMetadata(seq);

      List<JSONObject> oldRefs = seq.get_references();
      List<JSONObject> newPmidRefs = se.getPmids();
      List<JSONObject> newPatentRefs = se.getPatents();

      if (!oldRefs.isEmpty()) {
        Set<String> oldPmids = new HashSet<>();

        for (JSONObject oldRef : oldRefs) {
          if (oldRef.get(SRC).equals(PMID)) {
            oldPmids.add(oldRef.getString(VAL));
          }
        }

        for (JSONObject newPmidRef : newPmidRefs) {
          if (!oldPmids.contains(newPmidRef.getString(VAL))) {
            oldRefs.add(newPmidRef);
          }
        }

        for (JSONObject newPatentRef : newPatentRefs) {
          Boolean patentExists = false;
          String countryCode = (String) newPatentRef.get(COUNTRY_CODE);
          String patentNumber = (String) newPatentRef.get(PATENT_NUMBER);
          String patentYear = (String) newPatentRef.get(PATENT_YEAR);

          // checks if any patents are equivalent
          for (JSONObject newRef : oldRefs) {
            if (newRef.get(SRC).equals(PATENT) && newRef.get(COUNTRY_CODE).equals(countryCode)
                && newRef.get(PATENT_NUMBER).equals(patentNumber) && newRef.get(PATENT_YEAR).equals(patentYear)) {
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
      String msg = String.format("Argument parsing failed: %s\n", e.getMessage());
      LOGGER.error(msg);
      HELP_FORMATTER.printHelp(GenbankInstaller.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(GenbankInstaller.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
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
