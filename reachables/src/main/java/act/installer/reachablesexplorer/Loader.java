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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Loader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);


  private static final String DATABASE_BING_ONLY_HOST = "localhost";
  private static final String DATABASE_BING_ONLY_PORT = "27017";

  private MongoDB db;
  private WordCloudGenerator wcGenerator;
  private MoleculeRenderer renderer;

  private DBCollection reachablesCollection;
  private JacksonDBCollection<Reachable, String> jacksonReachablesCollection;
  private L2InchiCorpus inchiCorpus;

  public Loader() throws UnknownHostException {
    db = new MongoDB("localhost", 27017, "validator_profiling_2");
    wcGenerator = new WordCloudGenerator(DATABASE_BING_ONLY_HOST, DATABASE_BING_ONLY_PORT);
    renderer = new MoleculeRenderer();

    MongoClient mongoClient = new MongoClient(new ServerAddress("localhost", 27017));
    DB reachables = mongoClient.getDB("wiki_reachables");
    reachablesCollection = reachables.getCollection("test");
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
  }


  public Reachable constructReachable(String inchi) throws IOException {
    Molecule mol;
    String smiles;
    String inchikey;
    String pageName;
    try {
      mol = MoleculeImporter.importMolecule(inchi);
    } catch (MolFormatException e) {
      LOGGER.error("Failed to import inchi %s", inchi);
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

    String renderingFilename = renderer.generateRendering(inchi, mol);
    File wordcloud = wcGenerator.generateWordCloud(inchi);

    return new Reachable(pageName, inchi, smiles, inchikey, renderingFilename, names, wordcloud.getAbsolutePath());
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
  public void updateWithPrecursorData(String inchi, PrecursorData precursorData) {
    // TODO: can we use updates instead of inserting a new precursor?
    Reachable reachable = jacksonReachablesCollection.findOne(new BasicDBObject("inchi", inchi));
    Reachable reachableOld = jacksonReachablesCollection.findOne(new BasicDBObject("inchi", inchi));
    if (reachable != null) {
      reachable.setPrecursorData(precursorData);
      jacksonReachablesCollection.update(reachableOld, reachable);
    }
  }

  public void updateWithPrecursor(String inchi, Precursor pre) throws IOException {
    // TODO: can we use updates instead of inserting a new precursor?
    DBObject query = new BasicDBObject("inchi", inchi);
    Reachable reachableOld = jacksonReachablesCollection.findOne(query);
    Reachable reachable = jacksonReachablesCollection.findOne(query);

    if (reachable != null) {
      LOGGER.info("Found previous reachable at InChI " + inchi + ".  Adding additional precursors to it.");
      reachable.getPrecursorData().addPrecursor(pre);
      jacksonReachablesCollection.update(reachableOld, reachable);
    } else {
      LOGGER.info("Did not find InChI " + inchi + " in database.  Creating a new reachable.");
      Reachable newReachable = constructReachable(inchi);
      newReachable.getPrecursorData().addPrecursor(pre);
      jacksonReachablesCollection.insert(newReachable);
    }
  }

  public void update(Reachable reachable){
    DBObject query = new BasicDBObject("inchi", reachable.getInchi());
    Reachable reachableOld = jacksonReachablesCollection.findOne(query);

    jacksonReachablesCollection.update(reachableOld, reachable);
  }

  public void updateFromReachablesFile(File file){
    MongoDB connection = new MongoDB("localhost", 27017, "validator_profiling_2");

    try {
      // Read in the file and parse it as JSON
      String jsonTxt = IOUtils.toString(new FileInputStream(file));
      JSONObject fileContents = new JSONObject(jsonTxt);

      // Get the parent chemicals from the database.  JSON file contains ID.
      // We want to update it because it may not exist, but we also don't want to overwrite.
      Chemical parent = connection.getChemicalFromChemicalUUID(fileContents.getLong("parent"));
      update(constructReachable(parent.getInChI()));

      // Get the actual chemical that is the product of the above chemical.
      Chemical current = connection.getChemicalFromChemicalUUID(fileContents.getLong("chemid"));
      List<String> substrates = Arrays.asList(parent.getInChI());

      // Update source as reachables, as these files are parsed from `cascade` construction
      Precursor pre = new Precursor(substrates, "reachables");
      updateWithPrecursor(current.getInChI(), pre);
    } catch (IOException e) {
      // We can only work with files we can parse, so if we can't
      // parse the file we just don't do anything and submit an error.
      LOGGER.warn("Unable to parse file " + file.getAbsolutePath());
    }
  }

  public void updateFromReachableFiles(List<File> files){
    files.stream().forEach(this::updateFromReachablesFile);
  }

  public static void main(String[] args) throws IOException {


//    Loader loader = new Loader();
//    loader.loadReachables(new File("/Volumes/shared-data/Thomas/L2inchis.test20"));
//    loader.updateWithPrecursorData("InChI=1S/C2H5NO2/c3-1-2(4)5/h1,3H2,(H,4,5)", new PrecursorData());
    Loader loader = new Loader();
    loader.updateFromReachablesFile(new File("/Volumes/shared-data/Michael/WikipediaProject/Reachables/r-2016-11-16-data", "c1121.json"));
  }
}
