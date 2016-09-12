package act.installer.pubchem;

import act.server.MongoDB;
import act.shared.Chemical;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaxen.JaxenException;
import org.jaxen.dom.DOMXPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.XMLEvent;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class PubchemParser {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemParser.class);
  private static final String OPTION_DATA_DIRECTORY = "i";
  private static final String OPTION_DB = "o";
  private static final String GZIP_FILE_EXT = ".gz";
  private static final String COMPOUND_DOC_TAG = "PC-Compound";

  private static final int GZIP_BUFFER_SIZE = 1 << 27; // ~128MB of buffer space to help GZip really move.

  private static final boolean ENABLE_XML_STREAM_TEXT_COALESCING = true;

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final String HELP_MESSAGE = "This class is used for parsing xml lcms and storing them in a db";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DATA_DIRECTORY)
        .argName("OPTION_DATA_DIRECTORY")
        .desc("The data directory where the pubchem lcms live")
        .hasArg()
        .required()
        .type(String.class)
    );
    add(Option.builder(OPTION_DB)
        .argName("OPTION_DB")
        .desc("The db to save the results in")
        .hasArg()
        .required()
        .type(String.class)
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  /**
   * Key terminologies for this file:
   *
   * a) An example of how an inchi is packaged in the XML document
   *
   <PC-InfoData>
    <PC-InfoData_urn>
      <PC-Urn>
        <PC-Urn_label>InChI</PC-Urn_label>
        <PC-Urn_name>Standard</PC-Urn_name>
        <PC-Urn_datatype>
          <PC-UrnDataType value="string">1</PC-UrnDataType>
        </PC-Urn_datatype>
        <PC-Urn_version>1.0.4</PC-Urn_version>
        <PC-Urn_software>InChI</PC-Urn_software>
        <PC-Urn_source>iupac.org</PC-Urn_source>
        <PC-Urn_release>2012.11.26</PC-Urn_release>
      </PC-Urn>
    </PC-InfoData_urn>
    <PC-InfoData_value>
      <PC-InfoData_value_sval>InChI=1S/C12H17FO/c1-12(2,3)10-6-4-9(5-7-10)11(13)8-14/h4-7,11,14H,8H2,1-3H3</PC-InfoData_value_sval>
    </PC-InfoData_value>
   </PC-InfoData>
   *
   * In order to parse the inchi, we first detect the element <PC-Urn_label>InChI</PC-Urn_label>, since it tells us this
   * node is an inchi node. The element PC-Urn_label is called a "ResourceName". The term "inchi", a value of the resource
   * name node, is called a "ResourceValue". For element <PC-Urn_label>InChI</PC-Urn_label>, two xml events occurs in sequence:
   * 1) START ELEMENT 2) CHARACTERS. We identify a ResourceName in (1) and ResourceValue in (2). In this case, we have two
   * nodes of interest:
   * a) <PC-Urn_label>InChI</PC-Urn_label>
   * b) <PC-InfoData_value_sval>InChI=1S/C12H17FO/c1-12(2,3)10-6-4-9(5-7-10)11(13)8-14/h4-7,11,14H,8H2,1-3H3</PC-InfoData_value_sval>
   *
   * Once we detect the node as an inchi node from a), we can easily detect the ResourceName "PC-InfoData_value_sval" and
   * store it's value for b). We call PC-Urn_label "PUBCHEM_KEY" since it is a key to the value of the inchi, whereas
   * PC-InfoData_value_sval is the "PUBCHEM_VALUE" since it is the value to the inchi.
   */

  private enum PC_XPATHS {
    /* Structure: <feature name>_<level>_[_<sub-feature or structure>]_<type>
     * [IUPAC_NAME]_[L1]_[NODES]: nodes in the original document (L1) that correspond to IUPAC name entries.
     * [IUPAC_NAME]_[L2]_[VALUE]_[TEXT]: textual names in the IUPAC name sub-tree (L2).
     */
    IUPAC_NAME_L1_NODES("/PC-Compound/PC-Compound_props/PC-InfoData[./PC-InfoData_urn/PC-Urn/PC-Urn_label/text()=\"IUPAC Name\"]"),
    IUPAC_NAME_L2_TYPE_TEXT("/PC-InfoData/PC-InfoData_urn/PC-Urn/PC-Urn_name/text()"),
    IUPAC_NAME_L2_VALUE_TEXT("/PC-InfoData/PC-InfoData_value/PC-InfoData_value_sval/text()"),

    // TODO: ensure there is exactly one id_cid per compound.
    PC_ID_L1_TEXT("/PC-Compound/PC-Compound_id/PC-CompoundType/PC-CompoundType_id/PC-CompoundType_id_cid/text()"),

    INCHI_L1_NODES("/PC-Compound/PC-Compound_props/PC-InfoData[./PC-InfoData_urn/PC-Urn/PC-Urn_label/text()=\"InChI\"]"),
    /* We could just use //PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()="InChI"]//PC-InfoData_value_sval/text()
     * but we split the InChI parsing into two pieces in case there are multiple InChI entries (which would be insane).
     */
    INCHI_L2_TEXT("/PC-InfoData/PC-InfoData_value/PC-InfoData_value_sval/text()"),

    SMILES_L1_NODES("//PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()=\"SMILES\"]"),
    SMILES_L2_TEXT("//PC-InfoData_value_sval/text()"),
    ;

    private String path;
    PC_XPATHS(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }

    DOMXPath compile() throws JaxenException {
      return new DOMXPath(this.getPath());
    }
  }

  private static final Set<String> TAGS_TO_INCLUDE = Collections.unmodifiableSet(new HashSet<String>() {{
    add("PC-Compound_id");
    add("PC-Compound_props");
  }});

  private final Map<PC_XPATHS, DOMXPath> xpaths = new HashMap<>(PC_XPATHS.values().length);

  private MongoDB db;
  private DocumentBuilder documentBuilder;
  private XMLInputFactory xmlInputFactory;

  public PubchemParser(MongoDB db) {
    this.db = db;
  }

  /**
   * Initializes a PubchemParser.  Must be called before the PubchemParser can be used.
   * @throws XPathExpressionException
   * @throws ParserConfigurationException
   */
  public void init() throws ParserConfigurationException, JaxenException {
    // Would rather do this in its own block, but have to handle the XPath exception. :(
    for (PC_XPATHS x : PC_XPATHS.values()) {
      xpaths.put(x, x.compile());
    }

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

  /**
   * This function writes a chemical record to the DB.
   * @param chemical Chemical to be written to the DB.
   */
  private void writeChemicalToDB(Chemical chemical) {
    Long id = db.getNextAvailableChemicalDBid();
    db.submitToActChemicalDB(chemical, id);
  }

  /**
   * Extracts compound features from a sub-document/sub-tree containing one PC-Compound element.  Nodes that contain
   * interesting features are found and their text extracted using XPath.
   *
   * @param d The document from which to extract features.
   * @return A PubchemEntry object corresponding to features from one PC-Compound document.
   * @throws XPathExpressionException
   */
  private PubchemEntry extractPCCompoundFeatures(Document d) throws JaxenException {
    Long id = Long.valueOf(xpaths.get(PC_XPATHS.PC_ID_L1_TEXT).stringValueOf(d));
    PubchemEntry entry = new PubchemEntry(id);

    // Jaxen's API is from a pre-generics age!
    List<Node> nodes = (List<Node>) xpaths.get(PC_XPATHS.IUPAC_NAME_L1_NODES).selectNodes(d);
    if (nodes.size() == 0) {
      LOGGER.warn("No names available for compound %d", id);
    }
    for (Node n : nodes) {
      /* In order to run XPath on a sub-document, we have to Extract the relevant nodes into their own document object.
       * If we try to run evaluate on `n` instead of this new document, we'll get matching paths for the original
       * document `d` but not for the nodes we're looking at right now.  Very weird.
       * TODO: remember this way of running XPath on documents the next time we need to write an XML parser. */
      Document iupacNameDoc = documentBuilder.newDocument();
      iupacNameDoc.adoptNode(n);
      iupacNameDoc.appendChild(n);
      String type = xpaths.get(PC_XPATHS.IUPAC_NAME_L2_TYPE_TEXT).stringValueOf(iupacNameDoc);
      String value = xpaths.get(PC_XPATHS.IUPAC_NAME_L2_VALUE_TEXT).stringValueOf(iupacNameDoc);
      entry.setNameByType(type, value);
    }

    // We really need an InChI for a chemical to make sense, so log errors if we can't find one.
    boolean hasInChI = false;
    nodes = xpaths.get(PC_XPATHS.INCHI_L1_NODES).selectNodes(d);
    if (nodes.size() == 0) {
      LOGGER.warn("Found chemical (%d) with no InChIs, hoping for SMILES instead", id);
    } else if (nodes.size() > 1) {
      LOGGER.error("Assumption violation: found chemical with multiple InChIs (%d), skipping", id);
      return null;
    } else {
      hasInChI = true;
      Node n = nodes.get(0);
      Document inchiDoc = documentBuilder.newDocument();
      inchiDoc.adoptNode(n);
      inchiDoc.appendChild(n);
      String value = xpaths.get(PC_XPATHS.INCHI_L2_TEXT).stringValueOf(inchiDoc);
      entry.setInchi(value);
    }

    nodes = xpaths.get(PC_XPATHS.SMILES_L1_NODES).selectNodes(d);
    if (nodes.size() == 0) {
      if (hasInChI) {
        LOGGER.warn("Found chemical (%d) with no SMILES, using only InChI");
      } else {
        LOGGER.warn("Found chemical (%d) with no InChI or SMILES, skipping");
        return null;
      }
    } else {
      for (Node n : nodes) {
        Document smilesDoc = documentBuilder.newDocument();
        smilesDoc.adoptNode(n);
        smilesDoc.appendChild(n);
        String smiles = xpaths.get(PC_XPATHS.SMILES_L2_TEXT).stringValueOf(smilesDoc);
        entry.appendSmiles(smiles);
      }
    }

    return entry;
  }

  /**
   * Incrementally parses a stream of XML events from a PubChem file, extracting the next available PC-Compound entry
   * as a Chemical object.
   * @param eventReader The xml event reader we are parsing the XML from
   * @return The constructed chemical
   * @throws XMLStreamException
   * @throws XPathExpressionException
   */
  public Chemical extractNextChemicalFromXMLStream(XMLEventReader eventReader)
      throws XMLStreamException, JaxenException {
    Document bufferDoc = null;
    Element currentElement = null;
    StringBuilder textBuffer = null;
    /* With help from
     * http://stackoverflow.com/questions/7998733/loading-local-chunks-in-dom-while-parsing-a-large-xml-file-in-sax-java
     */
    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          String eventName = event.asStartElement().getName().getLocalPart();
          if (COMPOUND_DOC_TAG.equals(eventName)) {
            // Create a new document if we've found the start of a compound object.
            bufferDoc = documentBuilder.newDocument();
            currentElement = bufferDoc.createElement(eventName);
            bufferDoc.appendChild(currentElement);
          } else if (currentElement != null) { // Wait until we've found a compound entry to start slurping up data.
            // Create a new child element and push down the current pointer when we find a new node.
            Element newElement = bufferDoc.createElement(eventName);
            currentElement.appendChild(newElement);
            currentElement = newElement;
          } // If we aren't in a PC-Compound tree, we just let the elements pass by.
          break;

        case XMLStreamConstants.CHARACTERS:
          if (currentElement == null) { // Ignore this event if we're not in a PC-Compound tree.
            continue;
          }

          Characters chars = event.asCharacters();
          // Ignore only whitespace strings, which just inflate the size of the DOM.  Text coalescing makes this safe.
          if (chars.isWhiteSpace()) {
            continue;
          }

          // Rely on the XMLEventStream to coalesce consecutive text events.
          Text textNode = bufferDoc.createTextNode(chars.getData());
          currentElement.appendChild(textNode);
          break;

        case XMLStreamConstants.END_ELEMENT:
          if (currentElement == null) { // Ignore this event if we're not in a PC-Compound tree.
            continue;
          }

          eventName = event.asEndElement().getName().getLocalPart();
          Node parentNode = currentElement.getParentNode();
          if (parentNode instanceof Element) {
            currentElement = (Element) parentNode;
          } else if (parentNode instanceof Document && eventName.equals(COMPOUND_DOC_TAG)) {
            // We're back at the top of the node stack!  Convert the buffered document into a Chemical.
            PubchemEntry entry = extractPCCompoundFeatures(bufferDoc);
            if (entry != null) {
              return entry.asChemical();
            } else {
              // Skip this entry if we can't process it correctly by resetting the world and continuing on.
              bufferDoc = null;
              currentElement = null;
            }
          } else {
            // This should not happen, but is here as a sanity check.
            throw new RuntimeException(String.format("Parent of XML element %s is of type %d, not Element",
                currentElement.getTagName(), parentNode.getNodeType()));
          }
          break;

        // TODO: do we care about attributes or other XML structures?
      }
    }

    // Return null when we run out of chemicals, just like readLine().
    return null;
  }

  /**
   * This function reads a given gzipped XML file, passes the xml event stream to a function to parse out the chemical,
   * and writes the chemical to the db.
   * @param file The input gzipped file that is being processed.
   * @throws XMLStreamException
   * @throws IOException
   */
  public void openCompressedXMLFileAndWriteChemicals(File file)
      throws XMLStreamException, JaxenException, IOException {
    XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(
        new GZIPInputStream(new FileInputStream(file), GZIP_BUFFER_SIZE));
    Chemical result;
    while ((result = extractNextChemicalFromXMLStream(eventReader)) != null) {
      writeChemicalToDB(result);
    }
  }

  /**
   * This function is the main driver of the class, which processes all the xml lcms of the pubchem dump.
   * @throws XMLStreamException
   * @throws IOException
   */
  private void run(List<File> filesToProcess) throws XMLStreamException, JaxenException, IOException {
    int counter = 1;
    for (File file : filesToProcess) {
      LOGGER.info("Processing file %d of %d", counter, filesToProcess.size());
      LOGGER.info("File name is %s", file.getPath());
      openCompressedXMLFileAndWriteChemicals(file);
      counter++;
    }
  }

  /**
   * This function extracts gzipped xml lcms from a file directory.
   * @param dataDirectory The directory of interest.
   * @return A list of lcms of gzipped xml lcms.
   * @throws XMLStreamException
   * @throws IOException
   */
  private static List<File> findGZippedFilesInDirectory(String dataDirectory) throws XMLStreamException, IOException {
    File folder = new File(dataDirectory);

    if (!folder.exists()) {
      String msg = String.format("The folder %s does not exists", folder.getAbsolutePath());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }

    List<File> result = Arrays.stream(folder.listFiles()).
        filter(f -> f.getName().endsWith(GZIP_FILE_EXT)).collect(Collectors.toList());

    // Sort lcms lexicographically for installer stability.
    Collections.sort(result);

    return result;
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
      LOGGER.error("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(PubchemParser.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(PubchemParser.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String dataDir = cl.getOptionValue(OPTION_DATA_DIRECTORY);
    String dbName = cl.getOptionValue(OPTION_DB);

    MongoDB db = new MongoDB("localhost", 27017, dbName);
    PubchemParser pubchemParser = new PubchemParser(db);
    pubchemParser.init();
    pubchemParser.run(findGZippedFilesInDirectory(dataDir));
  }
}
