package act.installer.pubchem;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * This class implements a parser for Pubchem's TTL (turtle) files.  These contain both the features available in the
 * full Pubchem compound corpus, as well as other features not available in that dataset.
 */
public class PubchemTTLMerger {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemTTLMerger.class);

  enum PC_RDF_DATA_TYPES {
    SYN_2_CMP("http://rdf.ncbi.nlm.nih.gov/pubchem/synonym/", "pc_synonym2compound"),
    MeSH("http://id.nlm.nih.gov/mesh/", "pc_synonym_topic"),
    COMPOUND_ID("http://rdf.ncbi.nlm.nih.gov/pubchem/compound/", "pc_synonym_value"),
    ;

    private static Map<String, PC_RDF_DATA_TYPES> reverseUrlMap = new HashMap<String, PC_RDF_DATA_TYPES>() {{
      for (PC_RDF_DATA_TYPES t : PC_RDF_DATA_TYPES.values()) {
        put(t.getUrl(), t);
      }
    }};

    public static PC_RDF_DATA_TYPES lookupByUrl(String url) {
      return reverseUrlMap.get(url);
    }


    private String url;
    private String filePrefix;

    PC_RDF_DATA_TYPES(String url, String filePrefix) {
      this.url = url;
      this.filePrefix = filePrefix;
    }

    public String getUrl() {
      return this.url;
    }

    public PC_RDF_DATA_TYPES getDataTypeForFile(File file) {
      String name = file.getName();
      for (PC_RDF_DATA_TYPES t : PC_RDF_DATA_TYPES.values()) {
        if (name.startsWith(t.filePrefix)) {
          return t;
        }
      }
      return null;
    }

  }

  enum COLUMN_FAMILIES {
    HASH_TO_SYNONYMS("hash_to_synonym"),
    CID_TO_HASHES("cid_to_hashes"),
    CID_TO_SYNONYMS("cid_to_synonyms"),
    ;

    private String name;

    COLUMN_FAMILIES(String name) {
      this.name = name;
    }

    public String getName() {
      return this.name;
    }
  }

  public static final String PC_RDF_SYNONYM_RDF_VALUE_TYPE = "langString";


  private static class TestHandler extends AbstractRDFHandler {

    public Map<String, List<String>> hash = new HashMap<>();

    public TestHandler() {}

    @Override
    public void handleStatement(Statement st) {
      if (!(st.getSubject() instanceof SimpleIRI)) {
        // If we can't even recognize the type of the subject, something is very wrong.
        String msg = String.format("Unknown type of subject: %s", st.getSubject().getClass().getCanonicalName());
        LOGGER.error(msg);
        throw new RuntimeIOException(msg);
      }

      SimpleIRI subjectIRI = (SimpleIRI) st.getSubject();
      if (PC_RDF_DATA_TYPES.lookupByUrl(subjectIRI.getNamespace()) == null) {
        // If we don't recognize the namespace of the subject, then we probably can't handle this triple.
        LOGGER.warn("Unrecognized subject namespace: %s\n", subjectIRI.getLocalName());
        return;
      }

      String key = subjectIRI.getLocalName();
      String val = null;
      if (st.getObject() instanceof SimpleIRI) {
        SimpleIRI objectIRI = (SimpleIRI) st.getObject();
        if (PC_RDF_DATA_TYPES.lookupByUrl(objectIRI.getNamespace()) == null) {
          // If we don't recognize the namespace of the subject, then we probably can't handle this triple.
          LOGGER.warn("Unrecognized object namespace: %s\n", objectIRI.getNamespace());
          return;
        }
        val = objectIRI.getLocalName();
      } else if (st.getObject() instanceof SimpleLiteral) {
        SimpleLiteral objectLiteral = (SimpleLiteral) st.getObject();
        IRI datatype = objectLiteral.getDatatype();
        if (!PC_RDF_SYNONYM_RDF_VALUE_TYPE.equals(datatype.getLocalName())) {
          // We're only expecting string values where we find literals.
          System.out.format("Unrecognized simple literal datatype: %s\n", datatype.getLocalName());
          return;
        }
        val = objectLiteral.getLabel();
      } else {
        throw new RuntimeIOException(String.format("Unknown type of object: %s", st.getObject().getClass().getCanonicalName()));
      }

      List<String> vals = hash.get(key);
      if (vals == null) {
        vals = new ArrayList<>();
        hash.put(key, vals);
      }
      vals.add(val);
    }
  }

  public static void main(String[] args) throws Exception {
    RDFParser parser = Rio.createParser(RDFFormat.TURTLE);

    TestHandler handler = new TestHandler();
    parser.setRDFHandler(handler);
    parser.parse(new GZIPInputStream(new FileInputStream(args[0])), "");

    LOGGER.info("Number of keys in file: %d\n", handler.hash.size());
  }
}
