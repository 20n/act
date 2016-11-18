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
import org.json.JSONException;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Loader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);
  private static final MongoDB reachablesConnection = new MongoDB("localhost", 27017, "validator_profiling_2");

  // This database contains the Bing XREF that we need!
  private static final String ACTV01_DATABASE = "actv01";

  // We extract the chemicals from this database
  private static final String VALIDATOR_PROFILING_DATABASE = "validator_profiling_2";

  // Default host. If running on a laptop, please set a SSH bridge to access speeakeasy
  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;

  // Target database and collection. We populate these with reachables
  private static final String TARGET_DATABASE = "wiki_reachables";
  private static final String TARGET_COLLECTION = "test";

  private MongoDB db;
  private WordCloudGenerator wcGenerator;

  private DBCollection reachablesCollection;
  private JacksonDBCollection<Reachable, String> jacksonReachablesCollection;
  private L2InchiCorpus inchiCorpus;

  public Loader(String host, Integer port, String targetDB, String targetCollection) throws UnknownHostException {
    db = new MongoDB(host, port, VALIDATOR_PROFILING_DATABASE);
    wcGenerator = new WordCloudGenerator(host, port, ACTV01_DATABASE);

    MongoClient mongoClient = new MongoClient(new ServerAddress(host, port));
    DB reachables = mongoClient.getDB(targetDB);
    reachablesCollection = reachables.getCollection(targetCollection);
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
  }

  public Loader() throws UnknownHostException {
    db = new MongoDB(DEFAULT_HOST, DEFAULT_PORT, VALIDATOR_PROFILING_DATABASE);
    wcGenerator = new WordCloudGenerator(DEFAULT_HOST, DEFAULT_PORT, ACTV01_DATABASE);

    MongoClient mongoClient = new MongoClient(new ServerAddress(DEFAULT_HOST, DEFAULT_PORT));
    DB reachables = mongoClient.getDB(TARGET_DATABASE);
    reachablesCollection = reachables.getCollection(TARGET_COLLECTION);
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
  }

  /**
   * Get smiles from molecule
   */
  private String getSmiles(Molecule mol) {
    try {
      return MoleculeExporter.exportMolecule(mol, MoleculeFormat.smiles$.MODULE$);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Get inchi key from molecule
   */
  private String getInchiKey(Molecule mol) {
    try {
      // TODO: add inchi key the Michael's Molecule Exporter
      String inchikey = MolExporter.exportToFormat(mol, "inchikey");
      return inchikey.replaceAll("InChIKey=", "");
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Heuristic to choose the best page name
   */
  private String getPageName(String chemaxonTraditionalName, List<String> brendaNames, String inchi) {
    if (chemaxonTraditionalName == null || chemaxonTraditionalName.length() > 50) {
      brendaNames.sort((n1, n2) -> Integer.compare(n1.length(), n2.length()));
      if (brendaNames.size() == 0) {
        return inchi;
      } else {
        return brendaNames.get(0);
      }
    }
    return chemaxonTraditionalName;
  }

  /**
   * Use Chemaxon to get the traditional name.
   * If no common name, Chemaxon will generate one
   */
  private String getChemaxonTraditionalName(Molecule mol) {
    try {
      return MolExporter.exportToFormat(mol, "name:t");
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Construct a Reachable.
   * Gets names and xref from `db` collection `chemicals`
   * Tries to import to molecule and export names
   */
  public Reachable constructReachable(String inchi) throws IOException {
    // Only construct a new one if one doesn't already exist.
    Reachable preconstructedReachable = queryByInchi(inchi);
    if (preconstructedReachable != null) {
      return preconstructedReachable;
    }
    Molecule mol;
    try {
      mol = MoleculeImporter.importMolecule(inchi);
    } catch (MolFormatException e) {
      LOGGER.error("Failed to import inchi %s", inchi);
      return null;
    }

    Chemical c = db.getChemicalFromInChI(inchi);
    List<String> names = c != null ? c.getBrendaNames() : Collections.emptyList();
    Map<Chemical.REFS, BasicDBObject> xref = c != null ? c.getXrefMap() : new HashMap<>();

    String smiles = getSmiles(mol);
    if (smiles == null) {
      LOGGER.error("Failed to export molecule %s to smiles", inchi);
    }

    String inchikey = getInchiKey(mol);
    if (inchikey == null) {
      LOGGER.error("Failed to export molecule %s to inchi key", inchi);
    }

    String chemaxonTraditionalName = getChemaxonTraditionalName(mol);
    if (chemaxonTraditionalName == null) {
      LOGGER.error("Failed to export molecule %s to traditional name", inchi);
    }

    String pageName = getPageName(chemaxonTraditionalName, names, inchi);
    return new Reachable(pageName, inchi, smiles, inchikey, names, xref);
  }

  /**
   * Update a single reachable with wordcloud info
   */
  public void updateReachableWithWordcloud(Reachable reachable) throws IOException {
    File wordcloud = wcGenerator.generateWordCloud(reachable.getInchi());
    if (wordcloud != null) {
      reachable.setWordCloudFilename(wordcloud.getName());
      upsert(reachable);
    }
  }

  /**
   * Update a single reachable with rendering info
   */
  public void updateReachableWithRendering(Reachable reachable) {
    String renderingFilename = MoleculeRenderer.generateRendering(reachable.getInchi());
    LOGGER.info("Generated rendering at %s", renderingFilename);
    reachable.setStructureFilename(renderingFilename);
    upsert(reachable);
  }

  public void loadReachables(File inchiFile) throws IOException {
    inchiCorpus = new L2InchiCorpus();
    inchiCorpus.loadCorpus(inchiFile);
    List<String> inchis = inchiCorpus.getInchiList();
    Reachable reachable;
    for (String inchi : inchis) {
      reachable = constructReachable(inchi);
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
    Reachable reachable = queryByInchi(inchi);

    // If is null we create a new one
    reachable = reachable == null ? constructReachable(inchi) : reachable;
    reachable.getPrecursorData().addPrecursor(pre);

    upsert(reachable);
  }

  private Reachable queryByInchi(String inchi){
    DBObject query = new BasicDBObject("inchi", inchi);
    return jacksonReachablesCollection.findOne(query);
  }

  public void upsert(Reachable reachable){
    Reachable reachableOld = queryByInchi(reachable.getInchi());

    if (reachableOld != null) {
      LOGGER.info("Found previous reachable at InChI " + reachable.getInchi() + ".  Adding additional precursors to it.");
      jacksonReachablesCollection.update(reachableOld, reachable);
    } else {
      LOGGER.info("Did not find InChI " + reachable.getInchi() + " in database.  Creating a new reachable.");
      jacksonReachablesCollection.insert(reachable);
    }
  }

  public void updateFromReachablesFile(File file){
    try {
      // Read in the file and parse it as JSON
      String jsonTxt = IOUtils.toString(new FileInputStream(file));
      JSONObject fileContents = new JSONObject(jsonTxt);
      // Parsing errors should happen as near to the point of loading as possible.
      Long parentId = fileContents.getLong("parent");
      Long currentId = fileContents.getLong("chemid");

      // Get the parent chemicals from the database.  JSON file contains ID.
      // We want to update it because it may not exist, but we also don't want to overwrite.

      List<InchiDescriptor> substrates = new ArrayList<>();
      if (parentId >= 0) {
        try {
          Chemical parent = reachablesConnection.getChemicalFromChemicalUUID(parentId);
          upsert(constructReachable(parent.getInChI()));
          InchiDescriptor parentDescriptor = new InchiDescriptor(constructReachable(parent.getInChI()));
          substrates.add(parentDescriptor);
        } catch (NullPointerException e){
          LOGGER.info("Null pointer, unable to write parent.");
        }
      }

      // Get the actual chemical that is the product of the above chemical.
      Chemical current = reachablesConnection.getChemicalFromChemicalUUID(currentId);

      // Update source as reachables, as these files are parsed from `cascade` construction
      if (!substrates.isEmpty()) {
        Precursor pre = new Precursor(substrates, "reachables");
        updateWithPrecursor(current.getInChI(), pre);
      } else {
        try {
          // TODO add a special native class?
          upsert(constructReachable(current.getInChI()));
        } catch (NullPointerException e) {
          LOGGER.info("Null pointer, unable tp parse InChI.");
        }
      }
    } catch (IOException e) {
      // We can only work with files we can parse, so if we can't
      // parse the file we just don't do anything and submit an error.
      LOGGER.warn("Unable to load file " + file.getAbsolutePath());
    } catch (JSONException e){
      LOGGER.warn("Unable to parse JSON of file at " + file.getAbsolutePath());
    }
  }

  public void updateFromReachableFiles(List<File> files){
    files.stream().forEach(this::updateFromReachablesFile);
  }

  public void updateFromReachableDir(File file){
    List<File> validFiles = Arrays.stream(file.listFiles()).filter(x ->
            x.getName().startsWith(("c")) && x.getName().endsWith("json")).collect(Collectors.toList());
    LOGGER.info("Found %d reachables files.",validFiles.size());
    updateFromReachableFiles(validFiles);
  }


  public void updateWordClouds() throws IOException {
    List<String> inchis = jacksonReachablesCollection.distinct("inchi");
    LOGGER.info("Found %d inchis in the database, now querying for usage words", inchis.size());


    List<String> bingInchis = wcGenerator.getBingInchis();
    LOGGER.info("Found %d inchis having bings results", bingInchis.size());

    bingInchis.retainAll(inchis);
    LOGGER.info("Now creating wordclouds for %d inchis", bingInchis.size());

    int i = 0;
    for (String inchi : bingInchis) {
      if (++i % 100 == 0) {
        LOGGER.info("#%d", i);
      }
      Reachable reachable = queryByInchi(inchi);
      if (reachable.getWordCloudFilename() == null) {
        updateReachableWithWordcloud(reachable);
      }
    }
  }

  public void updateMoleculeRenderings() throws IOException {
    List<String> inchis = jacksonReachablesCollection.distinct("inchi");
    LOGGER.info("Found %d inchis in the database", inchis.size());

    int i = 0;
    for (String inchi : inchis) {
      if (++i % 100 == 0) {
        LOGGER.info("#%d", i);
      }
      Reachable reachable = queryByInchi(inchi);
      if (reachable.getStructureFilename() == null) {
        updateReachableWithRendering(reachable);
      }
    }
  }

  public static void main(String[] args) throws IOException {

    Loader loader = new Loader();
    // loader.updateMoleculeRenderings();
    loader.updateFromReachableDir(new File("/Volumes/shared-data/Michael/WikipediaProject/Reachables/r-2016-11-16-data"));

  }
}
