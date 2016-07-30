package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenbankSeqEntry extends SequenceEntry {
  private static final String PROTEIN_SEQ_TYPE = "Protein";
  private static final String DNA_SEQ_TYPE = "DNA";
  private static final String TRANSLATION = "translation";
  private static final String EC_NUMBER = "EC_number";
  private static final String PMID = "PMID";
  private static final String PATENT = "Patent";
  private static final String COUNTRY_CODE = "countryCode";
  private static final String PATENT_NUMBER = "patentNumber";
  private static final String PATENT_YEAR = "patentYear";
  private static final String COUNTRY_CODE_SNAKE = "country_code";
  private static final String PATENT_NUMBER_SNAKE = "patent_number";
  private static final String PATENT_YEAR_SNAKE = "patent_year";
  private static final String SOURCE = "source";
  private static final String ORGANISM = "organism";
  private static final String PROTEIN_ID = "protein_id";
  private static final String PROTEIN = "Protein";
  private static final String NAME = "name";
  private static final String GENE = "gene";
  private static final String GENE_SYNONYM = "gene_synonym";
  private static final String PRODUCT = "product";
  private static final String VAL = "val";
  private static final String SRC = "src";
  private static final String SYNONYMS = "synonyms";
  private static final String PRODUCT_NAMES = "product_names";
  private static final String COMMENT = "comment";
  private static final String ACCESSION = "accession";
  private static final String PROTEIN_EXISTENCE = "proteinExistence";
  private static final Pattern GENE_NAME_PATTERN = Pattern.compile("(\\S*)\\s*.*");

  private AbstractSequence seqObject;
  private Map<String, List<Qualifier>> cdsQualifierMap;
  private String seqType;
  private DBObject metadata;
  private JSONObject accessions;
  private List<JSONObject> references;
  private List<JSONObject> pmids;
  private List<JSONObject> patents;
  private String sequence;
  private String geneName;
  private List<String> productNames;
  private List<String> geneSynonyms;
  private String org;
  private Long orgId;
  private String ec;
  private Set<Long> catalyzedRxns;
  private Set<Long> catalyzedSubstratesDiverse, catalyzedSubstratesUniform;
  private Set<Long> catalyzedProductsDiverse, catalyzedProductsUniform;
  private HashMap<Long, Set<Long>> catalyzedRxnsToSubstrates, catalyzedRxnsToProducts;
  private SAR sar;


  public GenbankSeqEntry(AbstractSequence sequence, MongoDB db) {
    this.seqObject = sequence;
    this.seqType = PROTEIN_SEQ_TYPE;
    init(db);
  }

  public GenbankSeqEntry(AbstractSequence sequence, Map<String, List<Qualifier>> cdsQualifierMap, MongoDB db) {
    this.seqObject = sequence;
    this.seqType = DNA_SEQ_TYPE;
    this.cdsQualifierMap = cdsQualifierMap;
    init(db);
  }

  public void init(MongoDB db) {
    this.ec = extractEc();
    this.accessions = extractAccessions();
    this.geneName = extractGeneName();
    this.geneSynonyms = extractGeneSynonyms();
    this.productNames = extractProductName();
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
  public List<JSONObject> getPmids() { return this.pmids; }
  public List<JSONObject> getPatents() { return this.patents; }
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

  private String extractSequence() {
    if (seqType.equals(PROTEIN_SEQ_TYPE)) {
      return seqObject.getSequenceAsString();
    } else if (seqType.equals(DNA_SEQ_TYPE)) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey(TRANSLATION)) {
        return cdsQualifierMap.get(TRANSLATION).get(0).getValue();
      }
    }

    return null;
  }

  private String extractEc() {
    Map<String, List<Qualifier>> qualifierMap = null;

    if (seqType.equals(PROTEIN_SEQ_TYPE)) {
      qualifierMap = getQualifierMap(PROTEIN_SEQ_TYPE);
    } else if (seqType.equals(DNA_SEQ_TYPE)) {
      qualifierMap = cdsQualifierMap;
    }

    if (qualifierMap != null && qualifierMap.containsKey(EC_NUMBER)) {
      String ec_value = qualifierMap.get(EC_NUMBER).get(0).getValue();

      // there was a case where the EC_Number qualifier existed, but the value was empty or null
      if (ec_value.isEmpty() || ec_value == null) {
        return null;
      }

      return ec_value;
    } else {
      return null;
    }
  }

  private List<JSONObject> extractPmids() {
    List<String> pmids = seqObject.getPMIDS();
    List<JSONObject> references = new ArrayList<>();

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put(VAL, pmid);
      obj.put(SRC, PMID);
      references.add(obj);
    }

    this.pmids = references;
    return references;
  }

  private List<JSONObject> extractPatents() {
    List<Map> patents = seqObject.getPatents();
    List<JSONObject> references = new ArrayList<>();

    for (Map patent : patents) {
      JSONObject obj = new JSONObject();
      obj.put(SRC, PATENT);
      obj.put(COUNTRY_CODE_SNAKE, patent.get(COUNTRY_CODE));
      obj.put(PATENT_NUMBER_SNAKE, patent.get(PATENT_NUMBER));
      obj.put(PATENT_YEAR_SNAKE, patent.get(PATENT_YEAR));
      references.add(obj);
    }

    this.patents = references;
    return references;
  }

  public List<JSONObject> extractReferences() {
    List<JSONObject> references = new ArrayList<>();

    references.addAll(extractPmids());
    references.addAll(extractPatents());

    return references;
  }

  private String extractOrg() {
    Map<String, List<Qualifier>> qualifierMap = getQualifierMap(SOURCE);

    if (qualifierMap != null && qualifierMap.containsKey(ORGANISM)) {
      return qualifierMap.get(ORGANISM).get(0).getValue();
    }

    return null;
  }

  private Long extractOrgId(MongoDB db) {
    long id = db.getOrganismId(org);
    if (id != -1L) {
      return id;
    } else {
      return db.submitToActOrganismNameDB(org);
    }
  }

  /** accessions are stored in a JSONObject where the keys are either "genbank-protein" or "genbank-nucleotide" and
   * the values are JSONArrays of the accession keys
   * @return the accession JSONObject
   */
  private JSONObject extractAccessions() {
    JSONArray proteinAccessions = null;
    JSONArray nucleotideAccessions = null;

    if (seqType.equals(PROTEIN_SEQ_TYPE)) {
      proteinAccessions = new JSONArray(Collections.singletonList(seqObject.getAccession().getID()));
    } else if (seqType.equals(DNA_SEQ_TYPE)) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey(PROTEIN_ID)) {
        // example: /protein_id="BAA25015.1"
        String[] splitId = cdsQualifierMap.get(PROTEIN_ID).get(0).getValue().split("\\.");
        proteinAccessions = new JSONArray(Collections.singletonList(splitId[0]));
      }
    }

    if (seqType.equals(DNA_SEQ_TYPE)) {
      nucleotideAccessions = new JSONArray(Collections.singletonList(seqObject.getAccession().getID()));
    }

    JSONObject accessions = new JSONObject();

    if (proteinAccessions != null) {
      accessions.put(Seq.AccType.genbank_protein.toString(), proteinAccessions);
    }

    if (nucleotideAccessions != null) {
      accessions.put(Seq.AccType.genbank_nucleotide.toString(), nucleotideAccessions);
    }

    return accessions;

  }

  public List<Seq> getSeqs(MongoDB db) {
    if (ec != null) {
      return db.getSeqFromSeqEcOrg(sequence, ec, org);
    } else {
      return db.getSeqFromGenbankProtAccession((accessions.getJSONArray(Seq.AccType.genbank_protein.toString())).getString(0));
    }
  }

  private String extractGeneName() {
    if (seqType.equals(PROTEIN_SEQ_TYPE)) {
      Map<String, List<Qualifier>> proteinQualifierMap = getQualifierMap(PROTEIN);

      // check if gene name is in Protein feature key, otherwise check for gene name in header
      if (proteinQualifierMap != null && proteinQualifierMap.containsKey(NAME)) {
        return proteinQualifierMap.get(NAME).get(0).getValue();
      } else {
        String header = seqObject.getOriginalHeader();
        Matcher m = GENE_NAME_PATTERN.matcher(header);
        if (m.find()) {
          // some cases where genbank files have accession id's in the place of the gene name in the header of the file
          if (m.group(1).equals((accessions.getJSONArray(Seq.AccType.genbank_protein.toString())).getString(0))) {
            return null;
          }
          return m.group(1);
        }
      }
    } else if (seqType.equals(DNA_SEQ_TYPE)) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey(GENE)) {
        return cdsQualifierMap.get(GENE).get(0).getValue();
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

    return MongoDBToJSON.conv(obj);
  }

  private List<String> extractGeneSynonyms() {
    ArrayList<String> geneSynonyms = new ArrayList<>();

    if (seqType.equals(PROTEIN_SEQ_TYPE)) {
      {
        Map<String, List<Qualifier>> qualifierMap = getQualifierMap(PROTEIN);
        if (qualifierMap != null && qualifierMap.containsKey(GENE_SYNONYM)) {
          for (Qualifier qualifier : qualifierMap.get(GENE_SYNONYM)) {
            geneSynonyms.add(qualifier.getValue());
          }
        }

        if (qualifierMap != null && qualifierMap.containsKey(GENE)) {
          for (Qualifier qualifier : qualifierMap.get(GENE)) {
            geneSynonyms.add(qualifier.getValue());
          }
        }
      }

      {
        Map<String, List<Qualifier>> qualifierMap = getQualifierMap(GENE);
        if (qualifierMap != null) {
          if (qualifierMap.containsKey(GENE)) {
            for (Qualifier qualifier : qualifierMap.get(GENE)) {
              if (!geneSynonyms.contains(qualifier.getValue())) {
                geneSynonyms.add(qualifier.getValue());
              }
            }
          }

          if (qualifierMap.containsKey(GENE_SYNONYM)) {
            for (Qualifier qualifier : qualifierMap.get(GENE_SYNONYM)) {
              if (!geneSynonyms.contains(qualifier.getValue())) {
                geneSynonyms.add(qualifier.getValue());
              }
            }
          }
        }
      }
    }

    return geneSynonyms;
  }

  private List<String> extractProductName() {
    Map<String, List<Qualifier>> qualifierMap = null;

    if (seqType.equals(PROTEIN_SEQ_TYPE)) {
      qualifierMap = getQualifierMap(PROTEIN);
    } else if (seqType.equals(DNA_SEQ_TYPE)) {
      qualifierMap = cdsQualifierMap;
    }

    if (qualifierMap != null && qualifierMap.containsKey(PRODUCT)) {
      return Collections.singletonList(qualifierMap.get(PRODUCT).get(0).getValue());
    }

    return null;
  }

  private Map<String, List<Qualifier>> getQualifierMap(String feature_type) {
    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature :
        (List<FeatureInterface<AbstractSequence<Compound>, Compound>>) seqObject.getFeatures()) {
      if (feature.getType().equals(feature_type)) {
        return feature.getQualifiers();
      }
    }
    return null;
  }

}
