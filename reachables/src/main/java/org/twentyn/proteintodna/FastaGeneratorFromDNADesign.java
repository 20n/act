package org.twentyn.proteintodna;

import act.server.MongoDB;
import com.act.utils.CLIUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.bson.types.ObjectId;
import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.JacksonDBCollection;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FastaGeneratorFromDNADesign {

  private static final String DEFAULT_DB_HOST = "localhost";
  private static final String DEFAULT_DB_PORT = "27017";
  private static final String DEFAULT_OUTPUT_DB_NAME = "wiki_reachables";
  private static final String DEFAULT_INPUT_DB_NAME = "jarvis_2016-12-09";

  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";
  private static final String OPTION_OUTPUT_DB_NAME = "o";
  private static final String OPTION_INPUT_DB_NAME = "i";
  public static final String DEFAULT_OUTPUT_DNA_SEQ_COLLECTION_NAME = "dna_designs";
  private static final String OPTION_OUTPUT_DNA_SEQ_COLLECTION_NAME = "e";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB_HOST)
        .argName("hostname")
        .desc(String.format("The DB host to which to connect (default: %s)", DEFAULT_DB_HOST))
        .hasArg()
        .longOpt("db-host")
    );
    add(Option.builder(OPTION_DB_PORT)
        .argName("port")
        .desc(String.format("The DB port to which to connect (default: %s)", DEFAULT_DB_PORT))
        .hasArg()
        .longOpt("db-port")
    );
    add(Option.builder(OPTION_OUTPUT_DNA_SEQ_COLLECTION_NAME)
    .argName("output-dna-seq-collection-name")
    .desc(String.format("The name of the output dna seq collection to write to (default: %s)", DEFAULT_OUTPUT_DNA_SEQ_COLLECTION_NAME))
        .hasArg()
    .longOpt("output-dna-seq-collection-name")
    );
  }};

  public static final String HELP_MESSAGE =
      "This class is the driver to extract protein sequences from pathways and construct DNA designs from these proteins.";

  private static final CLIUtil CLI_UTIL = new CLIUtil(ProteinToDNADriver.class, HELP_MESSAGE, OPTION_BUILDERS);

  public FastaGeneratorFromDNADesign() {

  }

  public static void main(String[] args) throws Exception {
    CommandLine cl = CLI_UTIL.parseCommandLine(args);
    String reactionDbName = cl.getOptionValue(OPTION_INPUT_DB_NAME, DEFAULT_INPUT_DB_NAME);
    String dbHost = cl.getOptionValue(OPTION_DB_HOST, DEFAULT_DB_HOST);
    Integer dbPort = Integer.valueOf(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_DB_PORT));
    MongoDB reactionDB = new MongoDB(dbHost, dbPort, reactionDbName);

    MongoClient inputClient = new MongoClient(new ServerAddress(dbHost, dbPort));
    DB db = inputClient.getDB(cl.getOptionValue(OPTION_OUTPUT_DB_NAME, DEFAULT_OUTPUT_DB_NAME));

    String outputDnaDeqCollectionName = cl.getOptionValue(OPTION_OUTPUT_DNA_SEQ_COLLECTION_NAME, DEFAULT_OUTPUT_DNA_SEQ_COLLECTION_NAME);

    JacksonDBCollection<DNADesign, String> dnaDesignCollection =
        JacksonDBCollection.wrap(db.getCollection(outputDnaDeqCollectionName), DNADesign.class, String.class);

    DBCursor<DNADesign> cursor = dnaDesignCollection.find(new BasicDBObject(), new BasicDBObject("_id", true));
    List<String> ids = new ArrayList<>();
    while (cursor.hasNext()) {
      ids.add(cursor.next().getId());
    }

    for (String id : ids) {

      DNADesign dnaDesign = dnaDesignCollection.findOne(DBQuery.is("_id", new ObjectId(id)));

      try (BufferedWriter fastaFile = new BufferedWriter(new FileWriter("/Users/vijaytramakrishnan/act/reachables/" + id + ".faa"))) {
        for (DNAOrgECNum design : dnaDesign.getDnaDesigns()) {
          for (Set<ProteinInformation> proteinSet : design.getListOfOrganismAndEcNums()) {
            String header = ">";
            String proteinSeq = "";
            List<String> descriptions = new ArrayList<>();
            List<String> organisms = new ArrayList<>();

            Boolean isEcNumSet = false;
            Boolean proteinSeqSet = false;

            for (ProteinInformation proteinInformation : proteinSet) {
              if (!isEcNumSet && proteinInformation.getEcnum() != null && !proteinInformation.getEcnum().equals("")) {
                header += proteinInformation.getEcnum();
                isEcNumSet = true;
              }

              if (!proteinSeqSet) {
                proteinSeq = proteinInformation.getProteinSeq();
                proteinSeqSet = true;
              }

              if (proteinInformation.getOrganism() != null && !proteinInformation.getOrganism().equals("")) {
                organisms.add(proteinInformation.getOrganism());
              }

              if (proteinInformation.getProteinDesc() != null && !proteinInformation.getProteinDesc().equals("")) {
                descriptions.add(proteinInformation.getProteinDesc());
              }
            }

            header += " | [";

            int counter = 0;
            for (String description : descriptions) {
              header += description;
              if (counter < descriptions.size() - 1) {
                header += ", ";
              }
              counter++;
            }

            header += "] [";

            counter = 0;
            for (String organism : organisms) {
              header += organism;
              if (counter < organisms.size() - 1) {
                header += ", ";
              }
              counter++;
            }

            header += "]";

            fastaFile.write(header);
            fastaFile.write("\n");
            fastaFile.write(proteinSeq);
            fastaFile.write("\n");
          }
        }
      }
    }
  }
}
