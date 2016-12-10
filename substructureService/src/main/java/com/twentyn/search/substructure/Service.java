package com.twentyn.search.substructure;

import chemaxon.formats.MolFormatException;
import chemaxon.license.LicenseManager;
import chemaxon.sss.search.MolSearch;
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

public class Service {
  static final Service INSTANCE = new Service();

  private static final Logger LOGGER = LogManager.getFormatterLogger(Service.class);

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();

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

    SubstructureSearch substructureSearch = new SubstructureSearch();

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
        MolSearch search = substructureSearch.constructSearch(queryString, searchOptions);

        List<TargetMolecule> matches = new ArrayList<>();
        for (TargetMolecule target : TARGETS) {
          if (substructureSearch.matchSubstructure(target.getMolecule(), search)) {
            matches.add(target);
          }
        }

        if (matches.size() == 0) {
          // This could be a 404, but it's not technically "not found": we ran the search, but nothing matched.
          return new ResponseEntity<>(INSTANCE.renderNoResultsPage(), HttpStatus.OK);
        } else {
          return new ResponseEntity<>(INSTANCE.renderResultsPage(matches), HttpStatus.OK);
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

  private static void setupFreemarker() throws TemplateException, IOException {
    FREEMARKER_CFG = new Configuration(Configuration.VERSION_2_3_25);

    FREEMARKER_CFG.setClassLoaderForTemplateLoading(
        Service.class.getClassLoader(), "/com/twentyn/search/substructure/templates");
    FREEMARKER_CFG.setDefaultEncoding("UTF-8");

    FREEMARKER_CFG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    FREEMARKER_CFG.setLogTemplateExceptions(true);

    // TODO: use dependency injection or something instead of this.
    INSTANCE.successTemplate = FREEMARKER_CFG.getTemplate("SearchResults.ftl");
    INSTANCE.failureTemplate = FREEMARKER_CFG.getTemplate("NoResultsFound.ftl");
  }

  private static void loadTargets(File inputFile) throws IOException {
    try (CSVParser parser = new CSVParser(new FileReader(inputFile), TSV_FORMAT)) {
      for (CSVRecord record : parser) {
        TARGETS.add(TargetMolecule.fromCSVRecord(record));
      }
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

    LOGGER.info("Loading freemarker templates");
    setupFreemarker();

    LOGGER.info("Loading targets");
    loadTargets(new File(cl.getOptionValue(OPTION_INPUT_FILE)));
    LOGGER.info("Read %d targets from input TSV", TARGETS.size());

    LOGGER.info("Starting service");
    SpringApplication.run(Controller.class);
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
