package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
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
  private static final Logger LOGGER = LogManager.getFormatterLogger(UniprotSeqEntry.class);
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
  private static final String SUBMITTED_NAME = "submittedName";
  private static final String ALTERNATIVE_NAME = "alternativeName";

  private Document seqFile;
  private String ec;
  private JSONObject accessions;
  private String geneName;
  private List<String> geneSynonyms;
  private List<String> productNames;
  private String catalyticActivity;
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


  public UniprotSeqEntry(Document doc, MongoDB db) {
    this.seqFile = doc;
    this.ec = extractEc();
    this.accessions = extractAccessions();
    this.geneName = extractGeneName();
    this.geneSynonyms = extractGeneSynonyms();
    this.productNames = extractProductNames();
    this.catalyticActivity = extractCatalyticActivity();
    this.metadata = extractMetadata();
    this.sequence = extractSequence();
    this.org = extractOrg();
    this.orgId = extractOrgId(db);
    this.references = extractReferences();
    extractCatalyzedReactions();
  }

  public DBObject getMetadata() { return this.metadata; }
  public JSONObject getAccession() { return this.accessions; }
  public String getGeneName() { return this.geneName; }
  public List<String> getGeneSynonyms() { return this.geneSynonyms; }
  public List<String> getProductName() { return this.productNames; }
  public List<JSONObject> getRefs() { return this.references; }
  public Long getOrgId() { return this.orgId; }
  public String getOrg() { return this.org; }
  public String getSeq() { return this.sequence; }
  public String getEc() { return this.ec; }
  public String getCatalyticActivity() {return this.catalyticActivity; }
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
    NodeList proteinNodeList = seqFile.getElementsByTagName(PROTEIN);

    if (proteinNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node proteinNode = proteinNodeList.item(0);

      NodeList proteinChildNodes = proteinNode.getChildNodes();

      for (int i = 0; i < proteinChildNodes.getLength(); i++) {
        Node proteinChildNode = proteinChildNodes.item(i);

        if ((proteinChildNode.getNodeName().equals(RECOMMENDED_NAME) ||
            proteinChildNode.getNodeName().equals(SUBMITTED_NAME)) &&
            proteinChildNode.getNodeType() == Node.ELEMENT_NODE) {

          Element recommendedNameElement = (Element) proteinChildNode;

          if (recommendedNameElement.getElementsByTagName(EC_NUMBER).getLength() > 0) {
            // should only be one EC Number per protein
            return recommendedNameElement.getElementsByTagName(EC_NUMBER).item(0).getTextContent();
          }
        }
      }

      return null;
    } else {
      throw new RuntimeException("multiple protein tags parsed");
    }
  }

  private JSONObject extractAccessions() {
    List<String> uniprotAccessions = new ArrayList<>();
    List<String> genbankNucleotideAccessions = new ArrayList<>();
    List<String> genbankProteinAccessions = new ArrayList<>();

    NodeList accessionNodeList = seqFile.getElementsByTagName(ACCESSION);

    for (int i = 0; i < accessionNodeList.getLength(); i++) {
      uniprotAccessions.add(accessionNodeList.item(i).getTextContent());
    }


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

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), genbankProteinAccessions);

    return accessions;
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
      throw new RuntimeException("multiple gene tags parsed");
    }
  }

  private List<String> extractGeneSynonyms() {
    NodeList geneNodeList = seqFile.getElementsByTagName(GENE);

    List<String> geneSynonyms = new ArrayList<>();

    if (geneNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the only node
      Node geneNode = geneNodeList.item(0);

      NodeList geneChildNodes = geneNode.getChildNodes();

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
      throw new RuntimeException("multiple gene tags parsed");
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

        if ((proteinChildNode.getNodeName().equals(RECOMMENDED_NAME) ||
            proteinChildNode.getNodeName().equals(SUBMITTED_NAME) ||
            proteinChildNode.getNodeName().equals(ALTERNATIVE_NAME)) &&
            proteinChildNode.getNodeType() == Node.ELEMENT_NODE) {

          Element recommendedNameElement = (Element) proteinChildNode;

          if (recommendedNameElement.getElementsByTagName(FULL_NAME).getLength() > 0) {
            // there should only be one full name
            String productName = recommendedNameElement.getElementsByTagName(FULL_NAME).item(0).getTextContent();

            // handles cases: Uncharacterized protein, Putative uncharacterized protein, etc
            if (productName.toLowerCase().contains("uncharacterized protein")) {
              break;
            }

            return Collections.singletonList(productName);
          }

        }
      }

      return new ArrayList<>();
    } else {
      throw new RuntimeException("multiple protein tags parsed");
    }
  }

  private String extractCatalyticActivity() {
    NodeList commentNodeList = seqFile.getElementsByTagName("comment");

    for (int i = 0; i < commentNodeList.getLength(); i++) {
      Node commentNode = commentNodeList.item(i);

      if (commentNode.getNodeType() == Node.ELEMENT_NODE) {
        Element commentElement = (Element) commentNode;

        if (commentElement.hasAttribute("type") && commentElement.getAttribute("type").equals("catalytic activity")) {
          NodeList commentChildNodes = commentElement.getChildNodes();

          // there should only be one text element child containing the string of interest
          if (commentChildNodes.getLength() == 1 && commentChildNodes.item(0).getNodeName().equals("text")) {
            return commentChildNodes.item(0).getTextContent();
          }
        }

      }
    }

    return null;

  }

  private DBObject extractMetadata() {
    JSONObject obj = new JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", geneName);
    obj.put("synonyms", geneSynonyms);
    obj.put("product_names", productNames);
    obj.put("comment", new ArrayList());
    obj.put("accession", accessions);
    obj.put("catalytic_activity", catalyticActivity);

    return MongoDBToJSON.conv(obj);
  }

  private String extractSequence() {
    NodeList sequenceNodeList = seqFile.getElementsByTagName(SEQUENCE);

    if (sequenceNodeList.getLength() == 1) {
      // since there is only one item in the list, retrieve the text for the only node
      return sequenceNodeList.item(0).getTextContent();

    } else {
      throw new RuntimeException("multiple sequence tags parsed");
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

          if (organismChildElement.hasAttribute(TYPE) && organismChildElement.getAttribute(TYPE).equals(SCIENTIFIC)) {
            return organismChildElement.getTextContent();
          }
        }
      }

      return null;

    } else {
      throw new RuntimeException("multiple organism tags parsed");
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
    JSONArray genbankProteinAccessions = accessions.getJSONArray(Seq.AccType.genbank_protein.toString());
    JSONArray genbankNucleotideAccessions = accessions.getJSONArray(Seq.AccType.genbank_nucleotide.toString());

    List<Seq> seqs = new ArrayList<>();

    if (ec != null) {

      return db.getSeqFromSeqEcOrg(sequence, ec, org);

    } else if (genbankProteinAccessions != null && genbankProteinAccessions.length() > 0) {

      for (int i = 0; i < genbankProteinAccessions.length(); i++) {
        seqs.addAll(db.getSeqFromGenbankProtAccession(genbankProteinAccessions.getString(i)));
      }

    } else if (genbankNucleotideAccessions != null && genbankNucleotideAccessions.length() > 0) {

      for (int i = 0; i < genbankNucleotideAccessions.length(); i++) {

        List<Seq> seqFromNucAcc =
            db.getSeqFromGenbankNucAccessionSeq(genbankNucleotideAccessions.getString(i), sequence);

        if (seqFromNucAcc.size() > 1) {
          LOGGER.error("multiple seq entries match nucleotide accession + protein sequence");
        }

        seqs.addAll(seqFromNucAcc);
      }

    }

    return seqs;
  }

}
