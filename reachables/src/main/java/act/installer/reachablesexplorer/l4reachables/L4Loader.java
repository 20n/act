package act.installer.reachablesexplorer.l4reachables;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemMeshSynonyms;
import act.installer.pubchem.PubchemSynonymType;
import act.installer.reachablesexplorer.Loader;
import act.installer.reachablesexplorer.MoleculeRenderer;
import act.installer.reachablesexplorer.SynonymData;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class L4Loader {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L4Loader.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String DEFAULT_INPUT_PATH = "/Volumes/shared-data/Gil/L4N2pubchem/n1_inchis/projectedReactions";
  private static final String DEFAULT_ASSETS_LOCATION = "/Volumes/data-level1/data/reachables-explorer-rendering-cache";

  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;

  // Target database and collection. We populate these with l4 reachables
  private static final String DEFAULT_TARGET_DATABASE = "wiki_reachables";
  private static final String DEFAULT_TARGET_COLLECTION = "l4reachablesv0";

  private static final Integer BASE_ID = 0;
  private Integer counter;

  private JacksonDBCollection<L4Reachable, String> jacksonL4ReachablesCollection;
  private PubchemMeshSynonyms pubchemSynonymsDriver;

  private MoleculeRenderer moleculeRenderer;


  public L4Loader() {
    this(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TARGET_DATABASE, DEFAULT_TARGET_COLLECTION, DEFAULT_ASSETS_LOCATION);
  }

  public L4Loader(String host, Integer port, String targetDB, String targetCollection, String renderingCache) {

    // Synonyms and rendering
    pubchemSynonymsDriver = new PubchemMeshSynonyms();
    moleculeRenderer = new MoleculeRenderer(new File(renderingCache));

    // Mongo connexions
    MongoClient mongoClient;
    try {
      mongoClient = new MongoClient(new ServerAddress(host, port));
    } catch (UnknownHostException e) {
      LOGGER.error("Connexion to MongoClient failed. Please double check the target database's host and port.");
      throw new RuntimeException(e);
    }
    DB reachables = mongoClient.getDB(targetDB);

    jacksonL4ReachablesCollection =
        JacksonDBCollection.wrap(reachables.getCollection(targetCollection), L4Reachable.class, String.class);
    jacksonL4ReachablesCollection.ensureIndex(new BasicDBObject(L4Reachable.inchiFieldName, "hashed"));

    List<Integer> existingReachables = jacksonL4ReachablesCollection.distinct("_id");
    if (existingReachables.size() > 0) {
      counter = Collections.max(existingReachables);
    } else {
      counter = BASE_ID;
    }
  }

  public JacksonDBCollection<L4Reachable, String> getJacksonL4ReachablesCollection() {
    return jacksonL4ReachablesCollection;
  }

  private List<L4Projection> getProjections(File path) throws IOException {
    L4Projection[] projections = MAPPER.readValue(path, L4Projection[].class);
    return(Arrays.asList(projections));
  }


  private SynonymData getSynonymData(String inchi) {
    String compoundID = pubchemSynonymsDriver.fetchCIDFromInchi(inchi);
    Map<MeshTermType, Set<String>> meshSynonyms = pubchemSynonymsDriver.fetchMeshTermsFromCID(compoundID);
    Map<PubchemSynonymType, Set<String>> pubchemSynonyms = pubchemSynonymsDriver.fetchPubchemSynonymsFromCID(compoundID);
    if (meshSynonyms.isEmpty() && pubchemSynonyms.isEmpty()) {
      return null;
    }
    return new SynonymData(pubchemSynonyms, meshSynonyms);
  }


  protected L4Reachable queryByInchi(String inchi) {
    DBObject query = new BasicDBObject(L4Reachable.inchiFieldName, inchi);
    return jacksonL4ReachablesCollection.findOne(query);
  }

  private List<L4Reachable> getL4ReachablesFromProjection(L4Projection projection) {
    return projection.getProducts().stream().map(this::constructReachable).collect(Collectors.toList());
  }

  private L4Reachable constructReachable(String inchi) {

    L4Reachable preconstructedReachable = queryByInchi(inchi);
    if (preconstructedReachable != null) {
      return preconstructedReachable;
    }

    Molecule mol;
    try {
      MoleculeImporter.assertNotFakeInchi(inchi);
      mol = MoleculeImporter.importMolecule(inchi);
    } catch (MolFormatException e) {
      LOGGER.error("Failed to import inchi %s", inchi);

      return null;
    } catch (MoleculeImporter.FakeInchiException e) {
      LOGGER.error("Failed to import inchi %s as it is fake.", inchi);
      return null;
    }

    String smiles = Loader.getSmiles(mol);
    if (smiles == null) {
      LOGGER.error("Failed to export molecule %s to smiles", inchi);
    }

    String inchikey = Loader.getInchiKey(mol);
    if (inchikey == null) {
      LOGGER.error("Failed to export molecule %s to inchi key", inchi);
    }

    String pageName = Loader.getPageName(mol, new ArrayList<>(), inchi);

    String renderingFilename = null;
    Optional<File> rendering = moleculeRenderer.generateRendering(inchi);
    if (rendering.isPresent()) {
      renderingFilename = rendering.get().getName();
    }

    SynonymData synonymData = getSynonymData(inchi);

    return new L4Reachable(++counter, pageName, inchi, smiles, inchikey, renderingFilename, synonymData);
  }


  public void load(File path) throws IOException {

    List<L4Projection> l4projections = getProjections(path);
    LOGGER.info("Found %d projections", l4projections.size());
    List<List<L4Reachable>> l4reachables = l4projections.stream().map(this::getL4ReachablesFromProjection).collect(Collectors.toList());
    LOGGER.info("Found %d reachables", l4reachables.size());
    l4reachables.forEach(r -> r.forEach(jacksonL4ReachablesCollection::save));
  }


  public static void main(String[] args) {

    L4Loader loader = new L4Loader(
        DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TARGET_DATABASE, DEFAULT_TARGET_COLLECTION, DEFAULT_ASSETS_LOCATION);
    try {
      loader.load(new File(DEFAULT_INPUT_PATH));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
