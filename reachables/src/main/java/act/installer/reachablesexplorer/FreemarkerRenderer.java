package act.installer.reachablesexplorer;

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

  // Note: there should be one of these per process.  TODO: make this a singleton.
  private Configuration cfg;
  private Template reachableTemplate;
  private Template pathwayTemplate;

  private JacksonDBCollection<DNADesign, String> sequenceCollection;

  public static void main(String[] args) throws Exception {
    Loader loader =
        new Loader("localhost", 27017, "wiki_reachables", "reachablesv7", "sequencesv7", "/tmp");

    FreemarkerRenderer renderer = FreemarkerRendererFactory.build(loader);
    //renderer.writePageToDir(new File("/Volumes/shared-data/Thomas/WikiPagesForUpload"));
    renderer.writePageToDir(
        new File("/Users/mdaly/work/act/reachables/vanillin_pages/template_test"),
        new File("/Users/mdaly/work/act/reachables/vanillin_pages/template_test"),
        new File("/Users/mdaly/work/act/reachables/vanillin_pages/sequence_test"));
  }

  private FreemarkerRenderer(Loader loader) {
    this.reachableTemplateName = DEFAULT_REACHABLE_TEMPLATE_FILE;
    this.pathwayTemplateName = DEFAULT_PATHWAY_TEMPLATE_FILE;
    this.loader = loader;

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

  public void writePageToDir(File reachableDestination,
                             File pathDestination,
                             File sequenceDestination) throws IOException, TemplateException{
    DBCursor<Reachable> reachableDBCursor = loader.getJacksonReachablesCollection().find(new BasicDBObject("names", "vanillin"));

    int i = 0;
    while(reachableDBCursor.hasNext()) {
      Reachable r = reachableDBCursor.next();
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
        String msg = String.format("Unable to located chemical %d in reachables db", path.getTarget());
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

    List<String> designs = new ArrayList<>(designDoc.getDnaDesigns());
    Collections.sort(designs);

    for (int i = 0; i < designs.size(); i++) {
      String design = designs.get(i);
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
          LOGGER.error("Unable to located chemical %d in reachables db", p.getTarget());
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
  }
}
