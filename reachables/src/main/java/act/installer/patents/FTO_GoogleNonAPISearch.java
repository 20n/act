package act.installer.patents;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import act.shared.helpers.P;

public class FTO_GoogleNonAPISearch {

  private final boolean _AddSynonymsToQuery = true;
  private final boolean _UseGoogleCustomSearchAPI = false;

  private final String _PatentCacheRootDir = "FTO_patents_cached";

  FTO_GoogleNonAPISearch() { }

  public String GetPatentText(String id) throws IOException {
    return FTO_Utils.GetPatentText(id);
  }

  public Set<String> GetPatentIDs(String inchi) throws IOException {
    // use the "common_name" to get all synonyms
    List<String> names = namesFromPubchem(inchi, true);
    // get all patent #s that mention these names
    Set<String> idSet = queryGoogleForPatentIDs(null, names);

    return idSet;
  }

  private Set<String> queryGoogleForPatentIDs(String common_name, List<String> names) throws IOException {
    if (!_AddSynonymsToQuery && common_name != null) {
      names = new ArrayList<String>();
      names.add(common_name);
    }

    // String searchPhrase = "(cerevisiae OR coli) AND (";
    String searchPhrase = "(yeast OR cerevisiae OR coli) AND (";
    searchPhrase+= "\"" + names.get(0) + "\"";
    for(int i=1; i<names.size(); i++) {
      String str = names.get(i);
      searchPhrase+= " OR ";
      searchPhrase+= "\"" + str + "\"";
    }
    searchPhrase+= ")";

    Set<String> idSet = new HashSet<String>();
    if (_UseGoogleCustomSearchAPI)
      idSet.addAll(QueryGoogleAPI.query(searchPhrase));
    idSet.addAll(QueryGooglePatents_NonAPI.query(searchPhrase));
    return idSet;
  }


  private List<String> namesFromPubchem(String name, boolean inputIsInChI) throws IOException {
    List<String> out = new ArrayList<>();

    // Query pubchem for synonyms
    String jsonStr;
    if (inputIsInChI) {
      String base = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/inchi/synonyms/json";
      List<P<String, String>> post_data = new ArrayList<>();
      post_data.add(new P<String, String>("inchi", name));
      jsonStr = FTO_Utils.fetch(base, post_data);
    } else {
      String base = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/";
      name = URLEncoder.encode(name, "UTF-8");
      String pubchem_query = base + name + "/synonyms/json";
      jsonStr = FTO_Utils.fetch(pubchem_query);
    }
    
    JSONObject json = new JSONObject(jsonStr);
    JSONObject InformationList = json.getJSONObject("InformationList");
    JSONArray Information = InformationList.getJSONArray("Information");
    JSONObject data = Information.getJSONObject(0);
    JSONArray Synonym = data.getJSONArray("Synonym");
    for(int i=0; i < Synonym.length(); i++) {
      String syn = Synonym.getString(i);

      out.add(syn);
      if(out.size() > 5) {
        break;
      }
    }

    return out;
  }

  private Map<String, Double> scorePatents(Set<String> idSet) { 
    // Collect up all the patent IDs
    Map<String, Double> patentScores = new HashMap<>();
    
    // For each patent ID
    // Fetch the text of the patent
    // Score the patent based on FTO 
    // Add the score and the patent ID to the Map
    for(String id : idSet) {
      String text = null;
      try {
        // Try to fetch it from disk first
        // text = readPatentFromDisk(id);
          
        // If not, get it online
        if (text == null) {
          text = FTO_Utils.GetPatentText(id);
        }
          
        // The Raw score is now not exposed by the PatentScorer
        // Instead it provides a computed probability
        // int rawScore = FTO_PatentScorer_TrainedModel.getModel().ScoreText(text);
        // savePatentToDisk(id, text, rawScore);

        // normalize to a probability of it being a biosynthesis patent
        double probability = FTO_PatentScorer_TrainedModel.getModel().ProbabilityOf(text);
          
        patentScores.put(id, probability);
      } catch (Exception ex) {
        System.err.println("error on " + id);
        ex.printStackTrace();
        System.err.println("\t" + ex.getMessage());
      }
    }
    
    return patentScores;
  }

  // Standalone wrapper around the entire functionality in this file
  // Only used for testing, and maybe calls from experimental/
  // But in main trunk, this is not used. Instead, in FTO.java calls
  // other functions directly to take:
  // inchis -> patent IDs -> patent texts -> scores
  private Map<String, Double> FTO_WriteToDisk(String common_name) throws Exception {

    // use the "common_name" to get all synonyms
    List<String> names = namesFromPubchem(common_name, false);
    // get all patent #s that mention these names
    Set<String> idSet = queryGoogleForPatentIDs(common_name, names);

    // score each patent based on its text
    Map<String, Double> results = scorePatents(idSet);
    System.out.println("Scored patents: " + results.size());
    
    // write the output to the directory with "common_name"
    File dir1 = new File(_PatentCacheRootDir);
    File dir2 = new File(_PatentCacheRootDir + "/" + common_name);
    boolean made1 = true, made2 = true;
    if(!dir1.exists()) { made1 = dir1.mkdir(); }
    if(!dir2.exists()) { made2 = dir2.mkdir(); }
    if (!made1 || !made2) { 
      throw new Exception("Could not make cache dir: " + dir2.getAbsolutePath()); 
    }
    
    StringBuilder sb = new StringBuilder();
    for(String str : results.keySet()) {
      Double probability = results.get(str);
      sb.append(str + "\t" + probability + System.getProperty("line.separator"));
    }
    FTO_Utils.writeFile(sb.toString(), dir2.getAbsolutePath() + "/" + common_name + "_fto.txt");

    return results;
  }

  // Only used for testing. See comment on FTO_WriteToDisk(..)
  public Map<String, Double> FTO_WriteToDisk(String common_name, double probability_threshold) throws Exception {
    Map<String, Double> all_patents = FTO_WriteToDisk(common_name);
    Map<String, Double> thresholded = new HashMap<>();
    for (String pid : all_patents.keySet())
      if (all_patents.get(pid) <= probability_threshold)
        thresholded.put(pid, all_patents.get(pid));
    return thresholded;
  }

  public static void main(String[] args) throws Exception {
    FTO_GoogleNonAPISearch google = new FTO_GoogleNonAPISearch(); 
    google.FTO_WriteToDisk(args[0]);
  }

}

class QueryGoogleAPI {
// You can find the CSE and API_KEY here:
// https://cse.google.com/cse/all
//   - Go to the custom engine you created (& paid)
//   - Edit Search Engine -> Business -> XML & JSON
// Custom search engine identifier from above
// API_KEY is the key allowing us to access google apis
  private static final String CSE="017093137427180703493:nlngl0nhkxg";
  private static final String API_KEY="AIzaSyCMGfdDaSfjqv5zYoS0mTJnOT3e9MURWkU";

// curl -s "https://www.googleapis.com/customsearch/v1?key=$API_KEY&cx=$CSE&q=$QUERY" > $QUERY.firstpage
// "template": "https://www.googleapis.com/customsearch/v1?q={searchTerms}&num={count?}&start={startIndex?}&lr={language?}&safe={safe?}&cx={cx?}&cref={cref?}&sort={sort?}&filter={filter?}&gl={gl?}&cr={cr?}&googlehost={googleHost?}&c2coff={disableCnTwTranslation?}&hq={hq?}&hl={hl?}&siteSearch={siteSearch?}&siteSearchFilter={siteSearchFilter?}&exactTerms={exactTerms?}&excludeTerms={excludeTerms?}&linkSite={linkSite?}&orTerms={orTerms?}&relatedSite={relatedSite?}&dateRestrict={dateRestrict?}&lowRange={lowRange?}&highRange={highRange?}&searchType={searchType}&fileType={fileType?}&rights={rights?}&imgSize={imgSize?}&imgType={imgType?}&imgColorType={imgColorType?}&imgDominantColor={imgDominantColor?}&alt=json"

  public static Set<String> query(String searchPhrase) throws IOException {
    Set<String> idSet = new HashSet<>();
    searchPhrase = URLEncoder.encode(searchPhrase, "UTF-8");
    String base = "https://www.googleapis.com/customsearch/v1?key=" + API_KEY + "&cx=" + CSE + "&q=" + searchPhrase;

    boolean hasNext = true;
    int start = 0;
    int page = 0;
    while (hasNext) {
      String url = base + "&num=10" + (start == 0 ? "" : "&start=" + start);
      String json_str = FTO_Utils.fetch(url);
      JSONObject json = new JSONObject(json_str);
      System.out.println("Total: " + json.getJSONObject("searchInformation").get("totalResults"));
      String filepath = "google_cse:page" + page;
      FTO_Utils.writeFile(json.toString(2), filepath);

      JSONObject meta = json.getJSONObject("queries");
      Set<String> patentIDs = extractAllIDs(json.getJSONArray("items"));
      idSet.addAll(patentIDs);

      int this_count = meta.getJSONArray("request").getJSONObject(0).getInt("count");
      System.out.println("\t Got: " + start + " -> " + (start + this_count));

      if (meta.has("nextPage")) {
        // start = meta.getJSONArray("nextPage").getJSONObject(0).getInt("startIndex");
        start += this_count; 
        page++;
      } else {
        hasNext = false;
      }
    }

    return idSet;
  }

  private static Set<String> extractAllIDs(JSONArray items) {
    Set<String> idSet = new HashSet<>();
    String prefix = "patents/";
    for (int i=0; i<items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      String url = item.getString("link");
      // e.g., http://www.google.com/patents/CN102406082A?cl=en"
      // e.g., https://www.google.com/patents/US7201928
      // because it might be https or http, we look for patents/ and chop..
      String pid = url.substring(url.indexOf(prefix) + prefix.length());
      int suffix = pid.indexOf("?");
      if (suffix != -1)
        pid = pid.substring(0, suffix);
      idSet.add(pid);
    }
    return idSet;
  }
}

/* 
  This class is the naive crawler that traverses google search result pages
   pretending to be a human (which is why the delay(5) seconds.
  We can make this much faster by using Google's custom search engines and 
   querying within our quota, without delays...
*/
class QueryGooglePatents_NonAPI {

  private static final int MAX_RESULTS = 10000;
  public static Set<String> query(String searchPhrase) throws IOException {
    Set<String> idSet = new HashSet<>();
    searchPhrase = URLEncoder.encode(searchPhrase, "UTF-8");
    
    String base = "https://www.google.com/search?q=" + searchPhrase + "&num=100&biw=1440&bih=557&tbm=pts&start=INSERTSTARTINDEX&sa=N";
    // String base = "https://www.google.com/?tbm=pts&gws_rd=ssl#tbm=pts&q=Bactericide+composition+and+abietic+methanol+bacteria+coli&num=100&biw=1440&bih=557&tbm=pts&start=INSERTSTARTINDEX&sa=N";

    outer: for(int i=0; i<MAX_RESULTS; i++) {
      String pageStart = Integer.toString(i*100);
      String url = base.replaceAll("INSERTSTARTINDEX", pageStart);
      try {
        FTO_Utils.delay(5);
        String text = FTO_Utils.fetch(url);
        List<String> ids = extractAllIDs(text);

        //Break if it is duplicating a page
        boolean breakit = true;
        inner: for(String str : ids) {
          if(!idSet.contains(str)) {
            breakit = false;
            break inner;
          }
        }
        if(breakit) {
          break outer;
        }

        idSet.addAll(ids);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  
    return idSet;
  }
  private static List<String> extractAllIDs(String text) {
    //First extract all urls that are within quotes
    List<String> results = new ArrayList<>();
    String patternString = "(?<=\")https://www.google.com/patents/[^\"]+(?=\")";

    Pattern patt = Pattern.compile(patternString);

    Matcher matcher = patt.matcher(text);
    boolean matches = matcher.matches();
    
    int count = 0;
    while(matcher.find()) {
      count++;
      results.add(text.substring(matcher.start(), matcher.end()));
    }
    
    // Clean up each URL to just the id
    List<String> out = new ArrayList<>();
    for(String rawurl : results) {
      String[] split = rawurl.split("[^0-9a-zA-Z]");
      String id = split[7];
      if(id.equals("related")) {
          continue;
      }
      if(!out.contains(id)) {
          out.add(id);
      }
    }
    return out;
  }
}

class FTO_PatentScorer_TrainedModel {

  private Map<String, Integer> model;
  private Double modelNormalizationParam;

  private final String _RootDir = "FTO_training";
  private final String _NegDataSet = _RootDir + "/bioneg";
  private final String _PosDataSet = _RootDir + "/biopos";
  private final String _ChemNegDataSet = _RootDir + "/chemneg";
  private final String _ChemPosDataSet = _RootDir + "/chempos";

  private static FTO_PatentScorer_TrainedModel instance = null;
  public static FTO_PatentScorer_TrainedModel getModel() {
    if (instance == null) {
      instance = new FTO_PatentScorer_TrainedModel();
    }
    return instance;
  }

  private FTO_PatentScorer_TrainedModel() {
    initModel();

    dumpValidationAgainstTrainingData();
  }

  public double ProbabilityOf(String text) {
    return NormalizeScoreToProbability(ScoreText(text));
  }

  private double NormalizeScoreToProbability(int score) {
    // normalization function is 1-e(-B x score)
    // where B is calculated optimally from the dataset
    return 1 - Math.exp(-this.modelNormalizationParam * score);
  }

  private int ScoreText(String text) {
    int out = 0;
    Set<String> extract = extractTokens(text);
    for(String str : this.model.keySet()) {
      if (extract.contains(str)) {
        out += this.model.get(str);
      }
    }
    return out;
  }

  private void initModel() {
    // check that there are training files in the positive, negative datasets
    if (!FTO_Utils.filesPresentIn(_PosDataSet) || !FTO_Utils.filesPresentIn(_NegDataSet)) {
      System.out.println("First time initialization. Downloading training set.");
      DownloadTrainingDataSets();
    }

    Map<String, Integer> pattern = calculatePattern(_NegDataSet, _PosDataSet);
    this.model = pattern;

    Double normParam = calculateNormalizationParam(_NegDataSet, _PosDataSet);
    this.modelNormalizationParam = normParam;

    System.out.println("FTO: Pattern size = " + pattern.size());
    System.out.println("FTO: 1-exp(-Bx) norm. B = " + normParam);
  }

  private void DownloadTrainingDataSets() {
    // download text for biosynthesis and chemosynthesis datasets
    CreateBiosynthesisDataSet();
    CreateChemosynthesisDataSet();
  }

  private void dumpValidationAgainstTrainingData() {
    try {
      // dump all scores and probabilities for training negatives
      File dir = new File(_NegDataSet);
      for(File fily : dir.listFiles()) {
        String text = FTO_Utils.readFile(fily.getAbsolutePath());
        dumpScoreProbability("-", fily.getName(), text);
      }

      // dump all scores and probabilities for training positives
      dir = new File(_PosDataSet);
      for(File fily : dir.listFiles()) {
        String text = FTO_Utils.readFile(fily.getAbsolutePath());
        dumpScoreProbability("+", fily.getName(), text);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void dumpScoreProbability(String posOrNeg, String name, String text) {
    double probability = ProbabilityOf(text);
    System.out.println(posOrNeg + "\t" + name + "\t" + probability);
  }

  private final int CUTOFF = 5;
  private Map<String, Integer> calculatePattern(String negDir, String posDir) {
    Map<String, Integer> negs = readFolderAndHashOut(negDir);
    Map<String, Integer> poss = readFolderAndHashOut(posDir);
    Map<String, Integer> pattern = new HashMap<>();

    for(String str : negs.keySet()) {
      Integer negvalue = negs.get(str);
      if(poss.containsKey(str)) {
        Integer posvalue = poss.get(str);
        Integer newval = posvalue-negvalue;
        if (newval > CUTOFF) {
          pattern.put(str, newval);
        }
      }
    }
    return pattern;
  }

  private Double calculateNormalizationParam(String negDir, String posDir) {
    Set<Integer> negs = scoreFolder(negDir);
    Set<Integer> poss = scoreFolder(posDir);
    
    Double Lp = average(poss), Hn = average(negs);
    if (Lp < Hn) {
      System.out.println("FTO: Error. Centroid of +ves < -ves. Bad training data.");
      System.out.println("FTO: This means that on average the +ve patents score.");
      System.out.println("FTO: less than the -ve patents; but higher scores are");
      System.out.println("FTO: supposed to mean more +ve. Abort!");
      System.exit(-1);
    }

    // fit a 1-e(-B * x) curve to the positive and negative dataset
    // where B is a positive real, which is learnt by maximizing 
    // the distance between the average of the negatives Hn and 
    // the average of the positives Lp. 
    // Maximization occurs where 
    //      d/dB( e(-Hn * B) - e(-Lp * B) ) = 0
    //      i.e., Hn * e(-Hn * B) = Lp * e(-Lp * B) - solve for B
    //      Or Log(Lp/Hn) = B(Lp - Hn)
    //      Or B = Log(Lp/Hn)/(Lp - Hn)
    Double B = Math.log(Lp/Hn) / (Lp - Hn);
  
    return B;
  }

  private Double average(Set<Integer> S) {
    Double avg = 0.0;
    int sz = S.size();
    for (Integer i : S) avg += (double)i/(double)sz;
    return avg;
  }

  private Set<Integer> scoreFolder(String path) {
    try {
      Set<Integer> out = new HashSet<>();
      File dir = new File(path);
      for(File afile : dir.listFiles()) {
        String text = FTO_Utils.readFile(afile.getAbsolutePath());
        out.add(ScoreText(text));
      }
      return out;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
      return null;
    }
  }

  private Map<String, Integer> readFolderAndHashOut(String path) {
    try {
      Map<String, Integer> out = new HashMap<>();
      File dir = new File(path);
      for(File afile : dir.listFiles()) {
        String text = FTO_Utils.readFile(afile.getAbsolutePath());
        Set<String> extract = extractTokens(text);
        for(String str : extract) {
          Integer value = 0;
          if(out.containsKey(str)) {
            value = out.get(str);
          }
          value++;
          out.put(str, value);
        }
      }
      return out;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
      return null;
    }
  }

  private Set<String> extractTokens(String text) {
    Set<String> out = new HashSet<>();
    String patternString = "[0-9a-zA-Z]+";

    Pattern patt = Pattern.compile(patternString);

    Matcher matcher = patt.matcher(text);
    boolean matches = matcher.matches();
    
    int count = 0;
    while(matcher.find()) {
      count++;
      out.add((text.substring(matcher.start(), matcher.end())).toLowerCase());
    }
    return out;
  }

  private void CreateBiosynthesisDataSet() {
    File training = new File(_RootDir);
    if(!training.exists()) {
      training.mkdir();
    }

    // Create a list of patent urls talking about biosynthesis
    List<String> positives = new ArrayList<>();
    positives.add("WO2012016177A2"); //Amyris farnesene
    positives.add("WO2013192543A2"); //Phytogene styrene
    positives.add("EP2438178A2"); //Genomatica BDO
    positives.add("US6194185");  //Wash U limonene
    positives.add("US8828693");  //isopropanol
    positives.add("EP1799828B1"); //phloroglucinol
    positives.add("CA2112374C"); //yeast xylitol
    positives.add("WO2013071112A1"); //yeast xylose
    positives.add("WO2014066892A1"); //Dupont isoprene
    positives.add("EP2252691B1"); //santalene Firmenich
    positives.add("US7374920"); //
    positives.add("US20120107893"); //Stephanopoulus very broad claim about something with indole and coli specifically
    positives.add("US8889381"); //A host cell, comprising a nucleic acid molecule encoding a cis-abienol synthase
    positives.add("US7238514"); //
    positives.add("US20130302861"); //mitochondrial targeting
    positives.add("US8062878"); //levopimaradiene synthase
    positives.add("US5994114"); //taxadiene synthase

    List<String> negatives = new ArrayList<>();
    negatives.add("US5274029"); //
    negatives.add("US3284393"); //
    negatives.add("US7141615"); //
    negatives.add("US3632822"); //
    negatives.add("US3787335"); //
    negatives.add("US8017658"); //
    negatives.add("WO2012173477A1"); //
    negatives.add("CN103275146A"); //
    negatives.add("CN103113443A"); //
    negatives.add("CN103755556A"); //
    negatives.add("US20130143826"); //
    negatives.add("WO2014078168A1"); //
    negatives.add("US20130005581"); //
    negatives.add("US20140303361"); //
    negatives.add("CN103193799A"); //
    negatives.add("CN103467567A"); //
    negatives.add("WO2002044197A2"); //
    negatives.add("US20140058063"); //
    negatives.add("EP2729123A2"); //
    negatives.add("US8470822"); //
    negatives.add("WO2014031646A3"); //
    negatives.add("CN102558143B"); //
    negatives.add("WO2000026174A2"); //
    negatives.add("US20110250626"); //coatings incorporating bioactive enzymes

    // NOT IN CHRIS' DATASET THAT HE SENT OVER....
    // negatives.add("US20130189677"); //terpenoid transporters

    negatives.add("US20090238811"); //Enzymatic antimicrobial and antifouling coatings
    negatives.add("US8846351"); //degrading cellulose
    negatives.add("US20100248334"); //Biological active coating components
    negatives.add("US20130338330"); //chemical synthesis
    negatives.add("US20130331342"); //hair/scalp care compositions
    negatives.add("CA2595380A1"); //Stabilized liquid polypeptide formulations

   
    File afile = new File(_PosDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : positives) {
      try {
        String text = FTO_Utils.GetPatentText(id);
        FTO_Utils.writeFile(text, _PosDataSet + "/" + id + ".txt");
      } catch(Exception err) {
        err.printStackTrace();
      }
    }
    
    afile = new File(_NegDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : negatives) {
      try {
        String text = FTO_Utils.GetPatentText(id);
        FTO_Utils.writeFile(text, _NegDataSet + "/" + id+".txt");
      } catch(Exception err) {
        err.printStackTrace();
      }
    }
  }

  private void CreateChemosynthesisDataSet() {
    File training = new File(_RootDir);
    if(!training.exists()) {
      training.mkdir();
    }
    
    // Create a list of patent urls talking about biosynthesis
    List<String> positives = new ArrayList<>();
    positives.add("US2623897"); //Galllic acid esters
    positives.add("WO2008065527A2"); //
    positives.add("US7045654"); //
    positives.add("US4788331"); //
    positives.add("EP0771782A1"); //
    positives.add("US2606186"); //
    positives.add("US1836568"); //
    positives.add("US2886438"); //
    positives.add("US6399810"); //
    positives.add("US2945068"); //
    positives.add("US2155856"); //
    
    List<String> negatives = new ArrayList<>();
    negatives.add("US6180666"); //use
    negatives.add("EP1159007A1"); //use
    negatives.add("EP2753336A1"); //use
    negatives.add("US3792014"); //use
    negatives.add("WO2011138345A2");
    negatives.add("US20100034762");
    negatives.add("WO2012131348A1");
    negatives.add("US6669964");
    negatives.add("WO2009084020A2");
    negatives.add("US2211485");
    negatives.add("US5223179");
    negatives.add("US20060286061");
    negatives.add("US5756446");
    negatives.add("EP2595599A1");
    negatives.add("US4368056");
    negatives.add("EP2582775A1");
    negatives.add("US4379168");
    negatives.add("US4915707");
    negatives.add("US6200625");
    negatives.add("USRE36982");
    negatives.add("US4818250");
    negatives.add("CA2118071C");
    negatives.add("US6194185");
    negatives.add("US5849680");
    negatives.add("WO1999021891A1");
    negatives.add("CA2492498C");
    negatives.add("US6342535");
    negatives.add("US5344776");
    negatives.add("US7622269");
    negatives.add("US20020058075");
    negatives.add("US20040204497");
    negatives.add("WO2014151732A1");
    negatives.add("EP2502621A1");
    negatives.add("US5427798");
    negatives.add("US6312716");
    negatives.add("WO1999038502A1");
    negatives.add("US6462237");
    negatives.add("EP2316456A1");
    negatives.add("WO1999038503A1");
    negatives.add("US4820522");
    negatives.add("EP2649993A1");
    negatives.add("US20110124718");
    negatives.add("US8518438");
    negatives.add("US20070237816");
    negatives.add("US8658631");
    negatives.add("US8609684");
    negatives.add("US20080293804");

    File afile = new File(_ChemPosDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : positives) {
      try {
        String text = FTO_Utils.GetPatentText(id);
        FTO_Utils.writeFile(text, _ChemPosDataSet + "/" + id + ".txt");
      } catch(Exception err) {
        err.printStackTrace();
      }
    }
    
    afile = new File(_ChemNegDataSet);
    if(!afile.exists()) {
        afile.mkdir();
    }
    for(String id : negatives) {
      try {
        String text = FTO_Utils.GetPatentText(id);
        FTO_Utils.writeFile(text, _ChemNegDataSet + "/" + id + ".txt");

        // We use the negatives in this training set to also serve as
        // training for the bio dataset; in addition to the chem dataset
        // This is because the bioalgorithm has already been seeded with
        // the positives, and could do with more negatives
        // FTO_Utils.writeFile(text, _NegDataSet + "/" + id + ".txt");

      } catch(Exception err) {
        err.printStackTrace();
      }
    }
  }
}

class FTO_Utils {

  public static String GetPatentText(String id) throws IOException {
    return fetch("https://www.google.com/patents/" + id);
  }

  public static String fetch(String link) throws IOException {
    URL url = new URL(link);
    String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.76 Safari/537.36";
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("GET");
    conn.setRequestProperty("User-Agent", USER_AGENT);

    int respCode = conn.getResponseCode();
    System.out.println("\nSearch Sending 'GET' request to URL : " + url);
    System.out.println("Response Code : " + respCode);

    if (respCode != 200) {
      throw new IOException(url + "\nGET returned not OK. Code = " + respCode);
    }

    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuffer resp = new StringBuffer();
    String inputLine;
    while ((inputLine = in.readLine()) != null) 
      resp.append(inputLine);
    in.close();

    return resp.toString();
  }

  public static String fetch(String link, List<P<String, String>> data) throws IOException {
    StringBuilder postData = new StringBuilder();

    for (P<String,String> param : data) {
      if (postData.length() != 0) postData.append('&');
      postData.append(URLEncoder.encode(param.fst(), "UTF-8"));
      postData.append('=');
      postData.append(URLEncoder.encode(String.valueOf(param.snd()), "UTF-8"));
    }
    byte[] postDataBytes = postData.toString().getBytes("UTF-8");

    URL url = new URL(link);
    String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.76 Safari/537.36";
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("POST");
    conn.setRequestProperty("User-Agent", USER_AGENT);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
    conn.setDoOutput(true);

    conn.getOutputStream().write(postDataBytes);

    int respCode = conn.getResponseCode();
    System.out.println("\nSearch Sending 'GET' request to URL : " + url);
    System.out.println("Response Code : " + respCode);

    if (respCode != 200) {
      throw new IOException(url + "\nGET returned not OK. Code = " + respCode);
    }

    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuffer resp = new StringBuffer();
    String inputLine;
    while ((inputLine = in.readLine()) != null) 
      resp.append(inputLine);
    in.close();

    return resp.toString();
  }

  // NOT USED
  private static String readPatentFromDisk(String id) throws IOException {
    String out = FTO_Utils.readFile("patents" + "/" + id.substring(0,4) + "/" + id + ".txt");;
    if (out == null || out.isEmpty()) {
      return null;
    }
    return out;
  }
  

  // NOT USED
  private static void savePatentToDisk(String id, String text, int score) {
    // Wierd! What does saving to disk have to do with CUTOFF_SCORES!
    // final int CUTOFF_SCORE = 700;
    // if(score < CUTOFF_SCORE) {
    //   text = Integer.toString(score);
    // }
    File dir = new File("patents");
    if(!dir.exists()) {
      dir.mkdir();
    }
    File subdir = new File("patents" + "/" + id.substring(0,4));
    if(!subdir.exists()) {
      subdir.mkdir();
    }
    String filename = "patents" + "/" + id.substring(0,4) + "/" + id + ".txt";
    String filepath = new File(filename).getAbsolutePath();
    FTO_Utils.writeFile(text, filepath);
  }

  public static boolean filesPresentIn(String dir) {
    File dirf = new File(dir);
    return dirf.isDirectory() && dirf.listFiles().length > 0;
  }

  public static String readFile(String path) throws IOException {
	  BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(path))));
		String line;
    StringBuffer sb = new StringBuffer();
		while ((line = br.readLine()) != null) {
      sb.append(line);
    }
    return sb.toString();
  }

  public static void writeFile(String datafile, String filePath) {
    try {
      Writer output = null;
      File file = new File(filePath);
      output = new FileWriter(file);
      output.write(datafile);
      output.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public static void delay(int seconds) {
    try {
      // long rand = 0;
      // while(rand < seconds*1000 || rand > seconds*2000) {
      //     rand = (long) (Math.random()*seconds*4000);
      // }
      long ms = seconds * (1000 + ((long) Math.random() * 1000));
      Thread.sleep(ms);
    } catch (InterruptedException ex) {
    }
  }

}
