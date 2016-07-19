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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UniprotSeqEntry {
  private static final List<String> ACCESSION_SOURCE = Collections.unmodifiableList(Collections.singletonList("uniprot"));

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

  private String extractEc() {
    NodeList proteinNodeList = seqFile.getElementsByTagName("protein");

    if (proteinNodeList.getLength() == 1) {
      Node proteinNode = proteinNodeList.item(0);
      NodeList proteinChildNodes = proteinNode.getChildNodes();

      for (int i = 0; i < proteinChildNodes.getLength(); i++) {
        Node proteinChildNode = proteinChildNodes.item(i);

        if (proteinChildNode.getNodeName().equals("recommendedName") && proteinChildNode.getNodeType() == Node.ELEMENT_NODE) {

          Element recommendedNameElement = (Element) proteinChildNode;

          // no possibility for multiple ecNumber tags?
          return recommendedNameElement.getElementsByTagName("ecNumber").item(0).getTextContent();

        }
      }

      return null;
    } else {

      // check if uniprot xmls tend to only have one protein tag; I think they should
      return null;
    }
  }

  private List<String> extractAccessions() {
    NodeList accessionNodeList = seqFile.getElementsByTagName("accession");

    List<String> accessions = new ArrayList<>();

    for (int i = 0; i < accessionNodeList.getLength(); i++) {
      accessions.add(accessionNodeList.item(i).getTextContent());
    }

    return accessions;
  }

  private String extractGeneName() {
    NodeList geneNodeList = seqFile.getElementsByTagName("gene");

    if (geneNodeList.getLength() == 1) {
      Node geneNode = geneNodeList.item(0);
      NodeList geneChildNodes = geneNode.getChildNodes();

      for (int i = 0; i < geneChildNodes.getLength(); i++) {
        Node geneChildNode = geneChildNodes.item(i);

        if (geneChildNode.getNodeType() == Node.ELEMENT_NODE) {
          Element geneChildElement = (Element) geneChildNode;

          if (geneChildElement.getTagName().equals("name") && geneChildElement.hasAttribute("type") &&
              geneChildElement.getAttribute("type").equals("primary")) {
            return geneChildElement.getTextContent();
          }

        }
      }

      return null;

    } else {

      // check if uniprot xmls tend to only have one gene tag; I think they should;
      return null;
    }
  }

  private List<String> extractGeneSynonyms() {
    NodeList geneNodeList = seqFile.getElementsByTagName("gene");

    List<String> geneSynonyms = new ArrayList<>();

    if (geneNodeList.getLength() == 1) {
      Node geneNode = geneNodeList.item(0);
      NodeList geneChildNodes = geneNode.getChildNodes();

      // check if gene synonyms are on separate nodes, or all in one string on one node; code currently assumes they are on separate nodes
      for (int i = 0; i < geneChildNodes.getLength(); i++) {
        Node geneChildNode = geneChildNodes.item(i);

        if (geneChildNode.getNodeType() == Node.ELEMENT_NODE) {
          Element geneChildElement = (Element) geneChildNode;

          if (geneChildElement.getTagName().equals("name") && geneChildElement.hasAttribute("type") &&
              geneChildElement.getAttribute("type").equals("synonym")) {
            geneSynonyms.add(geneChildElement.getTextContent());
          }
        }
      }

      return geneSynonyms;
    } else {

      // check if uniprot xmls tend to only have one gene tag; i think they should
      return null;
    }
  }

  private List<String> extractProductNames() {
    NodeList proteinNodeList = seqFile.getElementsByTagName("protein");

    if (proteinNodeList.getLength() == 1) {
      Node proteinNode = proteinNodeList.item(0);
      NodeList proteinChildNodes = proteinNode.getChildNodes();

      for (int i = 0; i < proteinChildNodes.getLength(); i++) {
        Node proteinChildNode = proteinChildNodes.item(i);

        if (proteinChildNode.getNodeName().equals("recommendedName") && proteinChildNode.getNodeType() == Node.ELEMENT_NODE) {

          Element recommendedNameElement = (Element) proteinChildNode;

          return Arrays.asList(recommendedNameElement.getElementsByTagName("fullName").item(0).getTextContent());

        }
      }

      return null;
    } else {

      // check if uniprot xmls tend to only have one protein tag; I think they should
      return null;
    }

  }

  private DBObject extractMetadata() {
    JSONObject obj = new org.json.JSONObject();

    obj.put("proteinExistence", new org.json.JSONObject());
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
    NodeList sequenceNodeList = seqFile.getElementsByTagName("sequence");

    if (sequenceNodeList.getLength() == 1) {
      Node sequenceNode = sequenceNodeList.item(0);
      return sequenceNode.getTextContent();
    } else {

      // check if uniprot xmls tend to only have one sequence tag; I think they should
      return null;
    }
  }

  private String extractOrg() {
    NodeList organismNodeList = seqFile.getElementsByTagName("organism");

    if (organismNodeList.getLength() == 1) {
      Node organismNode = organismNodeList.item(0);
      NodeList organismChildNodes = organismNode.getChildNodes();

      for (int i = 0; i < organismChildNodes.getLength(); i++) {
        Node organismChildNode = organismChildNodes.item(i);

        if (organismChildNode.getNodeType() == Node.ELEMENT_NODE) {
          Element organismChildElement = (Element) organismChildNode;

          if (organismChildElement.getNodeName().equals("name") && organismChildElement.hasAttribute("type") &&
              organismChildElement.getAttribute("type").equals("scientific")) {
            return organismChildElement.getTextContent();
          }
        }
      }

      return null;

    } else {

      // check if uniprot xmls tend to only have one organism tag; I think they should
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

  // need to check whether I can automatically cast Nodes as Elements or if I have to check the node type first
  private List<JSONObject> extractReferences() {
    NodeList referenceNodeList = seqFile.getElementsByTagName("reference");

    List<String> pmids = new ArrayList<>();

    for (int i = 0; i < referenceNodeList.getLength(); i++) {
      Element referenceNode = (Element) referenceNodeList.item(i);

      // assumes only one citation element
      Element citationNode = (Element) referenceNode.getElementsByTagName("citation").item(0);

      NodeList dbReferenceNodeList = citationNode.getElementsByTagName("dbReference");

      for (int j = 0; j < dbReferenceNodeList.getLength(); j++) {
        Node dbReferenceNode = dbReferenceNodeList.item(j);

        if (dbReferenceNode.getNodeType() == Node.ELEMENT_NODE) {
          Element dbReferenceElement = (Element) dbReferenceNode;

          if (dbReferenceElement.hasAttribute("type") && dbReferenceElement.getAttribute("type").equals("PubMed") &&
              dbReferenceElement.hasAttribute("id")) {
            pmids.add(dbReferenceElement.getAttribute("id"));
          }
        }
      }
    }

    List<JSONObject> references = new ArrayList<>();

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("val", pmid);
      obj.put("src", "PMID");
      references.add(obj);
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