package act.installer;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.Utils.OrgMinimalPrefixGenerator;
import com.act.biointerpretation.test.util.MockedMongoDB;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GenbankInstallerTest {

  private MockedMongoDB mockAPI;

  private static final String protSeqNullNull = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGL" +
      "CYELNSLLYYFLMDCGFQVYKVAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGTHILEMRKGANGESTNFLQ" +
      "SEPSHEWKVGYAFTLDPIDEKKVNNIQKVIVEHKESPFNKGAITCKLTDYGHVSLTNKNYTETFKGTKNKRPIESKDYAHILRESFGITQVKYVGKTLERG";

  private static final String protSeqNullFull = "MELIQDTSRPPLEYVKGVPLIKYFAEALGPLQSFQARPDDLLISTYPKSGTTWVSQILDMIYQGGDLE" +
      "KCHRAPIFMRVPFLEFKAPGIPSGMETLKDTPAPRLLKTHLPLALLPQTLLDQKVKVVYVARNAKDVAVSYYHFYHMAKVHPEPGTWDSFLEKFMVGEVSYGSWYQHVQE" +
      "WWELSRTHPVLYLFYEDMKENPKREIQKILEFVGRSLPEETVDFVVQHTSFKEMKKNPMTNYTTVPQEFMDHSISPFMRKGMAGDWKTTFTVAQNERFDADYAEKMAGCS" +
      "LSFRSEL";

  private static final String protSeqFullNull = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGL" +
      "CYELNSLLYYFLMDCGFQVYKVAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGT";

  private static final String protSeqFullFull = "MDNKDEYLLNFKGYNFQKTLVKMEVVENIENYEIRDDDIFIVTYPKSGTIWTQQILSLIYFEGHRNRT" +
      "ENIETIDRAPFFEYNIHKLDYAKMPSPRIFSSHIPYYLVPKGLKDKKAKILYMYRNPKDVLISYFHFSNLMLIFQNPDTVESFMQTFLDGDVVGSLWFDHIRGWYEHRHD" +
      "FNIMFMSFEDMKKDFRSSVLKICSFLEKELSEEDVDAVVRQATFQKMKADPRANYEHIIKDELGTRNEMGSFLRKGVVGAWKHYLTVDQSERFDKIFHRNMKNIPLKFIW" +
      "DINEE";

  private static final String protSeqEcSeqOrgQuery = "MDLLPREKDKLLLFTAALLAERRRARGLKLNYPEAIAFISSAVVEGAREGRTVAELMCYGATL" +
      "LTREDVMDGVAEMIHDIQVEATFADGTKLVTVHNPIP";

  private static final String protSeqAccQuery1 = "MKWGPCKAFFTKLANFLWMLSRSSWCPLLISLYFWPFCLASPSPVGWWSFASDWFAPRYSVRALPFT" +
      "LSNYRRSYEAFLSQCQVDIPTWGTKHPLGMLWHHKVSTLIDEMVSRRMYRIMEKAGQAAWKQVVSEATLSRISSLDVVAHFQHLAAIEAETCKYLASRLPMLHNLRMTGS" +
      "NVTIVYNSTLNQVFAIFPTPGSRPKLNDFQQWLIAVHSSIFSSVAASCTLFVVLWLRVPILRTVFGFRWLGAIFLSNSQ";

  private static final String protSeqAccQuery2 = "MTTRRRKLSELEGISLGIIYKQQPCTAYRIRSELKEAPSSHWRASAGSLYPLLVRLEAEGLVASTTD" +
      "KNDGRGRKLLKVTPQGRQSLKAWVMAGADQQLISSVTDPIRSRTFFLNVLAAPKRREYLDNLIVLTESYLSETKDHLEQKKMTGELFDYLGSLGAMKVTEARLDWLRVVR" +
      "KQS";

  private static final String dnaSeq1 = "MNLSPREKEKLLVSLAAMVARNRLARGVKLNHPEAIAIISDFVVEGAREGRSVADLMEAGAQVITRDQCMEGIAEM" +
      "IHSIQVEATFPDGTKLVTVHHPIR";

  private static final String dnaSeq2 = "MIPGEIFPAEGDIELNAGAATITLMVANTGDRPVQVGSHYHFAETNPGLVFDRTAARGYRLDIAAGTAVRFEPGQS" +
      "REVQLVPLSGARRVFGFNAKVMGEL";

  private static final String dnaSeq3 = "MPRLISRATYADMFGPTTGDKVRLADTDLIIEVEKDLTTYGEEVKFGGGKVIRDGMGQSQIPRSGGAMDTVITNAL" +
      "IVDHTGIYKADVGLRDGRIAGIGKAGNPDTQPGVTLIIGPGTEVIAGEGKILTAGGIDTHIHFICPQQIEDALASGITTMLGGGTGPAHGTLATTCTPGPWHISRMLQSF" +
      "EAFPMNLALAGKGNASLPEGLVEQVKAGACALKLHEDWGTTPAAIDCCLTVAEDMDVQVMIHTDTLNESGFVENTLAAFKGRTIHAFHTEGAGGGHAPDILKVVSSQNVI" +
      "PSSTNPTRPYTKNTVEEHLDMLMVCHHLDNKVPEDVAFAESRIRKETIAAEDILHDMGAMAVISSDSQAMGRVGEIIIRCWQTADKMRKQRGRLAEETGANDNFRVRRYI" +
      "AKYTINPAITHGLAEHVGSVEVGKRADLVLWHPAFFGAKPEMVLMGGMIVAAQMGDPNGSIPAQPFYTRPMFGAFGKALSNSAVTFVSAAAEAEGVAGKLGLSKTVLPVK" +
      "GTRTIGKASMRLNSATPQIEVDPETYEVRADGEILTCEPAETLPLAQRYFLY";

  private static final String dnaSeq4 = "MFDSATKPRLQRSHGQAAVAFEGARLKGLVQRGSAKALLPHVRGVPEVVFLNTSGGLTAGDTLRYGLDLDAGAKVV" +
      "ATTQAAERAYRAEGEAARVSVAHRVGQGGWLDWLPQETILFDRARLHRETTVDLAEDAGCLLLEAVVLGRAAMGETLHDLHFSDMRRINRSGKPVFLEPFLQNSNLLAKG" +
      "PRGALLGSARAFATLALCAQGAEDAVGPARAALTVPGVQAAASGFDGKCVVRLLAEDGWPLRQQILQLMGALRRGAPPPRVWQT";

  private static final String dnaSeq5 = "MTNGPLRVGIGGPVGAGKTTLTEQLCRALAGRLSMAVVTNDIYTREDAEALMRAQVLPADRIRGVETGGCPHTAIR" +
      "EDASINLAAIADLTRAHPDLELILIESGGDNLAATFSPELADLTIYVIDTAAGQDIPRKRGPGVTRSDLLVVNKTDLAPHVGVDPVLLEADTQRARGPRPYVMAQLRHGV" +
      "GIDEIVAFLIREGGLEQASAPA";

  private static final String dnaSeq6 = "MASERQALMLILLTTFFFTIKPSQASTTGGITIYWGQNIDDGTLTSTCDTGNFEIVNLAFLNAFGCGITPSWNFAG" +
      "HCGDWNPCSILEPQIQYCQQKGVKVFLSLGGAKGTYSLCSPEDAKEVANYLYQNFLSGKPGPLGSVTLEGIDFDIELGSNLYWGDLAKELDALRHQNDHYFYLSAAPQCF" +
      "MPDYHLDNAIKTGLFDHVNVQFYNNPPCQYSPGNTQLLFNSWDDWTSNVLPNNSVFFGLPASPDAAPSGGYIPPQVLISEVLPYVKQASNYGGVMLWDRYHDVLNYHSDQ" +
      "IKDYVPKYAMRFVTAVSDAIYESVSARTHRILQKKPY";

  @Before
  public void setUp() throws Exception {
    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("CUB13083")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);

    Seq emptyTestSeq = new Seq(91973L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    Seq emptyTestSeq3 = new Seq(91974L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("P50225")));

    metadata.remove("accession");
    metadata.put("accession", accessionObject);

    Seq emptyTestSeq2 = new Seq(29034L, "2.8.2.1", 4000002681L, "Homo sapiens", protSeqNullFull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("NUR84963")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
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

    JSONObject refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "JP");
    refObj.put("patent_number", "2008518610");
    refObj.put("patent_year", "2008");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "2904117");
    refObj.put("patent_year", "2015");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "1731531");
    refObj.put("patent_year", "2006");
    references.add(refObj);

    Seq fullTestSeq = new Seq(93766L, "2.4.1.8", 4000006340L, "Thermus sp.", protSeqFullNull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("O35403")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    Seq fullTestSeq2 = new Seq(82754L, "2.8.2.3", 4000003474L, "Mus musculus", protSeqFullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("AKJ32561")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);

    Seq proteinAccessionTestQuery = new Seq(89045L, null, 5L,
        "Porcine reproductive and respiratory syndrome virus", protSeqAccQuery1, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21065")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);

    Seq dnaTestSeq1 = new Seq(84937L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq1, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21066")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata.remove("accession");
    metadata.put("accession", accessionObject);

    Seq dnaTestSeq2 = new Seq(84938L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq2, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21067")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata.remove("accession");
    metadata.put("accession", accessionObject);

    Seq dnaTestSeq3 = new Seq(84939L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq3, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21064")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata.remove("accession");
    metadata.put("accession", accessionObject);

    Seq dnaTestSeq4 = new Seq(23849L, null, 4000005381L, "Rhodobacter capsulatus", dnaSeq4, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    mockAPI = new MockedMongoDB();

    Map<Long, String> orgNames = new HashMap<>();
    orgNames.put(4000005381L, "Rhodobacter capsulatus");
    orgNames.put(4000003474L, "Mus musculus");
    orgNames.put(4000006340L, "Thermus sp.");
    orgNames.put(4000002681L, "Homo sapiens");
    orgNames.put(4000000648L, "Bacillus cereus");

    mockAPI.installMocks(new ArrayList<Reaction>(),
        Arrays.asList(emptyTestSeq, emptyTestSeq2, emptyTestSeq3, fullTestSeq, fullTestSeq2, proteinAccessionTestQuery,
            dnaTestSeq1, dnaTestSeq2, dnaTestSeq3, dnaTestSeq4),
        orgNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    Map<String, Long> orgMap = new HashMap<>();

    // manually assemble an Org Iterator since you can't mock DBCollection in getDbIteratorOverOrgs()
    List<Organism> orgs = new ArrayList<>();
    for (Map.Entry<Long, String> orgName : orgNames.entrySet()) {
      orgs.add(new Organism(orgName.getKey(), -1, orgName.getValue()));
    }

    Iterator<Organism> orgIterator = orgs.iterator();

    while (orgIterator.hasNext()) {
      Organism org = orgIterator.next();
      orgMap.put(org.getName(), 1L);
    }

    OrgMinimalPrefixGenerator prefixGenerator = new OrgMinimalPrefixGenerator(orgMap);
    Map<String, String> minimalPrefixMapping = prefixGenerator.getMinimalPrefixMapping();

    GenbankInstaller genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_protein.gb").getFile()), "Protein", mockDb,
        minimalPrefixMapping);
    genbankInstaller.init();

    genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_dna.gb").getFile()), "DNA", mockDb,
        minimalPrefixMapping);
    genbankInstaller.init();

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null and the
   * information acquired from the protein file is also null. Also tests that ec, seq, org queries can match with
   * multiple sequences in the database.
   */
  @Test
  public void testProteinNullNull() {
    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("CUB13083")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);

    Map<Long, Seq> seqs = mockAPI.getSeqMap();
    Seq emptyTestSeq = new Seq(91973L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testProteinNullNull (query by ec, seq, org; database match exists)", emptyTestSeq,
        seqs.get(91973L));
    compareSeqs("for testProteinNullNull (query by ec, seq, org; database match exists)", emptyTestSeq,
        seqs.get(91974L));

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null but
   * the protein file has all fields of information
   */
  @Test
  public void testProteinNullFull() {
    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("P50225")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);
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

    JSONObject refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "JP");
    refObj.put("patent_number", "2008518610");
    refObj.put("patent_year", "2008");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "2904117");
    refObj.put("patent_year", "2015");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "1731531");
    refObj.put("patent_year", "2006");
    references.add(refObj);

    Seq testSeq = new Seq(29034L, "2.8.2.1", 4000002681L, "Homo sapiens", protSeqNullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testProteinNullFull; (query by ec, seq, org; database match exists)", testSeq, seqs.get(29034L));

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null but
   * the information acquired from the protein file is null
   */
  @Test
  public void testProteinFullNull() {
    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("NUR84963")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);
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

    JSONObject refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "JP");
    refObj.put("patent_number", "2008518610");
    refObj.put("patent_year", "2008");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "2904117");
    refObj.put("patent_year", "2015");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "1731531");
    refObj.put("patent_year", "2006");
    references.add(refObj);

    Seq fullTestSeq = new Seq(93766L, "2.4.1.8", 4000006340L, "Thermus sp.", protSeqFullNull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testProteinFullNull (query by ec, seq, org; database match exists)", fullTestSeq,
        seqs.get(93766L));
  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null and
   * the protein file has all fields of information
   */
  @Test
  public void testProteinFullFull() {
    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("O35403")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);
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

    JSONObject refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "JP");
    refObj.put("patent_number", "2008518610");
    refObj.put("patent_year", "2008");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "2904117");
    refObj.put("patent_year", "2015");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "1731531");
    refObj.put("patent_year", "2006");
    references.add(refObj);

    JSONObject pmid_obj = new JSONObject();
    pmid_obj.put("src", "PMID");
    pmid_obj.put("val", "9647753");
    references.add(pmid_obj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "WO");
    refObj.put("patent_number", "8472927");
    refObj.put("patent_year", "2009");
    references.add(refObj);

    Seq fullTestSeq2 = new Seq(82754L, "2.8.2.3", 4000003474L, "Mus musculus", protSeqFullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testProteinFullFull (query by ec, seq, org; database match exists)", fullTestSeq2,
        seqs.get(82754L));
  }

  /**
   * Tests the case where the protein file does have an EC_number listed and so a normal query to the database is
   * performed, but no database match exists.
   */
  @Test
  public void testProteinEcSeqOrgQuery() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("AKK24634")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", Collections.singletonList("urease subunit gamma"));
    metadata.put("xref", new JSONObject());

    Seq proteinEcSeqOrgTestQuery = new Seq(89342L, "3.5.1.5", 7L,
        "Pandoraea oxalativorans", protSeqEcSeqOrgQuery, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(protSeqEcSeqOrgQuery)) {
        compareSeqs("for testProteinEcSeqOrgQuery (query by ec, org, seq with no database match)",
            proteinEcSeqOrgTestQuery, seqentry.getValue());
      }
    }

  }

  /**
   * Tests the case where the protein file doesn't have an EC_number listed and instead the query to the database must
   * be performed by accession number, both in the case when a database match exists and when it doesn't. Also tests the
   * addition of more than one new organism to the database and the assignment of orgId.
   */
  @Test
  public void testProteinAccessionQuery() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();
    JSONObject refObj = new JSONObject();
    refObj.put("src", "PMID");
    refObj.put("val", "26889041");
    references.add(refObj);

    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("AKJ32561")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("product_names", Collections.singletonList("envelope glycoprotein GP2"));
    metadata.put("name", "ORF2");

    Seq proteinAccessionTestQuery1 = new Seq(89045L, null, 5L,
        "Porcine reproductive and respiratory syndrome virus", protSeqAccQuery1, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    references = new ArrayList<>();
    refObj = new JSONObject();
    refObj.put("src", "PMID");
    refObj.put("val", "27268727");
    references.add(refObj);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("AEJ31929")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", Collections.singletonList("transcriptional regulator PadR-like family protein"));
    metadata.put("xref", new JSONObject());

    Seq proteinAccessionTestQuery2 = new Seq(79542L, null, 6L, "uncultured microorganism", protSeqAccQuery2,
        references, MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testProteinAccessionQuery (query by accession; database match exists)", proteinAccessionTestQuery1,
        seqs.get(89045L));

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(protSeqAccQuery2)) {
        compareSeqs("for testProteinAccessionQuery (query by accession with no database match)",
            proteinAccessionTestQuery2, seqentry.getValue());
      }
    }
  }

  @Test
  public void testDnaInstall() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();

    JSONObject refObj = new JSONObject();
    refObj.put("src", "PMID");
    refObj.put("val", "9484481");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "JP");
    refObj.put("patent_number", "2008518610");
    refObj.put("patent_year", "2008");
    references.add(refObj);

    refObj = new JSONObject();
    refObj.put("src", "Patent");
    refObj.put("country_code", "EP");
    refObj.put("patent_number", "2904117");
    refObj.put("patent_year", "2015");
    references.add(refObj);

    JSONObject accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21065")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("product_names", Collections.singletonList("gamma subunit of urase"));
    metadata.put("name", "ureA");


    Seq dnaTestSeq1 = new Seq(84937L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq1, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21066")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("product_names", Collections.singletonList("beta subunit of urease"));
    metadata.put("name", "ureB");

    Seq dnaTestSeq2 = new Seq(84938L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq2, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21067")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("product_names", Collections.singletonList("alpha subunit of urease"));
    metadata.put("name", "ureC");

    Seq dnaTestSeq3 = new Seq(84939L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq3, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21064")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("name", "ureD");

    Seq dnaTestSeq4 = new Seq(23849L, null, 4000005381L, "Rhodobacter capsulatus", dnaSeq4, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAB21071")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("name", "ureG");
    metadata.put("xref", new JSONObject());
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", new ArrayList());

    Seq dnaTestSeq5 = new Seq(23894L, null, 4000005381L, "Rhodobacter capsulatus", dnaSeq5, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    accessionObject = new JSONObject();
    accessionObject.put("genbank_protein", new JSONArray(Collections.singletonList("BAA25015")));
    accessionObject.put("genbank_nucleotide", new JSONArray(Collections.singletonList("AB006984")));

    metadata = new JSONObject();
    metadata.put("accession", accessionObject);
    metadata.put("xref", new JSONObject());
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", Collections.singletonList("class III acidic endochitinase"));

    Seq dnaTestSeq6 = new Seq(89345L, "3.2.1.14", 4000005381L, "Rhodobacter capsulatus", dnaSeq6, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testDnaInstall (query by ec, seq, org; database match exists)", dnaTestSeq1, seqs.get(84937L));
    compareSeqs("for testDnaInstall (query by ec, seq, org; database match exists)", dnaTestSeq2, seqs.get(84938L));
    compareSeqs("for testDnaInstall (query by ec, seq, org; database match exists)", dnaTestSeq3, seqs.get(84939L));
    compareSeqs("for testDnaInstall (query by accession; database match exists)", dnaTestSeq4, seqs.get(23849L));

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(dnaSeq5)) {
        compareSeqs("for testDnaInstall (query by accession with no database match)", dnaTestSeq5, seqentry.getValue());
        continue;
      }

      if (seqentry.getValue().get_sequence().equals(dnaSeq6)) {
        compareSeqs("for testDnaInstall (query by ec, seq, org with no database match)", dnaTestSeq6,
            seqentry.getValue());
      }
    }
  }

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
