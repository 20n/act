package act.installer;

import act.installer.sequence.UniprotSeqEntry;
import act.installer.sequence.UniprotSeqEntryFactory;
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
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class UniprotInstaller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(UniprotInstaller.class);
  private static final UniprotSeqEntryFactory seqEntryFactory = new UniprotSeqEntryFactory();
  private static final String OPTION_UNIPROT_PATH = "p";
  private static final String OPTION_DB_NAME = "d";
  private static final String NAME = "name";
  private static final String ACCESSION = "accession";
  private static final String SYNONYMS = "synonyms";
  private static final String PRODUCT_NAMES = "product_names";
  private static final String VAL = "val";
  private static final String SRC = "src";
  private static final String PMID = "PMID";
  private static final String CATALYTIC_ACTIVITY = "catalytic_activity";

  //  http://www.uniprot.org/help/accession_numbers
  private static final Pattern UNIPROT_ACCESSION_PATTERN = Pattern.compile("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}");


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

    UniprotSeqEntry seqEntry = seqEntryFactory.createFromDocumentReference(uniprotInterpreter.getXmlDocument(), db);
    addSeqEntryToDb(seqEntry, db);
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
          String msg = String.format("%s accession not the right format: %s\n", accType.toString(), newAccTypeAccessions.getString(i));
          LOGGER.error(msg);
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
   * @param se an instance of the UniprotSeqEntry class that extracts all the relevant information from a sequence
   *           object
   * @param db reference to the database that should be queried and updated
   */
  private void addSeqEntryToDb(UniprotSeqEntry se, MongoDB db) {
    List<Seq> seqs = se.getMatchingSeqs();

    // no prior data on this sequence
    if (seqs.isEmpty()) {
      se.writeToDB(db, Seq.AccDB.uniprot);
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
            GenbankInstaller.NUCLEOTIDE_ACCESSION_PATTERN);
        metadata = updateAccessions(accessions, metadata, Seq.AccType.genbank_protein,
            GenbankInstaller.PROTEIN_ACCESSION_PATTERN);
        metadata = updateAccessions(accessions, metadata, Seq.AccType.uniprot, UNIPROT_ACCESSION_PATTERN);
      }

      List<String> geneSynonyms = se.getGeneSynonyms();

      if (se.getGeneName() != null) {
        if (!metadata.has(NAME) || metadata.isNull(NAME)) {
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

      List<String> productNames = se.getProductName();

      if (!productNames.isEmpty()) {
        for (int i = 0; i < productNames.size(); i++) {
          metadata = updateArrayField(PRODUCT_NAMES, productNames.get(i), metadata);
        }
      }

      if (se.getCatalyticActivity() != null) {
        metadata.put(CATALYTIC_ACTIVITY, se.getCatalyticActivity());
      }

      seq.set_metadata(metadata);

      db.updateMetadata(seq);

      List<JSONObject> oldRefs = seq.get_references();
      List<JSONObject> newPmidRefs = se.getRefs();

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

        seq.set_references(oldRefs);

      } else {
        seq.set_references(se.getRefs());
      }

      if (seq.get_references() != null) {
        db.updateReferences(seq);
      }
    }
  }

  public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException,
      CompoundNotFoundException {
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
      HELP_FORMATTER.printHelp(UniprotInstaller.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(UniprotInstaller.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
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
