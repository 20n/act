package act.installer.reachablesexplorer;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemMeshSynonyms;
import act.installer.pubchem.PubchemSynonymType;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Seq;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Loader {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);

  // This database contains the Bing XREF that we need!
  private static final String ACTV01_DATABASE = "actv01";

  // We extract the chemicals from this database
  private static final String VALIDATOR_PROFILING_DATABASE = "validator_profiling_2";

  // Default host. If running on a laptop, please set a SSH bridge to access speeakeasy
  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;

  // Target database and collection. We populate these with reachables
  private static final String TARGET_DATABASE = "wiki_reachables";
  private static final String TARGET_COLLECTION = "reachablesv6_test_rebase_min";
  private static final String SEQUENCE_COLLECTION = "sequences_test_rebase_v6";

  private static final int ORGANISM_CACHE_SIZE = 1000;
  private static final float ORGANISM_CACHE_LOAD = 1.0f;
  private static final String ORGANISM_UNKNOWN = "(unknown)";
  private final LinkedHashMap<Long, String> organismCache =
      new LinkedHashMap<Long, String>(ORGANISM_CACHE_SIZE + 1, ORGANISM_CACHE_LOAD, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
          return this.size() == ORGANISM_CACHE_SIZE;
        }
      };
  private MongoDB db;

  // TODO We use word cloud statically, figure out if there is a use case for this variable
  private WordCloudGenerator wcGenerator;
  private DBCollection reachablesCollection;
  private JacksonDBCollection<Reachable, String> jacksonReachablesCollection;
  private JacksonDBCollection<SequenceData, String> jacksonSequenceCollection;
  private PubchemMeshSynonyms pubchemSynonymsDriver;

  public Loader(String host, Integer port, String targetDB, String targetCollection) throws UnknownHostException {
    db = new MongoDB(host, port, VALIDATOR_PROFILING_DATABASE);
    wcGenerator = new WordCloudGenerator(host, port, ACTV01_DATABASE);
    pubchemSynonymsDriver = new PubchemMeshSynonyms();

    MongoClient mongoClient = new MongoClient(new ServerAddress(host, port));
    DB reachables = mongoClient.getDB(targetDB);
    reachablesCollection = reachables.getCollection(targetCollection);
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
    jacksonReachablesCollection.ensureIndex(new BasicDBObject("inchi", "hashed"));
  }


  public Loader() throws UnknownHostException {
    // TODO Make this less specific constructor call the more specific one

    db = new MongoDB(DEFAULT_HOST, DEFAULT_PORT, VALIDATOR_PROFILING_DATABASE);
    wcGenerator = new WordCloudGenerator(DEFAULT_HOST, DEFAULT_PORT, ACTV01_DATABASE);
    pubchemSynonymsDriver = new PubchemMeshSynonyms();

    MongoClient mongoClient = new MongoClient(new ServerAddress(DEFAULT_HOST, DEFAULT_PORT));
    DB reachables = mongoClient.getDB(TARGET_DATABASE);
    reachablesCollection = reachables.getCollection(TARGET_COLLECTION);
    jacksonReachablesCollection = JacksonDBCollection.wrap(reachablesCollection, Reachable.class, String.class);
    jacksonSequenceCollection =
        JacksonDBCollection.wrap(reachables.getCollection(SEQUENCE_COLLECTION), SequenceData.class, String.class);
    // TODO Text or hashed index?
    jacksonReachablesCollection.ensureIndex(new BasicDBObject("inchi", "hashed"));
    jacksonSequenceCollection.createIndex(new BasicDBObject("sequence", "hashed"));
    jacksonSequenceCollection.createIndex(new BasicDBObject("organism_name", 1));
  }

  public static void main(String[] args) throws IOException {
    Loader loader = new Loader();
    loader.updateFromReachableDir(new File("/Volumes/shared-data/Michael/WikipediaProject/MinimalReachables"));
    loader.updatePubchemSynonyms();
  }

  // TODO Move these getters to a different place/divide up concerns better?
  private String getSmiles(Molecule mol) {
    try {
      // TODO Add a smiles export in the exporter?
      return MoleculeExporter.exportMolecule(mol, MoleculeFormat.smiles$.MODULE$);
      // TODO Do correct error handling on the specific exceptions here
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
      // Michael's comment: Definitely doable, all current formats are symmetric, can we import w/ inchikey too?
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
      // TODO Solve edge case where really short names are picked
      brendaNames.sort((n1, n2) -> Integer.compare(n1.length(), n2.length()));
      if (brendaNames.isEmpty()) {
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

  private void assertNotFakeInchi(String inchi) throws FakeInchiException {
    // TODO Also add in R groups here.
    // TODO Assess if this is still necessary
    if (inchi != null && inchi.contains("FAKE")) {
      throw new FakeInchiException(inchi);
    }
  }

  /**
   * Construct a Reachable.
   * Gets names and xref from `db` collection `chemicals`
   * Tries to import to molecule and export names
   */
  private Reachable constructReachable(String inchi) {
    // TODO Better break the logic into discrete components
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

    File rendering = MoleculeRenderer.getRenderingFile(inchi);
    File wordcloud = WordCloudGenerator.getWordcloudFile(inchi);
    String renderingFilename = null;
    String wordcloudFilename = null;
    if (rendering.exists()) {
      renderingFilename = rendering.getName();
    }
    if (wordcloud.exists()) {
      wordcloudFilename = wordcloud.getName();
    }

    return new Reachable(pageName, inchi, smiles, inchikey, renderingFilename, names, wordcloudFilename, xref);
  }

  private void updateReachableWithSynonyms(Reachable reachable) {
    String inchi = reachable.getInchi();
    String compoundID = pubchemSynonymsDriver.fetchCIDFromInchi(inchi);
    Map<MeshTermType, List<String>> meshSynonyms = pubchemSynonymsDriver.fetchMeshTermsFromCID(compoundID).
        entrySet().stream().
        collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new ArrayList<>(e.getValue())));
    Map<PubchemSynonymType, List<String>> pubchemSynonyms = pubchemSynonymsDriver.fetchPubchemSynonymsFromCID(compoundID).
        entrySet().stream().
        collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new ArrayList<>(e.getValue())));
    SynonymData synonymData = new SynonymData(pubchemSynonyms, meshSynonyms);
    reachable.setSynonyms(synonymData);
    upsert(reachable);
  }

  private void updateWithPrecursors(String inchi, List<Precursor> pre) throws IOException {
    Reachable reachable = queryByInchi(inchi);

    // If is null we create a new one
    reachable = reachable == null ? constructReachable(inchi) : reachable;

    if (reachable == null) {
      LOGGER.warn("Still couldn't construct InChI after retry, aborting");
      return;
    }

    reachable.getPrecursorData().addPrecursors(pre);

    upsert(reachable);
  }

  private Reachable queryByInchi(String inchi){
    DBObject query = new BasicDBObject("inchi", inchi);
    return jacksonReachablesCollection.findOne(query);
  }

  private void upsert(Reachable reachable) {
    // TODO Can we make this more efficient in any way?
    Reachable reachableOld = queryByInchi(reachable.getInchi());

    if (reachableOld != null) {
      LOGGER.info("Found previous reachable at InChI " + reachable.getInchi() + ".  Adding additional precursors to it.");
      jacksonReachablesCollection.update(reachableOld, reachable);
    } else {
      LOGGER.info("Did not find InChI " + reachable.getInchi() + " in database.  Creating a new reachable.");
      jacksonReachablesCollection.insert(reachable);
    }
  }

  /**
   * Finds the set of reaction ids in a cascade document that have parentId as a substrate.  Assuming the cascade is
   * loaded for a fixed childId, this will find all ids that represent reactions that convert the parent into the child.
   * @param cascadeDoc The cascade JSON doc to search for matching reactions.
   * @param childId The id of the child or current chemical.  Used only for logging.
   * @param parentId The parent id for which to search.
   * @return A set of reaction ids from the cascade document that include parentId as a substrate.
   */
  private Set<Long> gatherReactionIdsWithParentFromCascade(JSONObject cascadeDoc, Long childId, Long parentId) {
    Set<Long> rxnIds = new HashSet<>();

    // TODO: add better error checking to this method.  Right now, any malformed cascades doc will break us.
    JSONArray upstreamRxns = cascadeDoc.getJSONArray("upstream");
    for (int i = 0; i < upstreamRxns.length(); i++) {
      JSONObject rxn = upstreamRxns.getJSONObject(i);
      JSONArray substrates = rxn.getJSONArray("substrates");
      boolean foundParent = false;
      for (int j = 0; j < substrates.length(); j++) {
        long id = substrates.getLong(j);
        if (Long.valueOf(id).equals(parentId)) {
          foundParent = true;
          break;
        }
      }

      if (foundParent) {
        rxnIds.add(rxn.getLong("rxnid"));
      }
    }

    if (rxnIds.size() == 0) {
      LOGGER.error("Found zero matching reactions for parent/child ids %d/%d", parentId, childId);
    }
    return rxnIds;
  }

  /**
   * Get an organism name using an organism name id, with a healthy dose of caching since there are only about 21k
   * organisms for 9M reactions.
   * @param id The organism name id to fetch.
   * @return The organism name or "(unknown)" if that organism can't be found, which should never ever happen.
   */
  private String getOrganismName(Long id) {
    String cachedName = organismCache.get(id);
    if (cachedName != null) {
      return cachedName;
    }

    String name = db.getOrganismNameFromId(id);
    if (name != null) {
      this.organismCache.put(id, name);
    } else {
      // Hopefully this will never happen, but better not allow a null string to pass through.
      LOGGER.error("Got null organism name for id %d, defaulting to %s", ORGANISM_UNKNOWN);
      name = ORGANISM_UNKNOWN;
    }

    return name;
  }

  /**
   * Fetches all (organism name, sequence) pairs (as SequenceData objects) for a set of reaction ids.  Results are
   * de-duplicated and sorted on organism/sequence.  If the set of reaction ids captures all reactions that represent
   * parentId -> childId from a cascade, then this should return the complete, unique set of sequences that encode the
   * enzymes that catalize that family of reactions.
   * @param rxnIds The set of reaction ids whose sequences should be fetched.
   * @return SequenceData objects for each of the sequences associated with the specified reactions.
   */
  private List<SequenceData> extractOrganismsAndSequencesForReactions(Set<Long> rxnIds) {
    Set<SequenceData> uniqueSequences = new HashSet<>();
    for (Long rxnId : rxnIds) {
      // Note: this exploits a new index on seq.rxn_refs to make this quicker than an indirect lookup through rxns.
      List<Seq> sequences = db.getSeqWithRxnRef(rxnId);
      for (Seq seq : sequences) {
        if (seq.getSequence() == null) {
          LOGGER.warn("Found seq entry with id %d has null sequence.  How did that happen?", seq.getUUID());
          continue;
        }
        String organismName = getOrganismName(seq.getOrgId());
        uniqueSequences.add(new SequenceData(organismName, seq.getSequence()));
      }
    }

    List<SequenceData> sortedSequences = new ArrayList<>(uniqueSequences);
    // Sort for stability and sanity.  Hurrah.
    Collections.sort(sortedSequences);

    return sortedSequences;
  }

  private void updateFromReachablesFile(File file) {
    // TODO Break this into a bunch of unique functions, quite long
    LOGGER.info("Processing file %s", file.getName());
    try {
      // Read in the file and parse it as JSON
      String jsonTxt = IOUtils.toString(new FileInputStream(file));
      JSONObject fileContents = new JSONObject(jsonTxt);
      // Parsing errors should happen as near to the point of loading as possible.
      // TODO Add all json parsing up here.
      Long parentId = fileContents.getLong("parent");
      Long currentId = fileContents.getLong("chemid");

      LOGGER.info("Chem id is: " + currentId);

      // Get the actual chemical that is the product of the above chemical.  Bail quickly if we can't find it.
      Chemical current = db.getChemicalFromChemicalUUID(currentId);
      LOGGER.info("Tried to fetch chemical id %d: %s", currentId, current);

      if (current == null) {
        return;
      }

      JSONArray upstreamReactions = fileContents.getJSONArray("upstream");

      // Get the parent chemicals from the database.  JSON file contains ID.
      // We want to update it because it may not exist, but we also don't want to overwrite.

      Map<Long, InchiDescriptor> substrateCache = new HashMap<>();
      List<Precursor> precursors = new ArrayList<>();
      Map<List<InchiDescriptor>, Precursor> substratesToPrecursor = new HashMap<>();

      for (int i = 0; i < upstreamReactions.length(); i++) {
        JSONObject obj = upstreamReactions.getJSONObject(i);
        if (!obj.getBoolean("reachable")) {
          continue;
        }

        List<InchiDescriptor> thisRxnSubstrates = new ArrayList<>();

        JSONArray substratesArrays = (JSONArray) obj.get("substrates");
        for (int j = 0; j < substratesArrays.length(); j++) {
          Long subId = substratesArrays.getLong(j);
          InchiDescriptor parentDescriptor;
          if (subId >= 0 && !substrateCache.containsKey(subId)) {
            try {
              Chemical parent = db.getChemicalFromChemicalUUID(subId);
              upsert(constructReachable(parent.getInChI()));
              parentDescriptor = new InchiDescriptor(constructReachable(parent.getInChI()));
              thisRxnSubstrates.add(parentDescriptor);
              substrateCache.put(subId, parentDescriptor);
              // TODO Remove null pointer exception check
            } catch (NullPointerException e){
              LOGGER.info("Null pointer, unable to write parent.");
            }
          } else if (substrateCache.containsKey(subId)) {
            thisRxnSubstrates.add(substrateCache.get(subId));
          }
        }

        if (!thisRxnSubstrates.isEmpty()) {
          // This is a previously unseen reaction, so add it to the list of precursors.
          List<SequenceData> rxnSequences =
              extractOrganismsAndSequencesForReactions(Collections.singleton(obj.getLong("rxnid")));
          List<String> sequenceIds = new ArrayList<>();
          for (SequenceData seq : rxnSequences) {
            WriteResult<SequenceData, String> result = jacksonSequenceCollection.insert(seq);
            sequenceIds.add(result.getSavedId());
          }

          // TODO: make sure this is what we actually want to do, and figure out why it's happening.
          // De-duplicate reactions based on substrates; somehow some duplicate cascade paths are appearing.
          if (substratesToPrecursor.containsKey(thisRxnSubstrates)) {
            substratesToPrecursor.get(thisRxnSubstrates).getSequences().addAll(sequenceIds);
          } else {
            Precursor precursor = new Precursor(thisRxnSubstrates, "reachables", sequenceIds);
            precursors.add(precursor);
            // Map substrates to precursor for merging later.
            substratesToPrecursor.put(thisRxnSubstrates, precursor);
          }
        }
      }

      if (parentId >= 0 && !substrateCache.containsKey(parentId)) {
        // Note: this should be impossible.
        LOGGER.error("substrate cache does not contain parent id %d after all upstream reactions processed", parentId);
      }

      assertNotFakeInchi(current.getInChI());

      // Update source as reachables, as these files are parsed from `cascade` construction
      if (!precursors.isEmpty()) {
        Reachable rech = constructReachable(current.getInChI());
        if (rech != null) {
          rech.setDotGraph("cscd" + String.valueOf(currentId) + ".dot");
          upsert(rech);
          updateWithPrecursors(current.getInChI(), precursors);
        }
      } else {
        try {
          Reachable rech = constructReachable(current.getInChI());
          rech.setIsNative(currentId == -1);
          upsert(rech);
          // TODO Remove null pointer exception check
        } catch (NullPointerException e) {
          LOGGER.info("Null pointer, unable to parse InChI %s.", current == null ? "null" : current.getInChI());
        }
      }
    } catch (FakeInchiException e) {
      LOGGER.warn("Skipping file %s due to fake InChI exception", file.getName());
    } catch (IOException e) {
      // We can only work with files we can parse, so if we can't
      // parse the file we just don't do anything and submit an error.
      LOGGER.warn("Unable to load file " + file.getAbsolutePath());
    } catch (JSONException e){
      LOGGER.warn("Unable to parse JSON of file at " + file.getAbsolutePath());
    }
  }

  private void updateFromReachableFiles(List<File> files) {
    files.stream().forEach(this::updateFromReachablesFile);
  }

  private void updateFromReachableDir(File file) throws IOException {
    // Get all the reachables from the teachables text file.

    File dataDirectory = Arrays.stream(file.listFiles()).filter(x -> x.getName().endsWith("data") && x.isDirectory()).collect(Collectors.toList()).get(0).getAbsoluteFile();
    File reachablesFile = Arrays.stream(file.listFiles()).filter(x -> x.getName().endsWith("reachables.txt") && x.isFile()).collect(Collectors.toList()).get(0).getAbsoluteFile();

    List<Integer> chemicalIds = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(reachablesFile))) {
      String line;
      while ((line = br.readLine()) != null) {
        chemicalIds.add(Integer.valueOf(line.split("\t")[0]));
      }
    }

    List<File> validFiles = chemicalIds.
            stream().
            map(i -> new File(dataDirectory, "c" + String.valueOf(i) + ".json")).
            collect(Collectors.toList());

    LOGGER.info("Found %d reachables files.",validFiles.size());
    updateFromReachableFiles(validFiles);
  }

  public void updateFromProjection(ReachablesProjectionUpdate projection) {
    // Construct substrates
    List<Reachable> substrates = projection.getSubstrates().stream().
            map(this::constructReachable).
            collect(Collectors.toList());

    // Add substrates in, or make sure they were added.
    substrates.stream().forEach(this::upsert);

    // Construct descriptors.
    List<InchiDescriptor> precursors = substrates.
            stream().
            map(s -> new InchiDescriptor(s.getPageName(), s.getInchi(), s.getInchiKey())).
            collect(Collectors.toList());

    // For each product, create and add precursors.
    projection.getProducts().stream().forEach(p -> {
      // Get product
      Reachable product = constructReachable(p);
      // TODO Don't punt on sequences
      product.getPrecursorData().addPrecursor(new Precursor(precursors, projection.getRos().get(0), new ArrayList<>()));
      upsert(product);
    });
  }

  private void updatePubchemSynonyms() {
    // TODO Check conversion to string
    List<String> inchis = jacksonReachablesCollection.distinct("inchi");
    LOGGER.info("Found %d inchis in the database", inchis.size());

    int i = 0;
    for (String inchi : inchis) {

      Reachable reachable = queryByInchi(inchi);
      if (reachable.getSynonyms() == null) {
        updateReachableWithSynonyms(reachable);
      }


      if (++i % 100 == 0) {
        LOGGER.info("#%d", i);
      }
    }
  }

  // TODO Move into unique class?  This issues comes up a good bit.
  private static class FakeInchiException extends Exception {
    public FakeInchiException(String inchi) {
      super(String.format("Found FAKE inchi: %s", inchi));
    }
  }
}
