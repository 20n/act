package act.installer.pubchem;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * The PubchemMeshSynonyms class provides an API to get Pubchem synonyms and MeSH terms given an InChI string.
 * It assumes that a Virtuoso SPARQL endpoint is running on 10.0.20.19 (Chimay), port 8890 and that the necessary data
 * has been loaded in.
 * TODO(thomas): create a Wiki page describing the Virtuoso setup and how to load the data
 * The HTML serveur can be accessed at http://10.0.20.19:8890/sparql, with a UI to run SPARQL queries. Try it!
 */

public class PubchemMeshSynonyms {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemMeshSynonyms.class);

  public static final String OPTION_SERVICE_HOST_IP = "h";
  public static final String OPTION_SERVICE_PORT = "p";
  public static final String OPTION_QUERY_INCHI = "i";

  public static final String HELP_MESSAGE =
      "This class provides an API to get Pubchem synonyms and MeSH terms given an InChI string.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_SERVICE_HOST_IP)
        .argName("SERVICE_HOST_IP")
        .desc("The SPARQL server host's IP.")
        .hasArg()
        .longOpt("service_host_ip")
        .type(String.class)
    );
    add(Option.builder(OPTION_SERVICE_PORT)
        .argName("SERVICE_PORT")
        .desc("The SPARQL server host's port")
        .hasArg()
        .longOpt("service_port")
        .type(Integer.class)
    );
    add(Option.builder(OPTION_QUERY_INCHI)
        .argName("QUERY_INCHI")
        .desc("The InChI string to fetch synonyms for")
        .hasArg()
        .longOpt("query_inchi")
        .type(String.class)
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  // The Virtuoso SPARQL endpoint, lives by default on Chimay at port 8890
  private static final String DEFAULT_SERVICE_HOST_IP = "10.0.20.19";
  private static final String DEFAULT_SERVICE_PORT = "8890";

  private static final String CID_PATTERN = "CID\\d+";
  private static final String ENGLISH_LANG_TAG = "en";

  // InChI string (representing APAP) to be used as example in the main method
  private static final String TEST_INCHI = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";

  private String sparqlService;

  /*
  The CID_QUERY_TMPL SelectBuilder constructs SPARQL queries like the below one:
  #########
  PREFIX  sio:  <http://semanticscience.org/resource/>
  SELECT DISTINCT  ?inchi_iri
  FROM <http://rdf.ncbi.nlm.nih.gov/pubchem/descriptor/compound>
  WHERE
  { ?inchi_iri  sio:has-value  "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"@en }
  #########
  */
  private static final SelectBuilder CID_QUERY_TMPL = new SelectBuilder()
      // PREFIX (shorthands for IRI namespaces which can be looked up on http://prefix.cc)
      .addPrefix("sio", "http://semanticscience.org/resource/")
      // SELECT
      .setDistinct(true)
      .addVar("inchi_iri")
      // FROM
      .from("http://rdf.ncbi.nlm.nih.gov/pubchem/descriptor/compound")
      // WHERE
      .addWhere("?inchi_iri", "sio:has-value", "?inchi_string" )
      ;

  /*
  The PUBCHEM_SYNO_QUERY_TMPL SelectBuilder constructs SPARQL queries like the below one:
  #########
  PREFIX  sio:  <http://semanticscience.org/resource/>
  PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
  PREFIX  compound: <http://rdf.ncbi.nlm.nih.gov/pubchem/compound/>

  SELECT DISTINCT  ?value ?type
  FROM <http://rdf.ncbi.nlm.nih.gov/pubchem/synonym>
  WHERE
    { ?syno  sio:is-attribute-of  compound:CID1983 ;
             rdf:type             ?type ;
             sio:has-value        ?value
    }
  #########
  */
  private static final SelectBuilder PUBCHEM_SYNO_QUERY_TMPL = new SelectBuilder()
      // PREFIX (shorthands for IRI namespaces which can be looked up on http://prefix.cc)
      .addPrefix("sio", "http://semanticscience.org/resource/")
      .addPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
      .addPrefix("compound", "http://rdf.ncbi.nlm.nih.gov/pubchem/compound/")
      // SELECT
      .setDistinct(true)
      .addVar("value")
      .addVar("type")
      // FROM
      .from("http://rdf.ncbi.nlm.nih.gov/pubchem/synonym")
      // WHERE
      .addWhere("?syno", "sio:is-attribute-of", "?compound")
      .addWhere("?syno", RDF.type, "?type")
      .addWhere("?syno", "sio:has-value", "?value")
      ;

  /*
  The MESH_TERMS_QUERY_TMPL SelectBuilder constructs SPARQL queries like the below one:
  #########
  PREFIX  sio:  <http://semanticscience.org/resource/>
  PREFIX  dcterms: <http://purl.org/dc/terms/>
  PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>
  PREFIX  compound: <http://rdf.ncbi.nlm.nih.gov/pubchem/compound/>
  PREFIX  meshv: <http://id.nlm.nih.gov/mesh/vocab#>

  SELECT DISTINCT  ?concept_label ?lexical_tag
  FROM <http://rdf.ncbi.nlm.nih.gov/pubchem/synonym>
  FROM <http://id.nlm.nih.gov/mesh/>
  WHERE
    { ?syno     sio:is-attribute-of  compound:CID1983 ;
                dcterms:subject      ?mesh_concept .
      ?mesh_concept
                rdfs:label           ?concept_label ;
                meshv:preferredTerm  ?mesh_term .
      ?mesh_term  meshv:lexicalTag   ?lexical_tag
    }
  #########
  */
  private static final SelectBuilder MESH_TERMS_QUERY_TMPL = new SelectBuilder()
      // PREFIX (shorthands for IRI namespaces which can be looked up on http://prefix.cc)
      .addPrefix("sio", "http://semanticscience.org/resource/")
      .addPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
      .addPrefix("compound", "http://rdf.ncbi.nlm.nih.gov/pubchem/compound/")
      .addPrefix("dcterms", "http://purl.org/dc/terms/")
      .addPrefix("meshv", "http://id.nlm.nih.gov/mesh/vocab#")
      // SELECT
      .setDistinct(true)
      .addVar("concept_label")
      .addVar("lexical_tag")
      // FROM
      .from("http://rdf.ncbi.nlm.nih.gov/pubchem/synonym")
      .from("http://id.nlm.nih.gov/mesh/")
      // WHERE
      .addWhere("?syno", "sio:is-attribute-of", "?compound")
      .addWhere("?syno", "dcterms:subject", "?mesh_concept")
      .addWhere("?mesh_concept", "rdfs:label", "?concept_label")
      .addWhere("?mesh_concept", "meshv:preferredTerm", "?mesh_term")
      .addWhere("?mesh_term", "meshv:lexicalTag", "?lexical_tag")
      ;

  public static void main(final String[] args) {

    // Parse the command line options
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
      HELP_FORMATTER.printHelp(PubchemMeshSynonyms.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(PubchemMeshSynonyms.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String serviceHostIp = cl.getOptionValue(OPTION_SERVICE_HOST_IP, DEFAULT_SERVICE_HOST_IP);
    Integer servicePort = Integer.parseInt(cl.getOptionValue(OPTION_SERVICE_PORT, DEFAULT_SERVICE_PORT));
    String queryInchi = cl.getOptionValue(OPTION_QUERY_INCHI, TEST_INCHI);

    PubchemMeshSynonyms pubchemMeshSynonyms = new PubchemMeshSynonyms(serviceHostIp, servicePort);
    String cid = pubchemMeshSynonyms.fetchCIDFromInchi(queryInchi);
    if (cid != null) {
      Map<PubchemSynonymType, Set<String>> pubchemSynonyms = pubchemMeshSynonyms.fetchPubchemSynonymsFromCID(cid);
      LOGGER.info("Resulting Pubchem synonyms for %s are: %s", queryInchi, pubchemSynonyms);
      Map<MeshTermType, Set<String>> meshTerms = pubchemMeshSynonyms.fetchMeshTermsFromCID(cid);
      LOGGER.info("Resulting MeSH term s for %s are: %s", queryInchi, meshTerms);
    }
  }

  public PubchemMeshSynonyms() {
    sparqlService = getServiceFromHostParams(DEFAULT_SERVICE_HOST_IP, Integer.parseInt(DEFAULT_SERVICE_PORT));
  }

  public PubchemMeshSynonyms(String hostIp, Integer port) {
    sparqlService = getServiceFromHostParams(hostIp, port);
  }

  private String getServiceFromHostParams(String hostIp, Integer port) {
    URI uri = null;
    try {
      uri = new URIBuilder()
          .setScheme("http")
          .setHost(hostIp)
          .setPort(port)
          .setPath("/sparql")
          .build();

    } catch (URISyntaxException e) {
      LOGGER.error("An error occurred when trying to build the SPARQL service URI", e);
      System.exit(1);
    }
    LOGGER.debug("Constructed the following URL for SPARQL service: %s", uri.toString());
    return uri != null? uri.toString(): null;
  }

  public String fetchCIDFromInchi(String inchi) {
    // The clone method has its own implementation in the SelectBuilder. Thus safe to use!
    SelectBuilder sb = CID_QUERY_TMPL.clone();
    // The inchi litteral needs to be create with a language tag, otherwise it will not match anything
    // See "Matching Litteral with Language Tags" (https://www.w3.org/TR/rdf-sparql-query/#matchLangTags)
    // for more information
    sb.setVar(Var.alloc("inchi_string"), NodeFactory.createLiteral(inchi, ENGLISH_LANG_TAG));
    Query query = sb.build();

    String result;
    LOGGER.debug("Executing SPARQL query: %s", query.toString());
    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlService, query)) {
      ResultSet results = qexec.execSelect();
      // TODO: we assume here that there is at most one CID per InChI and return the first CID
      // Improve that behavior so we can stitch together many CID's synonyms.
      if (!results.hasNext()) {
        LOGGER.info("Could not find Pubchem Compound Id for input InChI %s", inchi);
        return null;
      }
      result = results.nextSolution().getResource("inchi_iri").getLocalName();
    }

    String cid = extractCIDFromResourceName(result);
    LOGGER.info("Found Pubchem Compound Id %s for input InChI %s", cid, inchi);
    return cid;
  }

  private String extractCIDFromResourceName(String resourceName) {
    Pattern p = Pattern.compile(CID_PATTERN);
    Matcher m = p.matcher(resourceName);
    String cid = null;
    while (m.find()) {
      cid = m.group(0);
    }
    return cid;
  }

  public Map<PubchemSynonymType, Set<String>> fetchPubchemSynonymsFromCID(String cid) {
    // The clone method has its own implementation in the SelectBuilder. Thus safe to use!
    SelectBuilder sb = PUBCHEM_SYNO_QUERY_TMPL.clone();
    sb.setVar(Var.alloc("compound"), String.format("compound:%s", cid));
    Query query = sb.build();
    LOGGER.debug("Executing SPARQL query: %s", query.toString());
    Map<PubchemSynonymType, Set<String>> map = new HashMap<>();

    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(sparqlService, query)) {
      ResultSet results = queryExecution.execSelect();
      while(results.hasNext()) {
        QuerySolution solution = results.nextSolution();
        String cheminfId = solution.getResource("type").getLocalName();
        String synonym = solution.getLiteral("value").getString();
        LOGGER.debug("Found synonym %s with type %s", synonym, cheminfId);
        PubchemSynonymType synonymType = PubchemSynonymType.getByCheminfId(cheminfId);
        Set synonyms = map.get(synonymType);
        if (synonyms == null) {
          synonyms = new HashSet<>();
          map.put(synonymType, synonyms);
        }
        synonyms.add(synonym);
      }
    }
    return map;
  }

  public Map<MeshTermType, Set<String>> fetchMeshTermsFromCID(String cid) {
    // The clone method has its own implementation in the SelectBuilder. Thus safe to use!
    SelectBuilder sb = MESH_TERMS_QUERY_TMPL.clone();
    sb.setVar(Var.alloc("compound"), String.format("compound:%s", cid));
    Query query = sb.build();
    LOGGER.debug("Executing SPARQL query: %s", query.toString());
    Map<MeshTermType, Set<String>> map = new HashMap<>();

    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(sparqlService, query)) {
      ResultSet results = queryExecution.execSelect();
      while(results.hasNext()) {
        QuerySolution solution = results.nextSolution();
        String conceptLabel = solution.getLiteral("concept_label").getString();
        String lexicalTag = solution.getLiteral("lexical_tag").getString();
        LOGGER.debug("Found term %s with tag %s", conceptLabel, lexicalTag);
        MeshTermType meshTermsType = MeshTermType.getByLexicalTag(lexicalTag);
        Set synonyms = map.get(meshTermsType);
        if (synonyms == null) {
          synonyms = new HashSet<>();
          map.put(meshTermsType, synonyms);
        }
        synonyms.add(conceptLabel);
      }
    }
    return map;
  }
}
