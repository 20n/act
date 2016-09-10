package act.installer;

import act.server.MongoDB;
import act.shared.Chemical;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jaxen.dom.DOMXPath;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HMDBParser {
  private static final Logger LOGGER = LogManager.getFormatterLogger(HMDBParser.class);

  private static final boolean ENABLE_XML_STREAM_TEXT_COALESCING = true;

  private enum HMDB_XPATH {
    // Names
    PRIMARY_NAME_TEXT("/metabolite/name/text()"),
    IUPAC_NAME_TEXT("/metabolite/iupac_name/text()"),
    SYNONYMS_NODES("/metabolite/synonyms/synonym"),
    // Structures
    INCHI_TEXT("/metabolite/inchi/text()"),
    INCHI_KEY_TEXT("/metabolite/inchikey/text()"),
    SMILES_TEXT("/metabolite/smiles/text()"),
    // Ontology
    ONTOLOGY_STATUS_TEXT("/metabolite/ontology/status/text()"),
    ONTOLOGY_ORIGINS_NODES("/metabolite/ontology/origins/origin"),
    ONTOLOGY_FUNCTIONS_NODES("/metabolite/ontology/functions/function"),
    ONTOLOGY_APPLICATIONS_NODES("/metabolite/ontology/applications/application"),
    ONTOLOGY_LOCATIONS_NODES("/metabolite/ontology/cellular_locations/cellular_location"),
    // Physiological locality
    LOCATIONS_FLUID_NODES("/metabolite/biofluid_locations/biofluid"),
    LOCATIONS_TISSUE_NODES("/metabolite/tissue_locations/tissue"),
    // Metabolic pathways
    PATHWAY_NAME_NODES("/metabolite/pathways/pathway/name"),
    // Diseases
    DISEASE_NAME_NODES("/metabolite/diseases/disease/name"),
    // External IDs
    METLIN_ID_TEXT("/metabolite/metlin_id/text()"),
    PUBCHEM_ID_TEXT("/metabolite/pubchem_compound_id/text()"),
    CHEBI_ID_TEXT("/metabolite/chebi_id/text()"),
    // Proteins
    PROTEIN_L1_NODES("/metabolite/protein_associations/protein"),
    PROTEIN_L2_NAME_TEXT("/protein/name/text()"),
    PROTEIN_L2_UNIPROT_ID_TEXT("/protein/uniprot_id/text()"),
    PROTEIN_L2_GENE_NAME_TEXT("/protein/gene_name/text()"),

    /* Features we're not extracting right now:
     * * Normal/abnormal concentrations in different fluids/tissues (too many different kinds of expression/units)
     * * Experimentally derived and predicted properties (many of the latter come from Chemaxon anyway)
     * * "specdb" ids, which represent NRM/MS2 data out there, not sure how useful this is right now
     * * Pathway details and ids, which hopefully are already captured via Metacyc
     * * Literature references, which we'd only inspect manually at present and we can always return to the source
     */
    ;

    String path;

    HMDB_XPATH(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }

    // We rely on Jaxen because we've experienced performance problems using the built-in/xerces XPath implementation.
    DOMXPath compile() throws JaxenException {
      return new DOMXPath(this.getPath());
    }
  }

  private final Map<HMDB_XPATH, XPath> xpaths = new HashMap<>();
  private MongoDB db;
  private DocumentBuilder documentBuilder;
  private XMLInputFactory xmlInputFactory;

  protected HMDBParser(MongoDB db) {
    this.db = db;
  }

  protected void init() throws JaxenException, ParserConfigurationException {
    for (HMDB_XPATH xpath : HMDB_XPATH.values()) {
      xpaths.put(xpath, xpath.compile());
    }

    // This method pilfered from PubchemParser.java.
    // TODO: next time we use this, put it in a common super class (do it once, do it again, then do it right!).
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    documentBuilder = factory.newDocumentBuilder();

    xmlInputFactory = XMLInputFactory.newInstance();

    /* Configure the XMLInputFactory to return event streams that coalesce consecutive character events.  Without this
     * we can end up with malformed names and InChIs, as XPath will only fetch the first text node if there are several
     * text children under one parent. */
    xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, ENABLE_XML_STREAM_TEXT_COALESCING);
    if ((Boolean) xmlInputFactory.getProperty(XMLInputFactory.IS_COALESCING)) {
      LOGGER.info("Successfully configured XML stream to coalesce character elements.");
    } else {
      LOGGER.error("Unable to configure XML stream to coalesce character elements.");
    }
  }


  // TODO: add some constraints on HMDB_XPATHs that allow us to programmatically check they're being applied correctly.
  private static List<String> extractNodesText(List<Node> n) {
    return n.stream().map(Node::getTextContent).collect(Collectors.toList());
  }

  private List<Node> getNodes(HMDB_XPATH xpath, Document doc) throws JaxenException {
    return (List<Node>) xpaths.get(xpath).selectNodes(doc); // No check, but guaranteed to return List<Node>.
  }

  private List<String> getTextFromNodes(HMDB_XPATH xpath, Document doc) throws JaxenException {
    return extractNodesText(getNodes(xpath, doc));
  }

  private String getText(HMDB_XPATH xpath, Document doc) throws JaxenException {
    return xpaths.get(xpath).stringValueOf(doc);
  }

  protected Chemical extractChemicalFromXMLDocument(Document doc) throws JaxenException, JSONException {
    String primaryName = getText(HMDB_XPATH.PRIMARY_NAME_TEXT, doc);
    String iupacName = getText(HMDB_XPATH.IUPAC_NAME_TEXT, doc);
    List<String> synonyms = getTextFromNodes(HMDB_XPATH.SYNONYMS_NODES, doc);

    String inchi = getText(HMDB_XPATH.INCHI_TEXT, doc);
    String inchiKey = getText(HMDB_XPATH.INCHI_KEY_TEXT, doc);
    String smiles = getText(HMDB_XPATH.SMILES_TEXT, doc);

    String ontologyStatus = getText(HMDB_XPATH.ONTOLOGY_STATUS_TEXT, doc);
    List<String> ontologyOrigins = getTextFromNodes(HMDB_XPATH.ONTOLOGY_ORIGINS_NODES, doc);
    List<String> ontologyFunctions = getTextFromNodes(HMDB_XPATH.ONTOLOGY_FUNCTIONS_NODES, doc);
    List<String> ontologyApplications = getTextFromNodes(HMDB_XPATH.ONTOLOGY_APPLICATIONS_NODES, doc);
    List<String> ontologyLocations = getTextFromNodes(HMDB_XPATH.ONTOLOGY_LOCATIONS_NODES, doc);

    List<String> locationFluids = getTextFromNodes(HMDB_XPATH.LOCATIONS_FLUID_NODES, doc);
    List<String> locationTissues = getTextFromNodes(HMDB_XPATH.LOCATIONS_TISSUE_NODES, doc);

    List<String> pathwayNames = getTextFromNodes(HMDB_XPATH.PATHWAY_NAME_NODES, doc);

    List<String> diseaseNames = getTextFromNodes(HMDB_XPATH.DISEASE_NAME_NODES, doc);

    String metlinId = getText(HMDB_XPATH.METLIN_ID_TEXT, doc);
    String pubchemId = getText(HMDB_XPATH.PUBCHEM_ID_TEXT, doc);
    String chebiId = getText(HMDB_XPATH.CHEBI_ID_TEXT, doc);

    List<Node> proteins = getNodes(HMDB_XPATH.PROTEIN_L1_NODES, doc);
    // Simple triples of name, uniprot id, gene name.
    List<Triple<String, String, String>> proteinAttributes = new ArrayList<>(proteins.size());

    for (Node n : proteins) {
      /* In order to run XPath on a sub-document, we have to Extract the relevant nodes into their own document object.
       * If we try to run evaluate on `n` instead of this new document, we'll get matching paths for the original
       * document `d` but not for the nodes we're looking at right now.  Very weird. */
      Document proteinDoc = documentBuilder.newDocument();
      proteinDoc.adoptNode(n);
      proteinDoc.appendChild(n);
      String name = getText(HMDB_XPATH.PROTEIN_L2_NAME_TEXT, doc);
      String uniprotId = getText(HMDB_XPATH.PROTEIN_L2_UNIPROT_ID_TEXT, doc);
      String geneName = getText(HMDB_XPATH.PROTEIN_L2_GENE_NAME_TEXT, doc);
      proteinAttributes.add(Triple.of(name, uniprotId, geneName));
    }

    // Assumption: there will always be an InChI.
    Chemical chem = new Chemical(inchi);
    chem.setSmiles(smiles);
    chem.setInchiKey(inchiKey); // Is this even necessary?

    chem.setCanon(primaryName);

    if (pubchemId != null) {
      chem.setPubchem(Long.valueOf(pubchemId));
    }

    synonyms.forEach(chem::addSynonym);
    chem.addSynonym(iupacName); // TODO: is there a better place for this?

    JSONObject meta = new JSONObject()
        .append("ontology", new JSONObject()
            .append("status", ontologyStatus)
            .append("origins", new JSONArray(ontologyOrigins))
            .append("functions", new JSONArray(ontologyFunctions))
            .append("applications", new JSONArray(ontologyApplications))
            .append("locations", new JSONArray(ontologyLocations))
        )
        .append("location", new JSONObject()
            .append("fluid", new JSONArray(locationFluids))
            .append("tissue", new JSONArray(locationTissues))
        )
        .append("pathway_names", new JSONArray(pathwayNames))
        .append("disease_names", new JSONArray(diseaseNames))
        .append("metlin_id", metlinId)
        .append("chebi_id", chebiId)
        .append("proteins", new JSONArray(proteinAttributes.stream()
            .map(t -> new JSONObject().
                append("name", t.getLeft()).
                append("uniprot_id", t.getMiddle()).
                append("gene_name", t.getRight())
            ).collect(Collectors.toList())
        )
    );

    chem.putRef(Chemical.REFS.HMDB, meta);

    return chem;
  }
}
