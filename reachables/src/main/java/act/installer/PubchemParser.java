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
  private MongoDB db;
  private String dataDirectory;

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

  public PubchemParser(MongoDB db, String dataDirectory) {
    this.db = db;
    this.dataDirectory = dataDirectory;
  }

  private void writeChemicalRecordsToDB(List<Chemical> chemicals) {
    for (Chemical chemical : chemicals) {
      Long id = db.getNextAvailableChemicalDBid();
      db.submitToActChemicalDB(chemical, id);
    }
  }

  private List<Chemical> parseCompressedXMLFileAndConstructChemicals(File file) throws XMLStreamException, IOException {
    Boolean cid = false;
    Boolean key = false;
    Boolean value = false;
    Boolean iupacName = false;
    Boolean inchi = false;
    Boolean inchiKey = false;
    Boolean molFormula = false;
    Boolean smiles = false;
    Boolean labelName = false;

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader eventReader = factory.createXMLEventReader(new GZIPInputStream(new FileInputStream(file)));

    List<Chemical> result = new ArrayList<>();
    Long counter = 0L;
    Chemical chemical = new Chemical(counter);
    String urnName = "";

    while (eventReader.hasNext()) {

      XMLEvent event = eventReader.nextEvent();

      switch (event.getEventType()){
        case XMLStreamConstants.START_ELEMENT:
          StartElement startElement = event.asStartElement();
          String qName = startElement.getName().getLocalPart();
          if (qName.equalsIgnoreCase("PC-CompoundType_id_cid")) {
            cid = true;
          } else if (qName.equalsIgnoreCase("PC-Urn_label")) {
            key = true;
          } else if (qName.equalsIgnoreCase("PC-InfoData_value_sval")) {
            value = true;
          } else if (qName.equalsIgnoreCase("PC-Urn_name")) {
            labelName = true;
          }
          break;
        case XMLStreamConstants.CHARACTERS:
          Characters characters = event.asCharacters();
          if (cid) {
            Long pubchemId = Long.parseLong(characters.getData());
            chemical.setPubchem(pubchemId);
            cid = false;
          }
          if (key) {
            String data = characters.getData();
            if (data.equalsIgnoreCase("IUPAC Name")) {
              iupacName = true;
            } else if (data.equalsIgnoreCase("InChI")) {
              inchi = true;
            } else if (data.equalsIgnoreCase("InChIKey")) {
              inchiKey = true;
            } else if (data.equalsIgnoreCase("Molecular Formula")) {
              molFormula = true;
            } else if (data.equalsIgnoreCase("SMILES")) {
              smiles = true;
            }
            key = false;
          } else if (value) {
            String data = characters.getData();
            if (iupacName) {
              //System.out.println("iupacName: " + data);
              chemical.addNames(urnName, new String[] {data});
              iupacName = false;
            } else if (inchi) {
              //System.out.println("inchi: " + data);
              chemical.setInchi(data);
              inchi = false;
            } else if (inchiKey) {
              //System.out.println("inchiKey: " + data);
              chemical.setInchiKey(data);
              inchiKey = false;
            } else if (molFormula) {
              //System.out.println("molFormula: " + data);
              molFormula = false;
            } else if (smiles) {
              //System.out.println("smiles: " + data);
              chemical.setSmiles(data);
              smiles = false;
            }
            value = false;
          } else if (labelName) {
            if (iupacName) {
              urnName = characters.getData();
              labelName = false;
            }
          }
          break;
        case XMLStreamConstants.END_ELEMENT:
          EndElement endElement = event.asEndElement();
          if(endElement.getName().getLocalPart().equalsIgnoreCase("PC-Compound")) {
            result.add(chemical);
            counter++;
            chemical = new Chemical(counter);
            //System.out.println("Completed chemical");
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
