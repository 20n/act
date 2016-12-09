package com.twentyn.search.substructure;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Service {
  static final Service INSTANCE = new Service();

  private static final Logger LOGGER = LogManager.getFormatterLogger(Service.class);

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();

  // TODO: are these options sufficient?  Are there others we might want to use?
  /* Chemaxon exposes a very non-uniform means of configuring substructure search.  Hence the mess of lambdas below.
   * Consumer solves the Function<T, void> problem. */
  private static final Map<String, Consumer<MolSearchOptions>> SEARCH_OPTION_ENABLERS =
      Collections.unmodifiableMap(new HashMap<String, Consumer<MolSearchOptions>>() {{
        put("CHARGE_MATCHING_EXACT", (so -> so.setChargeMatching(SearchConstants.CHARGE_MATCHING_EXACT)));
        put("CHARGE_MATCHING_IGNORE", (so -> so.setChargeMatching(SearchConstants.CHARGE_MATCHING_IGNORE)));
        put("IMPLICIT_H_MATCHING_ENABLED", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_ENABLED)));
        put("IMPLICIT_H_MATCHING_DISABLED", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_DISABLED)));
        put("IMPLICIT_H_MATCHING_IGNORE", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_IGNORE)));
        put("STEREO_EXACT", (so -> so.setStereoSearchType(SearchConstants.STEREO_EXACT)));
        put("STEREO_IGNORE", (so -> so.setStereoSearchType(SearchConstants.STEREO_IGNORE)));
        put("STEREO_MODEL_COMPREHENSIVE", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_COMPREHENSIVE)));
        put("STEREO_MODEL_GLOBAL", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_GLOBAL)));
        put("STEREO_MODEL_LOCAL", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL)));
        put("TAUTOMER_SEARCH_ON", (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON)));
        put("TAUTOMER_SEARCH_OFF", (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_OFF)));
        put("TAUTOMER_SEARCH_ON_IGNORE_TAUTOMERSTEREO",
            (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON_IGNORE_TAUTOMERSTEREO)));
        put("VAGUE_BOND_OFF", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_OFF)));
        put("VAGUE_BOND_LEVEL_HALF", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL_HALF)));
        put("VAGUE_BOND_LEVEL1", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL1)));
        put("VAGUE_BOND_LEVEL2", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL2)));
        put("VAGUE_BOND_LEVEL3", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL3)));
        put("VAGUE_BOND_LEVEL4", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4)));
      }});
  private static final List<String> VALID_SEARCH_OPTION_SORTED;

  static {
    List<String> keys = new ArrayList<>(SEARCH_OPTION_ENABLERS.keySet());
    Collections.sort(keys);
    VALID_SEARCH_OPTION_SORTED = Collections.unmodifiableList(keys);
  }

  private static final MolSearchOptions DEFAULT_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    DEFAULT_SEARCH_OPTIONS.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_DEFAULT);
    DEFAULT_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_DEFAULT);
    DEFAULT_SEARCH_OPTIONS.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_DEFAULT);
    DEFAULT_SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE); // TODO: is this preferable?
  }

  public static final String OPTION_INPUT_FILE = "f";
  public static final String OPTION_LICENSE_FILE = "l";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILE)
        .argName("tsv file")
        .desc("The name of the input TSV file to read")
        .hasArg().required()
        .longOpt("tsv")
    );
    add(Option.builder(OPTION_LICENSE_FILE)
        .argName("license file")
        .desc("A chemaxon license file to use")
        .hasArg().required()
        .longOpt("license")
    );
    // Everybody needs a little help from their friends.
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  private static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class runs a web server that does substructure matching against a TSV file using a single SMILES query.  ",
      "All matching chemicals are outputted; non-matches are ignored."
  }, "");
  private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final List<TargetMolecule> TARGETS = new ArrayList<>();

  private static Configuration FREEMARKER_CFG;

  private Template successTemplate;
  private Template failureTemplate;

  @RestController
  @EnableAutoConfiguration
  public static class Controller {

    @RequestMapping("/hello")
    @ResponseBody
    public String handler() {
      return "Hello world!\n";
    }

    @RequestMapping("/search")
    @ResponseBody
    public ResponseEntity<String> search(
        @RequestParam(name = "q", required = true) String queryString,
        @RequestParam(name = "options", required = false) List<String> searchOptions) {
      try {
        MolSearch search = INSTANCE.constructSearch(queryString, searchOptions);

        List<TargetMolecule> matches = new ArrayList<>();
        for (TargetMolecule target : TARGETS) {
          if (INSTANCE.matchSubstructure(target.getMolecule(), search)) {
            matches.add(target);
          }
        }

        if (matches.size() == 0) {
          // This could be a 404, but it's not technically "not found": we ran the search, but nothing matched.
          return new ResponseEntity<String>(INSTANCE.renderNoResultsPage(), HttpStatus.OK);
        } else {
          return new ResponseEntity<String>(INSTANCE.renderResultsPage(matches), HttpStatus.OK);
        }
      } catch (MolFormatException e) {
        LOGGER.warn("Caught MolFormatException: %s", e.getMessage());
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      } catch (Exception e) {
        LOGGER.error("Caught unexpected exception: %s", e.getMessage());
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  private static class TargetMolecule {
    Molecule molecule;
    String inchi;
    String displayName;
    String inchiKey;
    String imageName;

    public TargetMolecule(Molecule molecule, String inchi, String displayName, String inchiKey, String imageName) {
      this.molecule = molecule;
      this.inchi = inchi;
      this.displayName = displayName;
      this.inchiKey = inchiKey;
      this.imageName = imageName;
    }

    public Molecule getMolecule() {
      return molecule;
    }

    public String getInchi() {
      return inchi;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getInchiKey() {
      return inchiKey;
    }

    public String getImageName() {
      return imageName;
    }

    public static TargetMolecule fromCSVRecord(CSVRecord record) throws MolFormatException {
      String inchi = record.get("inchi");
      String displayName = record.get("display_name");
      String inchiKey = record.get("inchi_key");
      String imageName = record.get("image_name");

      Molecule mol = MolImporter.importMol(inchi);

      return new TargetMolecule(mol, inchi, displayName, inchiKey, imageName);
    }
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(Service.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(Service.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    if (cl.hasOption(OPTION_LICENSE_FILE)) {
      LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));
    }

    FREEMARKER_CFG = new Configuration(Configuration.VERSION_2_3_25);

    FREEMARKER_CFG.setClassLoaderForTemplateLoading(
        Service.class.getClassLoader(), "/com/twentyn/search/substructure/templates");
    FREEMARKER_CFG.setDefaultEncoding("UTF-8");

    FREEMARKER_CFG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    FREEMARKER_CFG.setLogTemplateExceptions(true);

    // TODO: use dependency injection or something instead of this.
    INSTANCE.successTemplate = FREEMARKER_CFG.getTemplate("SearchResults.ftl");
    INSTANCE.failureTemplate = FREEMARKER_CFG.getTemplate("NoResultsFound.ftl");


    try (CSVParser parser = new CSVParser(new FileReader(new File(cl.getOptionValue(OPTION_INPUT_FILE))), TSV_FORMAT)) {
      Iterator<CSVRecord> iter = parser.iterator();
      while (iter.hasNext()) {
        CSVRecord record = iter.next();
        TARGETS.add(TargetMolecule.fromCSVRecord(record));
      }
    }
    LOGGER.info("Read %d targets from input TSV", TARGETS.size());

    SpringApplication.run(Controller.class);
  }

  private MolSearch constructSearch(String smiles, List<String> extraOpts) throws MolFormatException {
    // Process any custom options.
    MolSearchOptions searchOptions;
    if (extraOpts == null || extraOpts.size() == 0) {
      searchOptions = DEFAULT_SEARCH_OPTIONS;
    } else {
      searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
      // Apply all the specified extra search options using the key -> function mapping above.
      for (String opt : extraOpts) {
        if (!SEARCH_OPTION_ENABLERS.containsKey(opt)) {
          throw new IllegalArgumentException(String.format("Unrecognized search option: %s", opt));
        }
        SEARCH_OPTION_ENABLERS.get(opt).accept(searchOptions);
      }

    }

    // Import the query and set it + the specified or default search options.
    MolSearch ms = new MolSearch();
    ms.setSearchOptions(searchOptions);
    Molecule query = new MolHandler(smiles, true).getMolecule();
    ms.setQuery(query);
    return ms;
  }

  private boolean matchSubstructure(Molecule target, MolSearch search) throws SearchException {
    search.setTarget(target);
    /* hits are arrays of atom ids in the target that matched the query.  If multiple sites in the target matched,
     * then there should be multiple arrays of atom ids (but we don't care since we're just looking for any match). */
    int[][] hits = search.findAll();
    if (hits != null) {
      for (int i = 0; i < hits.length; i++) {
        if (hits[i].length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  private String renderResultsPage(List<TargetMolecule> results) throws TemplateException, IOException {
    return renderTemplateAsString(successTemplate, constructResultsModel(results));
  }

  private Object constructResultsModel(List<TargetMolecule> results) {
    List<Map<String, String>> model = new ArrayList<>();
    for (TargetMolecule target : results) {
      model.add(new HashMap<String, String>() {{
        put("pageName", target.getDisplayName());
        put("inchiKey", target.getInchiKey());
        put("imageName", target.imageName);
      }});
    }
    return new HashMap<String, Object>() {{
      put("results", model);
    }};
  }

  private String renderNoResultsPage() throws TemplateException, IOException {
    return renderTemplateAsString(failureTemplate, Collections.emptyMap());
  }

  private String renderTemplateAsString(Template template, Object model)  throws TemplateException, IOException {
    Writer outputWriter = new StringWriter();
    template.process(model, outputWriter);
    outputWriter.flush();
    return outputWriter.toString();
  }
}
