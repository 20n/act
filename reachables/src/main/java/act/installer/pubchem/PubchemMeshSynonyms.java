package act.installer.pubchem;

import act.installer.bing.BingSearchRanker;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PubchemMeshSynonyms {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BingSearchRanker.class);

  private static final String SERVICE = "http://10.0.20.19:8890/sparql";
  private static final String CID_PATTERN = "CID\\d+";

  private static final String TEST_INCHI = "InChI=1S/C6H12N3O/c1-2-3-4-9-5-6(7)10-8-9/h5H,2-4,7H2,1H3/q+1";
  private static final String TEST_INCHI_2 = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";
  private static final String TEST_INCHI_3 =
      "InChI=1S/C13H18O2/c1-9(2)8-11-4-6-12(7-5-11)10(3)13(14)15/h4-7,9-10H,8H2,1-3H3,(H,14,15)";
  private static final String TEST_INCHI_4 =
      "InChI=1S/C17H21NO4/c1-18-12-8-9-13(18)15(17(20)21-2)14(10-12)22-16(19)11-6-4-3-5-7-11/h3-7," +
          "12-15H,8-10H2,1-2H3/t12?,13?,14-,15+/m0/s1";


  private static SelectBuilder CID_SELECT_STATEMENT = new SelectBuilder()
      // Prefix
      .addPrefix("sio", "http://semanticscience.org/resource/")
      // SELECT
      .setDistinct(true)
      .addVar("inchi_iri")
      // FROM
      .from("http://rdf.ncbi.nlm.nih.gov/pubchem/descriptor/compound")
      // WHERE
      .addWhere( "?inchi_iri", "sio:has-value", "?inchi_string" )
      ;

  private static SelectBuilder SYNO_SELECT_STATEMENT = new SelectBuilder()
      // PREFIX
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
      .addWhere( "?syno", "sio:is-attribute-of", "?compound" )
      .addWhere("?syno", RDF.type, "?type")
      .addWhere("?syno", "sio:has-value", "?value")
      ;

  private static SelectBuilder MESH_SYNO_SELECT_STATEMENT = new SelectBuilder()
      // PREFIX
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
      .addWhere( "?syno", "sio:is-attribute-of", "?compound" )
      .addWhere("?syno", "dcterms:subject", "?mesh_concept")
      .addWhere("?mesh_concept", "rdfs:label", "?concept_label")
      .addWhere("?mesh_concept", "meshv:preferredTerm", "?mesh_term")
      .addWhere("?mesh_term", "meshv:lexicalTag", "?lexical_tag")
      ;

  public enum PC_SYNONYM_TYPES {
    // Names derived from the Semantic Chemistry Ontology: https://github.com/egonw/semanticchemistry
    TRIVIAL_NAME("CHEMINF_000109"),
    DEPOSITORY_NAME("CHEMINF_000339"),
    IUPAC_NAME("CHEMINF_000382"),
    DRUG_BANK_ID("CHEMINF_000406"),
    CHEBI_ID("CHEMINF_000407"),
    KEGG_ID("CHEMINF_000409"),
    CHEMBL_ID("CHEMINF_000412"),
    CAS_REGISTRY_NUMBER("CHEMINF_000446"),
    EC_NUMBER("CHEMINF_000447"),
    VALIDATED_CHEM_DB_ID("CHEMINF_000467"),
    DRUG_TRADE_NAME("CHEMINF_000561"),
    INTL_NONPROPRIETARY_NAME("CHEMINF_000562"),
    UNIQUE_INGREDIENT_ID("CHEMINF_000563"),
    LIPID_MAPS_ID("CHEMINF_000564"),
    NSC_NUMBER("CHEMINF_000565"),
    RTECS_ID("CHEMINF_000566"),
    UNKNOWN("NO_ID")
    ;

    private static final Map<String, PC_SYNONYM_TYPES> CHEMINF_TO_TYPE = new HashMap<String, PC_SYNONYM_TYPES>() {{
      for (PC_SYNONYM_TYPES type : PC_SYNONYM_TYPES.values()) {
        put(type.getCheminfId(), type);
      }
    }};


    public static PC_SYNONYM_TYPES getByCheminfId(String cheminfId) {
      return CHEMINF_TO_TYPE.getOrDefault(cheminfId, UNKNOWN);
    }

    String cheminfId;

    public String getCheminfId() {
      return cheminfId;
    }

    PC_SYNONYM_TYPES(String cheminfId) {
      this.cheminfId = cheminfId;
    }
  }

  public static void main(final String[] args) {
    PubchemMeshSynonyms pubchemMeshSynonyms = new PubchemMeshSynonyms();
    String inchi = TEST_INCHI;
    String cid = pubchemMeshSynonyms.fetchCIDFromInchi(inchi);
    Map<PC_SYNONYM_TYPES, Set<String>> pubchemSynonyms = pubchemMeshSynonyms.fetchPubchemSynonymsFromCID(cid);
    LOGGER.info("Resulting Pubchem synonyms for %s are: \n%s", inchi, pubchemSynonyms);
    Map<String, Set<String>> meshTerms = pubchemMeshSynonyms.fetchMeshTermsFromCID(cid);
    LOGGER.info("Resulting MeSH term s for %s are: \n%s", inchi, meshTerms);
  }


  public Map<PC_SYNONYM_TYPES, Set<String>> fetchPubchemSynonymsFromInchi(String inchi) {
    String cid = fetchCIDFromInchi(inchi);
    return fetchPubchemSynonymsFromCID(cid);
  }

  public Map<String, Set<String>> fetchMeshTermsFromInchi(String inchi) {
    String cid = fetchCIDFromInchi(inchi);
    return fetchMeshTermsFromCID(cid);
  }


  public String fetchCIDFromInchi(String inchi) {
    Query query = prepareCIDQueryFromInchi(inchi);
    LOGGER.debug("Executing SPARQL query: \n%s", query.toString());
    String result = getSingleResultFromQueryString(query, "inchi_iri");
    String cid = extractCIDFromResourceName(result);
    LOGGER.info("Found Pubchem Compound Id %s for input InChI %s", cid, inchi);
    return cid;
  }


  public String extractCIDFromResourceName(String resourceName) {
    Pattern p = Pattern.compile(CID_PATTERN);
    Matcher m = p.matcher(resourceName);
    String cid = null;
    while (m.find()) {
      cid = m.group(0);
    }
    return cid;
  }



  public Map<PC_SYNONYM_TYPES, Set<String>> fetchPubchemSynonymsFromCID(String cid) {

    SelectBuilder sb = SYNO_SELECT_STATEMENT.clone();
    sb.setVar(Var.alloc("compound"), String.format("compound:%s", cid));
    Query query = sb.build();
    LOGGER.debug("Executing SPARQL query: \n%s", query.toString());
    Map<PC_SYNONYM_TYPES, Set<String>> map = new HashMap<>();

    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SERVICE, query)) {
      ResultSet results = qexec.execSelect() ;
      for ( ; results.hasNext() ; )
      {
        QuerySolution soln = results.nextSolution() ;
        Resource type = soln.getResource("type") ; // Get a result variable - must be a resource
        String syno = soln.getLiteral("value").getString() ;   // Get a result variable - must be a literal
        String cheminfId = type.getLocalName();
        LOGGER.debug("Found synonym %s with type %s", syno, cheminfId);
        PC_SYNONYM_TYPES synonymType = PC_SYNONYM_TYPES.getByCheminfId(cheminfId);
        Set synoSet = map.get(synonymType);
        if (synoSet == null) {
          synoSet = new HashSet<>();
          map.put(synonymType, synoSet);
        }
        synoSet.add(syno);
      }

    }
    return map;
  }

  public Map<String, Set<String>> fetchMeshTermsFromCID(String cid) {

    SelectBuilder sb = MESH_SYNO_SELECT_STATEMENT.clone();
    sb.setVar(Var.alloc("compound"), String.format("compound:%s", cid));
    Query query = sb.build();
    LOGGER.debug("Executing SPARQL query: \n%s", query.toString());
    Map<String, Set<String>> map = new HashMap<>();

    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SERVICE, query)) {
      ResultSet results = qexec.execSelect() ;
      for ( ; results.hasNext() ; )
      {
        QuerySolution soln = results.nextSolution() ;
        String conceptLabel = soln.getLiteral("concept_label").getString() ;
        String lexicalTag = soln.getLiteral("lexical_tag").getString() ;
        Set synoSet = map.get(lexicalTag);
        if (synoSet == null) {
          synoSet = new HashSet<>();
          map.put(lexicalTag, synoSet);
        }
        synoSet.add(conceptLabel);
      }
    }
    return map;
  }


  public String getSingleResultFromQueryString(Query query, String header) {

    String result = null;

    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SERVICE, query)) {
      ResultSet results = qexec.execSelect() ;
      result = results.nextSolution().getResource(header).toString();
    }
    return result;
  }


  public Query prepareCIDQueryFromInchi(String inchi) {
    SelectBuilder sb = CID_SELECT_STATEMENT.clone();
    sb.setVar( Var.alloc( "inchi_string" ), NodeFactory.createLiteral(inchi, "en"));
    return sb.build();
  }
}