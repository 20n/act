package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UniprotSeqEntry extends SequenceEntry {
  private static final List<String> ACCESSION_SOURCE = Collections.unmodifiableList(Collections.singletonList("uniprot"));
  private static final String PROTEIN = "protein";
  private static final String RECOMMENDED_NAME = "recommendedName";
  private static final String EC_NUMBER = "ecNumber";
  private static final String ACCESSION = "accession";
  private static final String GENE = "gene";
  private static final String NAME = "name";
  private static final String TYPE = "type";
  private static final String PRIMARY = "primary";
  private static final String SYNONYM = "synonym";
  private static final String FULL_NAME = "fullName";
  private static final String SEQUENCE = "sequence";
  private static final String ORGANISM = "organism";
  private static final String SCIENTIFIC = "scientific";
  private static final String REFERENCE = "reference";
  private static final String CITATION = "citation";
  private static final String DB_REFERENCE = "dbReference";
  private static final String PUBMED = "PubMed";
  private static final String ID = "id";
  private static final String PMID = "PMID";
  private static final String EMBL = "EMBL";
  private static final String PROPERTY = "property";
  private static final String PROTEIN_SEQUENCE_ID = "protein sequence ID";
  private static final String VALUE = "value";

  private Document seqFile;
  private String ec;
  private List<String> accessions;
  private String geneName;
  private List<String> geneSynonyms;
  private List<String> productNames;
  private DBObject metadata;
  private String sequence;
  private String org;
  private Long orgId;
  private List<JSONObject> references;
  private Set<Long> catalyzedRxns;
  private Set<Long> catalyzedSubstratesDiverse, catalyzedSubstratesUniform;
  private Set<Long> catalyzedProductsDiverse, catalyzedProductsUniform;
  private HashMap<Long, Set<Long>> catalyzedRxnsToSubstrates, catalyzedRxnsToProducts;
  private SAR sar;

//  http://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/

  public UniprotSeqEntry(Document doc, MongoDB db) {
    this.seqFile = doc;
    this.ec = extractEc();
    this.accessions = extractAccessions();
    this.geneName = extractGeneName();
    this.geneSynonyms = extractGeneSynonyms();
    this.productNames = extractProductNames();
    this.metadata = extractMetadata();
    this.sequence = extractSequence();
    this.org = extractOrg();
    this.orgId = extractOrgId(db);
    this.references = extractReferences();
    extractCatalyzedReactions();
  }

  public DBObject getMetadata() { return this.metadata; }
  public List<String> getAccession() { return this.accessions; }
  public List<String> getAccessionSource() { return this.ACCESSION_SOURCE; }
  public String getGeneName() { return this.geneName; }
  public List<String> getGeneSynonyms() { return this.geneSynonyms; }
  public List<String> getProductName() { return this.productNames; }
  public List<JSONObject> getRefs() { return this.references; }
  public Long getOrgId() { return this.orgId; }
  public String getOrg() { return this.org; }
  public String getSeq() { return this.sequence; }
  public String getEc() { return this.ec; }
  public Set<Long> getCatalyzedRxns() { return this.catalyzedRxns; }
  public Set<Long> getCatalyzedSubstratesUniform() { return this.catalyzedSubstratesUniform; }
  public Set<Long> getCatalyzedSubstratesDiverse() { return this.catalyzedSubstratesDiverse; }
  public Set<Long> getCatalyzedProductsUniform() { return this.catalyzedProductsUniform; }
  public Set<Long> getCatalyzedProductsDiverse() { return this.catalyzedProductsDiverse; }
  public HashMap<Long, Set<Long>> getCatalyzedRxnsToSubstrates() { return this.catalyzedRxnsToSubstrates; }
  public HashMap<Long, Set<Long>> getCatalyzedRxnsToProducts() { return this.catalyzedRxnsToProducts; }
  public SAR getSar() { return this.sar; }

  private void extractCatalyzedReactions() {
    this.catalyzedRxns = new HashSet<Long>();
    this.catalyzedSubstratesUniform = new HashSet<Long>();
    this.catalyzedSubstratesDiverse = new HashSet<Long>();
    this.catalyzedProductsDiverse = new HashSet<Long>();
    this.catalyzedProductsUniform = new HashSet<Long>();
    this.catalyzedRxnsToSubstrates = new HashMap<Long, Set<Long>>();
    this.catalyzedRxnsToProducts = new HashMap<Long, Set<Long>>();
    this.sar = new SAR();
  }

  // <dbReference type="EC" id="1.1.1.1" evidence="18 22"/>
  private String extractEc() {
    NodeList proteinNodeList = seqFile.getElementsByTagName(PROTEIN);

    if (proteinNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node proteinNode = proteinNodeList.item(0);

      NodeList proteinChildNodes = proteinNode.getChildNodes();

      for (int i = 0; i < proteinChildNodes.getLength(); i++) {
        Node proteinChildNode = proteinChildNodes.item(i);

        if (proteinChildNode.getNodeName().equals(RECOMMENDED_NAME) &&
            proteinChildNode.getNodeType() == Node.ELEMENT_NODE) {

          Element recommendedNameElement = (Element) proteinChildNode;

          // should only be one EC Number per protein
          return recommendedNameElement.getElementsByTagName(EC_NUMBER).item(0).getTextContent();
        }
      }

      return null;
    } else {
      // throw: multiple protein tags detected, potentially may need two seqentries to represent this document

      // TODO: check if uniprot xmls tend to only have one protein tag; I think they should
      return null;
    }
  }

  // TODO: change format of accessions to fit new data model
  private List<String> extractAccessions() {
    List<String> uniprotAccessions = new ArrayList<>();

    NodeList accessionNodeList = seqFile.getElementsByTagName(ACCESSION);

    for (int i = 0; i < accessionNodeList.getLength(); i++) {
      uniprotAccessions.add(accessionNodeList.item(i).getTextContent());
    }

    List<String> genbankNucleotideAccessions = new ArrayList<>();
    List<String> genbankProteinAccessions = new ArrayList<>();

    NodeList dbReferenceNodeList = seqFile.getElementsByTagName(DB_REFERENCE);

    for (int i = 0; i < dbReferenceNodeList.getLength(); i++) {
      Node dbReferenceNode = dbReferenceNodeList.item(i);

      if (dbReferenceNode.getNodeType() == Node.ELEMENT_NODE) {
        Element dbReferenceElement = (Element) dbReferenceNode;

        // EMBL and Genbank Accession IDs are the same
        if (dbReferenceElement.hasAttribute(TYPE) && dbReferenceElement.getAttribute(TYPE).equals(EMBL) &&
            dbReferenceElement.hasAttribute(ID)) {


          NodeList propertyNodeList = dbReferenceElement.getElementsByTagName(PROPERTY);

          /* there are some duplicate dbReferenceElements, so we want to make sure we only add those with
           'property' sub tags */
          if (propertyNodeList.getLength() > 0) {
            genbankNucleotideAccessions.add(dbReferenceElement.getAttribute(ID));
          }

          for (int j = 0; j < propertyNodeList.getLength(); j++) {
            Node propertyNode = propertyNodeList.item(j);

            if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
              Element propertyElement = (Element) propertyNode;

              if (propertyElement.hasAttribute(TYPE) && propertyElement.getAttribute(TYPE).equals(PROTEIN_SEQUENCE_ID)
                  && propertyElement.hasAttribute(VALUE)) {

                // example: <property type="protein sequence ID" value="BAA19616.1"/>
                genbankProteinAccessions.add(propertyElement.getAttribute(VALUE).split("\\.")[0]);

              }
            }
          }
        }
      }
    }

    uniprotAccessions.addAll(genbankNucleotideAccessions);
    uniprotAccessions.addAll(genbankProteinAccessions);

    return uniprotAccessions;
  }

  private String extractGeneName() {
    NodeList geneNodeList = seqFile.getElementsByTagName(GENE);

    if (geneNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node geneNode = geneNodeList.item(0);

      NodeList geneChildNodes = geneNode.getChildNodes();

      for (int i = 0; i < geneChildNodes.getLength(); i++) {
        Node geneChildNode = geneChildNodes.item(i);

        if (geneChildNode.getNodeName().equals(NAME) && geneChildNode.getNodeType() == Node.ELEMENT_NODE) {
          Element geneChildElement = (Element) geneChildNode;

          if (geneChildElement.hasAttribute(TYPE) && geneChildElement.getAttribute(TYPE).equals(PRIMARY)) {
            return geneChildElement.getTextContent();
          }
        }
      }

      return null;
    } else {
      // TODO: check if uniprot xmls tend to only have one gene tag; I think they should;
      // TODO: throw error
      return null;
    }
  }

  private List<String> extractGeneSynonyms() {
    NodeList geneNodeList = seqFile.getElementsByTagName(GENE);

    List<String> geneSynonyms = new ArrayList<>();

    if (geneNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node geneNode = geneNodeList.item(0);

      NodeList geneChildNodes = geneNode.getChildNodes();

      /* TODO: check if gene synonyms are on separate nodes, or all in one string on one node; code currently
       assumes they are on separate nodes */
      for (int i = 0; i < geneChildNodes.getLength(); i++) {
        Node geneChildNode = geneChildNodes.item(i);

        if (geneChildNode.getNodeName().equals(NAME) && geneChildNode.getNodeType() == Node.ELEMENT_NODE) {
          Element geneChildElement = (Element) geneChildNode;

          if (geneChildElement.hasAttribute(TYPE) && geneChildElement.getAttribute(TYPE).equals(SYNONYM)) {
            geneSynonyms.add(geneChildElement.getTextContent());
          }
        }
      }

      return geneSynonyms;
    } else {

      // TODO: check if uniprot xmls tend to only have one gene tag; i think they should
      // TODO: throw error
      return null;
    }
  }

  private List<String> extractProductNames() {
    NodeList proteinNodeList = seqFile.getElementsByTagName(PROTEIN);

    if (proteinNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node proteinNode = proteinNodeList.item(0);

      NodeList proteinChildNodes = proteinNode.getChildNodes();

      for (int i = 0; i < proteinChildNodes.getLength(); i++) {
        Node proteinChildNode = proteinChildNodes.item(i);

        if (proteinChildNode.getNodeName().equals(RECOMMENDED_NAME) &&
            proteinChildNode.getNodeType() == Node.ELEMENT_NODE) {

          Element recommendedNameElement = (Element) proteinChildNode;

          // there should only be one full name
          // TODO: do we want to extract the shortName? aka product synonyms?
          return Collections.singletonList(recommendedNameElement.getElementsByTagName(FULL_NAME).item(0).getTextContent());
        }
      }

      return null;
    } else {

      // TODO: check if uniprot xmls tend to only have one protein tag; I think they should
      // TODO: throw error
      return null;
    }
  }

  private DBObject extractMetadata() {
    JSONObject obj = new JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", geneName);
    obj.put("synonyms", geneSynonyms);
    obj.put("product_names", productNames);
    obj.put("comment", new ArrayList());
    obj.put("accession", accessions);
    obj.put("nucleotide_accession", new ArrayList());
    obj.put("accession_sources", ACCESSION_SOURCE);

    return MongoDBToJSON.conv(obj);
  }

  private String extractSequence() {
    NodeList sequenceNodeList = seqFile.getElementsByTagName(SEQUENCE);

    if (sequenceNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the text for the only node
      return sequenceNodeList.item(0).getTextContent();

    } else {

      // TODO: check if uniprot xmls tend to only have one sequence tag; I think they should
      // TODO: throw error
      return null;
    }
  }

  private String extractOrg() {
    NodeList organismNodeList = seqFile.getElementsByTagName(ORGANISM);

    if (organismNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node organismNode = organismNodeList.item(0);

      NodeList organismChildNodes = organismNode.getChildNodes();

      for (int i = 0; i < organismChildNodes.getLength(); i++) {
        Node organismChildNode = organismChildNodes.item(i);

        if (organismChildNode.getNodeName().equals(NAME) && organismChildNode.getNodeType() == Node.ELEMENT_NODE) {
          Element organismChildElement = (Element) organismChildNode;

          // TODO: do we want to extract the common name for the organism as well?
          if (organismChildElement.hasAttribute(TYPE) && organismChildElement.getAttribute(TYPE).equals(SCIENTIFIC)) {
            return organismChildElement.getTextContent();
          }
        }
      }

      return null;

    } else {

      // TODO: check if uniprot xmls tend to only have one organism tag; I think they should
      // TODO: throw error
      return null;
    }
  }

  private Long extractOrgId(MongoDB db) {
    long id = db.getOrganismId(org);

    if (id != -1L) {
      return id;
    } else {
      return db.submitToActOrganismNameDB(org);
    }
  }

  private List<JSONObject> extractReferences() {
    NodeList referenceNodeList = seqFile.getElementsByTagName(REFERENCE);

    List<JSONObject> references = new ArrayList<>();


    for (int i = 0; i < referenceNodeList.getLength(); i++) {
      Node referenceNode = referenceNodeList.item(i);

      if (referenceNode.getNodeType() == Node.ELEMENT_NODE) {
        Element referenceElement = (Element) referenceNode;

        Node citationNode = referenceElement.getElementsByTagName(CITATION).item(0);

        if (citationNode.getNodeType() == Node.ELEMENT_NODE) {
          Element citationElement = (Element) citationNode;

          NodeList dbReferenceNodeList = citationElement.getElementsByTagName(DB_REFERENCE);

          for (int j = 0; j < dbReferenceNodeList.getLength(); j++) {
            Node dbReferenceNode = dbReferenceNodeList.item(j);

            if (dbReferenceNode.getNodeType() == Node.ELEMENT_NODE) {
              Element dbReferenceElement = (Element) dbReferenceNode;

              if (dbReferenceElement.hasAttribute(TYPE) && dbReferenceElement.getAttribute(TYPE).equals(PUBMED) &&
                  dbReferenceElement.hasAttribute(ID)) {
                JSONObject obj = new JSONObject();
                obj.put("val", dbReferenceElement.getAttribute(ID));
                obj.put("src", PMID);
                references.add(obj);
              }
            }
          }
        }
      }
    }

    return references;
  }

  public List<Seq> getSeqs(MongoDB db) {
    if (ec != null) {
      return db.getSeqFromGenbank(sequence, ec, org);
    } else {
      List<Seq> seqs = new ArrayList<>();

      for (String accession : accessions) {
        seqs.addAll(db.getSeqFromGenbank(accession));
      }

      return seqs;
    }
  }


  public static void main(String[] args) {

  }

}
