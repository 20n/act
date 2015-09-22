package act.installer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mongodb.BasicDBObject;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.ReactionWithAnalytics;
import act.shared.helpers.P;

public class Rarity {
  private MongoDB DB;
  long lowUUID, highUUID;
  BufferedWriter log;
  String mongohost; int mongodbPort; String mongodatabase;

  public Rarity(long lowUUID, long highUUID, String mongohost, int mongodbPort, String mongodatabase) {
    this.mongohost = mongohost;
    this.mongodbPort = mongodbPort;
    this.mongodatabase = mongodatabase;
    openMongoConnection();
    this.lowUUID = lowUUID;
    this.highUUID = highUUID;
  }

  private void openMongoConnection() {
    this.DB = new MongoDB(this.mongohost, this.mongodbPort, this.mongodatabase);
  }

  public void installRarityMetrics() {
    try {
      log = new BufferedWriter(new FileWriter("rarity-log.txt"));

      HashMap<Long, Integer> frequency = computeCmpdFreqInDB();
      // this unused cofactor computation results in out of memory
      // List<Chemical> cofactors = findCofactors(frequency);
      updateEachReactionInDB(frequency);

      log.close();
    } catch (IOException e) {
      System.err.println("Could not write to log file.");
    }
  }

  private List<Chemical> findCofactors(HashMap<Long, Integer> freq) {
    List<P<Long, Integer>> mostFrequent = new ArrayList<P<Long, Integer>>();
    for (Long uuid: freq.keySet()) {
      int f = freq.get(uuid);
      int i = 0;
      while (i<mostFrequent.size() && mostFrequent.get(i).fst() >= f)
        i++;
      if (i < mostFrequent.size()) {
        mostFrequent.add(i, new P<Long, Integer>(uuid, f));
      } else {
        mostFrequent.add(new P<Long, Integer>(uuid, f));
      }
    }
    List<Chemical> cofactors = new ArrayList<Chemical>();
    long uuid;
    for (int i = 0; i<mostFrequent.size(); i++) {
      uuid = mostFrequent.get(i).fst();
      // pulling in the entire chemicals causes out of memory
      // since we are pulling in the full chemical which now has metadata from sigma, drugbank etc.
      Chemical chem = DB.getChemicalFromChemicalUUID(uuid);
      cofactors.add(chem);

      System.out.format("%d\tChem[%d] = %s\t%s\n", chem.getUuid(), i, chem.getBrendaNames().toString(), chem.getSmiles());
    }
    return cofactors;
  }

  private void updateEachReactionInDB(HashMap<Long, Integer> freq) {
    Float[] subProb, prdProb;
    Reaction r;
    DBIterator iterator = this.DB.getIteratorOverReactions(this.DB.getRangeUUIDRestriction(lowUUID, highUUID), false /* notimeout=false */, getKeys());
    Long[] sub, prd;
    while ((r = this.DB.getNextReaction(iterator)) != null) {
      sub = r.getSubstrates();
      prd = r.getProducts();
      subProb = new Float[sub.length];
      prdProb = new Float[prd.length];
      int totalSub = 0, totalPrd = 0;
      for (Long s : sub) totalSub += freq.get(s);
      for (Long p : prd) totalPrd += freq.get(p);
      for (int i = 0; i < sub.length; i++) subProb[i] =  (freq.get(sub[i]) / (float) totalSub);
      for (int i = 0; i < prd.length; i++) prdProb[i] =  (freq.get(prd[i]) / (float) totalPrd);
      ReactionWithAnalytics rExtra = new ReactionWithAnalytics(r, subProb, prdProb);
      this.DB.updateReaction(rExtra);

      System.out.println("Updated reaction " + r);
    }

  }

  private HashMap<Long, Integer> computeCmpdFreqInDB() {
    HashMap<Long, Integer> freq = new HashMap<Long, Integer>();
    Reaction r;
    DBIterator iterator = this.DB.getIteratorOverReactions(this.DB.getRangeUUIDRestriction(lowUUID, highUUID), false /* notimeout=false */, getKeys());
    while ((r = this.DB.getNextReaction(iterator)) != null) {
      for (Long s : r.getSubstrates())
        updateCount(freq, s);
      for (Long p : r.getProducts())
        updateCount(freq, p);
      System.out.println("Read reaction " + r);
    }
    try {
      log.write("Frequencies:\n" + freq + "\n");
    } catch (IOException e) {
      e.printStackTrace();
    }
    return freq;
  }

  private BasicDBObject getKeys() {
    BasicDBObject keys = new BasicDBObject();
    // keys.put(projection, 1); // 1 means include, rest are excluded
    return keys;
  }

  private void updateCount(HashMap<Long, Integer> freq, Long chemid) {
    if (!freq.containsKey(chemid))
      freq.put(chemid, 0);
    freq.put(chemid, freq.get(chemid) + 1);
  }
}
