package com.twentyn.search.substructure;

import chemaxon.formats.MolFormatException;
import chemaxon.license.LicenseManager;
import chemaxon.sss.search.MolSearch;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyn.TargetMolecule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Service implements Daemon {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Service.class);

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();

  public static final String OPTION_CONFIG_FILE = "c";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CONFIG_FILE)
        .argName("config file")
        .desc("Path to a file containing JSON configuration parameters, used instead of CLI args")
        .hasArg().required()
        .longOpt("config")
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

  private static final Long MAX_RESULTS = 100L;

  private static final List<TargetMolecule> TARGETS = new ArrayList<>();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private Server jettyServer;

  public void init(String[] args) throws Exception {
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

    File configFile = new File(cl.getOptionValue(OPTION_CONFIG_FILE));
    if (!configFile.exists() || !configFile.isFile()) {
      System.err.format("Config file at %s could not be read, but is required for startup",
          configFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(Service.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    ServiceConfig config = null;
    try {
      config = OBJECT_MAPPER.readValue(configFile, new TypeReference<ServiceConfig>() {});
    } catch (IOException e) {
      System.err.format("Unable to read config file at %s: %s",
          configFile.getAbsolutePath(), e.getMessage());
      HELP_FORMATTER.printHelp(Service.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    LicenseManager.setLicenseFile(config.getLicenseFile());

    LOGGER.info("Loading targets");
    TARGETS.addAll(TargetMolecule.loadTargets(new File(config.getReachablesFile()))); // Ensure TARGETS != null.
    LOGGER.info("Read %d targets from input TSV", TARGETS.size());

    LOGGER.info("Constructing service");
    jettyServer = new Server(config.getPort()); // TODO: take this as a CLI arg.
    /* Note: a ContextHandler here can help organize controllers by path, so we could use one to only dispatch /search
     * requests to the controller.  Unfortunately, because context handlers assume responsibility for an entire sub-
     * path, they always require a trailing slash on the URL, which we don't want.  Instead, we'll always send requests
     * to our controller but only accept them if their target is /search.
     */
    jettyServer.setHandler(new Controller(config));

    // Use Apache-style access logging, because it's The Right Thing To Do.
    NCSARequestLog logger = new NCSARequestLog();
    logger.setAppend(true);
    jettyServer.setRequestLog(logger);
  }

  @Override
  public void init(DaemonContext context) throws DaemonInitException, Exception {
    String args[] = context.getArguments();
    LOGGER.info("Daemon initializing with arguments: %s", StringUtils.join(args, " "));
    init(args);
  }

  @Override
  public void start() throws Exception {
    LOGGER.info("Starting server");
    jettyServer.start();
    LOGGER.info("Server started, waiting for termination");
  }

  @Override
  public void stop() throws Exception {
    jettyServer.stop();
  }

  @Override
  public void destroy() {
    jettyServer.destroy();
  }

  public static void main(String[] args) throws Exception {
    Service service = new Service();
    service.init(args);

    service.start();
    service.jettyServer.join();
  }

  public static class Controller extends AbstractHandler {
    private static final String EXPECTED_TARGET = "/search";
    private static final String PARAM_QUERY = "q";
    private static final String PARAM_SEARCH_OPTIONS = "options";

    SubstructureSearch substructureSearch = new SubstructureSearch();

    String wikiUrlBase;
    String imagesUrlBase;

    public Controller(ServiceConfig config) {
      this.wikiUrlBase = config.getWikiUrlPrefix();
      this.imagesUrlBase = config.getImageUrlPrefix();

      // Add trailing slashes so we can assume they exist later.
      if (!this.wikiUrlBase.endsWith("/")) {
        this.wikiUrlBase = this.wikiUrlBase + "/";
      }

      if (!this.imagesUrlBase.endsWith("/")) {
        this.imagesUrlBase = this.imagesUrlBase + "/";
      }
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      // Only handle /search GET queries.
      if (!EXPECTED_TARGET.equals(target)) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if(!HttpMethod.GET.asString().equalsIgnoreCase(request.getMethod())) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }

      Map<String, String[]> parameters = request.getParameterMap();

      // Search query is required.
      if (!parameters.containsKey(PARAM_QUERY) && parameters.get(PARAM_QUERY).length == 0) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      String queryString = parameters.get(PARAM_QUERY)[0];
      if (queryString == null || queryString.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      // TODO: should we do additional validation of the query string here?

      // Search options are optional.
      List<String> searchOptions = Collections.emptyList();
      if (parameters.containsKey(PARAM_SEARCH_OPTIONS)) {
        searchOptions = Arrays.asList(parameters.get(PARAM_SEARCH_OPTIONS));
      }

      try {
        MolSearch search = substructureSearch.constructSearch(queryString, searchOptions);

        List<TargetMolecule> matches = new ArrayList<>();
        for (TargetMolecule targetMol : TARGETS) {
          if (substructureSearch.matchSubstructure(targetMol.getMolecule(), search)) {
            matches.add(targetMol);
          }
        }

        List<SearchResult> results = matches.stream().
            limit(MAX_RESULTS).
            map(mol -> new SearchResult(
                // TODO: parameterize these URLs based on some CLI or configuration parameter.
                this.imagesUrlBase + mol.getImageName(),
                mol.getDisplayName(),
                this.wikiUrlBase +  mol.getInchiKey())
            ).collect(Collectors.toList());

        // TODO: are there constants for these somewhere?
        response.addHeader("Content-type", "application/json");
        /* IMPORTANT TODO: remove access-control-allow-origin before deployment!  This should not be necessary
         * when all of the service components are served by a single web server.  This header represents an
         * unnecessary security risk for production deployments, but is useful for testing. */
        response.addHeader("Access-control-allow-origin", "*");

        response.getWriter().write(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        response.getWriter().flush();
        response.setStatus(HttpServletResponse.SC_OK);
      } catch (MolFormatException e) {
        LOGGER.warn("Caught MolFormatException: %s", e.getMessage());
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      } catch (Exception e) {
        LOGGER.error("Caught unexpected exception: %s", e.getMessage());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
      baseRequest.setHandled(true);
    }
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
