package com.twentyn.search.substructure;

import chemaxon.formats.MolFormatException;
import chemaxon.license.LicenseManager;
import chemaxon.sss.search.MolSearch;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @RestController
  @EnableAutoConfiguration
  public static class Controller {
    SubstructureSearch substructureSearch = new SubstructureSearch();

    @RequestMapping("/search")
    @ResponseBody
    public ResponseEntity<String> search(
        @RequestParam(name = "q", required = true) String queryString,
        @RequestParam(name = "options", required = false) List<String> searchOptions) {
      // TODO: add additional validations on q.
      LOGGER.info("Got request: %s", queryString);
      try {
        MolSearch search = substructureSearch.constructSearch(queryString, searchOptions);

        List<TargetMolecule> matches = new ArrayList<>();
        for (TargetMolecule target : TARGETS) {
          if (substructureSearch.matchSubstructure(target.getMolecule(), search)) {
            matches.add(target);
          }
        }

        List<SearchResult> results = new ArrayList<SearchResult>() {{
          for (TargetMolecule mol : matches) {
            add(new SearchResult(
                // TODO: parameterize these URLs based on some CLI or configuration parameter.
                String.format("http://localhost:8989/assets/img/%s", mol.getImageName()),
                mol.getDisplayName(),
                String.format("http://localhost:8989/mediawiki/%s", mol.getInchiKey())
            ));
          }
        }};

        return new ResponseEntity<>(
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results),
            new LinkedMultiValueMap<String, String>() {{
              put("Content-type", Collections.singletonList("application/json"));
              /* IMPORTANT TODO: remove access-control-allow-origin before deployment!  This should not be necessary
               * when all of the service components are served by a single web server.  This header represents an
               * unnecessary security risk for production deployments, but is useful for testing. */
              put("Access-control-allow-origin", Collections.singletonList("*"));
            }},
            HttpStatus.OK);
      } catch (MolFormatException e) {
        LOGGER.warn("Caught MolFormatException: %s", e.getMessage());
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      } catch (Exception e) {
        LOGGER.error("Caught unexpected exception: %s", e.getMessage());
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
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

    LOGGER.info("Loading targets");
    loadTargets(new File(cl.getOptionValue(OPTION_INPUT_FILE)));
    LOGGER.info("Read %d targets from input TSV", TARGETS.size());

    LOGGER.info("Starting service");
    SpringApplication.run(Controller.class);
  }

  private static class SearchResult {
    @JsonProperty("image_name")
    String imageLink;

    @JsonProperty("page_name")
    String pageName;

    @JsonProperty("link")
    String link;

    private SearchResult() {

    }

    public SearchResult(String imageLink, String pageName, String link) {
      this.imageLink = imageLink;
      this.pageName = pageName;
      this.link = link;
    }

    public String getImageLink() {
      return imageLink;
    }

    public void setImageLink(String imageLink) {
      this.imageLink = imageLink;
    }

    public String getPageName() {
      return pageName;
    }

    public void setPageName(String pageName) {
      this.pageName = pageName;
    }

    public String getLink() {
      return link;
    }

    public void setLink(String link) {
      this.link = link;
    }
  }
}
