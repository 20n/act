package act.installer.reachablesexplorer;


import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import com.act.jobs.FileChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.impl.ExternalTypeHandler;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBUpdate;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Loader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);

  private static final String ASSETS_LOCATION = "/Volumes/data-level1/data/reachables-explorer-rendering-cache";
  private static final String PNG_EXTENSION = ".png";
  private static final String DATABASE_BING_ONLY_HOST = "localhost";
  private static final String DATABASE_BING_ONLY_PORT = "27017";

  private MongoDB db;
  private WordCloudGenerator wcGenerator;
  private ReactionRenderer renderer;
  private DBCollection reachablesCollection;
  private JacksonDBCollection<Reachable, String> jacksonReachablesCollection;
  private L2InchiCorpus inchiCorpus;

  public Loader() throws UnknownHostException {
    db = new MongoDB();
    wcGenerator = new WordCloudGenerator(DATABASE_BING_ONLY_HOST, DATABASE_BING_ONLY_PORT);
    renderer = new ReactionRenderer();

    MongoClient mongoClient = new MongoClient(new ServerAddress("localhost", 27018));
    DB reachables = mongoClient.getDB("wiki_reachables");
    reachablesCollection = reachables.getCollection("test");
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
  }


  public String generateWordcloud(String inchi) {

    // TODO: improve wordcloud generation. Currently, each instance open a mongo connection on the R side.
    // By doing data manipulation in Java and utilizing Rengine, we could make this much better
    // Wordclouds could be generated ahead of time this way, using the inchi coprus
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String wordcloudFilename = String.join("", "wordcloud", postfix);

    File wordcloud = Paths.get(ASSETS_LOCATION, wordcloudFilename).toFile();

    if (!Files.exists(wordcloud.toPath())) {
      try {
        wcGenerator.generateWordCloud(inchi, wordcloud);
        FileChecker.verifyInputFile(wordcloud);
      } catch (IOException e) {
        LOGGER.error("Unable to generate wordcloud for %s at location %s", inchi, wordcloud.toPath().toString());
        return null;
      }
    }

    return wordcloudFilename;
  }


  public String generateRendering(String inchi, Molecule mol) {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String renderingFilename = String.join("", "molecule", postfix);
    File rendering = Paths.get(ASSETS_LOCATION, renderingFilename).toFile();

    if (!Files.exists(rendering.toPath())) {
      try {
        renderer.drawMolecule(mol, rendering);
        FileChecker.verifyInputFile(rendering);
      } catch (IOException e) {
        LOGGER.error("Unable to generate rendering for %s at location %s", inchi, rendering.toPath().toString());
        return null;
      }
    }

    return renderingFilename;
  }

  public Reachable constructReachable(String inchi) {
    Molecule mol;
    String smiles;
    String inchikey;
    String pageName;
    try {
      mol = MoleculeImporter.importMolecule(inchi);
    } catch (MolFormatException e) {
      // TODO: add logging
      return null;
    }

    Chemical c = db.getChemicalFromInChI(inchi);
    List<String> names = c != null ? c.getBrendaNames() : Collections.emptyList();

    try {
      smiles = MoleculeExporter.exportMolecule(mol, MoleculeFormat.smiles$.MODULE$);
    } catch (Exception e) {
      LOGGER.error("Failed to export molecule %s to smiles", inchi);
      smiles = null;
    }

    try {
      // TODO: add inchi key the Michael's Molecule Exporter
      inchikey = MolExporter.exportToFormat(mol, "inchikey");
    } catch (Exception e) {
      LOGGER.error("Failed to export molecule %s to inchi key", inchi);
      inchikey = null;
    }

    try {
      pageName = MolExporter.exportToFormat(mol, "name:t");
      int l = pageName.length();
      if (l > 50) {
        names.sort((n1, n2) -> Integer.compare(n1.length(), n2.length()));
        if (names.size() == 0) {
          pageName = inchikey;
        } else {
          pageName = names.get(0);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Failed to export molecule %s to traditional name", inchi);
      pageName = null;
    }

    String renderingFilename = generateRendering(inchi, mol);
    String wordcloudFilename = generateWordcloud(inchi);

    return new Reachable(pageName, inchi, smiles, inchikey, renderingFilename, names, wordcloudFilename);
  }

  public void loadReachables(File inchiFile) throws IOException {
    inchiCorpus = new L2InchiCorpus();
    inchiCorpus.loadCorpus(inchiFile);
    List<String> inchis = inchiCorpus.getInchiList();
    Reachable reachable;
    for (String inchi : inchis) {
      reachable = constructReachable(inchi);
      System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(reachable));
      // TODO: change the following to update the database maybe?
      jacksonReachablesCollection.insert(reachable);
    }

  }
  public void updateWithPrecursorData(String inchi, Reachable.PrecursorData precursorData) {
    // TODO: is there a better way to perform the update? probably!
    Reachable reachable = jacksonReachablesCollection.findOne(new BasicDBObject("inchi", inchi));
    Reachable reachableOld = jacksonReachablesCollection.findOne(new BasicDBObject("inchi", inchi));
    if (reachable != null) {
      reachable.setPrecursorData(precursorData);
      jacksonReachablesCollection.update(reachableOld, reachable);
    }


    //DBUpdate.Builder builder = new DBUpdate.Builder();
    //builder.set("precursor", precursorData);
    // jacksonReachablesCollection.update(new BasicDBObject("inchi", inchi), builder);

  }

  public static void main(String[] args) throws IOException {

    Loader loader = new Loader();
    loader.loadReachables(new File("/Volumes/shared-data/Thomas/L2inchis.test20"));
  }
}
