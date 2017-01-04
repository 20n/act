package act.installer.reachablesexplorer;

import act.shared.Chemical;
import com.act.reachables.Cascade;
import com.act.reachables.ReactionPath;
import com.act.utils.CLIUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.JacksonDBCollection;
import org.twentyn.proteintodna.DNADesign;
import org.twentyn.proteintodna.DNAOrgECNum;
import org.twentyn.proteintodna.OrgAndEcnum;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FreemarkerRenderer {
  public static final Logger LOGGER = LogManager.getFormatterLogger(FreemarkerRenderer.class);

  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";
  private static final String OPTION_DB_NAME = "n";
  private static final String OPTION_INSTALLER_SOURCE_DB = "i";
  private static final String OPTION_REACHABLES_COLLECTION = "r";
  private static final String OPTION_SEQUENCES_COLLECTION = "s";
  private static final String OPTION_DNA_COLLECTION = "d";
  private static final String OPTION_RENDERING_CACHE = "c";
  private static final String OPTION_OUTPUT_DEST = "o";
  private static final String OPTION_OMIT_PATHWAYS_AND_DESIGNS = "x";
  private static final String OPTION_RENDER_SOME = "m";

  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;
  private static final String DEFAULT_CHEMICALS_DATABASE = "jarvis_2016-12-09";
  private static final String DEFAULT_DB_NAME = "wiki_reachables";
  private static final String DEFAULT_REACHABLES_COLLECTION = "reachables_2016-12-26";
  private static final String DEFAULT_SEQUENCES_COLLECTION = "sequences_2016-12-26";
  private static final String DEFAULT_DNA_COLLECTION = "designs_2016-12-26";
  private static final String DEFAULT_RENDERING_CACHE = "/mnt/data-level1/data/reachables-explorer-rendering-cache";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class consumes and renders a DB of reachable molecules, pathways, and DNA designs."
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
        .longOpt("source-db-name")
    );
    add(Option.builder(OPTION_DB_NAME)
        .argName("DB name")
        .desc(String.format("The name of the DB where reachables, pathways, and designs are located(default: %s)",
            DEFAULT_DB_NAME))
        .hasArg()
        .longOpt("dest-db-name")
    );
    add(Option.builder(OPTION_REACHABLES_COLLECTION)
        .argName("collection name")
        .desc(String.format(
            "The name of the collection from which to read reachables data (default: %s)",
            DEFAULT_REACHABLES_COLLECTION))
        .hasArg()
        .longOpt("reachables-collection")
    );
    add(Option.builder(OPTION_SEQUENCES_COLLECTION)
        .argName("collection name")
        .desc(String.format(
            "The name of the collection from which to read sequence documents (default: %s)",
            DEFAULT_SEQUENCES_COLLECTION))
        .hasArg()
        .longOpt("seq-collection")
    );
    add(Option.builder(OPTION_DNA_COLLECTION)
        .argName("collection name")
        .desc(String.format(
            "The name of the collection from which to read DNA designs (default: %s)", DEFAULT_DNA_COLLECTION))
        .hasArg()
        .longOpt("dna-collection")
    );
    add(Option.builder(OPTION_RENDERING_CACHE)
        .argName("path to cache")
        .desc(String.format(
            "A directory in which to find rendered images for reachables documents (default: %s)",
            DEFAULT_RENDERING_CACHE))
        .hasArg()
        .longOpt("cache-dir")
    );
    add(Option.builder(OPTION_OUTPUT_DEST)
        .argName("path")
        .desc("A directory into which to write wiki pages (subdirectories will be automatically created)")
        .hasArg().required()
        .longOpt("output")
    );
    add(Option.builder(OPTION_OMIT_PATHWAYS_AND_DESIGNS)
        .desc("Omit pathways and designs, replacing them with an order link")
        .longOpt("no-pathways")
    );
    add(Option.builder(OPTION_OMIT_PATHWAYS_AND_DESIGNS)
        .desc("Omit pathways and designs, replacing them with an order link")
        .longOpt("no-pathways")
    );
    add(Option.builder(OPTION_RENDER_SOME)
        .argName("molecule")
        .desc("Render pages for specified molecules; can be a numeric ids, InChIs, or InChI Keys, *separated by '|'* " +
            "for InChI compatibility.  Molecules ust exist in chemical source DB if InChI or InChI Key is used.")
        .hasArgs().valueSeparator('|')
        .longOpt("render-this")
    );
  }};

  private static final String DEFAULT_REACHABLE_TEMPLATE_FILE = "Mediawiki.ftl";
  private static final String DEFAULT_PATHWAY_TEMPLATE_FILE = "MediaWikiPathways.ftl";

  private static final int MAX_SEQUENCE_LENGTH = 50;

  private static final int SEQUENCE_SAMPLE_START = 3000;
  private static final int SEQUENCE_SAMPLE_SIZE = 80;

  private static final Pattern REGEX_ID = Pattern.compile("^\\d+$");
  private static final Pattern REGEX_INCHI = Pattern.compile("^InChI=1S?/");
  // Based on https://en.wikipedia.org/wiki/International_Chemical_Identifier#InChIKey
  private static final Pattern REGEX_INCHI_KEY = Pattern.compile("^[A-Z]{14}-[A-Z]{10}-[A-Z]$");

  private static final String ORDER_PATH = "/order";
  private static final String ORDER_INCHI_KEY_PARAM = "inchi_key";

  private String reachableTemplateName;
  private String pathwayTemplateName;
  private Loader loader;
  private File reachablesDest;
  private File pathsDest;
  private File seqsDest;
  private Boolean hidePathways = false;

  // Note: there should be one of these per process.  TODO: make this a singleton.
  private Configuration cfg;
  private Template reachableTemplate;
  private Template pathwayTemplate;

  private JacksonDBCollection<DNADesign, String> dnaDesignCollection;

  private Map<Long, List<PathwayDoc>> completedPathways = new HashMap<>();
  private Cache<Long, Reachable> reachablesCache = Caffeine.newBuilder().maximumSize(100).build();


  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(Loader.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    File baseOutputDir = new File(cl.getOptionValue(OPTION_OUTPUT_DEST));
    if (!baseOutputDir.exists()) {
      cliUtil.failWithMessage("Unable to find output directory at %s", baseOutputDir.getAbsolutePath());
      return;
    }

    File reachablesOut = new File(baseOutputDir, "Reachables");
    File pathsOut = new File(baseOutputDir, "Paths");
    File seqsOut = new File(baseOutputDir, "Sequences");

    for (File subdir : Arrays.asList(reachablesOut, pathsOut, seqsOut)) {
      if (!subdir.exists()) {
        LOGGER.info("Creating output directory at %s", subdir.getAbsolutePath());
        subdir.mkdir();
      } else if (!subdir.isDirectory()) {
        cliUtil.failWithMessage("Output directory at %s is not a directory", subdir.getAbsolutePath());
        return;
      }
    }

    FreemarkerRenderer renderer = FreemarkerRendererFactory.build(
        cl.getOptionValue(OPTION_DB_HOST, DEFAULT_HOST),
        Integer.valueOf(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_PORT.toString())),
        cl.getOptionValue(OPTION_DB_NAME, DEFAULT_DB_NAME),
        cl.getOptionValue(OPTION_REACHABLES_COLLECTION, DEFAULT_REACHABLES_COLLECTION),
        cl.getOptionValue(OPTION_SEQUENCES_COLLECTION, DEFAULT_SEQUENCES_COLLECTION),
        cl.getOptionValue(OPTION_DNA_COLLECTION, DEFAULT_DNA_COLLECTION),
        cl.getOptionValue(OPTION_RENDERING_CACHE, DEFAULT_RENDERING_CACHE),
        cl.getOptionValue(OPTION_INSTALLER_SOURCE_DB, DEFAULT_CHEMICALS_DATABASE),
        cl.hasOption(OPTION_OMIT_PATHWAYS_AND_DESIGNS),
        reachablesOut,
        pathsOut,
        seqsOut
    );
    LOGGER.info("Page generation starting");

    List<Long> idsToRender = Collections.emptyList();
    if (cl.hasOption(OPTION_RENDER_SOME)) {
      idsToRender = Arrays.stream(cl.getOptionValues(OPTION_RENDER_SOME))
          .map(renderer::lookupMolecule).collect(Collectors.toList());
    }
    renderer.generatePages(idsToRender);
  }

  private Long lookupMolecule(String someKey) {
    if (REGEX_ID.matcher(someKey).find()) {
      // Note: this doesn't verify that the chemical id is valid.  Maybe we should do that?
      return Long.valueOf(someKey);
    } else if (REGEX_INCHI.matcher(someKey).find()) {
      Chemical c = loader.getChemicalSourceDB().getChemicalFromInChI(someKey);
      if (c != null) {
        return c.getUuid();
      }
    } else if (REGEX_INCHI_KEY.matcher(someKey).find()) {
      Chemical c = loader.getChemicalSourceDB().getChemicalFromInChIKey(someKey);
      if (c != null) {
        return c.getUuid();
      }
    } else {
      String msg = String.format("Unable to find key type for query '%s'", someKey);
      LOGGER.error(msg);
      throw new IllegalArgumentException(msg);
    }
    String msg = String.format("Unable to find matching chemical for query %s", someKey);
    LOGGER.error(msg);
    throw new IllegalArgumentException(msg);
  }

  private FreemarkerRenderer(Loader loader, Boolean hidePathways,
                             File reachablesDest, File pathsDest, File seqsDest) {
    this.reachableTemplateName = DEFAULT_REACHABLE_TEMPLATE_FILE;
    this.pathwayTemplateName = DEFAULT_PATHWAY_TEMPLATE_FILE;
    this.loader = loader;
    this.hidePathways = hidePathways;

    this.reachablesDest = reachablesDest;
    this.pathsDest = pathsDest;
    this.seqsDest = seqsDest;
  }

  private void init(String dbHost, Integer dbPort, String dbName, String dnaCollection) throws IOException {
    cfg = new Configuration(Configuration.VERSION_2_3_23);

    cfg.setClassLoaderForTemplateLoading(
        this.getClass().getClassLoader(), "/act/installer/reachablesexplorer/templates");
    cfg.setDefaultEncoding("UTF-8");

    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(true);

    reachableTemplate = cfg.getTemplate(reachableTemplateName);
    pathwayTemplate = cfg.getTemplate(pathwayTemplateName);

    // TODO: move this elsewhere.
    MongoClient client = new MongoClient(new ServerAddress(dbHost, dbPort));
    DB db = client.getDB(dbName);

    dnaDesignCollection = JacksonDBCollection.wrap(db.getCollection(dnaCollection), DNADesign.class, String.class);
  }

  public void generatePages(List<Long> idsToRender) throws IOException, TemplateException {

    // Limit iteration to only molecules we care about if any are specified.
    DBCursor<ReactionPath> cascadeCursor = idsToRender == null || idsToRender.size() == 0 ?
        Cascade.get_pathway_collection().find() :
        Cascade.get_pathway_collection().find(DBQuery.in("target", idsToRender));

    int i = 0;
    while (cascadeCursor.hasNext()) {
      // Hacked cursor munging to only consider targets of pathways.
      ReactionPath thisPath = cascadeCursor.next();

      Reachable r = reachablesCache.getIfPresent(thisPath.getTarget());
      if (r == null) {
        /* Temporary fix: create reachables on the fly for pathway targets to ensure we have documents to use when
         * generating the molecule pages.  This should not be necessary in a world where reachables are all loaded into
         * the DB before pathways. */

        r = loader.constructOrFindReachableById(thisPath.getTarget());
        if (r == null) {
          // This should be impossible, but there was previously a check for this condition so...
          String msg =
              String.format("Could not construct reachable %d, because not found in the DB", thisPath.getTarget());
          LOGGER.error(msg);
          throw new RuntimeException(msg);
        }
        reachablesCache.put(r.getId(), r);
      }

      /* Don't generate any pathway pages if we're instructed to skip pathways.  We still have to make sure the
       * Reachable objects are constructed, however, so allow the loop to progress to this point before continuing. */
      if (this.hidePathways) {
        continue;
      }

      String inchiKey = r.getInchiKey();
      if (inchiKey != null) {
        PathwayDoc pathwayDoc = generatePathDoc(r, thisPath, this.pathsDest, this.seqsDest);
        List<PathwayDoc> attributions = completedPathways.get(thisPath.getTarget());
        if (attributions == null) {
          attributions = new ArrayList<>();
          completedPathways.put(thisPath.getTarget(), attributions);
        }
        attributions.add(pathwayDoc);

        i++;
        if (i % 100 == 0) {
          LOGGER.info("Completed %d pathways", i);
        }
      } else {
        LOGGER.error("page does not have an inchiKey");
      }
    }

    LOGGER.info("Done generating pathway pages, moving on to reachables");

    // No iterate over all the reachable documents we've created and generate pages for each using our pathway links.
    DBCursor<Reachable> reachableCursor = idsToRender == null || idsToRender.size() == 0 ?
        loader.getJacksonReachablesCollection().find() :
        loader.getJacksonReachablesCollection().find(DBQuery.in("_id", idsToRender));

    i = 0;
    while (reachableCursor.hasNext()) {
      Reachable r = reachableCursor.next();

      if (r.getInchiKey() == null || r.getInchiKey().isEmpty()) {
        LOGGER.error("Found reachable %d with no InChI key--skipping", r.getId());
        continue;
      }

      try (Writer w = new PrintWriter(new File(this.reachablesDest, r.getInchiKey()))) {
        List<PathwayDoc> pathwayDocs = completedPathways.getOrDefault(r.getId(), new ArrayList<>());
        Object model = buildReachableModel(r, pathwayDocs);
        reachableTemplate.process(model, w);
      }

      i++;
      if (i % 100 == 0) {
        LOGGER.info("Completed %d reachables", i);
      }
    }

    LOGGER.info("Page generation complete");
  }

  private Object buildReachableModel(Reachable r, List<PathwayDoc> pathwayDocs) {
    /* Freemarker's template language is based on a notion of "hashes," which are effectively just an untyped hierarchy
     * of maps and arrays culminating in scalar values (think of it like a JSON doc done up in plain Java types).
     * There are new facilities to run some Java accessors from within freemarker templates, but the language is
     * sufficiently brittle on its own that complicating things seems like a recipe for much pain.
     *
     * This is just how Freemarker works.  Oh well.
     */
    Map<String, Object> model = new HashMap<>();

    model.put("pageTitle", r.getPageName());

    model.put("inchi", r.getInchi());

    model.put("smiles", r.getSmiles());

    model.put("structureRendering", r.getStructureFilename());

    model.put("cascade", r.getPathwayVisualization());

    if (hidePathways) {
      model.put("hideCascades", true);
      // TODO: is there a cleaner way to make this URL?
      model.put("orderLink", String.format("%s?%s=%s", ORDER_PATH, ORDER_INCHI_KEY_PARAM, r.getInchiKey()));
    }

    if (r.getWordCloudFilename() != null) {
      model.put("wordcloudRendering", r.getWordCloudFilename());
    }

    List<Precursor> orderedPrecursors = new ArrayList<>(r.getPrecursorData().getPrecursors());
    Collections.sort(orderedPrecursors, (a, b) -> {
      if (a.getSequences() != null && b.getSequences() == null) {
        return -1;
      }
      if (a.getSequences() == null && b.getSequences() != null) {
        return 1;
      }
      return Integer.valueOf(b.getSequences().size()).compareTo(a.getSequences().size());
    });

    List<Object> pathways = new ArrayList<>();
    for (PathwayDoc doc : pathwayDocs) {
      pathways.add(new HashMap<String, String>() {{
        put("link", doc.getPageName());
        put("name", doc.getPathText());
        // With help from http://stackoverflow.com/questions/13183982/html-entity-for-check-mark
        put("hasDna", doc.getHasDNA() ? "&#10003;" : "");
      }});
    }
    if (pathways.size() > 0) {
      model.put("pathways", pathways);
    }

    List<PatentSummary> patentSummaries = r.getPatentSummaries();
    if (patentSummaries != null && !patentSummaries.isEmpty()) {
      List<Map<String, String>> patentModel = patentSummaries.stream().
          map(p -> {
            return new HashMap<String, String>() {{
              put("title", p.getTitle());
              put("link", p.generateUSPTOURL());
              put("id", p.getId().replaceFirst("-.*$", "")); // Strip date + XML suffix, just leave grant number.
            }};
          }).
          collect(Collectors.toList());
      model.put("patents", patentModel);
    }

    Map<Chemical.REFS, BasicDBObject> xrefs = r.getXref();

    // Each XREF being populated in a Reachable as DBObjects, serialization and deserialization causes funky problems
    // to appear. In particular, casting to BasicDBObjects fails because Jackson deserialized it as a LinkedHashMap
    // See http://stackoverflow.com/questions/28821715/java-lang-classcastexception-java-util-linkedhashmap-cannot-be-cast-to-com-test
    if (xrefs.containsKey(Chemical.REFS.BING)) {
      BasicDBObject bingXref = xrefs.get(Chemical.REFS.BING);
      Map<String, Object> bingMetadata = (Map) bingXref.get("metadata");
      List<Map> bingUsageTerms = (List) bingMetadata.get("usage_terms");
      if (bingUsageTerms.size() > 0) {
        List<Map<String, Object>> bingUsageTermsModel = bingUsageTerms.stream()
            .map(usageTerm -> new HashMap<String, Object>() {{
              put("usageTerm", usageTerm.get("usage_term"));
              put("urls", usageTerm.get("urls"));
            }})
            .collect(Collectors.toList());
        Collections.sort(
            bingUsageTermsModel,
            (o1, o2) -> ((ArrayList) o2.get("urls")).size() - ((ArrayList) o1.get("urls")).size());
        model.put("bingUsageTerms", bingUsageTermsModel);
      }
    }

    if (xrefs.containsKey(Chemical.REFS.WIKIPEDIA)) {
      BasicDBObject wikipediaXref = xrefs.get(Chemical.REFS.WIKIPEDIA);
      String wikipediaUrl = wikipediaXref.getString("dbid");
      model.put("wikipediaUrl", wikipediaUrl);
    }

    if (r.getSynonyms() != null) {
      if (r.getSynonyms().getPubchemSynonyms() != null) {
        List<Map<String, Object>> pubchemSynonymModel = r.getSynonyms().getPubchemSynonyms().entrySet().stream()
            .map(entry -> new HashMap<String, Object>() {{
              put("synonymType", entry.getKey().toString());
              put("synonyms", entry.getValue().stream().collect(Collectors.toList()));
            }})
            .collect(Collectors.toList());
        model.put("pubchemSynonyms", pubchemSynonymModel);
      }
      if (r.getSynonyms().getMeshHeadings() != null) {
        List<Map<String, Object>> meshHeadingModel = r.getSynonyms().getMeshHeadings().entrySet().stream()
            .map(entry -> new HashMap<String, Object>() {{
              String key = entry.getKey().toString();
              put("synonymType", "NONE".equals(key) ? "MeSH" : key);
              put("synonyms", entry.getValue().stream().collect(Collectors.toList()));
            }})
            .collect(Collectors.toList());

        model.put("meshHeadings", meshHeadingModel);
      }
    }

    if (r.getPhysiochemicalProperties() != null) {
      PhysiochemicalProperties physiochemicalProperties = r.getPhysiochemicalProperties();
      model.put("physiochemicalProperties", new HashMap<String, String>() {{
        put("pka", String.format("%.2f", physiochemicalProperties.getPkaAcid1()));
        put("logp", String.format("%.2f", physiochemicalProperties.getLogPTrue()));
        put("hlb", String.format("%.2f", physiochemicalProperties.getHlbVal()));
      }});
    }

    return model;
  }

  private static class PathwayDoc {
    String pageName;
    String pathText;
    Boolean hasDNA;

    public PathwayDoc(String pageName, String pathText, Boolean hasDNA) {
      this.pageName = pageName;
      this.pathText = pathText;
      this.hasDNA = hasDNA;
    }

    public String getPageName() {
      return pageName;
    }

    public String getPathText() {
      return pathText;
    }

    public Boolean getHasDNA() {
      return hasDNA;
    }
  }

  public PathwayDoc generatePathDoc(
      Reachable target, ReactionPath path, File pathDestination, File sequenceDestination) throws IOException, TemplateException {
    DBCursor<ReactionPath> cursor = Cascade.get_pathway_collection().find(new BasicDBObject("target", target.getId()));

    String sourceDocName = makeSourceDocName(target);
    if (sourceDocName == null) {
      LOGGER.error("Target %d does not have inchiKey", path.getTarget());
      return null;
    }

    String pathwayDocName = String.format("Pathway_%s_%d", sourceDocName, path.getRank());

    List<Triple<String, String, DNAOrgECNum>> designDocsAndSummaries = path.getDnaDesignRef() != null ?
        renderSequences(sequenceDestination, pathwayDocName, path.getDnaDesignRef()) : Collections.emptyList();

    Pair<Object, String> model = buildPathModel(path, target, designDocsAndSummaries);
    if (model != null) {
      pathwayTemplate.process(model.getLeft(), new FileWriter(new File(pathDestination, pathwayDocName)));
    }

    return new PathwayDoc(
        pathwayDocName,
        model.getRight(),
        designDocsAndSummaries != null && designDocsAndSummaries.size() > 0
    );
  }

  private List<Triple<String, String, DNAOrgECNum>> renderSequences(File sequenceDestination, String docPrefix, String seqRef) throws IOException {
    DNADesign designDoc = dnaDesignCollection.findOneById(seqRef);
    if (designDoc == null) {
      LOGGER.error("Could not find dna seq for id %s", seqRef);
      return Collections.emptyList();
    }

    List<Triple<String, String, DNAOrgECNum>> sequenceFilesAndSummaries = new ArrayList<>();

    List<DNAOrgECNum> designs = new ArrayList<>(designDoc.getDnaDesigns());
    Collections.sort(designs, (a, b) -> {
      int result = a.getNumProteins().compareTo(b.getNumProteins());
      if (result != 0) {
        return result;
      }
      // Comparing org and ec number is expensive, so ignore it for now.
      return a.getDna().compareTo(b.getDna());
    });

    for (int i = 0; i < designs.size(); i++) {
      String design = designs.get(i).getDna();
      if (design == null) {
        LOGGER.error("No DNA found for design %d, reference %s", i, seqRef);
        continue;
      }
      design = design.toUpperCase();
      int designSize = design.length();
      String shortVersion;
      if (designSize > SEQUENCE_SAMPLE_START + SEQUENCE_SAMPLE_SIZE) {
        shortVersion = design.substring(SEQUENCE_SAMPLE_START, SEQUENCE_SAMPLE_START + SEQUENCE_SAMPLE_SIZE);
      } else {
        shortVersion = design.substring(
            designSize / 2 - SEQUENCE_SAMPLE_SIZE / 2,
            designSize / 2 + SEQUENCE_SAMPLE_SIZE / 2);
      }

      // Hack to get the DNA design sequence to be displayed on 4 lines. Introduce <br> tags at each 4th of the string.
      shortVersion = StringUtils.join(new String[]{
          shortVersion.substring(0, SEQUENCE_SAMPLE_SIZE / 4),
          shortVersion.substring(SEQUENCE_SAMPLE_SIZE / 4, SEQUENCE_SAMPLE_SIZE / 2),
          shortVersion.substring(SEQUENCE_SAMPLE_SIZE / 2, 3 * SEQUENCE_SAMPLE_SIZE / 4),
          shortVersion.substring(3 * SEQUENCE_SAMPLE_SIZE / 4)
      }, "<br />");

      String constructFilename = String.format("Design_%s_seq%d.txt", docPrefix, i + 1);

      try (FileWriter writer = new FileWriter(new File(sequenceDestination, constructFilename))) {
        writer.write(design);
        writer.write("\n");
      }

      sequenceFilesAndSummaries.add(Triple.of(constructFilename, shortVersion, designs.get(i)));
    }

    return sequenceFilesAndSummaries;
  }

  private String makeSourceDocName(Reachable r) throws IOException {
    String inchiKey = r.getInchiKey();
    if (inchiKey == null) {
      LOGGER.error("Reachable %s does not have inchiKey", r.getInchi());
      return null;
    }
    return inchiKey;
  }

  private Pair<Object, String> buildPathModel(ReactionPath p, Reachable target, List<Triple<String, String, DNAOrgECNum>> designs) throws IOException {

    Map<String, Object> model = new HashMap<>();

    LinkedList<Object> pathwayItems = new LinkedList<>();
    List<String> chemicalNames = new ArrayList<>();
    model.put("pathwayitems", pathwayItems);

    // Pathway nodes start at the target and work back, so reverse them to make page order go from start to finish.
    for (Cascade.NodeInformation i : p.getPath()) {
      Map<String, Object> nodeModel = new HashMap<>();
      pathwayItems.push(nodeModel);

      nodeModel.put("isreaction", i.getIsReaction());
      if (i.getIsReaction()) {
        String label = i.getLabel();
        label = label.replaceAll("&+", " ");
        List<String> ecNums = Arrays.stream(label.split("\\s")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        nodeModel.put("ecnums", ecNums); // TODO: clean up this title to make mediawiki happy with it.
        List<String> organisms = new ArrayList<>(i.getOrganisms());
        Collections.sort(organisms);
        nodeModel.put("organisms", organisms);
      } else {
        if (target == null) {
          LOGGER.error("Unable to locate pathway chemical %d in reachables db", i.getId());
          nodeModel.put("name", "(unknown)");
        } else {
          nodeModel.put("link", target.getInchiKey());
          // TODO: we really need a way of picking a good name for each molecule.
          // If the page name is the InChI, we reduce it to the formula for the purpose of pathway visualisation.
          String name = target.getPageName().startsWith("InChI") ? target.getPageName().split("/")[1] : target.getPageName();
          nodeModel.put("name", name);
          chemicalNames.add(name);
          if (target.getStructureFilename() != null) {
            nodeModel.put("structureRendering", target.getStructureFilename());
          } else {
            LOGGER.warn("No structure filename for %s", target.getPageName());
          }
        }
      }
    }

    String pageTitle = StringUtils.join(chemicalNames, " <- ");
    model.put("pageTitle", pageTitle);

    List<Map<String, Object>> dna = new ArrayList<>();
    int i = 1;

    for (Triple<String, String, DNAOrgECNum> design : designs) {
      final int num = i; // Sigh, must be final to use in this initialization block.

      dna.add(new HashMap<String, Object>() {{
        put("file", design.getLeft());
        put("sample", design.getMiddle());
        put("num", Integer.valueOf(num).toString());
        put("org_ec", renderDNADesignMetadata(design.getRight()));
      }});
      i++;
    }
    if (dna.size() > 0) {
      model.put("dna", dna);
    }

    return Pair.of(model, pageTitle);
  }


  private List<String> renderDNADesignMetadata(DNAOrgECNum dnaOrgECNum) {
    List<Set<OrgAndEcnum>> setOfOrgAndEcnum = dnaOrgECNum.getListOfOrganismAndEcNums();
    return setOfOrgAndEcnum.stream().
        filter(Objects::nonNull).
        map(this::renderSetOfProteinDesignMetadata).
        collect(Collectors.toList());
  }

  private String renderSetOfProteinDesignMetadata(Set<OrgAndEcnum> orgAndEcnumSet) {
    return StringUtils.capitalize(StringUtils.join(orgAndEcnumSet.stream().
        filter(Objects::nonNull).
        map(this::renderProteinMetadata).
        collect(Collectors.toList()),
        ", ")
    );
  }

  private String renderProteinMetadata(OrgAndEcnum orgAndEcnum) {
    String proteinMetadata = orgAndEcnum.getEcnum() == null ?
        String.format("from organism %s", orgAndEcnum.getOrganism()) :
        String.format("from organism %s, enzyme from EC# %s", orgAndEcnum.getOrganism(), orgAndEcnum.getEcnum());
    return proteinMetadata;
  }


  public static class FreemarkerRendererFactory {
    public static FreemarkerRenderer build(
        String dbHost, Integer dbPort, String dbName,
        String reachablesCollection, String sequencesCollection, String dnaCollection, String renderingCache,
        String chemicalsDB,
        Boolean hidePathways,
        File reachablesDest, File pathsDest, File seqsDest)
        throws IOException {
      Loader loader =
          new Loader(dbHost, dbPort, dbName, reachablesCollection, sequencesCollection, renderingCache, chemicalsDB);
      FreemarkerRenderer renderer = new FreemarkerRenderer(loader, hidePathways, reachablesDest, pathsDest, seqsDest);
      renderer.init(dbHost, dbPort, dbName, dnaCollection);
      return renderer;
    }
  }
}
