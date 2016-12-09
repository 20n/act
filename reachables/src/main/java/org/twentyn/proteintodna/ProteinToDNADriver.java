package org.twentyn.proteintodna;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.reachables.Cascade;
import com.act.reachables.ReactionPath;
import com.act.utils.CLIUtil;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProteinToDNADriver {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ProteinToDNADriver.class);
  private static final String DEFAULT_DB_HOST = "localhost";
  private static final String DEFAULT_DB_PORT = "27017";
  private static final String DEFAULT_OUTPUT_DB_NAME = "wiki_reachables";
  private static final String DEFAULT_INPUT_DB_NAME = "validator_profiling_2";
  public static final String DEFAULT_INPUT_PATHWAY_COLLECTION_NAME = "vanillin_pathways";
  public static final String DEFAULT_OUTPUT_PATHWAY_COLLECTION_NAME = "pathways_vijay_4";
  public static final String DEFAULT_OUTPUT_DNA_SEQ_COLLECTION_NAME = "dna_designs_4";

  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";
  private static final String OPTION_OUTPUT_DB_NAME = "o";
  private static final String OPTION_INPUT_DB_NAME = "i";
  private static final String OPTION_INPUT_PATHWAY_COLLECTION_NAME = "c";
  private static final String OPTION_OUTPUT_PATHWAY_COLLECTION_NAME = "d";
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
    add(Option.builder(OPTION_OUTPUT_DB_NAME)
        .argName("output-db-name")
        .desc(String.format("The name of the database to read pathways and write seqs to (default: %s)", DEFAULT_OUTPUT_DB_NAME))
        .hasArg()
        .longOpt("db-name")
    );
    add(Option.builder(OPTION_INPUT_DB_NAME)
        .argName("input-db-name")
        .desc(String.format("The name of the database to read reactions from (default: %s)", DEFAULT_INPUT_DB_NAME))
        .hasArg()
        .longOpt("db-name")
    );
    add(Option.builder(OPTION_INPUT_PATHWAY_COLLECTION_NAME)
        .argName("input-pathway-collection-name")
        .desc(String.format("The name of the input pathway collection to read from (default: %s)", DEFAULT_INPUT_PATHWAY_COLLECTION_NAME))
        .hasArg()
        .longOpt("input-pathway-collection-name")
    );
    add(Option.builder(OPTION_OUTPUT_PATHWAY_COLLECTION_NAME)
        .argName("output-pathway-collection-name")
        .desc(String.format("The name of the output pathway collection to write to (default: %s)", DEFAULT_OUTPUT_PATHWAY_COLLECTION_NAME))
        .hasArg()
        .longOpt("output-pathway-collection-name")
    );
    add(Option.builder(OPTION_OUTPUT_DNA_SEQ_COLLECTION_NAME)
        .argName("output-dna-seq-collection-name")
        .desc(String.format("The name of the output dna seq collection to write to (default: %s)", DEFAULT_OUTPUT_DNA_SEQ_COLLECTION_NAME))
        .hasArg()
        .longOpt("output-dna-seq-collection-name")
    );
  }};

  /**
   * This function get all protein combinations of a pathway from candidate protein sequences from each reaction on
   * the pathway.
   * @param listOfSetOfProteinSequences A list of sets of candidate protein sequences in the pathway
   * @return A set of all possible combinations of proteins from all the reactions in the pathway.
   */
  public static Set<List<String>> getPathwayProteinCombinations(List<Set<String>> listOfSetOfProteinSequences) {
    Set<List<String>> combinations = new HashSet<>();
    Set<List<String>> newCombinations;

    int index = 0;

    // Build the combination set by extracting all the candidate proteins from the first reaction on the pathway
    for(String proteinSeq: listOfSetOfProteinSequences.get(index)) {
      List<String> newList = new ArrayList<>();
      newList.add(proteinSeq);
      combinations.add(newList);
    }

    index++;

    // Iterate on all other protein sequences
    while(index < listOfSetOfProteinSequences.size()) {
      Set<String> nextList = listOfSetOfProteinSequences.get(index);
      newCombinations = new HashSet<>();
      for(List<String> firstProteinSeq: combinations) {
        for(String secondProteinSeq: nextList) {
          List<String> newList = new ArrayList<>();
          newList.addAll(firstProteinSeq);
          newList.add(secondProteinSeq);
          newCombinations.add(newList);
        }
      }
      combinations = newCombinations;
      index++;
    }

    return combinations;
  }

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is the driver to extract protein sequences from pathways and construct DNA designs from these proteins.",
  }, "");

  private static final CLIUtil CLI_UTIL = new CLIUtil(ProteinToDNADriver.class, HELP_MESSAGE, OPTION_BUILDERS);

  public static void main(String[] args) throws Exception {
    CommandLine cl = CLI_UTIL.parseCommandLine(args);

    String reactionDbName = cl.getOptionValue(OPTION_INPUT_DB_NAME, DEFAULT_INPUT_DB_NAME);
    String dbHost = cl.getOptionValue(OPTION_DB_HOST, DEFAULT_DB_HOST);
    Integer dbPort = Integer.valueOf(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_DB_PORT));
    MongoDB reactionDB = new MongoDB(dbHost, dbPort, reactionDbName);

    MongoClient inputClient = new MongoClient(new ServerAddress(dbHost, dbPort));
    DB db = inputClient.getDB(cl.getOptionValue(OPTION_OUTPUT_DB_NAME, DEFAULT_OUTPUT_DB_NAME));


    String inputPathwaysCollectionName = cl.getOptionValue(OPTION_INPUT_PATHWAY_COLLECTION_NAME, DEFAULT_INPUT_PATHWAY_COLLECTION_NAME);
    String outputPathwaysCollectionName = cl.getOptionValue(OPTION_OUTPUT_PATHWAY_COLLECTION_NAME, DEFAULT_OUTPUT_PATHWAY_COLLECTION_NAME);
    String outputDnaDeqCollectionName = cl.getOptionValue(OPTION_OUTPUT_DNA_SEQ_COLLECTION_NAME, DEFAULT_OUTPUT_DNA_SEQ_COLLECTION_NAME);

    JacksonDBCollection<ReactionPath, String> inputPathwayCollection = JacksonDBCollection.wrap(db.getCollection(inputPathwaysCollectionName), ReactionPath.class, String.class);
    JacksonDBCollection<DNADesign, String> dnaDesignCollection = JacksonDBCollection.wrap(db.getCollection(outputDnaDeqCollectionName), DNADesign.class, String.class);
    JacksonDBCollection<ReactionPath, String> outputPathwayCollection = JacksonDBCollection.wrap(db.getCollection(outputPathwaysCollectionName), ReactionPath.class, String.class);

    Map<String, Set<OrgAndEcnum>> proteinSeqToOrgInfo = new HashMap<>();

    ProteinsToDNA2 p2d = ProteinsToDNA2.initiate();

    DBCursor<ReactionPath> cursor = inputPathwayCollection.find();

    while (cursor.hasNext()) {
      ReactionPath reactionPath = cursor.next();

      Boolean atleastOneSeqMissingInPathway = false;
      List<Set<String>> proteinPaths = new ArrayList<>();

      for (Cascade.NodeInformation nodeInformation : reactionPath.getPath()) {

        if (!nodeInformation.getIsReaction()) {
          continue;
        }

        Set<String> proteinSeqs = new HashSet<>();

        for (Long id : nodeInformation.getReactionIds()) {
          // Get the reaction
          Reaction reaction = reactionDB.getReactionFromUUID(id);

          for (JSONObject data : reaction.getProteinData()) {
            // Get the sequences
            if (data.has("sequences")) {
              JSONArray seqs = data.getJSONArray("sequences");

              for (int i = 0; i < seqs.length(); i++) {
                Long s = seqs.getLong(i);

                if (s != null) {
                  Seq sequenceInfo = reactionDB.getSeqFromID(s);
                  String dnaSeq = sequenceInfo.getSequence();

                  if (dnaSeq != null && dnaSeq.length() > 80 && dnaSeq.charAt(0) == 'M') {
                    proteinSeqs.add(dnaSeq);
                    OrgAndEcnum orgAndEcnum = new OrgAndEcnum(sequenceInfo.getOrgName(), sequenceInfo.getEc());
                    if (!proteinSeqToOrgInfo.containsKey(dnaSeq)) {
                      proteinSeqToOrgInfo.put(dnaSeq, new HashSet<>());
                    }
                    proteinSeqToOrgInfo.get(dnaSeq).add(orgAndEcnum);
                  }
                }
              }
            }
          }
        }

        if (proteinSeqs.size() == 0) {
          LOGGER.error("The reaction does not have any viable protein sequences");
          atleastOneSeqMissingInPathway = true;
          break;
        }

        // Now we select two representative protein seqs from the reaction. In order to do this deterministically,
        // we sort and pick the first and middle index protein seqs.
        List<String> proteinSeqArray = new ArrayList<>(proteinSeqs);
        Collections.sort(proteinSeqArray);

        int firstIndex = 0;
        int middleIndex = proteinSeqs.size() / 2;

        // get first seq
        Set<String> combination = new HashSet<>();
        combination.add(proteinSeqArray.get(firstIndex));

        // get middle index of the protein seq array
        if (proteinSeqs.size() > 1) {
          combination.add(proteinSeqArray.get(middleIndex));
        }

        proteinPaths.add(combination);
      }

      // We only compute the dna design if we can find atleast one sequence for each reaction in the pathway.
      if (!atleastOneSeqMissingInPathway) {
        Set<List<String>> pathwayProteinCombinations = getPathwayProteinCombinations(proteinPaths);
        Set<DNAOrgECNum> dnaDesigns = new HashSet<>();

        for (List<String> proteinsInPathway : pathwayProteinCombinations) {
          try {
            Construct dna = p2d.computeDNA(proteinsInPathway, Host.Ecoli);

            Set<Set<OrgAndEcnum>> seqMetadata = new HashSet<>();
            for (String protein : proteinsInPathway) {
              seqMetadata.add(proteinSeqToOrgInfo.get(protein));
            }

            DNAOrgECNum instance = new DNAOrgECNum(dna.toSeq(), seqMetadata, proteinsInPathway.size());
            dnaDesigns.add(instance);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }

        DNADesign dnaDesignSeq = new DNADesign(dnaDesigns);
        WriteResult<DNADesign, String> result = dnaDesignCollection.insert(dnaDesignSeq);
        String id = result.getSavedId();
        reactionPath.setDnaDesignRef(id);
      }

      outputPathwayCollection.insert(reactionPath);
    }
  }
}
