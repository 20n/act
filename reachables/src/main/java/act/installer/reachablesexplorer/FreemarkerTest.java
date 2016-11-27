package act.installer.reachablesexplorer;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

import java.io.File;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FreemarkerTest {

  public static void main(String[] args) throws Exception {

    // Note: there should be one of these per process.
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);

    cfg.setDirectoryForTemplateLoading(new File("src/main/resources/act/installer/reachablesexplorer/templates"));
    cfg.setDefaultEncoding("UTF-8");

    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(true);

    Template template = cfg.getTemplate("Mediawiki.ftl");

    Loader loader = new Loader();

    DBCursor<Reachable> reachableDBCursor = loader.getJacksonReachablesCollection().find();

    int i = 0;
    while(reachableDBCursor.hasNext()) {
      Reachable r = reachableDBCursor.next();
      template.process(buildReachableModel(r, loader.getJacksonSequenceCollection()),
          new OutputStreamWriter(System.out));
      System.out.println();
      i++;
      if (i > 10) {
        break;
      }
    }
  }

  static Object buildReachableModel(Reachable r, JacksonDBCollection<SequenceData, String> sequenceCollection) {
    // This is just how Freemarker works.  Oh well.
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

      List<Map<String, String>> sequences = precursor.getSequences().stream().
          map(sequenceCollection::findOneById).
          map(seq -> new HashMap<String, String>() {{
            put("organism", seq.getOrganismName());
            put("sequence", seq.getSequence());
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
}
