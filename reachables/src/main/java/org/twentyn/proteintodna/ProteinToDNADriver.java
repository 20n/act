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
import java.util.stream.Collectors;

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

  public static final String HELP_MESSAGE =
      "This class is the driver to extract protein sequences from pathways and construct DNA designs from these proteins.";

  private static final CLIUtil CLI_UTIL = new CLIUtil(ProteinToDNADriver.class, HELP_MESSAGE, OPTION_BUILDERS);

  /**
   * This function get all protein combinations of a pathway from candidate protein sequences from each reaction on
   * the pathway.
   * @param listOfSetOfProteinSequences A list of sets of candidate protein sequences in the pathway
   * @return A set of all possible combinations of proteins from all the reactions in the pathway.
   */
  public static Set<List<String>> makePermutations(List<Set<String>> listOfSetOfProteinSequences) {
    Set<List<String>> accum = new HashSet<>();
    makePermutationsHelper(accum, listOfSetOfProteinSequences, new ArrayList<>());
    return accum;
  }

  private static void makePermutationsHelper(Set<List<String>> accum, List<Set<String>> input, List<String> prefix) {
    // Base case: no more sequences to add.  Accumulate and return.
    if (input.isEmpty()) {
      accum.add(prefix);
      return;
    }

    // Recursive case: iterate through next level of input sequences, appending each to a prefix and recurring.
    Set<String> head = input.get(0);
    // Avoid index out of bounds exception.
    List<Set<String>> rest = input.size() > 1 ? input.subList(1, input.size()) : Collections.emptyList();
    for (String next : head) {
      List<String> newPrefix = new ArrayList<>(prefix);
      newPrefix.add(next);
      makePermutationsHelper(accum, rest, newPrefix);
    }
  }

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

      for (Cascade.NodeInformation nodeInformation :
          reactionPath.getPath().stream().filter(nodeInfo -> nodeInfo.getIsReaction()).collect(Collectors.toList())) {

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

      if (atleastOneSeqMissingInPathway) {
        LOGGER.info(String.format("There is atleast one reaction with no sequence in reaction path id: %s", reactionPath.getId()));
      } else {
        // We only compute the dna design if we can find at least one sequence for each reaction in the pathway.
        Set<List<String>> pathwayProteinCombinations = makePermutations(proteinPaths);
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
            LOGGER.error("The error thrown while trying to call computeDNA", ex.getMessage());
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
