package act.installer.reachablesexplorer;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemMeshSynonyms;
import act.installer.pubchem.PubchemSynonymType;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Seq;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.marvin.io.MolExportException;
import chemaxon.marvin.plugin.PluginException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.utils.CLIUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class Loader {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";

  private static final String OPTION_INSTALLER_SOURCE_DB = "i";
  private static final String OPTION_REACHABLES_SOURCE_DATA = "r";
  private static final String OPTION_PROJECTIONS_SOURCE_DATA = "P";
  private static final String OPTION_PROJECTED_INCHIS_SOURCE_DATA = "l";
  private static final String OPTION_TARGET_DB = "t";
  private static final String OPTION_TARGET_REACHABLES_COLLECTION = "c";
  private static final String OPTION_TARGET_SEQUENCES_COLLECTION = "s";
  private static final String OPTION_RENDERING_CACHE = "e";

  private static final String DEFAULT_ASSETS_LOCATION = "data/reachables-explorer-rendering-cache";

  private static final String DEFAULT_REACHABLES_PATH = "/mnt/shared-data/Michael/WikipediaProject/MinimalReachables";

  private static final String DEFAULT_PROJECTIONS_PATH = "/mnt/shared-data/Gil/L4N2pubchem/n1_inchis/projectedReactions";
  private static final Long BASE_ID_PROJECTIONS = 5000000L;

  // All of the source data on reactions and chemicals come from the specified source db
  private static final String DEFAULT_CHEMICALS_DATABASE = "SHOULD_COME_FROM_CMDLINE"; // "jarvis_2016-12-09";

  // Default host. If running on a laptop, please set a SSH bridge to access speakeasy
  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;

  // Target database and collection. We populate these with reachables
  private static final String DEFAULT_TARGET_DATABASE = "wiki_reachables";
  private static final String DUMMY_TARGET_DB = "dummy_DB";
  private static final String DUMMY_TARGET_COLLECTION = "dummy_target_collection";
  private static final String DUMMY_SEQUENCE_COLLECTION = "dummy_sequences_collection";

  private static final int ORGANISM_CACHE_SIZE = 1000;
  private static final String ORGANISM_UNKNOWN = "(unknown)";
  private final Cache<Long, String> organismCache = Caffeine.newBuilder().maximumSize(ORGANISM_CACHE_SIZE).build();

  // Constants related to Chemaxon name generation.
  // The format tag "name:t" refers to traditional name
  private static final String CHEMAXON_TRADITIONAL_NAME_FORMAT = "name:t";
  // The above format sometimes generates very long names (type IUPAC). We default to using inchis when the
  // generated name is too long.
  private static final Integer MAX_CHEMAXON_NAME_LENGTH = 50;

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class compiles reachables and cascades data into a DB of documents that can be used to render a collection",
      "of pages (one per reachable molecule) that is navigable by humans.  The data model employed by this class can",
      "be read by downstream modules to seamlessly fetch and deserialize these documents for rendering into some",
      "presentable form."
  }, " ");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB_HOST)
        .argName("DB host")
        .desc(String.format("The database host to which to connect (default: %s)", DEFAULT_HOST))
        .hasArg()
        .longOpt("db-host")
    );
    add(Option.builder(OPTION_DB_PORT)
        .argName("DB port")
        .desc(String.format("The port on which to connect to the database (default: %d)", DEFAULT_PORT))
        .hasArg()
        .longOpt("db-port")
    );
    add(Option.builder(OPTION_INSTALLER_SOURCE_DB)
        .argName("DB name")
        .desc(String.format(
            "The name of the database from which to fetch chemicals and reactions (default: %s)",
            DEFAULT_CHEMICALS_DATABASE))
        .hasArg()
        .required()
        .longOpt("source-db-name")
    );
    add(Option.builder(OPTION_REACHABLES_SOURCE_DATA)
        .argName("path")
        .desc(String.format(
            "A path to a directory containing the output of reachables and cascades computation to read (default: %s)",
            DEFAULT_REACHABLES_PATH))
        .hasArg()
        .longOpt("reachables-dir")
    );
    add(Option.builder(OPTION_PROJECTIONS_SOURCE_DATA)
        .argName("path")
        .desc(String.format("A path to a file containing the output of L3 or L4 projections to read (default: %s)",
            DEFAULT_PROJECTIONS_PATH))
        .hasArg()
        .longOpt("projections-file")
    );
    add(Option.builder(OPTION_PROJECTED_INCHIS_SOURCE_DATA)
        .argName("path")
        .desc("A path to a file containing the output of L3 or L4 projections, " +
            "in the form of an InChI list, to read (no default)")
        .hasArg()
        .longOpt("projected-inchis-file")
    );
    add(Option.builder(OPTION_TARGET_DB)
        .argName("DB name")
        .desc(String.format("The name of the DB into which to write reachable molecule documents (default: %s)",
            DEFAULT_TARGET_DATABASE))
        .hasArg()
        .longOpt("dest-db-name")
    );
    add(Option.builder(OPTION_TARGET_REACHABLES_COLLECTION)
        .argName("collection name")
        .desc("The name of the collection in the dest DB into which to write reachables documents (no default)")
        .hasArg()
        .required()
        .longOpt("out-reachables-coll")
    );
    add(Option.builder(OPTION_TARGET_SEQUENCES_COLLECTION)
        .argName("collection name")
        .desc("The name of the collection in the dest DB into which to write sequence documents (no default)")
        .hasArg()
        .required()
        .longOpt("out-seq-coll")
    );
    add(Option.builder(OPTION_RENDERING_CACHE)
        .argName("path to cache")
        .desc(String.format(
            "A directory in which to cache rendered images for reachables documents (default: %s)",
            DEFAULT_ASSETS_LOCATION))
        .hasArg()
        .longOpt("cache-dir")
    );
  }};


  // Database stuff
  private MongoDB sourceDBconn;
  private JacksonDBCollection<Reachable, String> jacksonReachablesCollection;
  private JacksonDBCollection<SequenceData, String> jacksonSequenceCollection;
  private PubchemMeshSynonyms pubchemSynonymsDriver;

  // Renderers
  private WordCloudGenerator wordCloudGenerator;
  private MoleculeRenderer moleculeRenderer;

  private PhysiochemicalPropertiesCalculator calculator;

  public static void main(String[] args) throws IOException {
    CLIUtil cliUtil = new CLIUtil(Loader.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    File reachablesDir = new File(cl.getOptionValue(OPTION_REACHABLES_SOURCE_DATA, DEFAULT_REACHABLES_PATH));
    if (!(reachablesDir.exists() && reachablesDir.isDirectory())) {
      cliUtil.failWithMessage("Reachables directory at %s does not exist or is not a directory",
          reachablesDir.getAbsolutePath());
    }

    Loader loader = new Loader(
        cl.getOptionValue(OPTION_DB_HOST, DEFAULT_HOST),
        Integer.parseInt(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_PORT.toString())),
        cl.getOptionValue(OPTION_INSTALLER_SOURCE_DB, DEFAULT_CHEMICALS_DATABASE),
        cl.getOptionValue(OPTION_TARGET_DB, DEFAULT_TARGET_DATABASE),
        cl.getOptionValue(OPTION_TARGET_REACHABLES_COLLECTION),
        cl.getOptionValue(OPTION_TARGET_SEQUENCES_COLLECTION),
        cl.getOptionValue(OPTION_RENDERING_CACHE, DEFAULT_ASSETS_LOCATION)
    );
    loader.updateFromReachableDir(reachablesDir);

    if (cl.hasOption(OPTION_PROJECTIONS_SOURCE_DATA)) {
      File projectionFile = new File(cl.getOptionValue(OPTION_PROJECTIONS_SOURCE_DATA, DEFAULT_PROJECTIONS_PATH));
      if (!projectionFile.exists() || projectionFile.isDirectory()) {
        cliUtil.failWithMessage("Projection file at %s does not exist or is a directory",
            projectionFile.getAbsolutePath());
      }
      loader.updateFromProjectionFile(projectionFile);
    }

    if (cl.hasOption(OPTION_PROJECTED_INCHIS_SOURCE_DATA)) {
      File projectedInchisFile = new File(cl.getOptionValue(OPTION_PROJECTED_INCHIS_SOURCE_DATA));
      if (!projectedInchisFile.exists() || projectedInchisFile.isDirectory()) {
        cliUtil.failWithMessage("InChI file at %s does not exist or is a directory",
            projectedInchisFile.getAbsolutePath());
      }
      loader.updateFromProjectedInchiFile(projectedInchisFile);
    }
  }


  /**
   * Constructor for Loader. Instantiates connexions to Mongo databases
   * and Virtuoso triple store (Pubchem synonyms only)
   * @param host The host for the target Reachables MongoDB
   * @param port The port for the target Reachables MongoDB
   * @param targetDB The database for the target Reachables MongoDB
   * @param targetCollection The collection for the target Reachables MongoDB
   * @param targetSequenceCollection The collection for the target SequenceData MongoDB
   * @param renderingCache A directory where rendered images should live
   */
  public Loader(String host, Integer port, String sourceDB, String targetDB,
                String targetCollection, String targetSequenceCollection, String renderingCache) {
    sourceDBconn = new MongoDB(host, port, sourceDB);
    pubchemSynonymsDriver = new PubchemMeshSynonyms();
    moleculeRenderer = new MoleculeRenderer(new File(renderingCache));
    // TODO Does this need to pass in the strings from the constructor?
    wordCloudGenerator = new WordCloudGenerator(host, port, sourceDB, renderingCache);

    MongoClient mongoClient;
    try {
      mongoClient = new MongoClient(new ServerAddress(host, port));
    } catch (UnknownHostException e) {
      LOGGER.error("Connection to MongoClient failed. Please double check the target database's host and port.");
      throw new RuntimeException(e);
    }
    DB reachables = mongoClient.getDB(targetDB);

    // TODO: this unsafe initialization does not belong in the constructor.
    try {
      calculator = new PhysiochemicalPropertiesCalculator.Factory().build();
    } catch (PluginException e) {
      LOGGER.error("Unable to initialize physiochemical calculator: %s", e.getMessage());
      throw new RuntimeException(e);
    }

    jacksonReachablesCollection =
            JacksonDBCollection.wrap(reachables.getCollection(targetCollection), Reachable.class, String.class);
    jacksonSequenceCollection =
            JacksonDBCollection.wrap(reachables.getCollection(targetSequenceCollection), SequenceData.class, String.class);

    jacksonReachablesCollection.ensureIndex(new BasicDBObject(Reachable.INCHI_FIELD_NAME, "hashed"));
    jacksonSequenceCollection.createIndex(new BasicDBObject(SequenceData.SEQUENCE_FIELD_NAME, "hashed"));
    jacksonSequenceCollection.createIndex(new BasicDBObject(SequenceData.ORGANISM_FIELD_NAME, 1));
  }

  JacksonDBCollection<Reachable, String> getJacksonReachablesCollection() {
    return jacksonReachablesCollection;
  }

  JacksonDBCollection<SequenceData, String> getJacksonSequenceCollection() {
    return jacksonSequenceCollection;
  }

  MongoDB getChemicalSourceDB() {
    return sourceDBconn;
  }

  /**
   * A convenience constructor for users who don't care about sequences or cached images.
   * @param host The host for the target Reachables MongoDB
   * @param port The port for the target Reachables MongoDB
   * @param targetDB The database for the target Reachables MongoDB
   * @param targetCollection The collection for the target Reachables MongoDB
   */
  public Loader(String host, Integer port, String sourceDB, String sourceDBColl) {
    this(host, port, sourceDB, DUMMY_TARGET_DB, DUMMY_TARGET_COLLECTION, DUMMY_SEQUENCE_COLLECTION, DEFAULT_ASSETS_LOCATION);
  }

  // TODO Move these getters to a different place/divide up concerns better?
  private static String getSmiles(Molecule mol) {
    try {
      return MoleculeExporter.exportAsSmiles(mol);
    } catch (MolExportException e) {
      return null;
    }
  }

  /**
   * Get inchi key from molecule
   */
  private static String getInchiKey(Molecule mol) {
    try {
      String inchikey = MoleculeExporter.exportAsInchiKey(mol);
      return inchikey.replaceAll("InChIKey=", "");
    } catch (MolExportException e) {
      return null;
    }
  }

  private static String getMoleculeName(Molecule mol, String inchi) {
    // Generate a Chemaxon name
    String chemaxonTraditionalName;
    try {
      chemaxonTraditionalName = MolExporter.exportToFormat(mol, CHEMAXON_TRADITIONAL_NAME_FORMAT);
    } catch (IOException e) {
      chemaxonTraditionalName = null;
    }
    // If a Chemaxon name was successfully generated, and is of reasonable length, get it
    if (chemaxonTraditionalName != null && chemaxonTraditionalName.length() < MAX_CHEMAXON_NAME_LENGTH) {
      return chemaxonTraditionalName;
    }
    // Finally default to returning the InChI if nothing better :(
    return inchi;
  }

  private SynonymData getSynonymData(String inchi) {
    String compoundID = pubchemSynonymsDriver.fetchCIDFromInchi(inchi);
    Map<MeshTermType, Set<String>> meshSynonyms = pubchemSynonymsDriver.fetchMeshTermsFromCID(compoundID);
    Map<PubchemSynonymType, Set<String>> pubchemSynonyms = pubchemSynonymsDriver.fetchPubchemSynonymsFromCID(compoundID);
    return new SynonymData(pubchemSynonyms, meshSynonyms);
  }

  /**
   * Construct a Reachable.
   * Gets names and xref from `sourceDBconn` collection `chemicals`
   * Tries to import to molecule and export names
   */
  // TODO let's have Optional<Reachable> be the return type here
  private Reachable constructOrFindReachable(String inchi) {
    // TODO Better break the logic into discrete components
    // Only construct a new one if one doesn't already exist.
    Reachable preconstructedReachable = queryByInchi(inchi);
    if (preconstructedReachable != null) {
      return preconstructedReachable;
    }

    Chemical chemical = sourceDBconn.getChemicalFromInChI(inchi);

    // For L3/L4 molecules, we won't find them in the DB but still need to construct a Reachable object.
    // In this case, fall back to `constructReachableFromInchi`
    Reachable reachable = chemical == null ?
        constructReachableFromInchi(inchi) : constructReachableFromChemical(chemical);

    // TODO: this should save the Reachable like constructOrFindReachableById.  Make sure that's safe.
    return reachable;
  }

  public Reachable constructOrFindReachableById(Long id) {
    Reachable preconstructedReachable = queryById(id);

    if (preconstructedReachable != null) {
      return preconstructedReachable;
    }

    Chemical chemical = sourceDBconn.getChemicalFromChemicalUUID(id);

    if (chemical != null) {
      Reachable reachable = constructReachableFromChemical(chemical);
      // We didn't find this in the DB before but expect to the next time we look for it, so store before returning.
      jacksonReachablesCollection.insert(reachable);
      return reachable;
    } else {
      LOGGER.error("Chemical ID %d not found in the database. Returning null");
      return null;
    }
  }

  private Reachable constructReachable(Long id, String inchi, List<String> names,
                                       String wordcloudFilename, Map<Chemical.REFS, BasicDBObject> xrefs) {

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

    String smiles = getSmiles(mol);
    if (smiles == null) {
      LOGGER.error("Failed to export molecule %s to smiles", inchi);
    }

    String inchikey = getInchiKey(mol);
    if (inchikey == null) {
      LOGGER.error("Failed to export molecule %s to inchi key", inchi);
    }

    String pageName = names.isEmpty() ? getMoleculeName(mol, inchi) : names.get(0);

    String renderingFilename = null;
    Optional<File> rendering = moleculeRenderer.generateRendering(inchi);
    if (rendering.isPresent()) {
      renderingFilename = rendering.get().getName();
    }

    SynonymData synonymData = getSynonymData(inchi);

    PhysiochemicalPropertiesCalculator.Features analysisFeatures = null;

    try {
      analysisFeatures = calculator.computeFeatures(mol);
    } catch (PluginException e) {
      LOGGER.error(String.format("Caught a PluginException when computing physiochemical properties for inchi %s: %s",
          inchi, e.getMessage()));
    } catch (IOException e) {
      LOGGER.error(String.format("Caught an IOException when computing physiochemical properties for inchi %s: %s",
          inchi, e.getMessage()));
    }
    PhysiochemicalProperties physiochemicalProperties = analysisFeatures == null ? null:
        new PhysiochemicalProperties(analysisFeatures.getpKa(), analysisFeatures.getLogP(), analysisFeatures.getHlb());

    return new Reachable(id, pageName, inchi, smiles, inchikey, names, synonymData, renderingFilename,
        wordcloudFilename, xrefs, physiochemicalProperties);
  }

  private Reachable constructReachableFromInchi(String inchi) {
    Long id = jacksonReachablesCollection.count() + BASE_ID_PROJECTIONS;
    List<String> names = new ArrayList<>();
    String wordcloudFilename = null;
    Map<Chemical.REFS, BasicDBObject> xrefs = null;
    return constructReachable(id, inchi, names, wordcloudFilename, xrefs);
  }

  private Reachable constructReachableFromChemical(Chemical c) {

    if (c == null) {
      LOGGER.error("Cannot create a Reachable from a null input Chemical.");
      return null;
    }
    String inchi = c.getInChI();
    List<String> names = c.getBrendaNames();
    Map<Chemical.REFS, BasicDBObject> xrefs = c.getXrefMap();
    Long id = c.getUuid();

    File wordcloud = wordCloudGenerator.getWordcloudFile(inchi);
    String wordcloudFilename = null;
    if (wordcloud.exists()) {
      wordcloudFilename = wordcloud.getName();
    }

    Reachable reachable = constructReachable(id, inchi, names, wordcloudFilename, xrefs);

    reachable.setPathwayVisualization("cscd" + reachable.getId() + ".dot");
    return reachable;
  }

  private void updateWithPrecursors(String inchi, List<Precursor> pre) throws IOException {
    Reachable reachable = queryByInchi(inchi);

    // If is null we create a new one
    reachable = reachable == null ? constructOrFindReachable(inchi) : reachable;

    if (reachable == null) {
      LOGGER.warn("Still couldn't construct InChI after retry, aborting");
      return;
    }

    reachable.getPrecursorData().addPrecursors(pre);

    upsert(reachable);
  }

  private Reachable queryByInchi(String inchi) {
    DBObject query = new BasicDBObject(Reachable.INCHI_FIELD_NAME, inchi);
    return jacksonReachablesCollection.findOne(query);
  }

  Reachable queryById(Long id) {
    DBObject query = new BasicDBObject(Reachable.ID_FIELD_NAME, id);
    return jacksonReachablesCollection.findOne(query);
  }

  // Package private so helpers (like the PatentFinder) can use it.
  void upsert(Reachable reachable) {
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
   * Get an organism name using an organism name id, with a healthy dose of caching since there are only about 21k
   * organisms for 9M reactions.
   * @param id The organism name id to fetch.
   * @return The organism name or "(unknown)" if that organism can't be found, which should never ever happen.
   */
  private String getOrganismName(Long id) {
    String cachedName = organismCache.getIfPresent(id);
    if (cachedName != null) {
      return cachedName;
    }

    String name = sourceDBconn.getOrganismNameFromId(id);
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
      List<Seq> sequences = sourceDBconn.getSeqWithRxnRef(rxnId);
      for (Seq seq : sequences) {
        if (seq.getSequence() == null) {
          LOGGER.debug("Found seq entry with id %d has null sequence.  How did that happen?", seq.getUUID());
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

  private void updateCurrentChemical(Chemical current, Long currentId, Long parentId, List<Precursor> precursors) throws IOException {
    // Update source as reachables, as these files are parsed from `cascade` construction
    Reachable rech = constructOrFindReachable(current.getInChI());
    rech.setNative(parentId == -1);
    if (!precursors.isEmpty()) {
      if (rech != null) {
        rech.setPathwayVisualization("cscd" + currentId + ".dot");
        upsert(rech);
        updateWithPrecursors(current.getInChI(), precursors);
      }
    } else {
      try {
        upsert(rech);
        // TODO Remove null pointer exception check
      } catch (NullPointerException e) {
        LOGGER.info("Null pointer, unable to parse InChI %s.", current == null ? "null" : current.getInChI());
      }
    }
  }

  private List<Precursor> getUpstreamPrecursors(Long parentId, JSONArray upstreamReactions) {
    Map<Long, InchiDescriptor> substrateCache = new HashMap<>();
    Map<List<InchiDescriptor>, Precursor> substratesToPrecursor = new HashMap<>();
    List<Precursor> precursors = new ArrayList<>();

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
            Chemical parent = sourceDBconn.getChemicalFromChemicalUUID(subId);
            upsert(constructOrFindReachable(parent.getInChI()));
            parentDescriptor = new InchiDescriptor(constructOrFindReachable(parent.getInChI()));
            thisRxnSubstrates.add(parentDescriptor);
            substrateCache.put(subId, parentDescriptor);
            // TODO Remove null pointer exception check
          } catch (NullPointerException e) {
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
          substratesToPrecursor.get(thisRxnSubstrates).addSequences(sequenceIds);
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

    return precursors;
  }

  private void updateFromReachablesFile(File file) {
    LOGGER.info("Processing file %s", file.getName());
    try {
      // Read in the file and parse it as JSON
      String jsonTxt = IOUtils.toString(new FileInputStream(file));
      JSONObject fileContents = new JSONObject(jsonTxt);

      // Parsing errors should happen as near to the point of loading as possible so it crashes fast.
      Long parentId = fileContents.getLong("parent");
      JSONArray upstreamReactions = fileContents.getJSONArray("upstream");
      Long currentId = fileContents.getLong("chemid");

      LOGGER.info("Chem id is: " + currentId);

      // Get the actual chemical that is the product of the above chemical.  Bail quickly if we can't find it.
      Chemical current = sourceDBconn.getChemicalFromChemicalUUID(currentId);
      LOGGER.info("Tried to fetch chemical id %d: %s", currentId, current);

      if (current == null) {
        return;
      }
      MoleculeImporter.assertNotFakeInchi(current.getInChI());

      List<Precursor> precursors = getUpstreamPrecursors(parentId, upstreamReactions);
      updateCurrentChemical(current, currentId, parentId, precursors);
    } catch (MoleculeImporter.FakeInchiException e) {
      LOGGER.warn("Skipping file %s due to fake InChI exception", file.getName());
    } catch (IOException e) {
      // We can only work with files we can parse, so if we can't
      // parse the file we just don't do anything and submit an error.
      LOGGER.warn("Unable to load file " + file.getAbsolutePath());
    } catch (JSONException e) {
      LOGGER.error("Unable to parse JSON of file at " + file.getAbsolutePath());
    }
  }

  private void updateFromReachableFiles(List<File> files) {
    files.forEach(this::updateFromReachablesFile);
  }

  private void updateFromReachableDir(File file) throws IOException {
    // Get all the reachables from the reachables text file so it doesn't take forever to look for all the files.

    File dataDirectory = Arrays.stream(file.listFiles())
            .filter(x -> x.getName().endsWith("data") && x.isDirectory())
            .collect(Collectors.toList()).get(0).getAbsoluteFile();

    File reachablesFile = Arrays.stream(file.listFiles())
            .filter(x -> x.getName().endsWith("reachables.txt") && x.isFile())
            .collect(Collectors.toList()).get(0).getAbsoluteFile();

    List<Integer> chemicalIds = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(reachablesFile))) {
      String line;
      while ((line = br.readLine()) != null) {
        chemicalIds.add(Integer.valueOf(line.split("\t")[0]));
      }
    }

    List<File> validFiles = chemicalIds.stream().
            map(i -> new File(dataDirectory, "c" + String.valueOf(i) + ".json")).
            collect(Collectors.toList());

    LOGGER.info("Found %d reachables files.",validFiles.size());
    updateFromReachableFiles(validFiles);
  }

  public void updateFromProjection(ReachablesProjectionUpdate projection) {
    // Construct substrates
    List<Reachable> substrates = projection.getSubstrates().stream()
            .map(this::constructOrFindReachable)
            .collect(Collectors.toList());

    // Add substrates in, or make sure they were added.
    substrates.forEach(this::upsert);

    // Construct descriptors.
    List<InchiDescriptor> precursors = substrates.stream()
            .map(InchiDescriptor::new)
            .collect(Collectors.toList());

    // For each product, create and add precursors.
    projection.getProducts().forEach(p -> {
      // Get product
      Reachable product = constructOrFindReachable(p);
      // TODO Don't punt on sequences
      product.getPrecursorData().addPrecursor(new Precursor(precursors, projection.getRos().get(0), new ArrayList<>()));
      upsert(product);
    });
  }

  private void createAndUpdateReachable(String inchi) {
    Reachable reachable = constructOrFindReachable(inchi);
    upsert(reachable);
  }

  public void updateFromProjectionFile(File file) throws IOException {
    LOGGER.info("Processing projection file: %s", file.getName());
    List<ReachablesProjectionResult> projectionResults = Arrays.asList(
        MAPPER.readValue(file, ReachablesProjectionResult[].class));
    List<ReachablesProjectionUpdate> projectionUpdates = projectionResults.stream().map(
        ReachablesProjectionUpdate::new).collect(Collectors.toList());
    projectionUpdates.forEach(this::updateFromProjection);
  }

  public void updateFromProjectedInchiFile(File file) throws IOException {
    LOGGER.info("Processing InChI file: %s", file.getName());
    L2InchiCorpus inchiCorpus = new L2InchiCorpus();
    inchiCorpus.loadCorpus(file);
    inchiCorpus.getInchiList().forEach(this::createAndUpdateReachable);
  }
}
