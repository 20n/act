package act.installer;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.test.util.MockedMongoDB;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class UniprotInstallerTest {

  private MockedMongoDB mockAPI;

  private String protSeqFullFull = "MDNKDEYLLNFKGYNFQKTLVKMEVVENIENYEIRDDDIFIVTYPKSGTIWTQQILSLIYFEGHRNRTENIETIDRAPFF" +
      "EYNIHKLDYAKMPSPRIFSSHIPYYLVPKGLKDKKAKILYMYRNPKDVLISYFHFSNLMLIFQNPDTVESFMQTFLDGDVVGSLWFDHIRGWYEHRHDFNIMFMSFEDM" +
      "KKDFRSSVLKICSFLEKELSEEDVDAVVRQATFQKMKADPRANYEHIIKDELGTRNEMGSFLRKGVVGAWKHYLTVDQSERFDKIFHRNMKNIPLKFIWDINEE";

  private String protSeqNullNull = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKI" +
      "LIQKRGGLCYELNSLLYYFLMDCGFQVYKVAGTVYDLYDNKWKPDDGHVIIVLTHNNKDY" +
      "VIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGTHILEMRKGANGESTNFLQSE" +
      "PSHEWKVGYAFTLDPIDEKKVNNIQKVIVEHKESPFNKGAITCKLTDYGHVSLTNKNYTE" +
      "TFKGTKNKRPIESKDYAHILRESFGITQVKYVGKTLERG";


  @Before
  public void setUp() throws Exception {

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Collections.singletonList("NUR84963"));
    metadata.put("accession_sources", Collections.singletonList("uniprot"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Collections.singletonList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    List<JSONObject> references = new ArrayList<>();

    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    Seq fullFullTestSeq = new Seq(93766L, "2.8.2.3", 4000003474L, "Mus musculus", protSeqFullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    metadata = new JSONObject();
    metadata.put("accession", Collections.singletonList("CUB13083"));
    metadata.put("accession_sources", Collections.singletonList("uniprot"));

    Seq nullNullTestSeq = new Seq(38942L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    mockAPI = new MockedMongoDB();

    Map<Long, String> orgNames = new HashMap<>();
    orgNames.put(4000003474L, "Mus musculus");
    orgNames.put(4000000648L, "Bacillus cereus");

    mockAPI.installMocks(new ArrayList<>(), Arrays.asList(fullFullTestSeq, nullNullTestSeq),
        orgNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    UniprotInstaller uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_1.xml").getFile()), mockDb);
    uniprotInstaller.init();

    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_2.xml").getFile()), mockDb);
    uniprotInstaller.init();

    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_3.xml").getFile()), mockDb);
    uniprotInstaller.init();

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null and the
   * information acquired from the protein file is also null. Also tests that ec, seq, org queries can match with
   * multiple sequences in the database.
   */
  @Test
  public void testProteinNullNull() {

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Collections.singletonList("CUB13083"));
    metadata.put("accession_sources", Collections.singletonList("uniprot"));

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    Seq nullNullTestSeq = new Seq(38942L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    compareSeqs("for testProteinNullNull; (query by ec, seq, org; database match exists)", nullNullTestSeq, seqs.get(38942L));

  }
//
//  /**
//   * Tests the case where the existing reference list and metadata json object in the database are null but
//   * the protein file has all fields of information
//   */
//  @Test
//  public void testProteinNullFull() {
//
//
//
//  }
//
//  /**
//   * Tests the case where the existing reference list and metadata json object in the database are not null but
//   * the information acquired from the protein file is null
//   */
//  @Test
//  public void testProteinFullNull() {
//    JSONObject metadata = new JSONObject();
//    metadata.put("accession", Arrays.asList("NUR84963"));
//    metadata.put("accession_sources", Arrays.asList("genbank"));
//    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
//    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
//    metadata.put("name", "SULT1A1");
//
//    Map<Long, Seq> seqs = mockAPI.getSeqMap();
//
//    List<JSONObject> references = new ArrayList<>();
//
//    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
//        "8912648", "8924211", "9855620");
//
//    for (String pmid : pmids) {
//      JSONObject obj = new JSONObject();
//      obj.put("src", "PMID");
//      obj.put("val", pmid);
//      references.add(obj);
//    }
//
//    JSONObject refObj = new JSONObject();
//    refObj.put("src", "Patent");
//    refObj.put("country_code", "JP");
//    refObj.put("patent_number", "2008518610");
//    refObj.put("patent_year", "2008");
//    references.add(refObj);
//
//    refObj = new JSONObject();
//    refObj.put("src", "Patent");
//    refObj.put("country_code", "EP");
//    refObj.put("patent_number", "2904117");
//    refObj.put("patent_year", "2015");
//    references.add(refObj);
//
//    refObj = new JSONObject();
//    refObj.put("src", "Patent");
//    refObj.put("country_code", "EP");
//    refObj.put("patent_number", "1731531");
//    refObj.put("patent_year", "2006");
//    references.add(refObj);
//
//    Seq fullTestSeq = new Seq(93766L, "2.4.1.8", 4000006340L, "Thermus sp.", protSeqFullNull, references,
//        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
//
//    compareSeqs("for testProteinFullNull (query by ec, seq, org; database match exists)", fullTestSeq, seqs.get(93766L));
//  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null and
   * the protein file has all fields of information
   */
  @Test
  public void testProteinFullFull() {
    List<String> oldAccessions = Collections.singletonList("NUR84963");

    List<String> uniprotAccessions = Collections.singletonList("O35403");

    List<String> genbankNucleotideAccessions = Collections.singletonList("AF026075");

    List<String> genbankProteinAccessions = Collections.singletonList("AAB82293");

    List<String> accessions = new ArrayList<>();
    accessions.addAll(oldAccessions);
    accessions.addAll(uniprotAccessions);
    accessions.addAll(genbankNucleotideAccessions);
    accessions.addAll(genbankProteinAccessions);


    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("accession_sources", Collections.singletonList("uniprot"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1", "St3a1", "Sult3a1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1", "Amine sulfotransferase"));
    metadata.put("name", "SULT1A1");

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();

    List<String> oldPmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620");

    List<String> newPmids = Collections.singletonList("9647753");

    List<String> pmids = new ArrayList<>();
    pmids.addAll(oldPmids);
    pmids.addAll(newPmids);

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    Seq fullTestSeq2 = new Seq(93766L, "2.8.2.3", 4000003474L, "Mus musculus", protSeqFullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    compareSeqs("for testProteinFullFull (query by ec, seq, org; database match exists)", fullTestSeq2, seqs.get(93766L));
  }

  /**
   * Tests the case where the protein file does have an EC_number listed and so a normal query to the database is
   * performed, but no database match exists. Also tests the addition of more than one new organism to the database
   * and the assignment of orgId.
   */
  @Test
  public void testProteinEcSeqOrgQuery() {
    String protSeqEcSeqOrgQuery = "MSTTGQIIRCKAAVAWEAGKPLVIEEVEVAPPQKHEVRIKILFTSLCHTDVYFWEAKGQT" +
        "PLFPRIFGHEAGGIVESVGEGVTDLQPGDHVLPIFTGECGECRHCHSEESNMCDLLRINT" +
        "ERGGMIHDGESRFSINGKPIYHFLGTSTFSEYTVVHSGQVAKINPDAPLDKVCIVSCGLS" +
        "TGLGATLNVAKPKKGQSVAIFGLGAVGLGAAEGARIAGASRIIGVDFNSKRFDQAKEFGV" +
        "TECVNPKDHDKPIQQVIAEMTDGGVDRSVECTGSVQAMIQAFECVHDGWGVAVLVGVPSK" +
        "DDAFKTHPMNFLNERTLKGTFFGNYKPKTDIPGVVEKYMNKELELEKFITHTVPFSEINK" +
        "AFDYMLKGESIRCIITMGA";

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

    List<String> accessions = new ArrayList<>();
    accessions.addAll(uniprotAccessions);
    accessions.addAll(genbankNucleotideAccessions);
    accessions.addAll(genbankProteinAccessions);

    JSONObject metadata = new JSONObject();
    metadata.put("proteinExistence", new JSONObject());
    metadata.put("accession", accessions);
    metadata.put("comment", new ArrayList());
    metadata.put("accession_sources", Collections.singletonList("uniprot"));
    metadata.put("synonyms", Arrays.asList("ADH"));
    metadata.put("product_names", Arrays.asList("Alcohol dehydrogenase class-P"));
    metadata.put("name", "ADH1");
    metadata.put("nucleotide_accession", new ArrayList());

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();

    List<String> pmids = Arrays.asList("2937058", "7851777", "8844162", "8587508", "11018155", "11158375", "11130712",
        "14593172", "10382288", "3377754", "2277648", "12231733", "8787023", "9522467", "9611167", "9880346",
        "11402191", "11402202", "11987307", "12509334", "12857811", "16055689", "18433157", "18441225", "19245862",
        "20508152", "22223895", "23707506", "24395201", "26566261", "25447145");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    Seq proteinEcSeqOrgTestQuery = new Seq(82934L, "1.1.1.1", 2L, "Arabidopsis thaliana", protSeqEcSeqOrgQuery, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(protSeqEcSeqOrgQuery)) {
        compareSeqs("for testProteinEcSeqOrgQuery (query by ec, org, seq with no database match)", proteinEcSeqOrgTestQuery,
            seqentry.getValue());
      }
    }

  }

// Not sure if this test will be necessary at all because all Uniprot files may come with EC numbers?
// ========================================================================================================
//  /**
//   * Tests the case where the protein file doesn't have an EC_number listed and instead the query to the database must
//   * be performed by accession number, both in the case when a database match exists and when it doesn't.
//   */
//  @Test
//  public void testProteinAccessionQuery() {
//    Map<Long, Seq> seqs = mockAPI.getSeqMap();
//
//    List<JSONObject> references = new ArrayList<>();
//    JSONObject refObj = new JSONObject();
//    refObj.put("src", "PMID");
//    refObj.put("val", "26889041");
//    references.add(refObj);
//
//    JSONObject metadata = new JSONObject();
//    metadata.put("accession", Arrays.asList("AKJ32561"));
//    metadata.put("accession_sources", Arrays.asList("genbank"));
//    metadata.put("product_names", Arrays.asList("envelope glycoprotein GP2"));
//    metadata.put("name", "ORF2");
//
//    Seq proteinAccessionTestQuery1 = new Seq(89045L, null, 5L,
//        "Porcine reproductive and respiratory syndrome virus", protSeqAccQuery1, references,
//        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
//
//    references = new ArrayList<>();
//    refObj = new JSONObject();
//    refObj.put("src", "PMID");
//    refObj.put("val", "27268727");
//    references.add(refObj);
//
//    metadata = new JSONObject();
//    metadata.put("accession", Arrays.asList("AEJ31929"));
//    metadata.put("accession_sources", Arrays.asList("genbank"));
//    metadata.put("synonyms", new ArrayList());
//    metadata.put("product_names", Arrays.asList("transcriptional regulator PadR-like family protein"));
//    metadata.put("nucleotide_accession", new ArrayList());
//    metadata.put("proteinExistence", new JSONObject());
//    metadata.put("comment", new ArrayList());
//
//    Seq proteinAccessionTestQuery2 = new Seq(79542L, null, 6L, "uncultured microorganism", protSeqAccQuery2,
//        references, MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
//
//    compareSeqs("for testProteinAccessionQuery (query by accession; database match exists)", proteinAccessionTestQuery1,
//        seqs.get(89045L));
//
//    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
//      if (seqentry.getValue().get_sequence().equals(protSeqAccQuery2)) {
//        compareSeqs("for testProteinAccessionQuery (query by accession with no database match)", proteinAccessionTestQuery2,
//            seqentry.getValue());
//      }
//    }
//  }

  private void compareSeqs(String message, Seq expectedSeq, Seq testSeq) {
    assertEquals("comparing ec " + message, expectedSeq.get_ec(), testSeq.get_ec());
    assertEquals("comparing org_id " + message, expectedSeq.getOrgId(), testSeq.getOrgId());
    assertEquals("comparing organism " + message, expectedSeq.get_org_name(), testSeq.get_org_name());
    assertEquals("comparing sequence " + message, expectedSeq.get_sequence(), testSeq.get_sequence());
    assertEquals("comparing references " + message, expectedSeq.get_references().toString(),
        testSeq.get_references().toString());
    assertEquals("comparing metadata " + message, expectedSeq.get_metadata().toString(),
        testSeq.get_metadata().toString());
    assertEquals("comapring src db " + message, expectedSeq.get_srcdb(), testSeq.get_srcdb());
  }

}
