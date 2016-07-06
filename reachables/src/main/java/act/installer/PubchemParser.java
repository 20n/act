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
import org.apache.commons.lang3.StringUtils;
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
  private MongoDB db;

  private static final String OPTION_DATA_DIRECTORY = "o";
  private static final String OPTION_DB = "i";
  private static final String EMPTY_STRING = "";
  private static final String GZIP_FILE_EXT = ".gz";

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class is used for parsing xml files and storing them in a db"
  }, " ");

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

  public enum ParentElement {
    PUBCHEM_COMPOUND_ID("PC-CompoundType_id_cid"),
    PUBCHEM_KEY("PC-Urn_label"),
    PUBCHEM_VALUE("PC-InfoData_value_sval"),
    PUBCHEM_MOLECULE_LABEL_NAME("PC-Urn_name"),
    PUBCHEM_COMPOUND("PC-Compound"),
    NULL_PARENT_ELEMENT("NULL");

    private String value;
    ParentElement(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public enum ChildElementValue {
    MOLECULE_NAME("IUPAC Name"),
    MOLECULE_NAME_CATEGORY("Molecule Name Category"),
    INCHI("InChI"),
    INCHI_KEY("InChIKey"),
    MOLECULAR_FORMULA("Molecular Formula"),
    SMILES("SMILES"),
    NULL_CHILD_ELEMENT("NULL");

    private String value;
    ChildElementValue(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private ParentElement lastParentElement;
  private ChildElementValue lastChildElement;
  private Map<ChildElementValue, String> childElementToTemplateString;
  private Set<String> setOfChildElements;
  private List<File> filesToProcess;

  public PubchemParser(MongoDB db, List<File> filesToProcess) {
    this.db = db;
    this.filesToProcess = filesToProcess;
    this.lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
    this.lastChildElement = ChildElementValue.NULL_CHILD_ELEMENT;
    this.childElementToTemplateString = new HashMap<>();
    this.setOfChildElements = new HashSet<>();
    constructChildElementToTemplateStringMapping();
  }

  /**
   * This function constructs enum Child Element values to template string mappings that store values in the XML file.
   */
  private void constructChildElementToTemplateStringMapping() {
    for (ChildElementValue value : ChildElementValue.values()) {
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
   * This function resets the internal variables for this class for each iteration on a to be parsed file.
   */
  private void resetPrivateVariables() {
    this.lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
    this.lastChildElement = ChildElementValue.NULL_CHILD_ELEMENT;

    for (Map.Entry<ChildElementValue, String> entry : this.childElementToTemplateString.entrySet()) {
      this.childElementToTemplateString.put(entry.getKey(), EMPTY_STRING);
    }
  }

  /**
   * This function compares an input string to a ParentElement enum and compares their values.
   * @param value Input string
   * @param parentElement Input Enum
   * @return True if they match
   */
  private Boolean compareStringToParentElement(String value, ParentElement parentElement) {
    return value.equalsIgnoreCase(parentElement.getValue());
  }

  /**
   * This function compares an input string to a ChildElementValue enum and compares their values.
   * @param value Input string
   * @param childElementValue Input Enum
   * @return True if they match
   */
  private Boolean compareStringToChildElementValue(String value, ChildElementValue childElementValue) {
    return value.equalsIgnoreCase(childElementValue.getValue());
  }

  /**
   * This function handles the start element of the XML file.
   * @param event XMLEvent to be parsed.
   */
  private void handleStartElementEvent(XMLEvent event) {
    StartElement startElement = event.asStartElement();
    String elementName = startElement.getName().getLocalPart();

    if (compareStringToParentElement(elementName, ParentElement.PUBCHEM_COMPOUND_ID)) {
      lastParentElement = ParentElement.PUBCHEM_COMPOUND_ID;
    } else if (compareStringToParentElement(elementName, ParentElement.PUBCHEM_KEY)) {
      lastParentElement = ParentElement.PUBCHEM_KEY;
    } else if (compareStringToParentElement(elementName, ParentElement.PUBCHEM_VALUE)) {
      lastParentElement = ParentElement.PUBCHEM_VALUE;
    } else if (compareStringToParentElement(elementName, ParentElement.PUBCHEM_MOLECULE_LABEL_NAME)) {
      lastParentElement = ParentElement.PUBCHEM_MOLECULE_LABEL_NAME;
    }
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
    lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
  }

  /**
   * This function handles pubchem key event data to be parsed.
   * @param event XMLEvent to be parsed.
   */
  private void handlePubchemKeyEvent(XMLEvent event) {
    Characters characters = event.asCharacters();
    String data = characters.getData();

    if (compareStringToChildElementValue(data, ChildElementValue.MOLECULE_NAME)) {
      lastChildElement = ChildElementValue.MOLECULE_NAME;
    } else if (compareStringToChildElementValue(data, ChildElementValue.INCHI)) {
      lastChildElement = ChildElementValue.INCHI;
    } else if (compareStringToChildElementValue(data, ChildElementValue.INCHI_KEY)) {
      lastChildElement = ChildElementValue.INCHI_KEY;
    } else if (compareStringToChildElementValue(data, ChildElementValue.MOLECULAR_FORMULA)) {
      lastChildElement = ChildElementValue.MOLECULAR_FORMULA;
    } else if (compareStringToChildElementValue(data, ChildElementValue.SMILES)) {
      lastChildElement = ChildElementValue.SMILES;
    }

    // Reset parent element after reading it
    lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
  }

  /**
   * This function handles all the child events that are of interest.
   * @param nextEvent The next event that is peeked at
   * @param value The current child element
   * @param templateChemical The template chemical that is being constructed
   */
  private void handleEvent(XMLEvent nextEvent, ChildElementValue value, Chemical templateChemical) {
    if (nextEvent != null) {
      if (nextEvent.getEventType() != XMLStreamConstants.CHARACTERS) {

        String result = childElementToTemplateString.get(value);

        if (value.getValue().equalsIgnoreCase(ChildElementValue.MOLECULE_NAME.getValue())) {
          templateChemical.addNames(childElementToTemplateString.get(ChildElementValue.MOLECULE_NAME_CATEGORY),
              new String[] { result });
          childElementToTemplateString.put(ChildElementValue.MOLECULE_NAME_CATEGORY, EMPTY_STRING);
        } else if (value.getValue().equalsIgnoreCase(ChildElementValue.INCHI.getValue())) {
          templateChemical.setInchi(result);
        } else if (value.getValue().equalsIgnoreCase(ChildElementValue.INCHI_KEY.getValue())) {
          templateChemical.setInchiKey(result);
        } else if (value.getValue().equalsIgnoreCase(ChildElementValue.SMILES.getValue())) {
          templateChemical.setSmiles(result);
        } else if (value.getValue().equalsIgnoreCase(ChildElementValue.MOLECULE_NAME_CATEGORY.getValue())) {
          lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
          return;
        }

        childElementToTemplateString.put(value, EMPTY_STRING);
        lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
        lastChildElement = ChildElementValue.NULL_CHILD_ELEMENT;
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
    resetPrivateVariables();

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = factory.createXMLEventReader(new GZIPInputStream(new FileInputStream(file)));

    List<Chemical> result = new ArrayList<>();
    Chemical templateChemical = new Chemical(-1L);

    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()) {
        case XMLStreamConstants.START_ELEMENT:
          handleStartElementEvent(event);
          break;

        case XMLStreamConstants.CHARACTERS:
          Characters characters = event.asCharacters();
          if (compareStringToParentElement(lastParentElement.getValue(), ParentElement.PUBCHEM_COMPOUND_ID)) {
            handlePubchemIdEvent(event, templateChemical);
          } else if (compareStringToParentElement(lastParentElement.getValue(), ParentElement.PUBCHEM_KEY)) {
            handlePubchemKeyEvent(event);
          } else if (lastParentElement.getValue().equalsIgnoreCase(ParentElement.PUBCHEM_VALUE.getValue())) {
            if (setOfChildElements.contains(lastChildElement.getValue().toLowerCase())) {
              String modifiedData = this.childElementToTemplateString.get(lastChildElement) + characters.getData();
              this.childElementToTemplateString.put(lastChildElement, modifiedData);
              handleEvent(eventReader.peek(), lastChildElement, templateChemical);
            } else {
              lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
              lastChildElement = ChildElementValue.NULL_CHILD_ELEMENT;
            }
          } else if (lastParentElement.getValue().equalsIgnoreCase(ParentElement.PUBCHEM_MOLECULE_LABEL_NAME.getValue())) {
            if (lastChildElement.getValue().equalsIgnoreCase(ChildElementValue.MOLECULE_NAME.getValue())) {
              String categoryName = childElementToTemplateString.get(ChildElementValue.MOLECULE_NAME_CATEGORY) +
                  characters.getData();
              this.childElementToTemplateString.put(ChildElementValue.MOLECULE_NAME_CATEGORY, categoryName);
              handleEvent(eventReader.peek(), ChildElementValue.MOLECULE_NAME_CATEGORY, templateChemical);
            } else {
              lastParentElement = ParentElement.NULL_PARENT_ELEMENT;
            }
          }
          break;
        case XMLStreamConstants.END_ELEMENT:
          EndElement endElement = event.asEndElement();
          if(endElement.getName().getLocalPart().equalsIgnoreCase(ParentElement.PUBCHEM_COMPOUND.getValue())) {
            result.add(templateChemical);
            templateChemical = new Chemical(-1L);
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
   * This function extractes gzipped xml files from a file directory.
   * @param dataDirectory The directory of interest.
   * @return A list of files of gzipped xml files.
   * @throws XMLStreamException
   * @throws IOException
   */
  private static List<File> extractFilesFromDirectory(String dataDirectory) throws XMLStreamException, IOException {
    File folder = new File(dataDirectory);
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
