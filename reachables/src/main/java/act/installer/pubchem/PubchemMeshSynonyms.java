package act.installer.pubchem;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * The PubchemMeshSynonyms class provides an API to get Pubchem synonyms and MeSH terms given an InChI string.
 * It assumes that a Virtuoso SPARQL endpoint is running on 10.0.20.19 (Chimay), port 8890 and that the necessary data
 * has been loaded in.
 * TODO(thomas): create a Wiki page describing the Virtuoso setup and how to load the data
 */

public class PubchemMeshSynonyms {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemMeshSynonyms.class);

  // URL for the Virtuoso SPARQL endpoint, living on Chimay on port 8890
  private static final String SERVICE = "http://10.0.20.19:8890/sparql";

  private static final String CID_PATTERN = "CID\\d+";

  // InChI string (representing APAP) to be used as example in the main method
  private static final String TEST_INCHI = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";


  private static final SelectBuilder CID_SELECT_STATEMENT = new SelectBuilder()
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

  private static final SelectBuilder PUBCHEM_SYNO_QUERY_TMPL = new SelectBuilder()
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

  private static final SelectBuilder MESH_TERMS_QUERY_TMPL = new SelectBuilder()
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


  //The PC_SYNONYM_TYPE enum was taken and simplified from the PubchemTTLMerger class
  public enum PC_SYNONYM_TYPE {
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

    private static final Map<String, PC_SYNONYM_TYPE> CHEMINF_TO_TYPE = new HashMap<String, PC_SYNONYM_TYPE>() {{
      for (PC_SYNONYM_TYPE type : PC_SYNONYM_TYPE.values()) {
        put(type.getCheminfId(), type);
      }
    }};


    public static PC_SYNONYM_TYPE getByCheminfId(String cheminfId) {
      return CHEMINF_TO_TYPE.getOrDefault(cheminfId, UNKNOWN);
    }

    String cheminfId;

    public String getCheminfId() {
      return cheminfId;
    }

    PC_SYNONYM_TYPE(String cheminfId) {
      this.cheminfId = cheminfId;
    }
  }

  public enum MESH_TERMS_TYPE {
    // Names derived from the MeSH XML data elements page (https://www.nlm.nih.gov/mesh/xml_data_elements.html)
    // section LexicalTags.

    // Abbreviation, embedded abbreviation and acronym are self explanatory and describe well what they tag
    // They will be ignored for web queries
    ABBREVIATION("ABB"),
    EMBEDDED_ABBREVIATION("ABX"),
    ACRONYM("ACR"),
    // Examples of EPO tagged terms: Thomsen-Friedenreich antigen", "Brompton mixture",
    // "Andrew's Liver Salt", "Evans Blue", "Schiff Bases", "Giemsa Stain"
    EPONYM("EPO"),
    // Lab number: ignore for web queries
    LAB_NUMBER("LAB"),
    // Examples of NAM tagged terms: "Benin", "India", "Rome", "Taiwan", "United Nations", "Saturn"
    // Ignore by all means
    PROPER_NAME("NAM"),
    // NON tags the non-classified names, including most of the good ones
    NONE("NON"),
    // Trade names are tagged with TRD and seem to be of good quality.
    TRADE_NAME("TRD"),
    ;

    private static final Map<String, MESH_TERMS_TYPE> LEXICAL_TAG_TO_TYPE = new HashMap<String, MESH_TERMS_TYPE>() {{
      for (MESH_TERMS_TYPE type : MESH_TERMS_TYPE.values()) {
        put(type.getLexicalTag(), type);
      }
    }};

    public static MESH_TERMS_TYPE getByLexicalTag(String lexicalTag) {
      return LEXICAL_TAG_TO_TYPE.getOrDefault(lexicalTag, NONE);
    }

    String lexicalTag;

    public String getLexicalTag() {
      return lexicalTag;
    }

    MESH_TERMS_TYPE(String lexicalTag) {
      this.lexicalTag = lexicalTag;
    }
  }

  public static void main(final String[] args) {
    PubchemMeshSynonyms pubchemMeshSynonyms = new PubchemMeshSynonyms();
    String inchi = TEST_INCHI;
    String cid = pubchemMeshSynonyms.fetchCIDFromInchi(inchi);
    if (cid != null) {
      Map<PC_SYNONYM_TYPE, Set<String>> pubchemSynonyms = pubchemMeshSynonyms.fetchPubchemSynonymsFromCID(cid);
      LOGGER.info("Resulting Pubchem synonyms for %s are: \n%s", inchi, pubchemSynonyms);
      Map<MESH_TERMS_TYPE, Set<String>> meshTerms = pubchemMeshSynonyms.fetchMeshTermsFromCID(cid);
      LOGGER.info("Resulting MeSH term s for %s are: \n%s", inchi, meshTerms);
    }
  }

  public String fetchCIDFromInchi(String inchi) {

    SelectBuilder sb = CID_SELECT_STATEMENT.clone();
    sb.setVar(Var.alloc("inchi_string"), NodeFactory.createLiteral(inchi, "en"));
    Query query = sb.build();

    String result;
    LOGGER.debug("Executing SPARQL query: \n%s", query.toString());
    try (QueryExecution qexec = QueryExecutionFactory.sparqlService(SERVICE, query)) {
      ResultSet results = qexec.execSelect() ;
      result = results.nextSolution().getResource("inchi_iri").toString();
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

  public Map<PC_SYNONYM_TYPE, Set<String>> fetchPubchemSynonymsFromCID(String cid) {

    SelectBuilder sb = PUBCHEM_SYNO_QUERY_TMPL.clone();
    sb.setVar(Var.alloc("compound"), String.format("compound:%s", cid));
    Query query = sb.build();
    LOGGER.debug("Executing SPARQL query: \n%s", query.toString());
    Map<PC_SYNONYM_TYPE, Set<String>> map = new HashMap<>();

    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(SERVICE, query)) {
      ResultSet results = queryExecution.execSelect();
      for ( ; results.hasNext() ; )
      {
        QuerySolution solution = results.nextSolution();
        String cheminfId = solution.getResource("type").getLocalName();
        String synonym = solution.getLiteral("value").getString();
        LOGGER.debug("Found synonym %s with type %s", synonym, cheminfId);
        PC_SYNONYM_TYPE synonymType = PC_SYNONYM_TYPE.getByCheminfId(cheminfId);
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

  public Map<MESH_TERMS_TYPE, Set<String>> fetchMeshTermsFromCID(String cid) {

    SelectBuilder sb = MESH_TERMS_QUERY_TMPL.clone();
    sb.setVar(Var.alloc("compound"), String.format("compound:%s", cid));
    Query query = sb.build();
    LOGGER.debug("Executing SPARQL query: \n%s", query.toString());
    Map<MESH_TERMS_TYPE, Set<String>> map = new HashMap<>();

    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(SERVICE, query)) {
      ResultSet results = queryExecution.execSelect();
      for ( ; results.hasNext() ; )
      {
        QuerySolution solution = results.nextSolution();
        String conceptLabel = solution.getLiteral("concept_label").getString();
        String lexicalTag = solution.getLiteral("lexical_tag").getString();
        LOGGER.debug("Found term %s with tag %s", conceptLabel, lexicalTag);
        MESH_TERMS_TYPE meshTermsType = MESH_TERMS_TYPE.getByLexicalTag(lexicalTag);
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
