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
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.List;

public class Loader {

  private static ObjectMapper mapper = new ObjectMapper();

  private static final String ASSETS_LOCATION = "~/test-cache/";
  private static final String DATABASE_BING_ONLY_HOST = "localhost";
  private static final String DATABASE_BING_ONLY_PORT = "27017";


  private MongoDB db;
  private WordCloudGenerator wc;
  private ReactionRenderer rr;
  private DBCollection col;
  private JacksonDBCollection<Reachable, String> coll;

  public Loader() throws UnknownHostException {
    db = new MongoDB();
    wc = new WordCloudGenerator(DATABASE_BING_ONLY_HOST, DATABASE_BING_ONLY_PORT);
    rr = new ReactionRenderer();

    MongoClient mongoClient = new MongoClient(new ServerAddress("localhost", 27017));
    DB reachables = mongoClient.getDB("wiki_reachables");
    assert reachables != null;
    col = reachables.getCollection("test");
    assert col != null;
    coll = JacksonDBCollection.wrap(col, Reachable.class, String.class);
  }

  public Reachable constructReachable(String inchi) throws IOException {
    Chemical c = db.getChemicalFromInChI(inchi);
    List<String> names = c.getBrendaNames();
    String smiles = c.getSmiles();
    Integer hash = inchi.hashCode();
    String renderingFilename = String.join("-", "molecule", hash.toString());
    String wordcloudFilename = String.join("-", "wordcloud", hash.toString());
    String rendering = Paths.get(ASSETS_LOCATION, renderingFilename).toString();
    String wordcloud = Paths.get(ASSETS_LOCATION, wordcloudFilename).toString();

    wc.generateWordCloud(inchi, rendering);
    File renderingFile = new File(rendering);
    rr.drawMolecule(MoleculeImporter.importMolecule(c), renderingFile);

    FileChecker.verifyInputFile(new File(rendering));
    FileChecker.verifyInputFile(new File(wordcloud));

    return new Reachable(inchi, smiles, renderingFilename, names, wordcloudFilename);
  }

  public void insertReachablesInDb(Reachable reachable) {
    coll.insert(reachable);
  }



  public static void main(String[] args) throws IOException {

    String inchi = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";
    Loader l = new Loader();
    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        l.constructReachable(inchi)));

  }


}
