package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.utils.parser.GenbankInterpreter;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GenbankSeqEntryTest {
  ArrayList<GenbankSeqEntry> proteinSeqEntries;
  ArrayList<GenbankSeqEntry> dnaSeqEntries;
  ArrayList<String> sequences;

  @Before
  public void setUp() throws Exception {
    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();

    Map<Long, String> organismNames = new HashMap<>();
    organismNames.put(4000000648L, "Bacillus cereus");
    organismNames.put(4000002681L, "Homo sapiens");
    organismNames.put(4000005381L, "Rhodobacter capsulatus");

    // only information needed for these set of tests is a db with organism id's.
    mockAPI.installMocks(new ArrayList<Reaction>(), new ArrayList<Seq>(), organismNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockReadMongoDB();

    dnaSeqEntries = new ArrayList<>();
    proteinSeqEntries = new ArrayList<>();
    sequences = new ArrayList<>();

    GenbankInterpreter giProtein =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein.gb").getFile()), "Protein");
    giProtein.init();
    sequences.add(giProtein.sequences.get(0).getSequenceAsString());
    proteinSeqEntries.add(new GenbankSeqEntry(giProtein.sequences.get(0), mockDb));

    giProtein =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein_2.gb").getFile()), "Protein");
    giProtein.init();
    sequences.add(giProtein.sequences.get(0).getSequenceAsString());
    proteinSeqEntries.add(new GenbankSeqEntry(giProtein.sequences.get(0), mockDb));


    GenbankInterpreter giDna =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_dna.gb").getFile()), "DNA");
    giDna.init();

    AbstractSequence sequence = giDna.sequences.get(0);
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


    JSONObject obj = new org.json.JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Arrays.asList("Arylamine N-acetyltransferase"));
    obj.put("comment", new ArrayList());
    obj.put("accession", Arrays.asList("CUB13083"));
    obj.put("accession_source", Arrays.asList("genbank"));
    obj.put("nucleotide_accession", new ArrayList());

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ST1A1_HUMAN");
    obj.put("synonyms", geneSynonyms);
    obj.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
    obj.put("comment", new ArrayList());
    obj.put("accession", Arrays.asList("P50225"));
    obj.put("accession_source", Arrays.asList("genbank"));
    obj.put("nucleotide_accession", new ArrayList());

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ureA");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Arrays.asList("gamma subunit of urase"));
    obj.put("comment", new ArrayList());
    obj.put("accession", Arrays.asList("BAB21065"));
    obj.put("accession_source", Arrays.asList("genbank"));
    obj.put("nucleotide_accession", Arrays.asList("AB006984"));

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ureB");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Arrays.asList("beta subunit of urease"));
    obj.put("comment", new ArrayList());
    obj.put("accession", Arrays.asList("BAB21066"));
    obj.put("accession_source", Arrays.asList("genbank"));
    obj.put("nucleotide_accession", Arrays.asList("AB006984"));

    metadatas.add(MongoDBToJSON.conv(obj));

    obj = new org.json.JSONObject();

    obj.put("proteinExistence", new JSONObject());
    obj.put("name", "ureC");
    obj.put("synonyms", emptyGeneSynonyms);
    obj.put("product_names", Arrays.asList("alpha subunit of urease"));
    obj.put("comment", new ArrayList());
    obj.put("accession", Arrays.asList("BAB21067"));
    obj.put("accession_source", Arrays.asList("genbank"));
    obj.put("nucleotide_accession", Arrays.asList("AB006984"));

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
    assertEquals("tests whether accession ID is extracted accurately", Arrays.asList("CUB13083"),
        proteinSeqEntries.get(0).getAccession());
    assertEquals("tests whether accession ID is extracted accurately", Arrays.asList("P50225"),
        proteinSeqEntries.get(1).getAccession());

    assertEquals("tests whether accession ID is extracted accurately", Arrays.asList("BAB21065"),
        dnaSeqEntries.get(0).getAccession());
    assertEquals("tests whether accession ID is extracted accurately", Arrays.asList("BAB21066"),
        dnaSeqEntries.get(1).getAccession());
    assertEquals("tests whether accession ID is extracted accurately", Arrays.asList("BAB21067"),
        dnaSeqEntries.get(2).getAccession());
  }

  @Test
  public void testGeneName() {
    assertEquals("tests whether gene name is extracted accurately", "",
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
    assertEquals("tests whether product names are extracted accurately", Arrays.asList("Arylamine N-acetyltransferase"),
        proteinSeqEntries.get(0).getProductName());
    assertEquals("tests whether product names are extracted accurately", Arrays.asList("Sulfotransferase 1A1"),
        proteinSeqEntries.get(1).getProductName());

    assertEquals("tests whether product names are extracted accurately", Arrays.asList("gamma subunit of urase"),
        dnaSeqEntries.get(0).getProductName());
    assertEquals("tests whether product names are extracted accurately", Arrays.asList("beta subunit of urease"),
        dnaSeqEntries.get(1).getProductName());
    assertEquals("tests whether product names are extracted accurately", Arrays.asList("alpha subunit of urease"),
        dnaSeqEntries.get(2).getProductName());
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
  public void testNucleotideAccessions() {
    assertEquals("tests whether nucleotide_accessions are extracted accurately", null,
        proteinSeqEntries.get(0).getNucleotideAccession());
    assertEquals("tests whether nucleotide_accessions are extracted accurately", null,
        proteinSeqEntries.get(1).getNucleotideAccession());

    assertEquals("tests whether nucleotide_accessions are extracted accurately", Arrays.asList("AB006984"),
        dnaSeqEntries.get(0).getNucleotideAccession());
    assertEquals("tests whether nucleotide_accessions are extracted accurately", Arrays.asList("AB006984"),
        dnaSeqEntries.get(1).getNucleotideAccession());
    assertEquals("tests whether nucleotide_accessions are extracted accurately", Arrays.asList("AB006984"),
        dnaSeqEntries.get(2).getNucleotideAccession());
  }

  @Test
  public void testAccessionSource() {
    assertEquals("tests whether accession source was assigned accurately", Arrays.asList("genbank"),
        proteinSeqEntries.get(0).getAccessionSource());
    assertEquals("tests whether accession source was assigned accurately", Arrays.asList("genbank"),
        proteinSeqEntries.get(1).getAccessionSource());

    assertEquals("tests whether accession source was assigned accurately", Arrays.asList("genbank"),
        dnaSeqEntries.get(0).getAccessionSource());
    assertEquals("tests whether accession source was assigned accurately", Arrays.asList("genbank"),
        dnaSeqEntries.get(1).getAccessionSource());
    assertEquals("tests whether accession source was assigned accurately", Arrays.asList("genbank"),
        dnaSeqEntries.get(2).getAccessionSource());
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

    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(), proteinSeqEntries.get(1).getPmids().toString());

    pmidRefs = new ArrayList<>();
    JSONObject obj = new JSONObject();
    obj.put("src", "PMID");
    obj.put("val", "9484481");
    pmidRefs.add(obj);

    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(), dnaSeqEntries.get(0).getPmids().toString());
    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(), dnaSeqEntries.get(1).getPmids().toString());
    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(), dnaSeqEntries.get(2).getPmids().toString());
  }

//  @Test
//  public void testPatents() {
//
//  }
}
