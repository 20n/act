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
  private static final String TEXT = "text";
  private static final String COMMENT = "comment";
  private static final String CATALYTIC_ACTIVITY = "catalytic activity";
  private static final String CATALYTIC_ACITIVITY_SNAKE = "catalytic_activity";
  private static final String VAL = "val";
  private static final String SRC = "src";
  private static final String PROTEIN_EXISTENCE = "proteinExistence";
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
  private static final String SYNONYMS = "synonyms";
  private static final String PRODUCT_NAMES = "product_names";
  private static final String UNCHARACTERIZED = "uncharacterized";

  private Document seqFile;
  private String ec;
  private JSONObject accessions;
  private String geneName;
  private List<String> geneSynonyms;
  private List<String> productNames;
  private List<Seq> matchingSeqs;
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

  private NodeList proteinNodeList;
  private NodeList sequenceNodeList;
  private NodeList organismNodeList;
  private NodeList geneNodeList;


  UniprotSeqEntry(Document doc) {
    this.seqFile = doc;
    checkNodeListLengths();
    this.ec = extractEc();
    this.accessions = extractAccessions();
    this.geneName = extractGeneName();
    this.geneSynonyms = extractGeneSynonyms();
    this.productNames = extractProductNames();
    this.catalyticActivity = extractCatalyticActivity();
    this.metadata = extractMetadata();
    this.sequence = extractSequence();
    this.org = extractOrg();
    this.references = extractReferences();
    initCatalyzedReactions();
  }

  public void init(MongoDB db) {
    this.orgId = extractOrgId(db);
    this.matchingSeqs = extractMatchingSeqs(db);
  }

  public DBObject getMetadata() { return this.metadata; }
  public JSONObject getAccession() { return this.accessions; }
  public String getGeneName() { return this.geneName; }
  public List<String> getGeneSynonyms() { return this.geneSynonyms; }
  public List<String> getProductName() { return this.productNames; }
  public List<Seq> getMatchingSeqs() { return this.matchingSeqs; }
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

  private void initCatalyzedReactions() {
    this.catalyzedRxns = new HashSet<Long>();
    this.catalyzedSubstratesUniform = new HashSet<Long>();
    this.catalyzedSubstratesDiverse = new HashSet<Long>();
    this.catalyzedProductsDiverse = new HashSet<Long>();
    this.catalyzedProductsUniform = new HashSet<Long>();
    this.catalyzedRxnsToSubstrates = new HashMap<Long, Set<Long>>();
    this.catalyzedRxnsToProducts = new HashMap<Long, Set<Long>>();
    this.sar = new SAR();
  }

  private void checkNodeListLengths() {
    proteinNodeList = seqFile.getElementsByTagName(PROTEIN);
    geneNodeList = seqFile.getElementsByTagName(GENE);
    sequenceNodeList = seqFile.getElementsByTagName(SEQUENCE);
    organismNodeList = seqFile.getElementsByTagName(ORGANISM);

    if (proteinNodeList.getLength() > 1) {

      throw new RuntimeException("multiple protein tags parsed");

    }

    if (geneNodeList.getLength() > 1) {

      throw new RuntimeException("multiple gene tags parsed");

    }

    if (organismNodeList.getLength() > 1) {

      throw new RuntimeException("multiple organism tags parsed");

    }

    if (sequenceNodeList.getLength() > 1) {

      throw new RuntimeException("multiple sequence tags parsed");

    } else if (sequenceNodeList.getLength() == 0) {

      throw new RuntimeException("no sequence tags parsed");

    }

  }

  /**
   * EC Numbers are stored as:
   *  <protein>
   *    <recommendedName>
   *      <fullName evidence="33">Alcohol dehydrogenase class-P</fullName>
   *      <shortName evidence="30">AtADH</shortName>
   *      <ecNumber evidence="18 22">1.1.1.1</ecNumber>
   *    </recommendedName>
   *  </protein>
   * Sometimes the <recommendedName> tag is replaced with a <submittedName> tag
   * @return the Ecnum as a string
   */
  private String extractEc() {
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

          if (recommendedNameElement.getElementsByTagName(EC_NUMBER).getLength() > 1) {

            throw new RuntimeException("multiple ec numbers per protein");

          } else if (recommendedNameElement.getElementsByTagName(EC_NUMBER).getLength() == 1) {

            return recommendedNameElement.getElementsByTagName(EC_NUMBER).item(0).getTextContent();

          }
        }
      }
    }

    return null;
  }

  /**
   * Uniprot accessions are stored as:
   * <accession>Q9SX08</accession>
   *
   * Nucleotide Accession in this example is: M12196
   * Protein Accession in this example is: AAA32728
   *<dbReference type="EMBL" id="M12196">
   *  <property type="protein sequence ID" value="AAA32728.1"/>
   *  <property type="molecule type" value="Genomic_DNA"/>
   *</dbReference>
   * @return a mapping of uniprot, genbank_nucleotide, and genbank_protein accessions
   */
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

  /**
   * The gene name is stored with the type="primary"
   *<gene>
   *  <name type="primary" evidence="32">ADH1</name>
   *  <name type="synonym" evidence="31">ADH</name>
   *  <name type="ordered locus" evidence="36">At1g77120</name>
   *  <name type="ORF" evidence="35">F22K20.19</name>
   *</gene>
   *
   * @return the primary gene name as a string
   */
  private String extractGeneName() {
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
    }

    return null;
  }

  /**
   * The gene name synonyms are stored with the type="synonym"
   *<gene>
   *  <name type="primary" evidence="32">ADH1</name>
   *  <name type="synonym" evidence="31">ADH</name>
   *  <name type="ordered locus" evidence="36">At1g77120</name>
   *  <name type="ORF" evidence="35">F22K20.19</name>
   *</gene>
   *
   * @return the gene name synonyms as a list
   */
  private List<String> extractGeneSynonyms() {
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
    }

    return geneSynonyms;
  }

  /**
   * Product names are stored as:
   *<protein>
   *  <recommendedName>
   *    <fullName>Amine sulfotransferase</fullName>
   *    <ecNumber>2.8.2.3</ecNumber>
   *  </recommendedName>
   *  <alternativeName>
   *    <fullName>SULT-X2</fullName>
   *  </alternativeName>
   *  <alternativeName>
   *    <fullName>Sulfotransferase 3A1</fullName>
   *    <shortName>ST3A1</shortName>
   *  </alternativeName>
   *</protein>
   * Sometimes the <recommendedName> tag is replaced with a <submittedName> tag
   * @return the list of product names
   */
  private List<String> extractProductNames() {
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
            if (productName.toLowerCase().contains(UNCHARACTERIZED)) {
              LOGGER.error("Skipping uncharacterized protein");
              break;
            }

            // Collections.singletonList used over Arrays.asList because it takes less memory
            return Collections.singletonList(productName);
          }

        }
      }
    }

    return new ArrayList<>();
  }

  /**
   * Catalytic activity strings are stored as:
   *<comment type="catalytic activity">
   *  <text evidence="18 22">An alcohol + NAD(+) = an aldehyde or ketone + NADH.</text>
   *</comment>
   * @return the catalytic activity string
   */
  private String extractCatalyticActivity() {
    NodeList commentNodeList = seqFile.getElementsByTagName(COMMENT);

    for (int i = 0; i < commentNodeList.getLength(); i++) {
      Node commentNode = commentNodeList.item(i);

      if (commentNode.getNodeType() == Node.ELEMENT_NODE) {
        Element commentElement = (Element) commentNode;

        if (commentElement.hasAttribute(TYPE) && commentElement.getAttribute(TYPE).equals(CATALYTIC_ACTIVITY)) {
          NodeList commentChildNodes = commentElement.getChildNodes();

          // there should only be one text element child containing the string of interest
          if (commentChildNodes.getLength() == 1 && commentChildNodes.item(0).getNodeName().equals(TEXT)) {

            return commentChildNodes.item(0).getTextContent();

          } else if (commentChildNodes.getLength() > 1) {

            LOGGER.error("more than one catalytic activity string");

          }
        }

      }
    }

    return null;

  }

  private DBObject extractMetadata() {
    JSONObject obj = new JSONObject();

    obj.put(PROTEIN_EXISTENCE, new JSONObject());
    obj.put(NAME, geneName);
    obj.put(SYNONYMS, geneSynonyms);
    obj.put(PRODUCT_NAMES, productNames);
    obj.put(COMMENT, new ArrayList());
    obj.put(ACCESSION, accessions);
    obj.put(CATALYTIC_ACITIVITY_SNAKE, catalyticActivity);

    return MongoDBToJSON.conv(obj);
  }

  /**
   * Sequence strings are stored as:
   *<sequence length="379" mass="41178" checksum="32550529538B9669" modified="2007-05-29" version="2">
   *MSTTGQIIRCKAAVAWEAGKPLVIEEVEVAPPQKHEVRIKILFTSLCHTDVYFWEAKGQT
   *PLFPRIFGHEAGGIVESVGEGVTDLQPGDHVLPIFTGECGECRHCHSEESNMCDLLRINT
   *ERGGMIHDGESRFSINGKPIYHFLGTSTFSEYTVVHSGQVAKINPDAPLDKVCIVSCGLS
   *TGLGATLNVAKPKKGQSVAIFGLGAVGLGAAEGARIAGASRIIGVDFNSKRFDQAKEFGV
   *TECVNPKDHDKPIQQVIAEMTDGGVDRSVECTGSVQAMIQAFECVHDGWGVAVLVGVPSK
   *DDAFKTHPMNFLNERTLKGTFFGNYKPKTDIPGVVEKYMNKELELEKFITHTVPFSEINK
   *AFDYMLKGESIRCIITMGA
   *</sequence>
   * @return the sequence string
   */
  private String extractSequence() {
    return sequenceNodeList.item(0).getTextContent();
  }

  /**
   * The organism name is stored with the type="scientific"
   * <organism>
   *  <name type="scientific">Arabidopsis thaliana</name>
   *  <name type="common">Mouse-ear cress</name>
   *  <dbReference type="NCBI Taxonomy" id="3702"/>
   *</organism>
   * @return the organism as a string
   */
  private String extractOrg() {
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
    }

    return null;
  }

  private Long extractOrgId(MongoDB db) {
    long id = db.getOrganismId(org);

    // if id == -1L, this means this organism does not exist in the database
    if (id != -1L) {
      return id;
    } else {
      return db.submitToActOrganismNameDB(org);
    }
  }

  /**
   * The Pubmed Ids are stored in the <dbReference> tags with type="Pubmed"
   *<reference key="4">
   *  <citation type="journal article" date="1996" name="Mol. Biol. Evol." volume="13" first="433" last="436">
   *    <title>Intra- and interspecific variation of the alcohol dehydrogenase locus region in wild plants Arabis gemmifera and Arabidopsis thaliana.</title>
   *    <authorList>
   *      <person name="Miyashita N.T."/>
   *    </authorList>
   *    <dbReference type="PubMed" id="8587508"/>
   *    <dbReference type="DOI" id="10.1093/oxfordjournals.molbev.a025603"/>
   *  </citation>
   *  <scope>NUCLEOTIDE SEQUENCE [GENOMIC DNA]</scope>
   *  <source>
   *    <strain>cv. Aa-0</strain>
   *  </source>
   *</reference>
   * @return a list of JSONObjects containing the extracted PubMed Ids
   */
  private List<JSONObject> extractReferences() {
    NodeList referenceNodeList = seqFile.getElementsByTagName(REFERENCE);

    List<JSONObject> references = new ArrayList<>();

    for (int i = 0; i < referenceNodeList.getLength(); i++) {
      Node referenceNode = referenceNodeList.item(i);

      if (referenceNode.getNodeType() == Node.ELEMENT_NODE) {
        Element referenceElement = (Element) referenceNode;

        if (referenceElement.getElementsByTagName(CITATION).getLength() > 1) {

          LOGGER.error("more than one citation per reference");

        } else if (referenceElement.getElementsByTagName(CITATION).getLength() == 0) {

          break;

        }

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
                obj.put(VAL, dbReferenceElement.getAttribute(ID));
                obj.put(SRC, PMID);
                references.add(obj);
              }
            }
          }
        }
      }
    }

    return references;
  }

  /**
   * In the case that ecnum, sequence, & org are all found in the uniprot file, this retrieves all sequence matches from
   * the installer database.
   * In the case that there is no ecnum, but there is a genbank protein accession number, this
   * retrieves all sequences that carry that genbank protein accession number.
   * In the case that there is no ecnum or genbank protein accession number, but there is a genbank nucleotide accession
   * number, this retrieves all sequences that carry that genbank nucleotide accession number and protein sequence.
   * If none of this is the case, then returns the empty list.
   * @param db
   * @return the list of Seq entries that should be updated with the data from the uniprot file
   */
  private List<Seq> extractMatchingSeqs(MongoDB db) {
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
