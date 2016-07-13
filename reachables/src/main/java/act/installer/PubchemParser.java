package act.installer;

import act.server.MongoDB;
import act.shared.Chemical;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class PubchemParser {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemParser.class);
  private static final String OPTION_DATA_DIRECTORY = "i";
  private static final String OPTION_DB = "o";
  private static final String GZIP_FILE_EXT = ".gz";
  private static final Long FAKE_ID = -1L;
  private static final String EMPTY_STRING = "";

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final String HELP_MESSAGE = "This class is used for parsing xml files and storing them in a db";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DATA_DIRECTORY)
        .argName("OPTION_DATA_DIRECTORY")
        .desc("The data directory where the pubchem files live")
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

  // Names: //PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()="IUPAC Name"]
  // ID: //PC-CompoundType_id_cid/text()
  // InChI: //PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()="InChI"]
  // Smiles: //PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()="SMILES"]

  private enum XPATHS {
    /* Structure: <feature name>_<level>_[_<sub-feature or structure>]_<type>
     * [IUPAC_NAME]_[L1]_[NODES]: nodes in the original document (L1) that correspond to IUPAC name entries.
     * [IUPAC_NAME]_[L2]_[VALUE]_[TEXT]: textual names in the IUPAC name sub-tree (L2).
     */
    IUPAC_NAME_L1_NODES("//PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()=\"IUPAC Name\"]"),
    IUPAC_NAME_L2_TYPE_TEXT("//PC-Urn_name/text()"),
    IUPAC_NAME_L2_VALUE_TEXT("//PC-InfoData_value_sval/text()"),

    // TODO: ensure there is exactly one id_cid per compound.
    PC_ID_L1_TEXT("//PC-CompoundType_id_cid/text()"),

    INCHI_L1_NODES("//PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()=\"InChI\"]"),
    /* We could just use //PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()="InChI"]//PC-InfoData_value_sval/text()
     * but we split the InChI parsing into two pieces in case there are multiple InChI entries (which would be insane).
     */
    INCHI_L2_TEXT("//PC-InfoData_value_sval/text()"),

    // TODO: consider extracting SMILES.
    SMILES_L1_NODES("//PC-InfoData[./PC-InfoData_urn//PC-Urn_label/text()=\"SMILES\"]"),
    SMILES_L2_TEXT("//PC-InfoData_value_sval/text()"),
    ;

    private String path;
    XPATHS(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }

    XPathExpression compile(XPath xpath) throws XPathExpressionException {
      return xpath.compile(this.path);
    }
  }

  private static class PubChemEntry implements Serializable {
    private static final long serialVersionUID = -6542683222963930035L;

    // TODO: use a builder for this instead of constructing and mutating.

    @JsonProperty("IUPAC_names")
    private Map<String, String> names = new HashMap<>(5); // There tend to be five name variants per chemical.
    @JsonProperty("ids")
    private List<Long> ids = new ArrayList<>(1); // Hopefully there's only one id.
    @JsonProperty("InChI")
    private String inchi;

    // For general use.
    public PubChemEntry(Long id) {
      this.ids.add(id);
    }

    // For deserialization.
    public PubChemEntry(Map<String, String> names, List<Long> ids, String inchi) {
      this.names = names;
      this.ids = ids;
      this.inchi = inchi;
    }

    public Map<String, String> getNames() {
      return names;
    }

    public void setNameByType(String type, String value) {
      names.put(type, value);
    }

    public List<Long> getIds() {
      return ids;
    }

    public void appendId(Long id) {
      ids.add(id);
    }

    public String getInchi() {
      return inchi;
    }

    public void setInchi(String inchi) {
      this.inchi = inchi;
    }
  }

  private final Map<XPATHS, XPathExpression> xpaths = new HashMap<>(XPATHS.values().length);

  private List<File> filesToProcess;
  private MongoDB db;

  public PubchemParser(MongoDB db, List<File> filesToProcess) {
    this.db = db;
    this.filesToProcess = filesToProcess;
  }

  public void init() throws XPathExpressionException {
    XPathFactory factory = XPathFactory.newInstance();
    XPath xpath = factory.newXPath();

    // Would rather do this in its own block, but have to handle the XPath exception. :(
    for (XPATHS x : XPATHS.values()) {
      xpaths.put(x, x.compile(xpath));
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

  private PubChemEntry convertSubdocToChemical(Document d) throws XPathExpressionException, TransformerException, TransformerConfigurationException, ParserConfigurationException {
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();

    Long id = Long.valueOf((String) xpaths.get(XPATHS.PC_ID_L1_TEXT).evaluate(d, XPathConstants.STRING));

    PubChemEntry entry = new PubChemEntry(id);

    NodeList nodes = (NodeList) xpaths.get(XPATHS.IUPAC_NAME_L1_NODES).evaluate(d, XPathConstants.NODESET);
    Map<String, String> names = new HashMap<>(5); // There tend to be five name variants per chemical.
    for (int i = 0; i < nodes.getLength(); i++) {
      Node n = nodes.item(i);
      Document d2 = builder.newDocument();
      d2.adoptNode(n);
      d2.appendChild(n);
      String type = (String) xpaths.get(XPATHS.IUPAC_NAME_L2_TYPE_TEXT).evaluate(d2, XPathConstants.STRING);
      String value = (String) xpaths.get(XPATHS.IUPAC_NAME_L2_VALUE_TEXT).evaluate(d2, XPathConstants.STRING);
      //DOMSource domSource = new DOMSource(d2);
      //StreamResult streamResult = new StreamResult(System.out);
      //transformer.transform(domSource, streamResult);
      //System.out.format("\n\n%s: %s\n\n\n", type, value);
      entry.setNameByType(type, value);
    }

    String inchi = null;
    nodes = (NodeList) xpaths.get(XPATHS.INCHI_L1_NODES).evaluate(d, XPathConstants.NODESET);
    if (nodes.getLength() > 1) {
      LOGGER.error("Assumption violation: found chemical with multiple InChIs (%d), skipping", id);
    } else if (nodes.getLength() == 0) {
      LOGGER.error("Assumption violation: found chemical no InChIs (%d), skipping", id);
    } else {
      Node n = nodes.item(0);
      Document d2 = builder.newDocument();
      d2.adoptNode(n);
      d2.appendChild(n);
      /*
      DOMSource domSource = new DOMSource(d2);
      StreamResult streamResult = new StreamResult(System.out);
      transformer.transform(domSource, streamResult);
      */
      String value = (String) xpaths.get(XPATHS.INCHI_L2_TEXT).evaluate(d2, XPathConstants.STRING);
      entry.setInchi(value);
    }

    return entry;
  }

  /**
   * This function parses the xml event stream and constructs a template chemical object.
   * @param eventReader The xml event reader we are parsing the XML from
   * @return The constructed chemical
   * @throws XMLStreamException
   */
  public Chemical constructChemicalFromEventReader(XMLEventReader eventReader)
      throws XMLStreamException, ParserConfigurationException, TransformerConfigurationException, TransformerException, XPathExpressionException {
    Chemical templateChemical = new Chemical(FAKE_ID);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document d = null;
    Element currentElement = null;

    /* With help from
     * http://stackoverflow.com/questions/7998733/loading-local-chunks-in-dom-while-parsing-a-large-xml-file-in-sax-java
     */
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();

    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          String eventName = event.asStartElement().getName().getLocalPart();
          if (eventName.equals("PC-Compound")) {
            d = builder.newDocument();
            currentElement = d.createElement(eventName);
            d.appendChild(currentElement);
          } else if (currentElement != null) { // Wait until we've found a compound entry to start slurping up data.
            // Create a new child element and push down the current pointer when we find a new node.
            Element newElement = d.createElement(eventName);
            currentElement.appendChild(newElement);
            currentElement = newElement;
          }
          break;


        case XMLStreamConstants.CHARACTERS:
          if (currentElement != null) {
            // Append text as a child node of the current element when we find it.
            Characters chars = event.asCharacters();
            Text textNode = d.createTextNode(chars.getData());
            currentElement.appendChild(textNode);
          }
          break;

        case XMLStreamConstants.END_ELEMENT:
          if (currentElement != null) {
            // Move current pointer to the parent when we find an end tag.
            Node parentNode = currentElement.getParentNode();
            eventName = event.asEndElement().getName().getLocalPart();
            if (parentNode instanceof Element) {
              currentElement = (Element) parentNode;
            } else if (parentNode instanceof Document && eventName.equals("PC-Compound")) {
              // We're back at the top of the node stack!  Print out the document and reset the world.
              //DOMSource domSource = new DOMSource(d);
              //StreamResult streamResult = new StreamResult(System.out);
              //transformer.transform(domSource, streamResult);
              PubChemEntry entry = convertSubdocToChemical(d);
              ObjectMapper mapper = new ObjectMapper();
              try {
                System.out.format(mapper.writeValueAsString(entry));
                // TODO: get rid of this ugly thing.
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
              System.out.println();
              System.exit(0);
              d = null;
              currentElement = null;
            } else {
              throw new RuntimeException(String.format("Parent of XML element %s is of type %d, not Element",
                  currentElement.getTagName(), parentNode.getNodeType()));
            }
          }
          break;

        // TODO: do we care about attributes or other XML structures?
      }
    }

    // If there are no more chemicals or if we cannot parse a chemical for some reason, we return null and the caller
    // is responsible for handling the logic correctly.
    return null;
  }

  /**
   * This function reads a given gzipped XML file, passes the xml event stream to a function to parse out the chemical,
   * and writes the chemical to the db.
   * @param file The input gzipped file that is being processed.
   * @return
   * @throws XMLStreamException
   * @throws IOException
   */
  public void openCompressedXMLFileAndWriteChemicals(File file)
      throws XMLStreamException, ParserConfigurationException, TransformerConfigurationException,
      TransformerException, XPathExpressionException, IOException {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = factory.createXMLEventReader(new GZIPInputStream(new FileInputStream(file)));
    Chemical templateChemical;
    while ((templateChemical = constructChemicalFromEventReader(eventReader)) != null) {
      writeChemicalToDB(templateChemical);
    }
  }

  /**
   * This function is the main driver of the class, which processes all the xml files of the pubchem dump.
   * @throws XMLStreamException
   * @throws IOException
   */
  private void run() throws XMLStreamException, ParserConfigurationException, TransformerConfigurationException,
      TransformerException, XPathExpressionException, IOException {
    int counter = 1;
    for (File file : this.filesToProcess) {
      LOGGER.info("Processing file %d of %d", counter, this.filesToProcess.size());
      LOGGER.info("File name is %s", file.getPath());
      openCompressedXMLFileAndWriteChemicals(file);
      counter++;
    }
  }

  /**
   * This function extracts gzipped xml files from a file directory.
   * @param dataDirectory The directory of interest.
   * @return A list of files of gzipped xml files.
   * @throws XMLStreamException
   * @throws IOException
   */
  private static List<File> extractFilesFromDirectory(String dataDirectory) throws XMLStreamException, IOException {
    File folder = new File(dataDirectory);

    if (!folder.exists()) {
      String msg = String.format("The folder %s does not exists", folder.getAbsolutePath());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }

    Pattern gzPattern = Pattern.compile("\\.gz$");

    List<File> result = Arrays.stream(folder.listFiles()).
        filter(f -> gzPattern.matcher(f.getName()).find()).collect(Collectors.toList());

    // Sort files lexicographically for installer stability.
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
    PubchemParser pubchemParser = new PubchemParser(db, extractFilesFromDirectory(dataDir));
    pubchemParser.init();
    pubchemParser.run();
  }
}
