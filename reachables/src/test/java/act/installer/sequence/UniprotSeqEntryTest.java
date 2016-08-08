package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.Utils.OrgMinimalPrefixGenerator;
import com.act.biointerpretation.test.util.MockedMongoDB;
import com.act.utils.parser.UniprotInterpreter;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class UniprotSeqEntryTest {
  private ArrayList<UniprotSeqEntry> seqEntries;
  private ArrayList<String> sequences;

  @Before
  public void setUp() throws Exception {
    MockedMongoDB mockAPI = new MockedMongoDB();

    Map<Long, String> organismNames = new HashMap<>();
    organismNames.put(4000000399L, "Arabidopsis thaliana");

    Map<String, Long> orgMap = new HashMap<>();

    // manually assemble an Org Iterator since you can't mock DBCollection in getDbIteratorOverOrgs()
    List<Organism> orgs = new ArrayList<>();
    for (Map.Entry<Long, String> orgName : organismNames.entrySet()) {
      orgs.add(new Organism(orgName.getKey(), -1, orgName.getValue()));
    }

    Iterator<Organism> orgIterator = orgs.iterator();

    while (orgIterator.hasNext()) {
      Organism org = orgIterator.next();
      orgMap.put(org.getName(), 1L);
    }

    OrgMinimalPrefixGenerator prefixGenerator = new OrgMinimalPrefixGenerator(orgMap);
    Map<String, String> minimalPrefixMapping = prefixGenerator.getMinimalPrefixMapping();

    // only information needed for these set of tests is a db with organism id's.
    mockAPI.installMocks(new ArrayList<Reaction>(), new ArrayList<Seq>(), organismNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    seqEntries = new ArrayList<>();
    sequences = new ArrayList<>();

    UniprotInterpreter upProtein =
        new UniprotInterpreter(new File(this.getClass().getResource("uniprot_test_1.xml").getFile()));
    upProtein.init();
    sequences.add(upProtein.getSequence());
    UniprotSeqEntry seqEntry = new UniprotSeqEntryFactory().createFromDocumentReference(upProtein.getXmlDocument(),
        mockDb, minimalPrefixMapping);
    seqEntries.add(seqEntry);
  }

  @Test
  public void testMetadata() {
    ArrayList<DBObject> metadatas = new ArrayList<>();

    List<String> uniprotAccessions = Arrays.asList("P06525", "O04080", "O04713", "O04717", "O04868", "O23821", "Q8LA61",
        "Q94AY6", "Q9CAZ2", "Q9CAZ3", "Q9SX08");

    List<String> genbankNucleotideAccessions = Arrays.asList("M12196", "X77943", "D84240", "D84241", "D84242", "D84243",
        "D84244", "D84245", "D84246", "D84247", "D84248", "D84249", "D63460", "D63461", "D63462", "D63463", "D63464",
        "AF110456", "AB048394", "AB048395", "AY536888", "AC002291", "CP002684", "AY045612", "AY090330", "AY088010",
        "AF056557");

    List<String> genbankProteinAccessions = Arrays.asList("AAA32728", "CAA54911", "BAA19615", "BAA19616", "BAA19617",
        "BAA19618", "BAA19619", "BAA19620", "BAA19621", "BAA19622", "BAA19623", "BAA19624", "BAA22983", "BAA22979",
        "BAA22980", "BAA22981", "BAA22982", "AAF23554", "BAB32568", "BAB32569", "AAS45601", "AAC00625", "AEE35937",
        "AAK73970", "AAL90991", "AAM65556", "AAD41572");

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), new JSONArray(uniprotAccessions));
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), new JSONArray(genbankNucleotideAccessions));
    accessions.put(Seq.AccType.genbank_protein.toString(), new JSONArray(genbankProteinAccessions));

    JSONObject obj = new JSONObject();

    obj.put("xref", new JSONObject());
    obj.put("name", "ADH1");
    obj.put("synonyms", Collections.singletonList("ADH"));
    obj.put("product_names", Collections.singletonList("Alcohol dehydrogenase class-P"));
    obj.put("accession", accessions);
    obj.put("catalytic_activity", "An alcohol + NAD(+) = an aldehyde or ketone + NADH.");

    metadatas.add(MongoDBToJSON.conv(obj));

    assertEquals("tests whether metadata is extracted accurately", metadatas.get(0),
        seqEntries.get(0).getMetadata());
  }

  @Test
  public void testAccession() {
    List<String> uniprotAccessions = Arrays.asList("P06525", "O04080", "O04713", "O04717", "O04868", "O23821", "Q8LA61",
        "Q94AY6", "Q9CAZ2", "Q9CAZ3", "Q9SX08");

    List<String> genbankNucleotideAccessions = Arrays.asList("M12196", "X77943", "D84240", "D84241", "D84242", "D84243",
        "D84244", "D84245", "D84246", "D84247", "D84248", "D84249", "D63460", "D63461", "D63462", "D63463", "D63464",
        "AF110456", "AB048394", "AB048395", "AY536888", "AC002291", "CP002684", "AY045612", "AY090330", "AY088010",
        "AF056557");

    List<String> genbankProteinAccessions = Arrays.asList("AAA32728", "CAA54911", "BAA19615", "BAA19616", "BAA19617",
        "BAA19618", "BAA19619", "BAA19620", "BAA19621", "BAA19622", "BAA19623", "BAA19624", "BAA22983", "BAA22979",
        "BAA22980", "BAA22981", "BAA22982", "AAF23554", "BAB32568", "BAB32569", "AAS45601", "AAC00625", "AEE35937",
        "AAK73970", "AAL90991", "AAM65556", "AAD41572");

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), new JSONArray(uniprotAccessions));
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), new JSONArray(genbankNucleotideAccessions));
    accessions.put(Seq.AccType.genbank_protein.toString(), new JSONArray(genbankProteinAccessions));


    assertEquals("tests whether accession ID is extracted accurately", accessions.toString(),
        seqEntries.get(0).getAccession().toString());
  }

  @Test
  public void testGeneName() {
    assertEquals("tests whether gene name is extracted accurately", "ADH1",
        seqEntries.get(0).getGeneName());
  }

  @Test
  public void testGeneSynonyms() {
    assertEquals("tests whether gene synonyms are extracted accurately", Collections.singletonList("ADH"),
        seqEntries.get(0).getGeneSynonyms());
  }

  @Test
  public void testProductName() {
    assertEquals("tests whether product names are extracted accurately",
        Collections.singletonList("Alcohol dehydrogenase class-P"), seqEntries.get(0).getProductName());
  }

  @Test
  public void testCatalyticActivity() {
    assertEquals("tests whether catalytic activity is extracted accurately",
        "An alcohol + NAD(+) = an aldehyde or ketone + NADH.", seqEntries.get(0).getCatalyticActivity());
  }

  @Test
  public void testOrgId() {
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000000399L,
        seqEntries.get(0).getOrgId());
  }

  @Test
  public void testOrg() {
    assertEquals("tests whether organism names are extracted accurately", "Arabidopsis thaliana",
        seqEntries.get(0).getOrg());
  }

  @Test
  public void testSeq() {
    assertEquals("tests whether sequences are extracted accurately", sequences.get(0),
        seqEntries.get(0).getSeq());
  }

  @Test
  public void testEc() {
    assertEquals("tests whether ec_numbers are extracted accurately", "1.1.1.1",
        seqEntries.get(0).getEc());
  }

  @Test
  public void testReferences() {
    List<JSONObject> pmidRefs = new ArrayList<>();

    List<String> pmids = Arrays.asList("2937058", "7851777", "8844162", "8587508", "11018155", "11158375", "11130712",
        "14593172", "10382288", "3377754", "2277648", "12231733", "8787023", "9522467", "9611167", "9880346",
        "11402191", "11402202", "11987307", "12509334", "12857811", "16055689", "18433157", "18441225", "19245862",
        "20508152", "22223895", "23707506", "24395201", "26566261", "25447145");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("val", pmid);
      obj.put("src", "PMID");
      pmidRefs.add(obj);
    }

    assertEquals("tests whether PMIDs were assigned accurately", pmidRefs.toString(),
        seqEntries.get(0).getRefs().toString());
  }


}
