package act.installer.reachablesexplorer;


import act.server.MongoDB;
import act.shared.Chemical;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import com.act.jobs.FileChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.codec.digest.DigestUtils;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Loader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String ASSETS_LOCATION = "/Users/tom/test-cache/";
  private static final String PNG_EXTENSION = ".png";
  private static final String DATABASE_BING_ONLY_HOST = "localhost";
  private static final String DATABASE_BING_ONLY_PORT = "27017";

  private MongoDB db;
  private WordCloudGenerator wcGenerator;
  private ReactionRenderer renderer;
  private DBCollection reachablesCollection;
  private JacksonDBCollection<Reachable, String> jacksonReachablesCollection;

  public Loader() throws UnknownHostException {
    db = new MongoDB();
    wcGenerator = new WordCloudGenerator(DATABASE_BING_ONLY_HOST, DATABASE_BING_ONLY_PORT);
    renderer = new ReactionRenderer();

    MongoClient mongoClient = new MongoClient(new ServerAddress("localhost", 27017));
    DB reachables = mongoClient.getDB("wiki_reachables");
    reachablesCollection = reachables.getCollection("test");
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
  }


  public String generateWordcloud(String inchi) throws IOException {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String wordcloudFilename = String.join("", "wordcloud", postfix);

    File wordcloud = Paths.get(ASSETS_LOCATION, wordcloudFilename).toFile();

    if (!Files.exists(wordcloud.toPath())) {
      wcGenerator.generateWordCloud(inchi, wordcloud);
      FileChecker.verifyInputFile(wordcloud);
    }

    return wordcloudFilename;
  }


  public String generateRendering(String inchi, Chemical c) throws IOException {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String renderingFilename = String.join("", "molecule", postfix);
    File rendering = Paths.get(ASSETS_LOCATION, renderingFilename).toFile();

    if (!Files.exists(rendering.toPath())) {
      renderer.drawMolecule(MoleculeImporter.importMolecule(c), rendering);
      FileChecker.verifyInputFile(rendering);
    }

    return renderingFilename;
  }

  public Reachable constructReachable(String inchi) throws IOException {
    Chemical c = db.getChemicalFromInChI(inchi);
    List<String> names = c.getBrendaNames();
    String smiles = c.getSmiles();
    String renderingFilename = generateRendering(inchi, c);
    String wordcloudFilename = generateWordcloud(inchi);

    return new Reachable(inchi, smiles, renderingFilename, names, wordcloudFilename);
  }

  public void insertReachablesInDb(Reachable reachable) {
    jacksonReachablesCollection.insert(reachable);
  }



  public static void main(String[] args) throws IOException {

    String inchi = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";
    Loader l = new Loader();
    Reachable r = l.constructReachable(inchi);
    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(r));
    l.jacksonReachablesCollection.insert(r);
  }


}
