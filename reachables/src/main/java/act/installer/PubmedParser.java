package act.installer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import act.server.SQLInterface.PubmedEntry;

abstract class IterativeParser {
    abstract Object getNext(); // get data from the data source
    // abstract boolean needsABee(Object data); // check if this data is worth processing
}

public class PubmedParser extends IterativeParser {
  String DataDir;
    int StartIndex;
    int EndIndex;

    static String DataPrefix = "medline11n";
    static String DataSuffix = ".xml"; // the real medline data
    // static String DataSuffix = "-sample.xml"; // small samples for debugging.

    int currentIndex;
    XMLStreamReader xml;

    public PubmedParser(String dataDir) {
      this.StartIndex = 1;
      this.EndIndex = 653;

        this.currentIndex = StartIndex;
        this.xml = null;
        this.DataDir = dataDir;
        initCurrentXML();
    }

    public PubmedParser(String dataDir, int start, int end) {
      if (start < 1 || start > 653 || end < 1 || end > 653 || start > end) {
        System.err.println("Start and end should be between [1, 653]");
        System.exit(-1);
      }

      this.StartIndex = start;
      this.EndIndex = end;

        this.currentIndex = StartIndex;
        this.xml = null;
        this.DataDir = dataDir;
        initCurrentXML();
    }

    @Override
    public Object getNext() {

        try {

            // debugging...
            // if (true) return readEntireFileToScreen();

            if (this.xml == null) {
                return null;
            }

            boolean reachedEnd = !moveToNextEntry();

            if (reachedEnd) {
                // we have reached the end of this XML, so now move to the next...

                this.xml = null;
                // two cases: 1) switch to next file, or 2) either done with all: do nothing
                if (this.currentIndex <= this.EndIndex) {
                    initCurrentXML();
                    if (!moveToNextEntry())
                        throw new CiderPubmedFormatException("Data file contains no entries?");
                }
            }

            if (this.xml == null) {
                // if done with all data from all files
                return null;
            }

            // if data elements remain then return the next one
            PubmedEntry entry = getNextDataElem();
            return entry;

        } catch (CiderPubmedFormatException ex) {
            ex.printStackTrace();
            System.err.println("Data elem parsing error.");
            System.exit(-1);
        } catch (XMLStreamException ex) {
            System.err.println("Could not step to next data piece.");
            System.exit(-1);
        }
        return null; // unreachable
    }

    private boolean moveToNextEntry() throws XMLStreamException, CiderPubmedFormatException {
        int eventType = this.xml.next();
        while (this.xml.isWhiteSpace() || eventType == XMLEvent.SPACE)
            eventType = this.xml.next();

        if (eventType == XMLEvent.END_ELEMENT && this.xml.getLocalName().equals("MedlineCitationSet")) {
            return false;
        } else if (!(eventType == XMLEvent.START_ELEMENT && this.xml.getLocalName().equals("MedlineCitation"))) {
            throw new CiderPubmedFormatException("Expecting entry start");
        }
        return true;
    }

    private void initCurrentXML() {

        if (this.xml != null) {
            System.err.println("init XML called when the previous XML was not finished yet.");
            System.exit(-1);
        }

        String currentfile = this.DataDir + PubmedParser.DataPrefix + String.format("%04d", this.currentIndex) + PubmedParser.DataSuffix;

        try {
            FileInputStream fileInputStream = new FileInputStream(currentfile);
            this.xml = XMLInputFactory.newInstance().createXMLStreamReader(fileInputStream);
            expect(XMLEvent.DTD, null);
            expect(XMLEvent.START_ELEMENT, "MedlineCitationSet");
        } catch (XMLStreamException ex) {
            ex.printStackTrace();
            System.out.println("XML stream error on file:" + currentfile);
            System.exit(-1);
        } catch (FileNotFoundException e) {
            System.err.println("Could not find XML file: " + currentfile);
            System.exit(-1);
        } catch (CiderPubmedFormatException ex) {
            System.err.println("XML file does not start with DTD, MedlineCitationSet: " + currentfile);
            System.exit(-1);
        }
        this.currentIndex++;
    }

    private PubmedEntry getNextDataElem() throws XMLStreamException, CiderPubmedFormatException {

        List<String> tagStack = new ArrayList<String>();
        tagStack.add("MedlineCitation"); // the start_element has already been read through, so artifically add it
        HashMap<String, Object> data = new HashMap<String, Object>();
        do {
            readExceptedTags(data, tagStack);
        } while (!tagStack.isEmpty());

        HashMap<String, List<String>> allXML = makeStrLists(data);
        return new PubmedEntry(allXML);

        // collapse any entries in the XML that come out as lists into concatenated strings; using the \n delimiter
        // HashMap<String, String> allFlattenedXML = flattenAll(data, "\n");
        // return new FullPubmedEntry(allFlattenedXML);

        /*
        if ("".length() == 10) { // dead code... but I don't like warnings...

          Object titleO, journalO_cn, journalO_md, journalO_id, pmidO, abstractO, chemsO;

          titleO = data.get("MedlineCitation/Article/ArticleTitle");
          abstractO = data.get("MedlineCitation/Article/Abstract/AbstractText");
          pmidO = data.get("MedlineCitation/PMID");
          journalO_cn = data.get("MedlineCitation/Article/Journal/Title"); // common name
          journalO_md = data.get("MedlineCitation/MedlineJournalInfo/MedlineTA"); // medline canonical abbr
          journalO_id = data.get("MedlineCitation/MedlineJournalInfo/NlmUniqueID"); // medline unique journal id
          chemsO = data.get("MedlineCitation/ChemicalList/Chemical/NameOfSubstance");

          int pmid = Integer.parseInt((String)pmidO);
          String title = flatten(titleO, ""); // if the title comes across as multiple lines, it is only because of special chars &quot; &amp;, or "[title]." type occurances, so collapse them together
          String journal_cn = flatten(journalO_cn, ""); // common name of journal in native lang
          String journal_md = flatten(journalO_md, ""); // canonical abbr of journal name in pubmed
          String journal_id = (String)journalO_id; // unique journal id in pubmed

          String abs = flatten(abstractO, "\n"); // various paragraphs separated by \n
          String chemStr = flatten(chemsO, ";");
          List<String> chems = chemStr == null ? null : Arrays.asList(chemStr.split(";"));

          System.out.println("[TODO] Set debug breakpoint here for all the cool data under MedlineCitation.");

          return new PubmedEntry(abs, title, journal_cn, journal_md, journal_id, pmid, chems);
        }
        */
    }

  private void expect(int expType, String expTag) throws XMLStreamException, CiderPubmedFormatException {
        try {

        int eventType = this.xml.next();
        while (this.xml.isWhiteSpace() || eventType == XMLEvent.SPACE)
            eventType = this.xml.next();

        if (eventType != expType)
            throw new CiderPubmedFormatException(expType + " entry expected. Not found.");

        if (expTag != null && !this.xml.getLocalName().equals(expTag))
            throw new CiderPubmedFormatException(expTag + " entry expected. Not found.");
        } catch (XMLStreamException ex) {
            System.err.println("NOTE: This error could be because you don't have a net connection that can fetch the right DTD schema.");
            throw ex;
        }
    }

    private String flatten(Object obj, String delim) {
        if (obj == null)
            // sometimes no elements are present: return null
            return null;

        String flat = null;
        if (obj instanceof List) {
            List<String> abstr = (List<String>) obj;
            for (String s : abstr) {
                flat = (flat == null ? s : (flat + delim + s));
            }
        } else {
            flat = (String) obj;
        }
        return flat;
    }

    private HashMap<String, String> flattenAll(HashMap<String, Object> data, String delim) {
    HashMap<String, String> flat = new HashMap<String, String>();
    for (String k : data.keySet())
      flat.put(k, flatten(data.get(k), delim));
    return flat;
  }

  private HashMap<String, List<String>> makeStrLists(HashMap<String, Object> data) {
    HashMap<String, List<String>> typeCasted = new HashMap<String, List<String>>();
    for (String k : data.keySet()) {
      Object obj = data.get(k);
      if (obj == null) { // sometimes no elements are present
        typeCasted.put(k, null);
      } else if (obj instanceof List) {
        typeCasted.put(k, (List<String>) obj);
      } else if (obj instanceof String){
        List<String> l = new ArrayList<String>();
        l.add((String) obj);
        typeCasted.put(k, l);
      } else {
        System.err.println("Found data element of unknown type: " + obj);
        System.exit(-1);
      }
    }
    return typeCasted;
  }

    private String readExceptedTags(HashMap<String, Object> data, List<String> tagStack) throws XMLStreamException, CiderPubmedFormatException {

        String tag;
        int eventType = this.xml.next();
        while (this.xml.isWhiteSpace() || eventType == XMLEvent.SPACE)
            eventType = this.xml.next();

        switch (eventType) {
            case XMLEvent.START_ELEMENT:
                // push onto stack
                tag = this.xml.getLocalName();
                // System.out.println("START_ELEMENT: " + tag);
                tagStack.add(0, tag);
                return tag;
            case XMLEvent.END_ELEMENT:
                // pop stack
                tag = this.xml.getLocalName();
                // System.out.println("END_ELEMENT: " + tag);
                if (tagStack.isEmpty())
                    throw new CiderPubmedFormatException("Tag end mismatch: (empty) " + " vs " + tag);
                if (!tagStack.get(0).equals(tag))
                    throw new CiderPubmedFormatException("Tag end mismatch: " + tagStack.get(0) + " vs " + tag);
                tagStack.remove(0);
                return tag;
            case XMLEvent.CHARACTERS:
                String txt = this.xml.getText();
                // System.out.println("C " + txt);
                addToMap(data, txt, pathFromStack(tagStack));
                return null; // do not return a tag indicator for text
            case XMLEvent.COMMENT:
                throw new CiderPubmedFormatException("Pubmed contains XML comments? Not expected here.");
            case XMLEvent.START_DOCUMENT:
            case XMLEvent.END_DOCUMENT:
                throw new CiderPubmedFormatException("Start/End_Document not expected here.");
            case XMLEvent.ENTITY_REFERENCE:
            case XMLEvent.ATTRIBUTE:
            case XMLEvent.PROCESSING_INSTRUCTION:
                throw new CiderPubmedFormatException("Processing instr/Entity reference/Attribute not expected here.");
            case XMLEvent.DTD:
                throw new CiderPubmedFormatException("DTD not expected here.");
            case XMLEvent.CDATA:
                throw new CiderPubmedFormatException("CDATA not expected here.");
            case XMLEvent.SPACE:
                throw new CiderPubmedFormatException("SPACE not expected here.");
        }

        throw new CiderPubmedFormatException("Unknown tag seen!");
    }

    private void addToMap(HashMap<String, Object> map, String txt, String path) throws CiderPubmedFormatException {
        if (!map.containsKey(path)) {
            // simple case, when unique string for path.
            map.put(path, txt);
            return;
        } else {
            // map already contains key; so need to convert to list<string>
            Object old = map.get(path);

            List<String> ls;
            if (old instanceof List) {
                ls = (List<String>)old;
                ls.add(txt);
            } else if (old instanceof String) {
                ls = new ArrayList<String>();
                ls.add((String)old);
                ls.add(txt);
            } else
                throw new CiderPubmedFormatException("Something other than a String or [String], not possible");;

            map.put(path, ls);
        }
    }

    private String pathFromStack(List<String> stk) {
        String s = stk.get(0);
        for (int i=1; i<stk.size(); i++)
            s = stk.get(i) + "/" + s;
        return s;
    }

    private void getNextTagData() throws XMLStreamException {
        int eventType = this.xml.next();
        switch (eventType) {
            case XMLEvent.START_ELEMENT:
                System.out.println("START_ELEMENT: " + this.xml.getLocalName());
                return;
            case XMLEvent.END_ELEMENT:
                System.out.println("END_ELEMENT: " + this.xml.getLocalName());
                return;
            case XMLEvent.PROCESSING_INSTRUCTION:
                System.out.println("PROCESSING_INSTRUCTION: " + this.xml.getLocalName());
                return;
            case XMLEvent.CHARACTERS:
                System.out.println("CHARACTERS: " + this.xml.getText());
                return;
            case XMLEvent.COMMENT:
                System.out.println("COMMENT: " + this.xml.getText());
                return;
            case XMLEvent.START_DOCUMENT:
                System.out.println("START_DOCUMENT: " + this.xml.getLocalName());
                return;
            case XMLEvent.END_DOCUMENT:
                System.out.println("END_DOCUMENT: " + this.xml.getLocalName());
                return;
            case XMLEvent.ENTITY_REFERENCE:
                System.out.println("ENTITY_REFERENCE: " + this.xml.getLocalName());
                return;
            case XMLEvent.ATTRIBUTE:
                System.out.println("ATTRIBUTE: " + this.xml.getLocalName());
                return;
            case XMLEvent.DTD:
                System.out.println("DTD");
                return;
            case XMLEvent.CDATA:
                System.out.println("CDATA");
                return;
            case XMLEvent.SPACE:
                System.out.println("SPACE");
                return;
        }
        System.out.println("Something else...");
    }

    private PubmedEntry readEntireFileToScreen() throws XMLStreamException {
        while (this.xml.hasNext()) getNextTagData();
        return null; // new PubmedEntrySummary("", "", "", "", "", 0, null);
    }

    /*
    @Override
    boolean needsABee(Object data) {
        if (!(data instanceof BeeHoney))
            return false;

        BeeHoney h = (BeeHoney)data;
        if (KnownJournals.Exclude.contains(h.journal_medlineID))
            return false;
        else if (KnownJournals.DontKnow.contains(h.journal_medlineID))
            return false; // we are testing what would happen if we ignore
        else if (KnownJournals.Include.contains(h.journal_medlineID))
            return true;
        else {
            System.err.println("J MD: " + h.journal_medlineTA);
            System.err.println("J CN: " + h.journal_commonName);
            System.err.println("J ID: " + h.journal_medlineID);
            System.err.println("Is it of interest?");
            System.err.format("        \"%s\",\t// \"%s\", \"%s\",\n", h.journal_medlineID, h.journal_medlineTA, h.journal_commonName);
            // System.exit(-1);
        }
        return true;
    }
    */
}



class KnownDBs {

    static String GetDBRef(Names db, int id) {
        return "#" + db.name() + "(" + id + ")";
    }

    public enum Names { PMID, GenBank, RefSeq }
}
