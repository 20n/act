package act.installer.patents;

import act.installer.WebData;

import java.net.URL;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.XML;

import act.client.CommandLineRun;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.MongoDBToJSON;
import com.mongodb.DBObject;

import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

public class FTO extends WebData {
  public void addPatents(MongoDB db, String patents_file, Set<String> priority_chems_files) {

    // first get all chemicals in the db
    System.out.println("reading all chemicals for patent lookup");
    Map<String, Long> all_db_chems = db.constructAllInChIs();
    // also the set tagged as priority to be looked up first
    Set<String> priority_chemicals = new HashSet<String>();

    // read the cached patents file (inchi<TAB>json_patents)
    System.out.println("reading patents for chemicals");
    try {
      // read list of chemicals tagged as priority,
      // these could be the reachables, or others...
      for (String priority_chems_file : priority_chems_files)
        priority_chemicals.addAll(readChemicalsFromFile(priority_chems_file));

      // now read and install into DB chemicals for
      // whom the patents were pulled in a past run
      // and cached in patents_file
      BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(patents_file))));
      String patentline;
      while ((patentline = br.readLine()) != null) {
        JSONObject cached = deconstruct_cache_format(patentline);
        String chem = cached.getString("inchi");
        Integer num_patents = cached.getInt("num_patents");
        JSONArray patents_json_cached = cached.getJSONArray("patents_json");

        // now install the data (that we just paged in) into the DB
        DBObject patents = MongoDBToJSON.conv(patents_json_cached);

        String inchi = CommandLineRun.consistentInChI(chem, "Adding patents");
        // install the patents data into the db
        // 1. update the chemical entry to point to all these patents
        // 2. update the patents collection with the (patent_id, scores, patent_text)
        db.updateChemicalWithPatents(inchi, num_patents, patents);

        // mark this chemical as installed in the db
        all_db_chems.remove(inchi);
        // in case this was a priority chemical, remove from that set too
        priority_chemicals.remove(inchi);
      }
      br.close();
    } catch (FileNotFoundException e) {
      // this happens when initializing the DB completely from
      // scratch, and not even a single chemical has been looked up
      // Ignore, as the lookups below will initialize a file...

    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("\nFTO Search: Installing from patents cached file: Done.\n");

    // the remaining inchis in all_db_chems that did not have a patent
    Integer num_patents = null;
    JSONArray patents_json = null;
    String inchi = null;
    try {
      // open the patents cache file for append now
      PrintWriter patents_cache = new PrintWriter(new BufferedWriter(new FileWriter(patents_file, true)));

      status_total = all_db_chems.size();

      for (String chem : priority_chemicals) {
        retrieveFromGooglePatents(chem, patents_cache, db);

        // mark this chemical as installed in the db
        all_db_chems.remove(chem);
      }
      System.out.println("\nFTO Search: Priority chemicals: Done.\n");

      // now pull the remaining chemicals in the dataset
      for (String chem : all_db_chems.keySet()) {
        retrieveFromGooglePatents(chem, patents_cache, db);
      }
      System.out.println("\nFTO Search: All chemicals: Done.\n");

      patents_cache.close();
    } catch (IOException e) {
      System.out.println("FTO Search: CRITICAL ERROR. Opening cache file " + patents_file + " failed. Abort."); System.exit(-1);
    } catch (Exception e) {
      System.out.println("FTO Searcg: SHOULD NOT HAPPEN. Unexpected error. Exception: " + e.getMessage() + "\n\tinchi: " + inchi + "\n\tnum_patents: " + num_patents + "\n\tjson: " + patents_json);
      try { System.in.read(); } catch (Exception edum) {}
    }
  }

  private void retrieveFromGooglePatents(String chem, PrintWriter patents_cache, MongoDB db) throws IOException {
    // call the web api to retrieve the results
    // and write to the cache
    int num_patents = apiCallCacheResults(chem, patents_cache, db);

    // report status to screen for running count
    logStatusToConsole(num_patents);
  }

  int apiCallCacheResults(String inchi, PrintWriter patents_cache, MongoDB db) throws IOException {

    // Dont waste time processing big molecules from MetaCyc with FAKE inchi
    if (inchi.startsWith("InChI=/FAKE"))
      return 0;

    // get vendors by searching Google Patent
    // note that this can return an empty JSON
    JSONArray patents_json = new JSONArray();
    Integer num_patents = 0;

    patents_json = googleQuery(inchi);
    num_patents = patents_json.length();

    DBObject patents = MongoDBToJSON.conv(patents_json);
    // add these patents to db
    // 1. update the chemical entry to point to all these patents
    // 2. update the patents collection with the (patent_id, scores, patent_text)
    db.updateChemicalWithPatents(inchi, num_patents, patents);

    // concatenate the retrieved vendors to this.chem_vendors file
    // so that for this chemical we dont have to retrieve the
    // vendors again in the future

    patents_cache.println(cache_format(inchi, num_patents, patents_json));
    patents_cache.flush();

    return num_patents;
  }

  JSONArray googleQuery(String inchi) throws IOException {
    FTO_GoogleNonAPISearch google = new FTO_GoogleNonAPISearch();

    // get all patent #s corresponding to these keywords
    Set<String> patentIDs = google.GetPatentIDs(inchi);

    // for each of those patent #s, get their plain text
    Map<String, String> patentText = new HashMap<String, String>();
    // use the plain text of the patent to score its relevance to biosynthesis
    Map<String, Double> patentScores = new HashMap<String, Double>();

    for (String patentID : patentIDs) {
      String plaintext = google.GetPatentText(patentID);
      patentText.put(patentID, plaintext);

      double score = FTO_PatentScorer_TrainedModel.getModel().ProbabilityOf(plaintext);
      patentScores.put(patentID, score);
    }

    JSONArray patents = new JSONArray();
    for (String patentID : patentIDs) {
      JSONObject found = new JSONObject();
      found.put("patent_num", patentID);
      found.put("patent_txt", patentText.get(patentID));
      found.put("likely_biosynthesis", patentScores.get(patentID));
      patents.put(found);
    }

    return patents;
  }

  // this function should be in sync with the fn deconstruct_cache_format below
  String cache_format(String inchi, Integer count, JSONArray json) {
    return inchi + "\t" +
            count + "\t" +
            json.toString();
  }

  // this function should be in sync with the fn cache_format above
  JSONObject deconstruct_cache_format(String cache_line) {
    String[] tokens = cache_line.split("\t");
    JSONObject cache_read = new JSONObject();
    cache_read.put("inchi"    , tokens[0]);
    cache_read.put("count"    , Integer.parseInt(tokens[1]));
    cache_read.put("json"     , new JSONArray(tokens[2]));
    return cache_read;
  }

}
