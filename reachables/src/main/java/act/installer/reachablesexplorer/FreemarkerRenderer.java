package act.installer.reachablesexplorer;

import com.act.reachables.Cascade;
import com.act.reachables.ReactionPath;
import com.mongodb.BasicDBObject;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

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

  private String reachableTemplateName;
  private String pathwayTemplateName;
  private Loader loader;

  // Note: there should be one of these per process.  TODO: make this a singleton.
  private Configuration cfg;
  private Template reachableTemplate;
  private Template pathwayTemplate;

  public static void main(String[] args) throws Exception {
    Loader loader =
        new Loader("localhost", 27017, "wiki_reachables", "reachablesv7", "sequencesv7", "/tmp");

    FreemarkerRenderer renderer = FreemarkerRendererFactory.build(loader);
    //renderer.writePageToDir(new File("/Volumes/shared-data/Thomas/WikiPagesForUpload"));
    renderer.generateSomePaths();
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
  }

  public void writePageToDir(File directory) throws IOException, TemplateException{
    DBCursor<Reachable> reachableDBCursor = loader.getJacksonReachablesCollection().find();

    int i = 0;
    while(reachableDBCursor.hasNext()) {
      Reachable r = reachableDBCursor.next();
      String inchiKey = r.getInchiKey();
      if (inchiKey != null) {
        LOGGER.info(inchiKey);
        File f = new File(directory, inchiKey);
        Writer w = new PrintWriter(f);
        reachableTemplate.process(buildReachableModel(r, loader.getJacksonSequenceCollection()), w);
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

  private Object buildReachableModel(Reachable r, JacksonDBCollection<SequenceData, String> sequenceCollection) {
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

  public void generateSomePaths() throws IOException, TemplateException {
    DBCursor<ReactionPath> cursor = Cascade.get_pathway_collection().find();

    while (cursor.hasNext()) {
      ReactionPath path = cursor.next();
      Pair<String, Object> pair = buildPathModel(path);
      if (pair != null) {
        pathwayTemplate.process(pair.getRight(),
            new FileWriter(new File("/Users/mdaly/work/act/reachables/template_test/", pair.getLeft())));
      }
    }
  }

  private Pair<String, Object> buildPathModel(ReactionPath p) throws IOException {
    Map<String, Object> model = new HashMap<>();

    Reachable target = loader.getJacksonReachablesCollection().findOne(new BasicDBObject("_id", p.getTarget()));
    if (target == null) {
      // This should not happen, methinks.
      String msg = String.format("Unable to located chemical %d in reachables db", p.getTarget());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }

    String inchiKey = target.getInchiKey();
    if (inchiKey == null) {
      LOGGER.error("Target %d does not have inchiKey", p.getTarget());
      return null;
    }

    String pageFile = String.format("Pathway_%s_%d", inchiKey, p.getRank());

    model.put("pageTitle", String.format("%s, path %d", target.getNames().get(0), p.getRank()));
    LinkedList<Object> pathwayItems = new LinkedList<>();
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
          if (r.getStructureFilename() != null) {
            nodeModel.put("structureRendering", r.getStructureFilename());
          } else {
            LOGGER.warn("No structure filename for %s", r.getPageName());
          }
        }
      }
    }
    return Pair.of(pageFile, model);
  }

  public static class FreemarkerRendererFactory {
    public static FreemarkerRenderer build(Loader loader) throws IOException {
      FreemarkerRenderer renderer = new FreemarkerRenderer(loader);
      renderer.init();
      return renderer;
    }
  }
}
