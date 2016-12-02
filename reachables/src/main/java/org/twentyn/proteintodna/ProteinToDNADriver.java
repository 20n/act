package org.twentyn.proteintodna;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.reachables.Cascade;
import com.act.reachables.ReactionPath;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.lang3.tuple.Pair;
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

  public static <T> Set<List<T>> getCombinations(List<List<T>> lists) {
    Set<List<T>> combinations = new HashSet<List<T>>();
    Set<List<T>> newCombinations;

    int index = 0;

    // extract each of the integers in the first list
    // and add each to ints as a new list
    for(T i: lists.get(0)) {
      List<T> newList = new ArrayList<T>();
      newList.add(i);
      combinations.add(newList);
    }
    index++;
    while(index < lists.size()) {
      List<T> nextList = lists.get(index);
      newCombinations = new HashSet<List<T>>();
      for(List<T> first: combinations) {
        for(T second: nextList) {
          List<T> newList = new ArrayList<T>();
          newList.addAll(first);
          newList.add(second);
          newCombinations.add(newList);
        }
      }
      combinations = newCombinations;

      index++;
    }

    return combinations;
  }

  public static void main(String[] args) throws Exception {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "validator_profiling_2");
    MongoClient client = new MongoClient(new ServerAddress("localhost", 27017));
    DB db = client.getDB("wiki_reachables");
    String collectionName = "vanillin_pathways";

    JacksonDBCollection collection = JacksonDBCollection.wrap(db.getCollection(collectionName), ReactionPath.class, String.class);
    JacksonDBCollection<DNADesign, String> coll = JacksonDBCollection.wrap(db.getCollection("dna_designs_3"), DNADesign.class, String.class);
    JacksonDBCollection col2 = JacksonDBCollection.wrap(db.getCollection("pathways_vijay_3"), ReactionPath.class, String.class);

    Map<String, List<String>> proteinSeqToOrgInfo = new HashMap<>();

    ProteinsToDNA2 p2d = ProteinsToDNA2.initiate();

    DBCursor cursor = collection.find();
    while (cursor.hasNext()) {
      ReactionPath reactionPath = (ReactionPath) cursor.next();

      Boolean noSeq = false;

      List<List<String>> proteinPaths = new ArrayList<>();

      for (Cascade.NodeInformation nodeInformation : reactionPath.getPath()) {

        if (nodeInformation.getIsReaction()) {
          // Pick two from here
          List<String> proteinSeqs = new ArrayList<>();

          for (Long id : nodeInformation.getReactionIds()) {

            // Get the reaction
            Reaction reaction = mongoDB.getReactionFromUUID(id);

            for (JSONObject data : reaction.getProteinData()) {

              // Get the sequences
              if (data.has("sequences")) {
                JSONArray seqs = data.getJSONArray("sequences");
                for (int i = 0; i < seqs.length(); i++) {
                  Long s = seqs.getLong(i);
                  if (s != null) {
                    Seq sequenceInfo = mongoDB.getSeqFromID(s);
                    String dnaSeq = sequenceInfo.getSequence();
                    if (dnaSeq != null && dnaSeq.length() > 80 && dnaSeq.charAt(0) == 'M') {
                      proteinSeqs.add(dnaSeq);

                      List<String> twoArray = new ArrayList<>();
                      twoArray.add(sequenceInfo.getOrgName());
                      twoArray.add(sequenceInfo.getEc());

                      proteinSeqToOrgInfo.put(dnaSeq, twoArray);

                      if (proteinSeqs.size() > 2) {
                        break;
                      }
                    }
                  }
                }
              }
            }
          }

          Collections.sort(proteinSeqs);

          if (proteinSeqs.size() == 0) {
            LOGGER.error("should have atleast one seq");
            noSeq = true;
            break;
          }

          List<String> combination = new ArrayList<>();

          // get first seq
          combination.add(proteinSeqs.get(0));

          if (proteinSeqs.size() > 1) {
            combination.add(proteinSeqs.get(proteinSeqs.size()/2));
          }

          proteinPaths.add(combination);
        }
      }

      if (!noSeq) {
        Set<List<String>> combinations = getCombinations(proteinPaths);
        Set<DNAOrgECNum> dnaDesigns = new HashSet<>();

        System.out.println(combinations.size());

        for (List<String> proteins : combinations) {
          try {
            Construct dna = p2d.computeDNA(proteins, Host.Ecoli);

            Set<List<String>> orgInfo = new HashSet<>();
            for (String protein : proteins) {
              orgInfo.add(proteinSeqToOrgInfo.get(protein));
            }

            DNAOrgECNum instance = new DNAOrgECNum(dna.toSeq(), orgInfo, proteins.size());
            dnaDesigns.add(instance);
            System.out.println(dna.toSeq());
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }

        DNADesign dnaDesignSeq = new DNADesign(dnaDesigns);
        WriteResult<DNADesign, String> result = coll.insert(dnaDesignSeq);
        String id = result.getSavedId();
        reactionPath.setDnaDesignRef(id);
      }

      col2.insert(reactionPath);
    }
  }
}
