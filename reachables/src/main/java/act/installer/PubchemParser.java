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
import java.util.List;
import java.util.zip.GZIPInputStream;

public class PubchemParser {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemParser.class);
  private static final String OPTION_DATA_DIRECTORY = "o";
  private static final String OPTION_DB = "i";
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

  /**
   * These enums are for XML elements that are interesting for our parser. NOTE: element events come after attribute
   * events.
   */
  private enum PubchemElementEvent {
    INCHI("InChI"),
    INCHI_KEY("InChIKey"),
    MOLECULE_NAME("IUPAC Name"),
    SMILES("SMILES"),
    MOLECULE_FORMULA("Molecular Formula"),
    NULL_EVENT("");

    private final String value;
    PubchemElementEvent(String name) { this.value = name; }
    public String getValue() { return value; }
  }

  /**
   * These events are for XML attributes that are interesting for our parser. NOTE: attribute events come before element
   * events
   */
  private enum PubchemAttributeEvent {
    PUBCHEM_ID("PC-CompoundType_id_cid"),
    PUBCHEM_LABEL("PC-Urn_label"),
    PUBCHEM_NAME("PC-Urn_name"),
    PUBCHEM_VALUE("PC-InfoData_value_sval"),
    PUBCHEM_COMPOUND("PC-Count"),
    NULL_EVENT("");

    private final String name;
    PubchemAttributeEvent(String name) { this.name = name; }
    public String getValue() { return name; }
  }

  // Instance variables
  private MongoDB db;
  private String dataDirectory;

  public PubchemParser(MongoDB db, String dataDirectory) {
    this.db = db;
    this.dataDirectory = dataDirectory;
  }

  /**
   * This function writes the list of chemicals to the db.
   * @param chemicals A list of in-memory chemical objects that are written to the db.
   */
  private void writeChemicalRecordsToDB(List<Chemical> chemicals) {
    for (Chemical chemical : chemicals) {
      Long id = db.getNextAvailableChemicalDBid();

      // We do not check for uniqueness before we submit the chemical because the submitToActChemicalDB function provides
      // use with additional functionality, like providing metadata on the two merged chemicals if their inchis are the same
      // instead of just ignoring the chemical in a basic uniqueness check.
      db.submitToActChemicalDB(chemical, id);
    }
  }

  private List<Chemical> parseCompressedXMLFileAndConstructChemicals(File file) throws XMLStreamException, IOException {
    PubchemAttributeEvent lastAttributeEvent = PubchemAttributeEvent.NULL_EVENT;
    PubchemElementEvent lastElementEvent = PubchemElementEvent.NULL_EVENT;

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = factory.createXMLEventReader(new GZIPInputStream(new FileInputStream(file)));

    List<Chemical> result = new ArrayList<>();
    Chemical templateChemical = new Chemical(-1L);

    String molecularCategory = "";
    String templateData = "";

    while (eventReader.hasNext()) {
      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()){
        case XMLStreamConstants.START_ELEMENT:
          StartElement startElement = event.asStartElement();
          String nameOfElement = startElement.getName().getLocalPart();
          if (nameOfElement.equalsIgnoreCase(PubchemAttributeEvent.PUBCHEM_ID.getValue())) {
            lastAttributeEvent = PubchemAttributeEvent.PUBCHEM_ID;
          } else if (nameOfElement.equalsIgnoreCase(PubchemAttributeEvent.PUBCHEM_LABEL.getValue())) {
            lastAttributeEvent = PubchemAttributeEvent.PUBCHEM_LABEL;
          } else if (nameOfElement.equalsIgnoreCase(PubchemAttributeEvent.PUBCHEM_VALUE.getValue())) {
            lastAttributeEvent = PubchemAttributeEvent.PUBCHEM_VALUE;
          } else if (nameOfElement.equalsIgnoreCase(PubchemAttributeEvent.PUBCHEM_NAME.getValue())) {
            lastAttributeEvent = PubchemAttributeEvent.PUBCHEM_NAME;
          }
          break;
        case XMLStreamConstants.CHARACTERS:
          Characters characters = event.asCharacters();
          if (lastAttributeEvent.getValue().equals(PubchemAttributeEvent.PUBCHEM_ID.getValue())) {
            Long pubchemId = Long.parseLong(characters.getData());
            templateChemical.setPubchem(pubchemId);
            lastAttributeEvent = PubchemAttributeEvent.NULL_EVENT;
          } else if (lastAttributeEvent.getValue().equals(PubchemAttributeEvent.PUBCHEM_LABEL.getValue())) {

            String element = characters.getData();
            if (element.equalsIgnoreCase(PubchemElementEvent.MOLECULE_NAME.getValue())) {
              lastElementEvent = PubchemElementEvent.MOLECULE_NAME;
            } else if (element.equalsIgnoreCase(PubchemElementEvent.INCHI.getValue())) {
              lastElementEvent = PubchemElementEvent.INCHI;
            } else if (element.equalsIgnoreCase(PubchemElementEvent.INCHI_KEY.getValue())) {
              lastElementEvent = PubchemElementEvent.INCHI_KEY;
            } else if (element.equalsIgnoreCase(PubchemElementEvent.MOLECULE_FORMULA.getValue())) {
              lastElementEvent = PubchemElementEvent.MOLECULE_FORMULA;
            } else if (element.equalsIgnoreCase(PubchemElementEvent.SMILES.getValue())) {
              lastElementEvent = PubchemElementEvent.SMILES;
            }

            // Reset the attribute event
            lastAttributeEvent = PubchemAttributeEvent.NULL_EVENT;
          } else if (lastAttributeEvent.getValue().equals(PubchemAttributeEvent.PUBCHEM_VALUE.getValue())) {

            String element = characters.getData();
            if (lastElementEvent.value.equalsIgnoreCase(PubchemElementEvent.MOLECULE_NAME.getValue()) ||
                lastElementEvent.value.equalsIgnoreCase(PubchemElementEvent.INCHI.getValue()) ||
                lastElementEvent.value.equalsIgnoreCase(PubchemElementEvent.INCHI_KEY.getValue()) ||
                lastElementEvent.value.equalsIgnoreCase(PubchemElementEvent.MOLECULE_FORMULA.getValue()) ||
                lastElementEvent.value.equalsIgnoreCase(PubchemElementEvent.SMILES.getValue())) {

              templateData = templateData + element;
              XMLEvent nextEvent = eventReader.peek();
              if (nextEvent != null) {
                if (nextEvent.getEventType() != XMLStreamConstants.CHARACTERS) {
                  templateChemical.addNames(molecularCategory, new String[] { templateData });
                  templateData = "";
                }
              }
            }

            // reset attribute event
            lastAttributeEvent = PubchemAttributeEvent.NULL_EVENT;

          } else if (lastAttributeEvent.getValue().equals(PubchemAttributeEvent.PUBCHEM_NAME.getValue())) {

            if (lastElementEvent.value.equalsIgnoreCase(PubchemElementEvent.MOLECULE_NAME.getValue())) {
              String element = characters.getData();
              templateData = templateData + element;
              XMLEvent nextEvent = eventReader.peek();
              if (nextEvent != null) {
                if (nextEvent.getEventType() != XMLStreamConstants.CHARACTERS) {
                  molecularCategory = templateData;
                  templateData = "";

                  // reset attribute event
                  lastAttributeEvent = PubchemAttributeEvent.NULL_EVENT;
                }
              }
            } else {
              // reset attribute event
              lastAttributeEvent = PubchemAttributeEvent.NULL_EVENT;
            }
          }
          break;
        case XMLStreamConstants.END_ELEMENT:
          EndElement endElement = event.asEndElement();
          if(endElement.getName().getLocalPart().equalsIgnoreCase(PubchemAttributeEvent.PUBCHEM_COMPOUND.getValue())) {
            result.add(templateChemical);
            templateChemical = new Chemical(-1L);
          }
          break;
      }
    }

    return result;
  }

  private void run() throws XMLStreamException, IOException {
    File folder = new File(this.dataDirectory);
    File[] listOfFiles = folder.listFiles();

    int counter = 1;
    for (File file : listOfFiles) {
      LOGGER.info("Processing file number %d out of %d", counter, listOfFiles.length);
      List<Chemical> chemicals = parseCompressedXMLFileAndConstructChemicals(file);
      LOGGER.info("Number of chemicals extracted are %d", chemicals.size());
      writeChemicalRecordsToDB(chemicals);
      counter++;
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
    PubchemParser pubchemParser = new PubchemParser(db, dataDir);
    pubchemParser.run();
  }
}
