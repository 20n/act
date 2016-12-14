package act.installer.reachablesexplorer;

import act.installer.reachablesexplorer.l4reachables.L4Loader;
import act.installer.reachablesexplorer.l4reachables.L4Reachable;
import act.shared.Chemical;
import com.act.reachables.Cascade;
import com.act.reachables.ReactionPath;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.twentyn.proteintodna.DNADesign;
import org.twentyn.proteintodna.DNAOrgECNum;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FreemarkerRenderer {
  public static final Logger LOGGER = LogManager.getFormatterLogger(FreemarkerRenderer.class);

  private static final String DEFAULT_REACHABLE_TEMPLATE_FILE = "Mediawiki.ftl";
  private static final String DEFAULT_PATHWAY_TEMPLATE_FILE = "MediaWikiPathways.ftl";

  private static final int MAX_SEQUENCE_LENGTH = 50;

  private static final int SEQUENCE_SAMPLE_START = 3000;
  private static final int SEQUENCE_SAMPLE_SIZE = 80;

  private String reachableTemplateName;
  private String pathwayTemplateName;
  private Loader loader;
  private L4Loader l4loader;

  // Note: there should be one of these per process.  TODO: make this a singleton.
  private Configuration cfg;
  private Template reachableTemplate;
  private Template pathwayTemplate;

  private JacksonDBCollection<DNADesign, String> sequenceCollection;

  public static void main(String[] args) throws Exception {
    Loader loader =
        new Loader("localhost", 27017, "wiki_reachables", "reachablesv7", "sequencesv7", "/tmp");
    L4Loader l4loader = new L4Loader();

    // FreemarkerRenderer renderer = FreemarkerRendererFactory.build(loader);
    //renderer.writePageToDir(new File("/Volumes/shared-data/Thomas/WikiPagesForUpload"));

    FreemarkerRenderer renderer = FreemarkerRendererFactory.build(loader, l4loader);
    renderer.writeL4PageToDir(new File("/Volumes/shared-data/Thomas/WikiL4PagesForTestUpload"));
  }

  private FreemarkerRenderer(Loader loader) {
    this.reachableTemplateName = DEFAULT_REACHABLE_TEMPLATE_FILE;
    this.pathwayTemplateName = DEFAULT_PATHWAY_TEMPLATE_FILE;
    this.loader = loader;
    this.l4loader = new L4Loader();
  }

  private FreemarkerRenderer(Loader loader, L4Loader l4loader) {
    this.reachableTemplateName = DEFAULT_REACHABLE_TEMPLATE_FILE;
    this.pathwayTemplateName = DEFAULT_PATHWAY_TEMPLATE_FILE;
    this.loader = loader;
    this.l4loader = l4loader;
  }

  private void init() throws IOException {
    cfg = new Configuration(Configuration.VERSION_2_3_23);

    cfg.setClassLoaderForTemplateLoading(
        this.getClass().getClassLoader(), "/act/installer/reachablesexplorer/templates");
    cfg.setDefaultEncoding("UTF-8");

    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(true);

    reachableTemplate = cfg.getTemplate(reachableTemplateName);
    pathwayTemplate = cfg.getTemplate(pathwayTemplateName);

    // TODO: move this elsewhere.
    MongoClient client = new MongoClient(new ServerAddress("localhost", 27017));
    DB db = client.getDB("wiki_reachables");

    sequenceCollection = JacksonDBCollection.wrap(db.getCollection("dna_designs_2"), DNADesign.class, String.class);
  }


  public void writeL4PageToDir(File l4ReachableDestination) throws IOException, TemplateException {

    DBCursor<L4Reachable> reachableDBCursor = l4loader.getJacksonL4ReachablesCollection().find();

    int i = 0;

    while(reachableDBCursor.hasNext()) {
      L4Reachable reachable = reachableDBCursor.next();
      String inchiKey = reachable.getInchiKey();
      if (inchiKey != null) {
        LOGGER.info(inchiKey);
        File f = new File(l4ReachableDestination, inchiKey);
        Writer w = new PrintWriter(f);
        reachableTemplate.process(buildL4ReachableModel(reachable), w);
        w.close();
        assert f.exists();
        i++;

        if (i % 100 == 0) {
          LOGGER.info(i);
        }
      } else {
        LOGGER.error("page does not have an inchiKey");
      }
    }
  }




  public void writePageToDir(File reachableDestination,
                             File pathDestination,
                             File sequenceDestination) throws IOException, TemplateException{
    //DBCursor<Reachable> reachableDBCursor = loader.getJacksonReachablesCollection().find(new BasicDBObject("names", "vanillin"));

    DBCursor<ReactionPath> reachableDBCursor = Cascade.get_pathway_collection().find();

    Set<Long> seenIds = new HashSet<>();

    int i = 0;
    while(reachableDBCursor.hasNext()) {
      // Hacked cursor munging to only consider targets of pathways.
      ReactionPath thisPath = reachableDBCursor.next();
      if (thisPath.getTarget().equals(878L)) {
        LOGGER.info("Skipping vanillin");
        continue;
      }
      Reachable r = loader.getJacksonReachablesCollection().findOne(new BasicDBObject("_id", thisPath.getTarget()));

      if (seenIds.contains(thisPath.getTarget())) {
        LOGGER.info("Skipping duplicate id %d", thisPath.getTarget());
        continue;
      }
      seenIds.add(thisPath.getTarget());

      String inchiKey = r.getInchiKey();
      if (inchiKey != null) {
        List<Pair<String, String>> pathwayDocsAndNames = generatePathDocuments(r, pathDestination, sequenceDestination);
        LOGGER.info(inchiKey);
        File f = new File(reachableDestination, inchiKey);
        Writer w = new PrintWriter(f);
        reachableTemplate.process(buildReachableModel(r, loader.getJacksonSequenceCollection(), pathwayDocsAndNames), w);
        w.close();
        assert f.exists();
        i++;

        if (i % 100 == 0) {
          LOGGER.info(i);
        }
      } else {
        LOGGER.error("page does not have an inchiKey");
      }
    }
  }


  private Object buildL4ReachableModel(L4Reachable r) {
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
              put("synonymType", entry.getKey().toString());
              put("synonyms", entry.getValue().stream().collect(Collectors.toList()));
            }})
            .collect(Collectors.toList());

        model.put("meshHeadings", meshHeadingModel);
      }
    }

    return model;
  }



  private Object buildReachableModel(Reachable r,
                                     JacksonDBCollection<SequenceData, String> sequenceCollection,
                                     List<Pair<String, String>> pathwayDocsAndNames
                                     ) {
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

    List<Map<String, Object>> precursors = orderedPrecursors.stream().map(precursor -> {
      // Pull out the molecule and sequence fields into a hash that Freemarker can consume.
      List<Map<String, String>> molecules = precursor.getMolecules().stream().
          map(mol -> new HashMap<String, String>() {{
            put("inchiKey", mol.getInchiKey());
            put("name", mol.getName() != null && !mol.getName().isEmpty() ? mol.getName() : mol.getInchi());
          }}).
          collect(Collectors.toList());

      // TODO: the following line is limiting the number of sequences we carry to the front end to only 2 sequences
      // Remove when not relevant anymore
      List<String> rawSequences = precursor.getSequences();
      List<String> limitedRawSeq = (rawSequences.size() > 2) ? rawSequences.subList(0, 2) : rawSequences;

      List<Map<String, String>> sequences = limitedRawSeq.stream().
          map(sequenceCollection::findOneById).
          map(seq -> new HashMap<String, String>() {{
            put("organism", seq.getOrganismName());
            put("sequence", truncateSequence(seq.getSequence()));
          }}).
          collect(Collectors.toList());

      return new HashMap<String, Object>() {{
        put("molecules", molecules);
        put("sequences", sequences);
      }};
    }).collect(Collectors.toList());

    model.put("precursors", precursors);

    List<Object> pathways = new ArrayList<>();
    for (Pair<String, String> pair : pathwayDocsAndNames) {
      pathways.add(new HashMap<String, String>() {{
        put("link", pair.getLeft());
        put("name", pair.getRight());
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
              put("synonymType", entry.getKey().toString());
              put("synonyms", entry.getValue().stream().collect(Collectors.toList()));
            }})
            .collect(Collectors.toList());

        model.put("meshHeadings", meshHeadingModel);
      }
    }

    return model;
  }

  // Limit long sequences to somethign we can show on a wiki page.
  private String truncateSequence(String seq) {
    if (seq.length() <= MAX_SEQUENCE_LENGTH) {
      return seq;
    }

    StringBuilder builder = new StringBuilder(MAX_SEQUENCE_LENGTH + 3);
    builder.append(seq.substring(0, MAX_SEQUENCE_LENGTH / 2));
    builder.append("...");
    builder.append(seq.substring(seq.length() - MAX_SEQUENCE_LENGTH / 2, seq.length() - 1));
    return builder.toString();
  }

  public List<Pair<String, String>> generatePathDocuments(
      Reachable target, File pathDestination, File sequenceDestination) throws IOException, TemplateException {
    DBCursor<ReactionPath> cursor = Cascade.get_pathway_collection().find(new BasicDBObject("target", target.getId()));

    List<Pair<String, String>> pathPagesAndNames = new ArrayList<>();
    while (cursor.hasNext()) {
      ReactionPath path = cursor.next();

      if (target == null) {
        // This should not happen, methinks.
        String msg = String.format("Unable to locate chemical %d in reachables db", path.getTarget());
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }

      String sourceDocName = makeSourceDocName(target);
      if (sourceDocName == null) {
        LOGGER.error("Target %d does not have inchiKey", path.getTarget());
        continue;
      }

      String pathwayDocName = String.format("Pathway_%s_%d", sourceDocName, path.getRank());

      List<Pair<String, String>> designDocsAndSummaries = path.getDnaDesignRef() != null ?
          renderSequences(sequenceDestination, pathwayDocName, path.getDnaDesignRef()) : Collections.emptyList();

      Pair<Object, String> model = buildPathModel(path, target, designDocsAndSummaries);
      if (model != null) {
        pathwayTemplate.process(model.getLeft(), new FileWriter(new File(pathDestination, pathwayDocName)));
      }

      pathPagesAndNames.add(Pair.of(pathwayDocName, model.getRight()));
    }
    return pathPagesAndNames;
  }

  private List<Pair<String, String>> renderSequences(File sequenceDestination, String docPrefix, String seqRef) throws IOException {
    DNADesign designDoc = sequenceCollection.findOneById(seqRef);
    if (designDoc == null) {
      LOGGER.error("Could not find dna seq for id %s", seqRef);
      return Collections.emptyList();
    }

    List<Pair<String, String>> sequenceFilesAndSummaries = new ArrayList<>();

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
      int designSize = design.length();
      String shortVersion;
      if (designSize > SEQUENCE_SAMPLE_START + SEQUENCE_SAMPLE_SIZE) {
        shortVersion = design.substring(SEQUENCE_SAMPLE_START, SEQUENCE_SAMPLE_START + SEQUENCE_SAMPLE_SIZE);
      } else {
        shortVersion = design.substring(
            designSize / 2 - SEQUENCE_SAMPLE_SIZE / 2,
            designSize / 2 + SEQUENCE_SAMPLE_SIZE / 2);
      }

      String constructFilename = String.format("Design_%s_seq%d.txt", docPrefix, i + 1);

      try (FileWriter writer = new FileWriter(new File(sequenceDestination, constructFilename))) {
        writer.write(design);
        writer.write("\n");
      }

      sequenceFilesAndSummaries.add(Pair.of(constructFilename, shortVersion));
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

  private Pair<Object, String> buildPathModel(ReactionPath p, Reachable target, List<Pair<String, String>> designs) throws IOException {

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
        Reachable r = loader.getJacksonReachablesCollection().findOne(new BasicDBObject("_id", i.getId()));
        if (r == null) {
          LOGGER.error("Unable to locate pathway chemical %d in reachables db", i.getId());
          nodeModel.put("name", "(unknown)");
        } else {
          nodeModel.put("link", r.getInchiKey());
          // TODO: we really need a way of picking a good name for each molecule.
          nodeModel.put("name", r.getPageName());
          chemicalNames.add(r.getPageName());
          if (r.getStructureFilename() != null) {
            nodeModel.put("structureRendering", r.getStructureFilename());
          } else {
            LOGGER.warn("No structure filename for %s", r.getPageName());
          }
        }
      }
    }

    String pageTitle = StringUtils.join(chemicalNames, " <- ");
    model.put("pageTitle", pageTitle);

    List<Map<String, String>> dna = new ArrayList<>();
    int i = 1;
    for (Pair<String, String> design : designs) {
      final int num = i; // Sigh, must be final to use in this initialization block.
      dna.add(new HashMap<String, String>() {{
        put("file", design.getLeft());
        put("sample", design.getRight());
        put("num", Integer.valueOf(num).toString());
      }});
      i++;
    }
    if (dna.size() > 0) {
      model.put("dna", dna);
    }

    return Pair.of(model, pageTitle);
  }

  public static class FreemarkerRendererFactory {
    public static FreemarkerRenderer build(Loader loader) throws IOException {
      FreemarkerRenderer renderer = new FreemarkerRenderer(loader);
      renderer.init();
      return renderer;
    }

    public static FreemarkerRenderer build(Loader loader, L4Loader l4Loader) throws IOException {
      FreemarkerRenderer renderer = new FreemarkerRenderer(loader, l4Loader);
      renderer.init();
      return renderer;
    }
  }
}
