package act.installer.reachablesexplorer;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FreemarkerRenderer {
  public static final Logger LOGGER = LogManager.getFormatterLogger(FreemarkerRenderer.class);

  private static final String DEFAULT_TEMPLATE_FILE = "Mediawiki.ftl";

  private static final int MAX_SEQUENCE_LENGTH = 50;

  private String templateName;
  private Loader loader;

  // Note: there should be one of these per process.  TODO: make this a singleton.
  private Configuration cfg;
  private Template template;

  public static void main(String[] args) throws Exception {
    Loader loader =
        new Loader("localhost", 27017, "wiki_reachables", "reachablesv7", "sequencesv7", "/tmp");

    FreemarkerRenderer renderer = FreemarkerRendererFactory.build(loader);
    renderer.writePageToDir(new File("/Volumes/shared-data/Thomas/WikiPagesForUpload"));
  }

  private FreemarkerRenderer(Loader loader) {
    this.templateName = DEFAULT_TEMPLATE_FILE;
    this.loader = loader;
  }

  private void init() throws IOException {
    cfg = new Configuration(Configuration.VERSION_2_3_23);

    cfg.setClassLoaderForTemplateLoading(
        this.getClass().getClassLoader(), "/act/installer/reachablesexplorer/templates");
    cfg.setDefaultEncoding("UTF-8");

    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(true);

    template = cfg.getTemplate(templateName);
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
        template.process(buildReachableModel(r, loader.getJacksonSequenceCollection()), w);
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

    List<Map<String, Object>> precursors = r.getPrecursorData().getPrecursors().stream().map(precursor -> {
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

  public static class FreemarkerRendererFactory {
    public static FreemarkerRenderer build(Loader loader) throws IOException {
      FreemarkerRenderer renderer = new FreemarkerRenderer(loader);
      renderer.init();
      return renderer;
    }
  }
}
