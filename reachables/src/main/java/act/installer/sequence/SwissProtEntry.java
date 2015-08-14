package act.installer.sequence;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;

import act.server.SQLInterface.MongoDB;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;

import com.mongodb.DBObject;

import org.apache.tools.ant.filters.StringInputStream;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.XML;
import org.json.JSONException;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

public class SwissProtEntry extends SequenceEntry {
  JSONObject data;

  public static void parsePossiblyMany(File uniprot_file, final MongoDB db) throws IOException {
    try (
        FileInputStream fis = new FileInputStream(uniprot_file);
    ) {
      SwissProtEntryHandler handler = new SwissProtEntryHandler() {
        @Override
        public void handle(SwissProtEntry entry) {
          // TODO: run this in a separate thread w/ a synchronized queue to connect it to the parser.
          entry.writeToDB(db, Seq.AccDB.swissprot);
        }
      };
      parsePossiblyMany(handler, fis, uniprot_file.toString());
    }
  }

  public static Set<SequenceEntry> parsePossiblyMany(String is) throws IOException {
    final HashSet<SequenceEntry> results = new HashSet<SequenceEntry>();
    SwissProtEntryHandler handler = new SwissProtEntryHandler() {
      @Override
      public void handle(SwissProtEntry entry) {
        results.add(entry);
      }
    };
    StringInputStream sis = new StringInputStream(is);
    parsePossiblyMany(handler, sis, "[String input]");
    return results;
  }

  private static void parsePossiblyMany(SwissProtEntryHandler handler,
                                        InputStream is, String debugSource) throws IOException {
    XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
    XMLEventReader xr = null;

    try {
      xr = xmlInputFactory.createXMLEventReader(is, "utf-8");

      StringWriter w = new StringWriter();
      XMLEventWriter xw = xmlOutputFactory.createXMLEventWriter(w);
      boolean inEntry = false;

      int processedEntries = 0;
      while (xr.hasNext()) {
        XMLEvent e = xr.nextEvent();
        if (!inEntry && e.isStartElement() && e.asStartElement().getName().getLocalPart().equals(("entry"))) {
          inEntry = true;
        } else if (e.isEndElement() && e.asEndElement().getName().getLocalPart().equals("entry")) {
          xw.flush();
          SwissProtEntry entry = new SwissProtEntry(XML.toJSONObject(w.toString()));
          handler.handle(entry);
          /* Note: this can also be accomplished with `w.getBuffer().setLength(0);`, but using a new event writer
           * seems safer. */
          xw.close();
          w = new StringWriter();
          xw = xmlOutputFactory.createXMLEventWriter(w);
          inEntry = false;

          processedEntries++;
          if (processedEntries % 1000 == 0) {
            // TODO: proper logging!
            System.out.println("Processed " + processedEntries + " UniProt/SwissProt entries from " + debugSource);
          }
        } else if (inEntry) {
          // Add this element if we're in an entry
          xw.add(e);
        }
      }
      xr.close();
      System.out.println("Completed processing " + processedEntries + " UniProt/SwissProt entries from " + debugSource);
    } catch (JSONException je) {
      System.out.println("Failed SwissProt parse: " + je.toString() + " XML file: " + debugSource);
    } catch (XMLStreamException e) {
      // TODO: do better.
      throw new IOException(e);
    }
  }

  private SwissProtEntry(JSONObject gene_entry) {
    this.data = gene_entry;
  }
  
  String get_ec() {
    // data.dbReference.[{id:x.x.x.x, type:"EC"}...]
    return lookup_ref(this.data, "EC");
  }

  DBObject get_metadata() {
    return MongoDBToJSON.conv(this.data);
  }

  Set<Long> get_catalyzed_rxns() {
    // optionally add reactions to actfamilies by processing 
    // "catalytic activity" annotations and then return those 
    // catalyzed reaction ids (Long _id of actfamilies). This 
    // function SHOULD NOT infer which actfamilies refer to 
    // this object, as that is done in map_seq install.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_substrates_diverse() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_products_diverse() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_substrates_uniform() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_products_uniform() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  HashMap<Long, Set<Long>> get_catalyzed_rxns_to_substrates() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashMap<Long, Set<Long>>();
  }

  HashMap<Long, Set<Long>> get_catalyzed_rxns_to_products() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashMap<Long, Set<Long>>();
  }

  SAR get_sar() {
    // sar is computed later; using "initdb infer_sar"
    // for now add the empty sar constraint set
    return new SAR();
  }

  List<String> get_pmids() {
    // data.reference.[ {citation: {type: "journal article", dbReference.{id:, type:PubMed}, title:XYA } ... } .. ]
    List<String> pmids = new ArrayList<String>();
    JSONArray refs = possible_list(this.data.get("reference"));
    for (int i = 0; i<refs.length(); i++) {
      JSONObject citation = (JSONObject)((JSONObject)refs.get(i)).get("citation");
      if (citation.get("type").equals("journal article")) {
        String id = lookup_ref(citation, "PubMed");
        if (id != null) pmids.add(id); 
      }
    }
    return pmids;
  }

  Long get_org_id() {
    // data.organism.dbReference.{id: 9606, type: "NCBI Taxonomy"}
    String id = lookup_ref(this.data.get("organism"), "NCBI Taxonomy");
    if (id == null) return null;
    return Long.parseLong(id);
  }

  String get_seq() {
    // data.sequence.content: "MSTAGKVIKCKAAV.."
    return (String)((JSONObject)this.data.get("sequence")).get("content");
  }

  private String lookup_ref(Object o, String typ) {
    // o.dbReference.{id: 9606, type: typ}
    // o.dbReference.[{id: x.x.x.x, type: typ}]
    JSONObject x = (JSONObject)o;
    if (!x.has("dbReference"))
      return null;

    JSONArray set = possible_list(x.get("dbReference"));

    for (int i = 0; i<set.length(); i++) {
      JSONObject entry = set.getJSONObject(i);
      if (typ.equals(entry.get("type"))) {
        return entry.get("id").toString();
      }
    }

    return null; // did not find the requested type; not_found indicated by null
  }

  private JSONArray possible_list(Object o) {
    JSONArray l = null;
    if (o instanceof JSONObject) {
      l = new JSONArray();
      l.put(o);
    } else if (o instanceof JSONArray) {
      l = (JSONArray) o;
    } else {
      System.out.println("Json object is neither an JSONObject nor a JSONArray. Abort.");
      System.exit(-1);
    }
    return l;
  }

  @Override
  public String toString() {
    return this.data.toString(2); // format it with 2 spaces
  }

  private static abstract class SwissProtEntryHandler {
    public abstract void handle(SwissProtEntry entry);
  }
}
