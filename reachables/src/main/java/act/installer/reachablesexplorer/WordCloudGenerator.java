package act.installer.reachablesexplorer;



import act.server.DBIterator;
import act.server.MongoDB;
import com.act.jobs.FileChecker;
import com.mongodb.BasicDBObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class WordCloudGenerator {

  /**
   * This class allow the generation of wordclouds, using R, for any inchi having a Bing reference.
   * It requires an R script, that takes an InChI as argument and writes a word cloud to a file
   */

  private static final String RSCRIPT_LOCATION = "src/main/java/act/installer/reachablesexplorer/RWordCloudGenerator.R";
  private static final String ASSETS_LOCATION = "/mnt/data-level1-1/data/reachables-explorer-rendering-cache";
  private static final Logger LOGGER = LogManager.getFormatterLogger(WordCloudGenerator.class);
  private static final String PNG_EXTENSION = ".png";


  private File rScript;

  private String host;
  private Integer port;
  private String database;


  public WordCloudGenerator(String host, Integer port, String database) {
    this.host = host;
    this.port = port;
    this.database = database;


    rScript = new File(RSCRIPT_LOCATION);
    try {
      FileChecker.verifyInputFile(rScript);
    } catch (IOException e) {
      System.out.println("Error reading r script");
    }
  }


  public List<String> getBingInchis() {
    MongoDB bingDb = new MongoDB(host, port, database);

    BasicDBObject query = new BasicDBObject("xref.BING.metadata.usage_terms.0", new BasicDBObject("$exists", true));
    BasicDBObject keys = new BasicDBObject("InChI", true);

    DBIterator ite = bingDb.getIteratorOverChemicals(query, keys);
    List<String> bingList = new ArrayList<>();
    while (ite.hasNext()) {
      BasicDBObject o = (BasicDBObject) ite.next();
      String inchi = o.getString("InChI");
      if (inchi != null) {
        bingList.add(inchi);
      }
    }
    return bingList;
  }



  public static File getWordcloudFile(String inchi) {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String wordcloudFilename = String.join("", "wordcloud", postfix);

    return Paths.get(ASSETS_LOCATION, wordcloudFilename).toFile();
  }

  public File generateWordCloud(String inchi) throws IOException {

    // TODO: improve wordcloud generation. Currently, each instance open a mongo connection on the R side.
    // By doing data manipulation in Java and utilizing Rengine, we could make this much better
    // Wordclouds could be generated ahead of time this way, using the inchi coprus

    File wordcloud = getWordcloudFile(inchi);

    if (!Files.exists(wordcloud.toPath())) {
      try {
        // TODO: this call a CL to run the R script. Maybe use Rengine instead?
        String cmd = String.format("Rscript %s %s %s %s %s", rScript.getAbsolutePath(), inchi, wordcloud.getAbsolutePath(), host, port);
        Runtime.getRuntime().exec(cmd);
        FileChecker.verifyInputFile(wordcloud);
      } catch (IOException e) {
        LOGGER.error("Unable to generate wordcloud for %s at location %s", inchi, wordcloud.toPath().toString());
        return null;
      }
    }
    return wordcloud;
  }

  // TODO: remove main method when done testing
  public static void main(String[] args) {
    WordCloudGenerator g = new WordCloudGenerator("localhost", 27017, "actv01");
    try {
      g.generateWordCloud("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)");
    } catch (IOException e) {System.out.println(String.format("Caught expection %s", e.getMessage()));}
  }

}
