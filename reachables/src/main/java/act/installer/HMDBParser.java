package act.installer;

import act.installer.pubchem.PubchemParser;
import act.server.MongoDB;
import act.shared.Chemical;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
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
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HMDBParser {
  private static final Logger LOGGER = LogManager.getFormatterLogger(HMDBParser.class);

  private static final String OPTION_INPUT_DIRECTORY = "i";
  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";
  private static final String OPTION_DB_NAME = "d";

  private static final String DEFAULT_DB_HOST = "localhost";
  private static final String DEFAULT_DB_PORT = "27017";
  private static final String DEFAULT_DB_NAME = "actv01";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class parses HMDB XML files, converts them into chemical documents, and stores them in a DB",
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_DIRECTORY)
        .argName("input dir")
        .desc("The directory where the HMDB XML files live")
        .hasArg()
        .required()
        .longOpt("input-dir")
    );
    add(Option.builder(OPTION_DB_HOST)
        .argName("hostname")
        .desc(String.format("The DB host to which to connect (default: %s)", DEFAULT_DB_HOST))
        .hasArg()
        .longOpt("db-host")
    );
    add(Option.builder(OPTION_DB_PORT)
        .argName("port")
        .desc(String.format("The DB port to which to connect (default: %s)", DEFAULT_DB_PORT))
        .hasArg()
        .longOpt("db-port")
    );
    add(Option.builder(OPTION_DB_NAME)
        .argName("name")
        .desc(String.format("The name of the DB to which to install the HMDB chemicals (default: %s)", DEFAULT_DB_NAME))
        .hasArg()
        .longOpt("db-name")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  /* HMDB files all have five digits from 1 through 61388 as of the initial writing of this class.  I've allowed for an
   * extra digit in case the next release of the DB exceeds 100k metabolites.  We also log rejected files just in case.
   */
  private static final Pattern HMDB_FILE_REGEX = Pattern.compile("^HMDB\\d{5,6}\\.xml$");

  /* Represent the HMDB paths as enums to constrain the universe of extracted features to a fixed set of paths.
   * Most of the features are not sub-tree dependent, so we can just separate them into TEXT and NODES paths (i.e. paths
   * that return a single string or paths that return nodes containing either a string or a sub-tree that needs to be
   * re-parsed together).  For sub-trees requiring dependent parsing, where one path doesn't do the trick, we use the
   * L1/L2 convention adopted in PubchemParser:
   *   Structure: <feature name>_<level>[_<sub-feature or structure>]_<type>
   * L1 extracts a sub-tree, while L2 extracts the features of the sub-tree so they can be linked together.
   *
   * Note also the description of features we're *not* currently extracting.  There are other data in the HMDB entries
   * that may be useful at some point, but in the interest of time are being ignored for now. */
  private enum HMDB_XPATH {
    HMDB_ID_TEXT("/metabolite/accession/text()"),
    // Names
    PRIMARY_NAME_TEXT("/metabolite/name/text()"),
    IUPAC_NAME_TEXT("/metabolite/iupac_name/text()"),
    SYNONYMS_NODES("/metabolite/synonyms/synonym"),
    // Structures
    INCHI_TEXT("/metabolite/inchi/text()"),
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

  public static void main(String[] args) throws Exception {
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
      HELP_FORMATTER.printHelp(PubchemParser.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(PubchemParser.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File inputDir = new File(cl.getOptionValue(OPTION_INPUT_DIRECTORY));
    if (!inputDir.isDirectory()) {
      System.err.format("Input directory at %s is not a directory\n", inputDir.getAbsolutePath());
      System.exit(1);
    }

    String dbName = cl.getOptionValue(OPTION_DB_NAME, DEFAULT_DB_NAME);
    String dbHost = cl.getOptionValue(OPTION_DB_HOST, DEFAULT_DB_HOST);
    Integer dbPort = Integer.valueOf(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_DB_PORT));

    LOGGER.info("Connecting to %s:%d/%s", dbHost, dbPort, dbName);
    MongoDB db = new MongoDB(dbHost, dbPort, dbName);
    HMDBParser parser = Factory.makeParser(db);

    LOGGER.info("Starting parser");
    parser.run(inputDir);
    LOGGER.info("Done");
  }

  private final Map<HMDB_XPATH, XPath> xpaths = new HashMap<>();
  private MongoDB db;
  // This is required for extracting locality-dependent features of sub-trees using XPath.
  private DocumentBuilder documentBuilder;

  protected HMDBParser(MongoDB db) {
    this.db = db;
  }

  protected void init() throws JaxenException, ParserConfigurationException {
    for (HMDB_XPATH xpath : HMDB_XPATH.values()) {
      xpaths.put(xpath, xpath.compile());
    }

    // This bit pilfered from PubchemParser.java.
    // TODO: next time we use this, put it in a common super class (do it once, do it again, then do it right!).
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    documentBuilder = factory.newDocumentBuilder();
  }


  // TODO: add some constraints on HMDB_XPATHs that allow us to programmatically check they're being applied correctly.
  /**
   * Get the text contents contained in a list of nodes.  Used for multi-valued fields that are siblings in the tree.
   * @param n A list of nodes whose text content should be extracted.
   * @return The text for each node.
   */
  private static List<String> extractNodesText(List<Node> n) {
    return n.stream().map(Node::getTextContent).collect(Collectors.toList());
  }

  private List<Node> getNodes(HMDB_XPATH xpath, Document doc) throws JaxenException {
    return (List<Node>) xpaths.get(xpath).selectNodes(doc); // No check, but guaranteed to return List<Node>.
  }

  /**
   * Extract the textual content for a set of sibling nodes appearing at some path in the specified document.
   * @param xpath The path to use as a query.
   * @param doc The document to query.
   * @return The textual content of the nodes that live at the specified path.
   * @throws JaxenException
   */
  private List<String> getTextFromNodes(HMDB_XPATH xpath, Document doc) throws JaxenException {
    return extractNodesText(getNodes(xpath, doc));
  }

  private String getText(HMDB_XPATH xpath, Document doc) throws JaxenException {
    return xpaths.get(xpath).stringValueOf(doc);
  }

  /**
   * Convert an HMDB XML document into a Chemical object.  Expects one chemical per document.
   * @param doc A parsed HMDB XML doc.
   * @return The corresponding chemical to store in the DB.
   * @throws JaxenException
   * @throws JSONException
   */
  protected Chemical extractChemicalFromXMLDocument(Document doc) throws JaxenException, JSONException {
    String hmdbId = getText(HMDB_XPATH.HMDB_ID_TEXT, doc);

    String primaryName = getText(HMDB_XPATH.PRIMARY_NAME_TEXT, doc);
    String iupacName = getText(HMDB_XPATH.IUPAC_NAME_TEXT, doc);
    List<String> synonyms = getTextFromNodes(HMDB_XPATH.SYNONYMS_NODES, doc);

    String inchi = getText(HMDB_XPATH.INCHI_TEXT, doc);
    String smiles = getText(HMDB_XPATH.SMILES_TEXT, doc);

    // Require an InChI if we're going to consume this molecule.
    if (inchi == null || inchi.isEmpty()) {
      LOGGER.warn("No InChI found for HMDB chemical %s, aborting", hmdbId);
      return null;
    }

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
      String name = getText(HMDB_XPATH.PROTEIN_L2_NAME_TEXT, proteinDoc);
      String uniprotId = getText(HMDB_XPATH.PROTEIN_L2_UNIPROT_ID_TEXT, proteinDoc);
      String geneName = getText(HMDB_XPATH.PROTEIN_L2_GENE_NAME_TEXT, proteinDoc);
      proteinAttributes.add(Triple.of(name, uniprotId, geneName));
    }

    // Assumption: when we reach this point there will always be an InChI.
    Chemical chem = new Chemical(inchi);
    chem.setSmiles(smiles);

    chem.setCanon(primaryName);

    if (pubchemId != null && !pubchemId.isEmpty()) {
      chem.setPubchem(Long.valueOf(pubchemId));
    }

    synonyms.forEach(chem::addSynonym);
    chem.addSynonym(iupacName); // TODO: is there a better place for this?

    JSONObject meta = new JSONObject()
        .put("hmdb_id", hmdbId)
        .put("ontology", new JSONObject()
            .put("status", ontologyStatus)
            .put("origins", new JSONArray(ontologyOrigins))
            .put("functions", new JSONArray(ontologyFunctions))
            .put("applications", new JSONArray(ontologyApplications))
            .put("locations", new JSONArray(ontologyLocations))
        )
        .put("location", new JSONObject()
            .put("fluid", new JSONArray(locationFluids))
            .put("tissue", new JSONArray(locationTissues))
        )
        .put("pathway_names", new JSONArray(pathwayNames))
        .put("disease_names", new JSONArray(diseaseNames))
        .put("metlin_id", metlinId)
        .put("chebi_id", chebiId)
        .put("proteins", new JSONArray(proteinAttributes.stream()
            .map(t -> new JSONObject().
                put("name", t.getLeft()).
                put("uniprot_id", t.getMiddle()).
                put("gene_name", t.getRight())
            ).collect(Collectors.toList())
        )
    );

    chem.putRef(Chemical.REFS.HMDB, meta);

    return chem;
  }

  protected SortedSet<File> findHMDBFilesInDirectory(File dir) throws IOException {
    // Sort for consistency + sanity.
    SortedSet<File> results = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
    for (File file : dir.listFiles()) { // Do our own filtering so we can log rejects, of which we expect very few.
      if (HMDB_FILE_REGEX.matcher(file.getName()).matches()) {
        results.add(file);
      } else {
        LOGGER.warn("Found non-conforming HMDB file in directory %s: %s", dir.getAbsolutePath(), file.getName());
      }
    }
    return results;
  }

  /**
   * Extract all chemicals from HMDB XML files that live in the specified directory and save them in the DB.
   * Note that this search is not recursive: documents in sub-directories will be ignored.
   * @param inputDir The directory to scan for HMDB XML files.
   * @throws IOException
   * @throws IllegalArgumentException
   */
  public void run(File inputDir) throws IOException, IllegalArgumentException {
    if (inputDir == null || !inputDir.isDirectory()) {
      String msg = String.format("Unable to read input directory at %s",
          inputDir == null ? "null" : inputDir.getAbsolutePath());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }

    SortedSet<File> files = findHMDBFilesInDirectory(inputDir);
    LOGGER.info("Found %d HMDB XML files in directory %s", files.size(), inputDir.getAbsolutePath());

    for (File file : files) {
      LOGGER.debug("Processing HMDB XML file %s", file.getAbsolutePath());

      /* Promote our XML-specific exceptions to generic IllegalArgumentExceptions to reduce error handling surface
       * area for the caller. */
      Document d;
      try {
        d = documentBuilder.parse(file);
      } catch (SAXException e) {
        String msg = String.format("Unable to parse XML file at %s: %s", file.getAbsolutePath(), e.getMessage());
        throw new IllegalArgumentException(msg, e);
      }

      /* Jaxen doesn't throw exceptions if it can't find a path, so a JaxenException here is completely unexpected.
       * It might mean corrupted XML or some unrecoverable XPath problem that we don't expect.  In any case, promote
       * the exception to the caller as it's unclear how we could deal with such an error here. */
      Chemical chem;
      try {
        chem = extractChemicalFromXMLDocument(d);
      } catch (JaxenException e) {
        String msg = String.format("Unable to extract features from XML file at %s: %s",
            file.getAbsolutePath(), e.getMessage());
        throw new IllegalArgumentException(msg, e);
      }

      // Not all HMDB entries contain
      if (chem == null) {
        LOGGER.warn("Unable to create chemical from file %s", file.getAbsolutePath());
        continue;
      }

      // submitToActChemicalDB creates or merges as necessary.
      Long id = db.getNextAvailableChemicalDBid();
      db.submitToActChemicalDB(chem, id);
      LOGGER.debug("Submitted chemical %d to the DB", id);
    }
    LOGGER.info("Loaded %d HMDB chemicals into DB", files.size());
  }

  public static class Factory {
    public static HMDBParser makeParser(MongoDB db) {
      HMDBParser parser = new HMDBParser(db);
      // Promote XML-specific exceptions from parser initialization to runtime exceptions, as they are definite bugs.
      try {
        parser.init();
      } catch (JaxenException e) {
        LOGGER.error("BUG: caught JaxenException on initialization, which means programmer error: %s",
            e.getMessage());
        throw new RuntimeException(e);
      } catch (ParserConfigurationException e) {
        LOGGER.error("BUG: caught ParserConfigurationException on initialization, which means programmer error: %s",
            e.getMessage());
        throw new RuntimeException(e);
      }

      return parser;
    }
  }
}
