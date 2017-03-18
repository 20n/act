/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.twentyn.reachables.order;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.twentyn.TargetMolecule;
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
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class Service implements Daemon {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Service.class);

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
      "This class runs a web server that sends emails to request access to pathways for specific molecules."
  }, "");
  private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final List<TargetMolecule> TARGETS = new ArrayList<>();
  private static final Map<String, TargetMolecule> INCHI_KEY_TO_TARGET = new HashMap<>();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // Based on https://en.wikipedia.org/wiki/International_Chemical_Identifier#InChIKey
  private static final Pattern REGEX_INCHI_KEY = Pattern.compile("^[A-Z]{14}-[A-Z]{10}-[A-Z]$");

  private static final String TEMPLATE_NAME_ORDER_FORM = "OrderForm.ftl";
  private static final String TEMPLATE_NAME_ORDER_INVALID = "OrderInvalid.ftl";
  private static final String TEMPLATE_NAME_ORDER_SUBMITTED = "OrderSubmitted.ftl";

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

    LOGGER.info("Initializing freemarker");
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_25);

    cfg.setClassLoaderForTemplateLoading(
        this.getClass().getClassLoader(), "/com/twentyn/reachables/order/templates");
    cfg.setDefaultEncoding("UTF-8");

    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(true);

    LOGGER.info("Loading targets");
    TARGETS.addAll(TargetMolecule.loadTargets(new File(config.getReachablesFile()))); // Ensure TARGETS != null.
    LOGGER.info("Read %d targets from input TSV", TARGETS.size());
    LOGGER.info("Building InChI Key Map");
    TARGETS.forEach(t -> INCHI_KEY_TO_TARGET.put(t.getInchiKey(), t));

    LOGGER.info("Constructing service");
    jettyServer = new Server(config.getPort());
    /* Note: a ContextHandler here can help organize controllers by path, so we could use one to only dispatch /search
     * requests to the controller.  Unfortunately, because context handlers assume responsibility for an entire sub-
     * path, they always require a trailing slash on the URL, which we don't want.  Instead, we'll always send requests
     * to our controller but only accept them if their target is /search.
     */
    Controller controller = new Controller(config, cfg);
    controller.init();
    jettyServer.setHandler(controller);

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
    private static final String EXPECTED_TARGET = "/order";
    private static final String PARAM_INCHI_KEY = "inchi_key";
    private static final String PARAM_EMAIL = "email";
    private static final String PARAM_ORDER_ID = "order_id";

    // Wait 5 seconds for an SNS request to complete.
    private static final long SNS_REQUEST_TIMEOUT = 5;
    private static final TimeUnit SNS_REQUEST_TIME_UNIT = TimeUnit.SECONDS;

    String wikiUrlBase;
    String imagesUrlBase;
    ServiceConfig serviceConfig;

    // TODO: make this a proper singleton.
    Configuration cfg;
    AmazonSNSAsync snsClient;

    Cache<UUID, String> orderIdCache = Caffeine.newBuilder()
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build();

    public Controller(ServiceConfig config, Configuration cfg) {
      this.serviceConfig = config;
      this.cfg = cfg;

      // Do some path munging once to ensure we can build clean URLs.
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

    public void init() throws IOException {
      AWSCredentials credentials =
          new BasicAWSCredentials(this.serviceConfig.getAccessKeyId(), this.serviceConfig.getSecretAccessKey());
      AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
      ClientConfiguration config = new ClientConfiguration();
      snsClient = AmazonSNSAsyncClientBuilder.standard().
          withCredentials(credentialsProvider).
          withRegion(serviceConfig.getRegion()).build();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      LOGGER.info("Expected target: '%s', actual target: '%s'", EXPECTED_TARGET, target);
      if (!EXPECTED_TARGET.equals(target)) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (HttpMethod.GET.asString().equalsIgnoreCase(request.getMethod())) {
        handleGet(request, response);
      } else if (HttpMethod.POST.asString().equalsIgnoreCase(request.getMethod())) {
        LOGGER.info("Handling post request.");
        handlePost(request, response);
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    }

    void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      LOGGER.info("In handle GET");
      Map<String, String[]> params = request.getParameterMap();

      // Basic invariant checks: request must have exactly one InChI Key parameter.
      if (!params.containsKey(PARAM_INCHI_KEY) ||
          params.get(PARAM_INCHI_KEY).length != 1 ||
          params.get(PARAM_INCHI_KEY)[0] == null || params.get(PARAM_INCHI_KEY)[0].isEmpty()) {
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_MOL, null, response);
        return;
      }

      String inchiKey = params.get(PARAM_INCHI_KEY)[0];
      // Content-aware invariant checks: InChI Key must be valid and exist in our list of targets.
      if (!REGEX_INCHI_KEY.matcher(inchiKey).matches() ||
          !INCHI_KEY_TO_TARGET.containsKey(inchiKey)) {
        LOGGER.info("Invalid inchi key: '%s', in targets: %s", inchiKey, INCHI_KEY_TO_TARGET.containsKey(inchiKey));
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_MOL, null, response);
        return;
      }

      // Save the order id for lookup later to make sure the POST endpoint can't be easily spammed.
      UUID orderId = UUID.randomUUID();
      orderIdCache.put(orderId, inchiKey);

      makeOrderFormResponse(inchiKey, orderId.toString(), null, response);
    }

    void handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      Map<String, String[]> params = request.getParameterMap();
      // Basic invariant checks: request must have exactly one InChI Key parameter.
      if (!params.containsKey(PARAM_INCHI_KEY) ||
          params.get(PARAM_INCHI_KEY).length != 1 ||
          params.get(PARAM_INCHI_KEY)[0] == null || params.get(PARAM_INCHI_KEY)[0].isEmpty()) {
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_MOL, null, response);
        return;
      }

      String inchiKey = params.get(PARAM_INCHI_KEY)[0];
      // Content-aware invariant checks: InChI Key must be valid and exist in our list of targets.
      if (!REGEX_INCHI_KEY.matcher(inchiKey).matches() ||
          !INCHI_KEY_TO_TARGET.containsKey(inchiKey)) {
        LOGGER.info("Invalid inchi key: '%s', %s", inchiKey, INCHI_KEY_TO_TARGET.containsKey(inchiKey));
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_MOL, null, response);
        return;
      }

      // Basic invariant checks: request must have exactly one order ID parameter.
      UUID orderId;
      if (!params.containsKey(PARAM_ORDER_ID) ||
          params.get(PARAM_ORDER_ID).length != 1 ||
          params.get(PARAM_ORDER_ID)[0] == null || params.get(PARAM_ORDER_ID)[0].isEmpty()) {
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_ID, inchiKey, response);
        return;
      }

      try {
        orderId = UUID.fromString(params.get(PARAM_ORDER_ID)[0]);
      } catch (IllegalArgumentException e) {
        LOGGER.error("Parsing order id resulted in illegal argument exception: %s", e.getMessage());
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_ID, inchiKey, response);
        return;
      }

      // Basic invariant checks: exactly one contact email address must be specified.
      if (!params.containsKey(PARAM_EMAIL) ||
          params.get(PARAM_EMAIL).length != 1 ||
          params.get(PARAM_EMAIL)[0] == null || params.get(PARAM_EMAIL)[0].isEmpty()) {
        makeOrderFormResponse(inchiKey, orderId.toString(), "A contact email address must be specified.", response);
        return;
      }

      String email = params.get(PARAM_EMAIL)[0];
      // Content-aware invariant checks: ensure email is valid.
      if (!EmailValidator.getInstance().isValid(email)) {
        makeOrderFormResponse(inchiKey, orderId.toString(), "The specified email address is invalid.", response);
        return;
      }

      // Verify that our order token hasn't timed out or already been used.
      String expectedInchiKey = orderIdCache.getIfPresent(orderId);
      if (expectedInchiKey == null) {
        LOGGER.warn("Couldn't find InChI Key for order id %s w/ user supplied InChI Key", orderId.toString(), inchiKey);
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_ID, inchiKey, response);
        return;
      }

      if (!expectedInchiKey.equals(inchiKey)) {
        LOGGER.warn("Expected and actual InChI Keys don't match for order id %s (%s vs %s)",
            orderId.toString(), expectedInchiKey, inchiKey);
        makeErrorResponse(ORDER_ERRORS.UNKNOWN_ID, inchiKey, response); // Take them back to the molecule they specified.
        return;
      }
      // Mark this order id as claimed by removing it from the cache.
      orderIdCache.invalidate(orderId);

      OrderRequest orderRequest = new OrderRequest(
          inchiKey, serviceConfig.getClientKeyword(), email, orderId.toString()
      );
      PublishRequest snsRequest = new PublishRequest(
          serviceConfig.getSnsTopic(),
          OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(orderRequest),
          "New reachables order request"
      );
      // Perform an async SNS request so we can time out if it takes too long to complete.
      Future<PublishResult> snsResultFuture = snsClient.publishAsync(snsRequest);

      try {
        PublishResult result = snsResultFuture.get(SNS_REQUEST_TIMEOUT, SNS_REQUEST_TIME_UNIT);
        LOGGER.info("Received SNS message id: %s", result.getMessageId());
      } catch (TimeoutException e) {
        // TODO: render an error page instead?
        LOGGER.error("Got timeout exception when sending SNS message: %s", e.getMessage());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      } catch (InterruptedException e) {
        LOGGER.error("Got interrupted exception when sending SNS message: %s", e.getMessage());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      } catch (ExecutionException e) {
        LOGGER.error("Got execution exception when sending SNS message: %s", e.getMessage());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      }

      LOGGER.info("Got order request for %s, assigned uuid %s", inchiKey, orderId);
      makeOrderSubmittedResponse(inchiKey, orderId.toString(), response);
    }

    void makeOrderFormResponse(String inchiKey, String orderId, String errorMessage, HttpServletResponse response)
        throws IOException, ServletException{
      TargetMolecule target = INCHI_KEY_TO_TARGET.get(inchiKey);

      // Make the order form for this molecule.
      Template t = cfg.getTemplate(TEMPLATE_NAME_ORDER_FORM);
      Map<String, String> model = new HashMap<String, String>() {{
        put("adminEmail", serviceConfig.getAdminEmail());
        put("imageLink", imagesUrlBase + target.getImageName());
        put("name", target.getDisplayName());
        put("inchiKey", target.getInchiKey());
        put("orderId", orderId);
        if (errorMessage != null && !errorMessage.isEmpty()) {
          put("errorMsg", errorMessage);
        }
      }};
      processTemplate(t, model, response);
      response.setStatus(HttpServletResponse.SC_OK);
    }

    void makeOrderSubmittedResponse(String inchiKey, String orderId, HttpServletResponse response)
        throws IOException, ServletException {
      TargetMolecule target = INCHI_KEY_TO_TARGET.get(inchiKey);

      Template t = cfg.getTemplate(TEMPLATE_NAME_ORDER_SUBMITTED);
      Map<String, String> model = new HashMap<String, String>() {{
        put("adminEmail", serviceConfig.getAdminEmail());
        put("inchiKey", target.getInchiKey());
        put("orderId", orderId);
        put("returnUrl", wikiUrlBase + target.getInchiKey());
      }};
      processTemplate(t, model, response);
      response.setStatus(HttpServletResponse.SC_OK);
    }

    void makeErrorResponse(ORDER_ERRORS error, String inchiKey, HttpServletResponse response)
        throws IOException, ServletException {
      Template t = cfg.getTemplate(TEMPLATE_NAME_ORDER_INVALID);
      Map<String, String> model = new HashMap<String, String>() {{
        put("adminEmail", serviceConfig.getAdminEmail());
        put(error.getTemplateKey(), Boolean.TRUE.toString()); // Just a flag.
        if (inchiKey != null) {
          put("sourcePageLink", String.format("%s?%s=%s", EXPECTED_TARGET, PARAM_INCHI_KEY, inchiKey));
        }
      }};
      processTemplate(t, model, response);
    }

    void processTemplate(Template template, Object model, HttpServletResponse response)
        throws IOException, ServletException {
      try {
        template.process(model, response.getWriter());
        response.getWriter().flush();
      } catch (TemplateException e) {
        LOGGER.error("Caught exception when trying to render invalid order template: %s", e.getMessage());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
      response.setStatus(HttpServletResponse.SC_OK);
    }
  }

  private enum ORDER_ERRORS {
    UNKNOWN_MOL("molNotRecognized"),
    UNKNOWN_ID("orderIdInvalid"),
    ;

    String templateKey;
    ORDER_ERRORS(String templateKey) {
      this.templateKey = templateKey;
    }

    public String getTemplateKey() {
      return this.templateKey;
    }
  }


  // Container class for easy JSON serialization of orders.
  private static class OrderRequest {
    @JsonProperty("inchi_key")
    String inchiKey;

    @JsonProperty("client_keyword")
    String clientKeyword;

    @JsonProperty("email")
    String email;

    @JsonProperty("order_id")
    String orderId;

    private OrderRequest() {

    }

    public OrderRequest(String inchiKey, String clientKeyword, String email, String orderId) {
      this.inchiKey = inchiKey;
      this.clientKeyword = clientKeyword;
      this.email = email;
      this.orderId = orderId;
    }

    public String getInchiKey() {
      return inchiKey;
    }

    public void setInchiKey(String inchiKey) {
      this.inchiKey = inchiKey;
    }

    public String getClientKeyword() {
      return clientKeyword;
    }

    public void setClientKeyword(String clientKeyword) {
      this.clientKeyword = clientKeyword;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getOrderId() {
      return orderId;
    }

    public void setOrderId(String orderId) {
      this.orderId = orderId;
    }
  }
}
