package act.installer;

import act.installer.brenda.FromBrendaDB;
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
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class PubchemParser {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemParser.class);
  private static final String OPTION_DATA_DIRECTORY = "o";
  private static final String OPTION_DB = "i";
  private static final String GZIP_FILE_EXT = ".gz";
  private static final Long FAKE_ID = -1L;
  private static final String EMPTY_STRING = "";
  public static final Charset UTF8 = Charset.forName("utf-8");

  private static Map<String, ResourceValue> STRING_RESOURCE_VALUE_MAP;

  static {
    STRING_RESOURCE_VALUE_MAP = ResourceValue.constructStringToResourceValue();
  }

  private static Map<String, ResourceName> STRING_RESOURCE_NAME_MAP;

  static {
    STRING_RESOURCE_NAME_MAP = ResourceName.constructStringToResourceName();
  }

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

  public enum ResourceName {
    PUBCHEM_COMPOUND_ID("PC-CompoundType_id_cid"),
    PUBCHEM_KEY("PC-Urn_label"),
    PUBCHEM_VALUE("PC-InfoData_value_sval"),
    PUBCHEM_MOLECULE_LABEL_NAME("PC-Urn_name"),
    PUBCHEM_COMPOUND("PC-Compound"),
    NULL_RESOURCE_NAME("NULL");

    private String value;
    ResourceName(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static Map<String, ResourceName> constructStringToResourceName() {
      Map<String, ResourceName> result = new HashMap<>();
      for (ResourceName value : ResourceName.values()) {
        result.put(value.getValue(), value);
      }
      return result;
    }
  }

  public enum ResourceValue {
    MOLECULE_NAME("IUPAC Name"),
    MOLECULE_NAME_CATEGORY("Molecule Name Category"),
    INCHI("InChI"),
    INCHI_KEY("InChIKey"),
    MOLECULAR_FORMULA("Molecular Formula"),
    SMILES("SMILES"),
    NULL_RESOURCE_VALUE("NULL");

    private String value;
    ResourceValue(String value) { this.value = value; }

    public String getValue() {
      return value;
    }

    public static Map<String, ResourceValue> constructStringToResourceValue() {
      Map<String, ResourceValue> result = new HashMap<>();
      for (ResourceValue value : ResourceValue.values()) {
        result.put(value.getValue(), value);
      }
      return result;
    }
  }

  private ResourceName lastResourceName;
  private ResourceValue lastResourceValue;
  private Map<ResourceValue, StringBuilder> resourceValueToTemplateString;
  private List<File> filesToProcess;
  private MongoDB db;
  private PubchemDB pubchemDB;

  public PubchemParser(MongoDB db, List<File> filesToProcess) {
    this.db = db;
    this.filesToProcess = filesToProcess;
    this.lastResourceName = ResourceName.NULL_RESOURCE_NAME;
    this.lastResourceValue = ResourceValue.NULL_RESOURCE_VALUE;
    this.resourceValueToTemplateString = new HashMap<>();
    this.constructResourceValueToTemplateStringMapping();
  }

  private void initializeRocksDB() throws RocksDBException {
    File pathToIndex = new File("Pubchem");
    RocksDB rocksDB = null; // Not auto-closable.
    try {
      org.rocksdb.Options options = new org.rocksdb.Options().setCreateIfMissing(true);
      System.out.println("Opening index at " + pathToIndex.getAbsolutePath());
      rocksDB = RocksDB.open(options, pathToIndex.getAbsolutePath());

      ColumnFamilyHandle cfh = rocksDB.createColumnFamily(new ColumnFamilyDescriptor("Pubchem".getBytes(UTF8)));
      this.pubchemDB = new PubchemDB(cfh, rocksDB);
      rocksDB.flush(new FlushOptions());
    } finally {
      if (rocksDB != null) {
        rocksDB.close();
      }
    }
  }

  /**
   * This function constructs enum Resource values to template string mappings that store values in the XML file.
   */
  private void constructResourceValueToTemplateStringMapping() {
    for (ResourceValue value : ResourceValue.values()) {
      this.resourceValueToTemplateString.put(value, new StringBuilder(EMPTY_STRING));
    }
  }

  /**
   * This function writes a chemical record to the DB.
   * @param chemical Chemical to be written to the DB.
   */
  private void writeChemicalToDB(Chemical chemical) throws IOException, RocksDBException, ClassNotFoundException {
//    Long id = db.getNextAvailableChemicalDBid();
//    db.submitToActChemicalDB(chemical, id);
    this.pubchemDB.createKeysAndWrite(chemical);
  }

  /**
   * This function resets the internal variables for this class for each iteration of parsing a file.
   */
  private void resetInstanceVariables() {
    this.lastResourceName = ResourceName.NULL_RESOURCE_NAME;
    this.lastResourceValue = ResourceValue.NULL_RESOURCE_VALUE;

    for (ResourceValue elementValue : this.resourceValueToTemplateString.keySet()) {
      this.resourceValueToTemplateString.put(elementValue, new StringBuilder(EMPTY_STRING));
    }
  }

  /**
   * This function handles the start element of the XML file.
   * @param event XMLEvent to be parsed.
   */
  private void handleStartElementEvent(XMLEvent event) {
    StartElement startElement = event.asStartElement();
    String elementName = startElement.getName().getLocalPart();
    ResourceName resourceName = STRING_RESOURCE_NAME_MAP.get(elementName);

    if (resourceName == null) {
      return;
    }

    lastResourceName = resourceName;
    // We ignore all other start element events that we are not interested in.
  }

  /**
   * This function handles the pubchem id event to be parsed.
   * @param event XMLEvent to be parsed.
   * @param templateChemical The template chemical who's internal data is being constructed.
   */
  private void handlePubchemIdEvent(XMLEvent event, Chemical templateChemical) {
    Characters characters = event.asCharacters();
    Long pubchemId = Long.parseLong(characters.getData());
    templateChemical.setPubchem(pubchemId);

    // Reset the last resource name element after reading it (the reading happened in the caller).
    lastResourceName = ResourceName.NULL_RESOURCE_NAME;
  }

  /**
   * This function handles a "key" event, described in the terminology section above.
   * @param event XMLEvent to be parsed.
   */
  private void handlePubchemKeyEvent(XMLEvent event) {
    Characters characters = event.asCharacters();
    String data = characters.getData();
    ResourceValue resourceValue = STRING_RESOURCE_VALUE_MAP.get(data);

    if (resourceValue != null) {
      lastResourceValue = resourceValue;
    }

    // Reset the last resource name element after reading it (the reading happened in the caller).
    lastResourceName = ResourceName.NULL_RESOURCE_NAME;
  }

  /**
   * This function is called right after we read a resource value element of interest. Since the XML event streams are not atomic,
   * ie. there are events like InchiEvent1(Inchi=1S) and InchiEvent2(/C12H17FO/c1-12(2,3)10), we have to peek at the
   * subsequent event to see if it is of the same type as the current one. If it is not, we know that we have a complete
   * inchi/another data type, so we store the value in the template chemical and flush it.
   * @param nextEvent The next event that is peeked at
   * @param resourceValue The current resource value element
   * @param templateChemical The template chemical that is being constructed
   */
  private void handleNextResourceValueEvent(XMLEvent nextEvent, ResourceValue resourceValue, Chemical templateChemical) {

    if (nextEvent == null || nextEvent.getEventType() == XMLStreamConstants.CHARACTERS) {
      return;
    }

    String result = resourceValueToTemplateString.get(resourceValue).toString();

    if (resourceValue == ResourceValue.MOLECULE_NAME) {
      templateChemical.addNames(resourceValueToTemplateString.get(ResourceValue.MOLECULE_NAME_CATEGORY).toString(),
          new String[] { result });

      // Comment label 42: This is where we finally flush the MOLECULE_NAME_CATEGORY value, once we store the association
      // between the key and value.
      resourceValueToTemplateString.put(ResourceValue.MOLECULE_NAME_CATEGORY, new StringBuilder(EMPTY_STRING));
    } else if (resourceValue == ResourceValue.INCHI) {
      templateChemical.setInchi(result);
    } else if (resourceValue == ResourceValue.INCHI_KEY) {
      templateChemical.setInchiKey(result);
    } else if (resourceValue == ResourceValue.SMILES) {
      templateChemical.setSmiles(result);
    } else if (resourceValue == ResourceValue.MOLECULE_NAME_CATEGORY) {

      // We handle the MOLECULE_NAME_CATEGORY differently. MOLECULE_NAME_CATEGORY is used to store the key of a
      // molecule name ie "Preferred" and "Systematic" in {"Preferred", <name1>}, {"Systematic", <name2>} that are
      // stored in the chemical object. In the XML file, it's event is triggered just before the name1 and name2
      // events are triggered. Therefore, we do not want to flush it's value just yet since we need the key value
      // to store the key->name for the molecule names. To see where we finally flush the value, look at the comment
      // labelled 42 above.
      lastResourceName = ResourceName.NULL_RESOURCE_NAME;
      return;
    }

    // Flush all of the previously recorded events and data value in the map.
    resourceValueToTemplateString.put(resourceValue, new StringBuilder(EMPTY_STRING));
    lastResourceName = ResourceName.NULL_RESOURCE_NAME;
    lastResourceValue = ResourceValue.NULL_RESOURCE_VALUE;
  }

  /**
   * This function parses the xml event stream and constructs a template chemical object.
   * @param eventReader The xml event reader we are parsing the XML from
   * @return The constructed chemical
   * @throws XMLStreamException
   */
  public Chemical constructChemicalFromEventReader(XMLEventReader eventReader) throws XMLStreamException {
    Chemical templateChemical = new Chemical(FAKE_ID);

    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          handleStartElementEvent(event);
          break;

        case XMLStreamConstants.CHARACTERS:

          // Casting the event as characters is safe here since the event is a Character event. Same for all the downstream
          // functions that call asCharacters in this code block.
          Characters characters = event.asCharacters();
          String data = characters.getData();

          if (lastResourceName == ResourceName.PUBCHEM_COMPOUND_ID) {
            handlePubchemIdEvent(event, templateChemical);
          } else if (lastResourceName == ResourceName.PUBCHEM_KEY) {
            handlePubchemKeyEvent(event);
          } else if (lastResourceName == ResourceName.PUBCHEM_VALUE) {
            // We only handle events that are from elements that we are interested in, which is stored in SET_OF_RESOURCE_VALUES_EXCEPT_NULL_EVENT.
            if (lastResourceValue != null && lastResourceValue != ResourceValue.NULL_RESOURCE_VALUE) {
              // We first append the results to our accumulator, followed up handling the next event if it is not the same
              // and this one.
              this.resourceValueToTemplateString.get(lastResourceValue).append(data);
              handleNextResourceValueEvent(eventReader.peek(), lastResourceValue, templateChemical);
            } else {
              // Reset both name and value if the event is unrelated to our parser.
              lastResourceName = ResourceName.NULL_RESOURCE_NAME;
              lastResourceValue = ResourceValue.NULL_RESOURCE_VALUE;
            }
          } else if (lastResourceName == ResourceName.PUBCHEM_MOLECULE_LABEL_NAME) {
            if (lastResourceValue == ResourceValue.MOLECULE_NAME) {
              resourceValueToTemplateString.get(ResourceValue.MOLECULE_NAME_CATEGORY).append(data);
              handleNextResourceValueEvent(eventReader.peek(), ResourceValue.MOLECULE_NAME_CATEGORY, templateChemical);
            } else {
              // Reset only name since value can still be accumulating data.
              lastResourceName = ResourceName.NULL_RESOURCE_NAME;
            }
          }
          break;

        case XMLStreamConstants.END_ELEMENT:
          EndElement endElement = event.asEndElement();
          String value = endElement.getName().getLocalPart();
          ResourceName resourceName = STRING_RESOURCE_NAME_MAP.get(value);
          if (resourceName != null && resourceName == ResourceName.PUBCHEM_COMPOUND) {
            return templateChemical;
          }
          break;
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
  public void openCompressedXMLFileAndWriteChemicals(File file) throws XMLStreamException, IOException, RocksDBException, ClassNotFoundException {
    resetInstanceVariables();
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
  private void run() throws XMLStreamException, IOException, RocksDBException, ClassNotFoundException {
    int counter = 1;
    for (File file : this.filesToProcess) {
      LOGGER.info("Processing file number %d out of %d", counter, this.filesToProcess.size());
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
      LOGGER.error("The folder %s does not exists", folder.getAbsolutePath());
      System.exit(1);
    }

    File[] listOfFiles = folder.listFiles();
    List<File> result = new ArrayList<>();

    for (File file : listOfFiles) {
      if (file.getAbsolutePath().contains(GZIP_FILE_EXT)) {
        result.add(file);
      }
    }

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
    pubchemParser.initializeRocksDB();
    pubchemParser.run();
  }
}
