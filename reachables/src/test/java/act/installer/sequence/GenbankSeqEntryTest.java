package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.test.util.MockedMongoDB;
import com.act.utils.parser.GenbankInterpreter;
import com.mongodb.DBObject;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GenbankSeqEntryTest {
  private ArrayList<GenbankSeqEntry> proteinSeqEntries;
  private ArrayList<GenbankSeqEntry> dnaSeqEntries;
  private ArrayList<String> sequences;

  @Before
  public void setUp() throws Exception {
    MockedMongoDB mockAPI = new MockedMongoDB();

    Map<Long, String> organismNames = new HashMap<>();
    organismNames.put(4000000648L, "Bacillus cereus");
    organismNames.put(4000002681L, "Homo sapiens");
    organismNames.put(4000005381L, "Rhodobacter capsulatus");

    // only information needed for these set of tests is a db with organism id's.
    mockAPI.installMocks(new ArrayList<Reaction>(), new ArrayList<Seq>(), organismNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    dnaSeqEntries = new ArrayList<>();
    proteinSeqEntries = new ArrayList<>();
    sequences = new ArrayList<>();

    GenbankInterpreter giProtein =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein.gb").getFile()), "Protein");
    giProtein.init();
    sequences.add(giProtein.getSequences().get(0).getSequenceAsString());
    proteinSeqEntries.add(new GenbankSeqEntry(giProtein.getSequences().get(0), mockDb));

    giProtein =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein_2.gb").getFile()), "Protein");
    giProtein.init();
    sequences.add(giProtein.getSequences().get(0).getSequenceAsString());
    proteinSeqEntries.add(new GenbankSeqEntry(giProtein.getSequences().get(0), mockDb));


    GenbankInterpreter giDna =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_dna.gb").getFile()), "DNA");
    giDna.init();

    AbstractSequence sequence = giDna.getSequences().get(0);
    List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = sequence.getFeatures();

    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
      if (feature.getType().equals("CDS") && feature.getQualifiers().containsKey("EC_number")) {
        sequences.add(feature.getQualifiers().get("translation").get(0).getValue());
        dnaSeqEntries.add(new GenbankSeqEntry(sequence, feature.getQualifiers(), mockDb));
      }
    }
  }

  @Test
  public void testMetadata() {
    ArrayList<DBObject> metadatas = new ArrayList<>();
    List<String> geneSynonyms = Arrays.asList("STP", "STP1", "SULT1A1");
    List<String> emptyGeneSynonyms = new ArrayList<>();


    JSONObject obj = new JSONObject();

    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("CUB13083")));

    obj.put("proteinExistence", new JSONObject());
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Collections.singletonList("Arylamine N-acetyltransferase"));
    obj.put("comment", new ArrayList());
    obj.put("accession", accessionObject);

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new JSONObject();

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("P50225")));

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ST1A1_HUMAN");
    obj.put("synonyms", geneSynonyms);
    obj.put("product_names", Collections.singletonList("Sulfotransferase 1A1"));
    obj.put("comment", new ArrayList());
    obj.put("accession", accessionObject);

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21065")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ureA");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Collections.singletonList("gamma subunit of urase"));
    obj.put("comment", new ArrayList());
    obj.put("accession", accessionObject);

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21066")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ureB");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Collections.singletonList("beta subunit of urease"));
    obj.put("comment", new ArrayList());
    obj.put("accession", accessionObject);

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21067")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ureC");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Collections.singletonList("alpha subunit of urease"));
    obj.put("comment", new ArrayList());
    obj.put("accession", accessionObject);

    metadatas.add(MongoDBToJSON.conv(obj));

    assertEquals("tests whether metadata is extracted accurately", metadatas.get(0),
        proteinSeqEntries.get(0).getMetadata());
    assertEquals("tests whether metadata is extracted accurately", metadatas.get(1),
        proteinSeqEntries.get(1).getMetadata());

    assertEquals("tests whether metadata is extracted accurately", metadatas.get(2),
        dnaSeqEntries.get(0).getMetadata());
    assertEquals("tests whether metadata is extracted accurately", metadatas.get(3),
        dnaSeqEntries.get(1).getMetadata());
    assertEquals("tests whether metadata is extracted accurately", metadatas.get(4),
        dnaSeqEntries.get(2).getMetadata());
  }

  @Test
  public void testAccession() {
    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("CUB13083")));

    assertEquals("tests whether accession ID is extracted accurately", accessionObject.toString(),
        proteinSeqEntries.get(0).getAccession().toString());

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("P50225")));

    assertEquals("tests whether accession ID is extracted accurately", accessionObject.toString(),
        proteinSeqEntries.get(1).getAccession().toString());

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21065")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    assertEquals("tests whether accession ID is extracted accurately", accessionObject.toString(),
        dnaSeqEntries.get(0).getAccession().toString());

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21066")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    assertEquals("tests whether accession ID is extracted accurately", accessionObject.toString(),
        dnaSeqEntries.get(1).getAccession().toString());

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21067")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    assertEquals("tests whether accession ID is extracted accurately", accessionObject.toString(),
        dnaSeqEntries.get(2).getAccession().toString());
  }

  @Test
  public void testGeneName() {
    assertEquals("tests whether gene name is extracted accurately", null,
        proteinSeqEntries.get(0).getGeneName());
    assertEquals("tests whether gene name is extracted accurately", "ST1A1_HUMAN",
        proteinSeqEntries.get(1).getGeneName());

    assertEquals("tests whether gene name is extracted accurately", "ureA", dnaSeqEntries.get(0).getGeneName());
    assertEquals("tests whether gene name is extracted accurately", "ureB", dnaSeqEntries.get(1).getGeneName());
    assertEquals("tests whether gene name is extracted accurately", "ureC", dnaSeqEntries.get(2).getGeneName());
  }

  @Test
  public void testGeneSynonyms() {
    List<String> geneSynonyms = Arrays.asList("STP", "STP1", "SULT1A1");
    assertEquals("tests whether gene synonyms are extracted accurately", geneSynonyms,
        proteinSeqEntries.get(1).getGeneSynonyms());

    geneSynonyms = new ArrayList<>();
    assertEquals("tests whether gene synonyms are extracted accurately", geneSynonyms,
        proteinSeqEntries.get(0).getGeneSynonyms());

    assertEquals("tests whether gene synonyms are extrated accurately", geneSynonyms,
        dnaSeqEntries.get(0).getGeneSynonyms());
    assertEquals("tests whether gene synonyms are extrated accurately", geneSynonyms,
        dnaSeqEntries.get(1).getGeneSynonyms());
    assertEquals("tests whether gene synonyms are extrated accurately", geneSynonyms,
        dnaSeqEntries.get(2).getGeneSynonyms());
  }

  @Test
  public void testProductName() {
    assertEquals("tests whether product names are extracted accurately",
        Collections.singletonList("Arylamine N-acetyltransferase"), proteinSeqEntries.get(0).getProductName());
    assertEquals("tests whether product names are extracted accurately",
        Collections.singletonList("Sulfotransferase 1A1"), proteinSeqEntries.get(1).getProductName());

    assertEquals("tests whether product names are extracted accurately",
        Collections.singletonList("gamma subunit of urase"), dnaSeqEntries.get(0).getProductName());
    assertEquals("tests whether product names are extracted accurately",
        Collections.singletonList("beta subunit of urease"), dnaSeqEntries.get(1).getProductName());
    assertEquals("tests whether product names are extracted accurately",
        Collections.singletonList("alpha subunit of urease"), dnaSeqEntries.get(2).getProductName());
  }

  @Test
  public void testOrgId() {
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000000648L,
        proteinSeqEntries.get(0).getOrgId());
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000002681L,
        proteinSeqEntries.get(1).getOrgId());

    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000005381L,
        dnaSeqEntries.get(0).getOrgId());
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000005381L,
        dnaSeqEntries.get(1).getOrgId());
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000005381L,
        dnaSeqEntries.get(2).getOrgId());
  }

  @Test
  public void testOrg() {
    assertEquals("tests whether organism names are extracted accurately", "Bacillus cereus",
        proteinSeqEntries.get(0).getOrg());
    assertEquals("tests whether organism names are extracted accurately", "Homo sapiens",
        proteinSeqEntries.get(1).getOrg());

    assertEquals("tests whether organism names are extracted accurately", "Rhodobacter capsulatus",
        dnaSeqEntries.get(0).getOrg());
    assertEquals("tests whether organism names are extracted accurately", "Rhodobacter capsulatus",
        dnaSeqEntries.get(1).getOrg());
    assertEquals("tests whether organism names are extracted accurately", "Rhodobacter capsulatus",
        dnaSeqEntries.get(2).getOrg());
  }

  @Test
  public void testSeq() {
    assertEquals("tests whether sequences are extracted accurately", sequences.get(0),
        proteinSeqEntries.get(0).getSeq());
    assertEquals("tests whether sequences are extracted accurately", sequences.get(1),
        proteinSeqEntries.get(1).getSeq());

    assertEquals("tests whether sequences are extracted accurately", sequences.get(2),
        dnaSeqEntries.get(0).getSeq());
    assertEquals("tests whether sequences are extracted accurately", sequences.get(3),
        dnaSeqEntries.get(1).getSeq());
    assertEquals("tests whether sequences are extracted accurately", sequences.get(4),
        dnaSeqEntries.get(2).getSeq());
  }

  @Test
  public void testEc() {
    assertEquals("tests whether ec_numbers are extracted accurately", "2.3.1.5",
        proteinSeqEntries.get(0).getEc());
    assertEquals("tests whether ec_numbers are extracted accurately", "2.8.2.1",
        proteinSeqEntries.get(1).getEc());

    assertEquals("tests whether ec_numbers are extracted accurately", "3.5.1.5",
        dnaSeqEntries.get(0).getEc());
    assertEquals("tests whether ec_numbers are extracted accurately", "3.5.1.5",
        dnaSeqEntries.get(1).getEc());
    assertEquals("tests whether ec_numbers are extracted accurately", "3.5.1.5",
        dnaSeqEntries.get(2).getEc());
  }

  @Test
  public void testPMID() {
    List<JSONObject> pmidRefs = new ArrayList<>();

    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs, proteinSeqEntries.get(0).getPmids());

    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620", "15616553", "15489334", "8288252", "8093002", "8033270", "24275569",
        "25944712", "12471039", "16221673", "20417180", "21723874", "22069470", "9345314", "10762004", "21269460");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("val", pmid);
      obj.put("src", "PMID");
      pmidRefs.add(obj);
    }

    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(),
        proteinSeqEntries.get(1).getPmids().toString());

    pmidRefs = new ArrayList<>();
    JSONObject obj = new JSONObject();
    obj.put("src", "PMID");
    obj.put("val", "9484481");
    pmidRefs.add(obj);

    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(),
        dnaSeqEntries.get(0).getPmids().toString());
    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(),
        dnaSeqEntries.get(1).getPmids().toString());
    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(),
        dnaSeqEntries.get(2).getPmids().toString());
  }

  @Test
  public void testPatents() {
    List<JSONObject> patentRefs = new ArrayList<>();

    assertEquals("tests whether Patent references were assigned accurately", patentRefs,
        proteinSeqEntries.get(0).getPatents());

    JSONObject obj = new JSONObject();

    obj.put("src", "Patent");
    obj.put("country_code", "JP");
    obj.put("patent_number", "2008518610");
    obj.put("patent_year", "2008");

    patentRefs.add(obj);

    assertEquals("tests whether Patent references were assigned accurately", patentRefs.toString(),
        proteinSeqEntries.get(1).getPatents().toString());

    JSONObject obj2 = new JSONObject();

    obj2.put("src", "Patent");
    obj2.put("country_code", "EP");
    obj2.put("patent_number", "2904117");
    obj2.put("patent_year", "2015");

    patentRefs.add(obj2);

    assertEquals("tests whether Patent references were assigned accurately", patentRefs.toString(),
        dnaSeqEntries.get(0).getPatents().toString());
    assertEquals("tests whether Patent references were assigned accurately", patentRefs.toString(),
        dnaSeqEntries.get(1).getPatents().toString());
    assertEquals("tests whether Patent references were assigned accurately", patentRefs.toString(),
        dnaSeqEntries.get(2).getPatents().toString());
  }
}
