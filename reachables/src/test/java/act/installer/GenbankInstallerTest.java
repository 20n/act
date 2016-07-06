package act.installer;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.test.util.MockedMongoDBAPI;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GenbankInstallerTest {

  MockedMongoDBAPI mockAPI;

  String seq = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGLCYELNSLLYYFLMDCGFQVYK" +
      "VAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGTHILEMRKGANGESTNFLQSEPSHEWKV" +
      "GYAFTLDPIDEKKVNNIQKVIVEHKESPFNKGAITCKLTDYGHVSLTNKNYTETFKGTKNKRPIESKDYAHILRESFGITQVKYVGKTLERG";

  String seq2 = "MELIQDTSRPPLEYVKGVPLIKYFAEALGPLQSFQARPDDLLISTYPKSGTTWVSQILDMIYQGGDLEKCHRAPIFMRVPFLEFKAPG" +
      "IPSGMETLKDTPAPRLLKTHLPLALLPQTLLDQKVKVVYVARNAKDVAVSYYHFYHMAKVHPEPGTWDSFLEKFMVGEVSYGSWYQHVQEWWELSRTH" +
      "PVLYLFYEDMKENPKREIQKILEFVGRSLPEETVDFVVQHTSFKEMKKNPMTNYTTVPQEFMDHSISPFMRKGMAGDWKTTFTVAQNERFDADYAEKM" +
      "AGCSLSFRSEL";

  String seq3 = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGLCYELNSLLYYFLMDCGFQVYK" +
      "VAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGT";

  String seq4 = "MDNKDEYLLNFKGYNFQKTLVKMEVVENIENYEIRDDDIFIVTYPKSGTIWTQQILSLIYFEGHRNRTENIETIDRAPFFEYNIHKLDY" +
      "AKMPSPRIFSSHIPYYLVPKGLKDKKAKILYMYRNPKDVLISYFHFSNLMLIFQNPDTVESFMQTFLDGDVVGSLWFDHIRGWYEHRHDFNIMFMSFED" +
      "MKKDFRSSVLKICSFLEKELSEEDVDAVVRQATFQKMKADPRANYEHIIKDELGTRNEMGSFLRKGVVGAWKHYLTVDQSERFDKIFHRNMKNIPLKFI" +
      "WDINEE";

  @Before
  public void setUp() throws Exception {

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("CUB13083"));
    metadata.put("accession_sources", Arrays.asList("genbank"));

    Seq emptyTestSeq = new Seq(91973L, "2.3.1.5", 4000000648L, "Bacillus cereus", seq, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata.remove("accession");
    metadata.put("accession", Arrays.asList("P50225"));

    Seq emptyTestSeq2 = new Seq(29034L, "2.8.2.1", 4000002681L, "Homo sapiens", seq2, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("NUR84963"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
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

    JSONObject ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "JP");
    ref_obj.put("patent_number", "2008518610");
    ref_obj.put("patent_year", "2008");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "2904117");
    ref_obj.put("patent_year", "2015");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "1731531");
    ref_obj.put("patent_year", "2006");
    references.add(ref_obj);

    Seq fullTestSeq = new Seq(93766L, "2.4.1.8", 4000006340L, "Thermus sp.", seq3, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("O35403"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    Seq fullTestSeq2 = new Seq(82754L, "2.8.2.3", 4000003474L, "Mus musculus", seq4, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    mockAPI = new MockedMongoDBAPI();

    mockAPI.installMocks(new ArrayList<Reaction>(),
        Arrays.asList(emptyTestSeq, emptyTestSeq2, fullTestSeq, fullTestSeq2), new HashMap<>(), new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    GenbankInstaller genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_null_protein.gb").getFile()), "Protein", mockDb);
    genbankInstaller.init();

    genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_full_protein.gb").getFile()), "Protein", mockDb);
    genbankInstaller.init();

    genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_null_protein_2.gb").getFile()), "Protein", mockDb);
    genbankInstaller.init();

    genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_full_protein_2.gb").getFile()), "Protein", mockDb);
    genbankInstaller.init();

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null and the
   * information acquired from the protein file is also null
   */
  @Test
  public void testNullNull() {

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("CUB13083"));
    metadata.put("accession_sources", Arrays.asList("genbank"));

    Map<Long, Seq> seqs = mockAPI.getSeqMap();
    Seq emptyTestSeq = new Seq(91973L, "2.3.1.5", 4000000648L, "Bacillus cereus", seq, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for test NullNull", emptyTestSeq, seqs.get(91973L));

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null but
   * the protein file has all fields of information
   */
  @Test
  public void testNullFull() {

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("P50225"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();

    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    JSONObject ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "JP");
    ref_obj.put("patent_number", "2008518610");
    ref_obj.put("patent_year", "2008");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "2904117");
    ref_obj.put("patent_year", "2015");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "1731531");
    ref_obj.put("patent_year", "2006");
    references.add(ref_obj);

    Seq testSeq = new Seq(29034L, "2.8.2.1", 4000002681L, "Homo sapiens", seq2, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for test NullFul", testSeq, seqs.get(29034L));

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null but
   * the information acquired from the protein file is null
   */
  @Test
  public void testFullNull() {
    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("NUR84963"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();

    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    JSONObject ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "JP");
    ref_obj.put("patent_number", "2008518610");
    ref_obj.put("patent_year", "2008");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "2904117");
    ref_obj.put("patent_year", "2015");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "1731531");
    ref_obj.put("patent_year", "2006");
    references.add(ref_obj);

    Seq fullTestSeq = new Seq(93766L, "2.4.1.8", 4000006340L, "Thermus sp.", seq3, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for test FullNull", fullTestSeq, seqs.get(93766L));
  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null and
   * the protein file has all fields of information
   */
  @Test
  public void testFullFull() {
    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("O35403"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1", "St3a1", "Sult3a1", "ST3A1_MOUSE"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1", "Amine sulfotransferase"));
    metadata.put("name", "SULT1A1");

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();

    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620");

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    JSONObject ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "JP");
    ref_obj.put("patent_number", "2008518610");
    ref_obj.put("patent_year", "2008");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "2904117");
    ref_obj.put("patent_year", "2015");
    references.add(ref_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "EP");
    ref_obj.put("patent_number", "1731531");
    ref_obj.put("patent_year", "2006");
    references.add(ref_obj);

    JSONObject pmid_obj = new JSONObject();
    pmid_obj.put("src", "PMID");
    pmid_obj.put("val", "9647753");
    references.add(pmid_obj);

    ref_obj = new JSONObject();
    ref_obj.put("src", "Patent");
    ref_obj.put("country_code", "WO");
    ref_obj.put("patent_number", "8472927");
    ref_obj.put("patent_year", "2009");
    references.add(ref_obj);

    Seq fullTestSeq2 = new Seq(82754L, "2.8.2.3", 4000003474L, "Mus musculus", seq4, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testFullFull", fullTestSeq2, seqs.get(82754L));

  }

  private void compareSeqs(String message, Seq expectedSeq, Seq testSeq) {
    assertEquals("comparing id " + message, expectedSeq.getUUID(), testSeq.getUUID());
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
