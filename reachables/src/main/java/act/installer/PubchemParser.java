package act.installer;

import act.installer.bing.BingSearchRanker;
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
import java.util.ArrayList;
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
  private static final String EMPTY_STRING = "";
  private static final String GZIP_FILE_EXT = ".gz";
  private static final Long FAKE_ID = -1L;

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
    NULL_PARENT_ELEMENT("NULL");

    private String value;
    ResourceName(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public enum ResourceValue {
    MOLECULE_NAME("IUPAC Name"),
    MOLECULE_NAME_CATEGORY("Molecule Name Category"),
    INCHI("InChI"),
    INCHI_KEY("InChIKey"),
    MOLECULAR_FORMULA("Molecular Formula"),
    SMILES("SMILES"),
    NULL_CHILD_ELEMENT("NULL");

    private String value;
    ResourceValue(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private ResourceName lastResourceName;
  private ResourceValue lastResourceValue;
  private Map<ResourceValue, String> childElementToTemplateString;
  private Set<String> setOfChildElements;
  private List<File> filesToProcess;
  private MongoDB db;

  public PubchemParser(MongoDB db, List<File> filesToProcess) {
    this.db = db;
    this.filesToProcess = filesToProcess;
    this.lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
    this.lastResourceValue = ResourceValue.NULL_CHILD_ELEMENT;
    this.childElementToTemplateString = new HashMap<>();
    this.setOfChildElements = new HashSet<>();
    this.constructChildElementToTemplateStringMapping();
  }

  /**
   * This function constructs enum Child Element values to template string mappings that store values in the XML file.
   */
  private void constructChildElementToTemplateStringMapping() {
    for (ResourceValue value : ResourceValue.values()) {
      this.childElementToTemplateString.put(value, EMPTY_STRING);
      setOfChildElements.add(value.getValue().toLowerCase());
    }
  }

  /**
   * This function writes the chemical records to the DB.
   * @param chemicals List of chemicals to be written to the DB.
   */
  private void writeChemicalRecordsToDB(List<Chemical> chemicals) {
    for (Chemical chemical : chemicals) {
      Long id = db.getNextAvailableChemicalDBid();
      db.submitToActChemicalDB(chemical, id);
    }
  }

  /**
   * This function resets the internal variables for this class for each iteration of parsing a file.
   */
  private void resetInstanceVariables() {
    this.lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
    this.lastResourceValue = ResourceValue.NULL_CHILD_ELEMENT;

    for (ResourceValue elementValue : this.childElementToTemplateString.keySet()) {
      this.childElementToTemplateString.put(elementValue, EMPTY_STRING);
    }
  }

  /**
   * This function compares an input string to a ParentElement enum and compares their values.
   * @param value Input string
   * @param resourceName Input Enum
   * @return True if they match
   */
  private Boolean compareStringToResourceName(String value, ResourceName resourceName) {
    return value.equalsIgnoreCase(resourceName.getValue());
  }

  /**
   * This function compares an input string to a ChildElementValue enum and compares their values.
   * @param value Input string
   * @param resourceValue Input Enum
   * @return True if they match
   */
  private Boolean compareStringToResourceValue(String value, ResourceValue resourceValue) {
    return value.equalsIgnoreCase(resourceValue.getValue());
  }

  /**
   * This function handles the start element of the XML file.
   * @param event XMLEvent to be parsed.
   */
  private void handleStartElementEvent(XMLEvent event) {
    StartElement startElement = event.asStartElement();
    String elementName = startElement.getName().getLocalPart();

    if (compareStringToResourceName(elementName, ResourceName.PUBCHEM_COMPOUND_ID)) {
      lastResourceName = ResourceName.PUBCHEM_COMPOUND_ID;
    } else if (compareStringToResourceName(elementName, ResourceName.PUBCHEM_KEY)) {
      lastResourceName = ResourceName.PUBCHEM_KEY;
    } else if (compareStringToResourceName(elementName, ResourceName.PUBCHEM_VALUE)) {
      lastResourceName = ResourceName.PUBCHEM_VALUE;
    } else if (compareStringToResourceName(elementName, ResourceName.PUBCHEM_MOLECULE_LABEL_NAME)) {
      lastResourceName = ResourceName.PUBCHEM_MOLECULE_LABEL_NAME;
    }

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

    // Reset the last parent element after reading it (the reading happened in the caller).
    lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
  }

  /**
   * This function handles a "key" event, described in the terminology section above.
   * @param event XMLEvent to be parsed.
   */
  private void handlePubchemKeyEvent(XMLEvent event) {
    Characters characters = event.asCharacters();
    String data = characters.getData();

    if (compareStringToResourceValue(data, ResourceValue.MOLECULE_NAME)) {
      lastResourceValue = ResourceValue.MOLECULE_NAME;
    } else if (compareStringToResourceValue(data, ResourceValue.INCHI)) {
      lastResourceValue = ResourceValue.INCHI;
    } else if (compareStringToResourceValue(data, ResourceValue.INCHI_KEY)) {
      lastResourceValue = ResourceValue.INCHI_KEY;
    } else if (compareStringToResourceValue(data, ResourceValue.MOLECULAR_FORMULA)) {
      lastResourceValue = ResourceValue.MOLECULAR_FORMULA;
    } else if (compareStringToResourceValue(data, ResourceValue.SMILES)) {
      lastResourceValue = ResourceValue.SMILES;
    }

    // Reset the last parent element after reading it (the reading happened in the caller).
    lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
  }

  /**
   * This function is called right after we read a child element of interest. Since the XML event streams at not atomic,
   * ie. there are events like InchiEvent1(Inchi=1S) and InchiEvent2(/C12H17FO/c1-12(2,3)10) this, we have to peek at the
   * subsequent event to see if it is the of the same type as the current one. If it not, we know that we have a complete
   * inchi/another data type, so we store the value in the template chemical and flush it.
   * @param nextEvent The next event that is peeked at
   * @param resourceValue The current child element
   * @param templateChemical The template chemical that is being constructed
   */
  private void handleNextChildElementEvent(XMLEvent nextEvent, ResourceValue resourceValue, Chemical templateChemical) {
    if (nextEvent != null) {
      if (nextEvent.getEventType() != XMLStreamConstants.CHARACTERS) {
        String result = childElementToTemplateString.get(resourceValue);
        String valueString  = resourceValue.getValue();

        if (compareStringToResourceValue(valueString, ResourceValue.MOLECULE_NAME)) {
          templateChemical.addNames(childElementToTemplateString.get(ResourceValue.MOLECULE_NAME_CATEGORY),
              new String[] { result });

          // Comment label 42: This is where we finally flush the MOLECULE_NAME_CATEGORY value, once we store the association
          // between the key and value.
          childElementToTemplateString.put(ResourceValue.MOLECULE_NAME_CATEGORY, EMPTY_STRING);
        } else if (compareStringToResourceValue(valueString, ResourceValue.INCHI)) {
          templateChemical.setInchi(result);
        } else if (compareStringToResourceValue(valueString, ResourceValue.INCHI_KEY)) {
          templateChemical.setInchiKey(result);
        } else if (compareStringToResourceValue(valueString, ResourceValue.SMILES)) {
          templateChemical.setSmiles(result);
        } else if (compareStringToResourceValue(valueString, ResourceValue.MOLECULE_NAME_CATEGORY)) {

          // We handle the MOLECULE_NAME_CATEGORY differently. MOLECULE_NAME_CATEGORY is used to store the key of a
          // molecule name ie "Preferred" and "Systematic" in {"Preferred", <name1>}, {"Systematic", <name2>} that are
          // stored in the chemical object. In the XML file, it's event is triggered just before the name1 and name2
          // events are triggered. Therefore, we do not want to flush it's value just yet since we need the key value
          // to store the key->name for the molecule names. To see where we finally flush the value, look at the comment
          // labelled 42 above.
          lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
          return;
        }

        // Flush all of the previously recorded events and data value in the map.
        childElementToTemplateString.put(resourceValue, EMPTY_STRING);
        lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
        lastResourceValue = ResourceValue.NULL_CHILD_ELEMENT;
      }
    }
  }

  /**
   * This function reads a given gzipped XML file, listens to interesting events and passes those events to a generic
   * event handlers for processing.
   * @param file The input gzipped file that is being processed.
   * @return A list of chemicals extracted from the xml file.
   * @throws XMLStreamException
   * @throws IOException
   */
  public List<Chemical> parseCompressedXMLFileAndConstructChemicals(File file) throws XMLStreamException, IOException {
    resetInstanceVariables();

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = factory.createXMLEventReader(new GZIPInputStream(new FileInputStream(file)));

    List<Chemical> result = new ArrayList<>();
    Chemical templateChemical = new Chemical(FAKE_ID);

    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          handleStartElementEvent(event);
          break;

        case XMLStreamConstants.CHARACTERS:
          Characters characters = event.asCharacters();

          if (compareStringToResourceName(lastResourceName.getValue(), ResourceName.PUBCHEM_COMPOUND_ID)) {
            handlePubchemIdEvent(event, templateChemical);
          } else if (compareStringToResourceName(lastResourceName.getValue(), ResourceName.PUBCHEM_KEY)) {
            handlePubchemKeyEvent(event);
          } else if (compareStringToResourceName(lastResourceName.getValue(), ResourceName.PUBCHEM_VALUE)) {
            // We only handle events that from elements that we are interested in, which is stored in setOfChildElements.
            if (setOfChildElements.contains(lastResourceValue.getValue().toLowerCase())) {
              String combinedData = this.childElementToTemplateString.get(lastResourceValue) + characters.getData();
              this.childElementToTemplateString.put(lastResourceValue, combinedData);
              handleNextChildElementEvent(eventReader.peek(), lastResourceValue, templateChemical);
            } else {
              // Reset about name and value if the event is unrelated to our parser.
              lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
              lastResourceValue = ResourceValue.NULL_CHILD_ELEMENT;
            }
          } else if (compareStringToResourceName(lastResourceName.getValue(), ResourceName.PUBCHEM_MOLECULE_LABEL_NAME)) {
            if (compareStringToResourceValue(lastResourceValue.getValue(), ResourceValue.MOLECULE_NAME)) {
              String categoryName = childElementToTemplateString.get(ResourceValue.MOLECULE_NAME_CATEGORY) + characters.getData();
              this.childElementToTemplateString.put(ResourceValue.MOLECULE_NAME_CATEGORY, categoryName);
              handleNextChildElementEvent(eventReader.peek(), ResourceValue.MOLECULE_NAME_CATEGORY, templateChemical);
            } else {
              // Reset only name is since value can still be accumulating data.
              lastResourceName = ResourceName.NULL_PARENT_ELEMENT;
            }
          }
          break;

        case XMLStreamConstants.END_ELEMENT:
          EndElement endElement = event.asEndElement();
          if(endElement.getName().getLocalPart().equalsIgnoreCase(ResourceName.PUBCHEM_COMPOUND.getValue())) {
            result.add(templateChemical);
            templateChemical = new Chemical(FAKE_ID);
          }
          break;
      }
    }

    return result;
  }

  /**
   * This function is the main driver of the class, which processes all the xml files of the pubchem dump.
   * @throws XMLStreamException
   * @throws IOException
   */
  private void run() throws XMLStreamException, IOException {
    int counter = 1;
    for (File file : this.filesToProcess) {
      LOGGER.info("Processing file number %d out of %d", counter, this.filesToProcess.size());
      List<Chemical> chemicals = parseCompressedXMLFileAndConstructChemicals(file);
      LOGGER.info("Number of chemicals extracted are %d", chemicals.size());
      writeChemicalRecordsToDB(chemicals);
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
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(BingSearchRanker.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(BingSearchRanker.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String dataDir = cl.getOptionValue(OPTION_DATA_DIRECTORY);
    String dbName = cl.getOptionValue(OPTION_DB);

    MongoDB db = new MongoDB("localhost", 27017, dbName);
    PubchemParser pubchemParser = new PubchemParser(db, extractFilesFromDirectory(dataDir));
    pubchemParser.run();
  }
}
