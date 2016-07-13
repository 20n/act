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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GenbankInstallerTest {

  MockedMongoDB mockAPI;

  String protSeqNullNull = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGLCYELNSLLYYFLMDCGFQVYK" +
      "VAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGTHILEMRKGANGESTNFLQSEPSHEWKV" +
      "GYAFTLDPIDEKKVNNIQKVIVEHKESPFNKGAITCKLTDYGHVSLTNKNYTETFKGTKNKRPIESKDYAHILRESFGITQVKYVGKTLERG";

  String protSeqNullFull = "MELIQDTSRPPLEYVKGVPLIKYFAEALGPLQSFQARPDDLLISTYPKSGTTWVSQILDMIYQGGDLEKCHRAPIFMRVPFLEFKAPG" +
      "IPSGMETLKDTPAPRLLKTHLPLALLPQTLLDQKVKVVYVARNAKDVAVSYYHFYHMAKVHPEPGTWDSFLEKFMVGEVSYGSWYQHVQEWWELSRTH" +
      "PVLYLFYEDMKENPKREIQKILEFVGRSLPEETVDFVVQHTSFKEMKKNPMTNYTTVPQEFMDHSISPFMRKGMAGDWKTTFTVAQNERFDADYAEKM" +
      "AGCSLSFRSEL";

  String protSeqFullNull = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGLCYELNSLLYYFLMDCGFQVYK" +
      "VAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGT";

  String protSeqFullFull = "MDNKDEYLLNFKGYNFQKTLVKMEVVENIENYEIRDDDIFIVTYPKSGTIWTQQILSLIYFEGHRNRTENIETIDRAPFFEYNIHKLDY" +
      "AKMPSPRIFSSHIPYYLVPKGLKDKKAKILYMYRNPKDVLISYFHFSNLMLIFQNPDTVESFMQTFLDGDVVGSLWFDHIRGWYEHRHDFNIMFMSFED" +
      "MKKDFRSSVLKICSFLEKELSEEDVDAVVRQATFQKMKADPRANYEHIIKDELGTRNEMGSFLRKGVVGAWKHYLTVDQSERFDKIFHRNMKNIPLKFI" +
      "WDINEE";

  String protSeqAccQuery1 = "MKWGPCKAFFTKLANFLWMLSRSSWCPLLISLYFWPFCLASPSPVGWWSFASDWFAPRYSVRALPFTLSNYRRSYEAFLSQCQVDIPTW" +
      "GTKHPLGMLWHHKVSTLIDEMVSRRMYRIMEKAGQAAWKQVVSEATLSRISSLDVVAHFQHLAAIEAETCKYLASRLPMLHNLRMTGSNVTIVYNSTLNQVFAIFPTPGS" +
      "RPKLNDFQQWLIAVHSSIFSSVAASCTLFVVLWLRVPILRTVFGFRWLGAIFLSNSQ";

  String protSeqAccQuery2 = "MTTRRRKLSELEGISLGIIYKQQPCTAYRIRSELKEAPSSHWRASAGSLYPLLVRLEAEGLVASTTDKNDGRGRKLLKVTPQGRQSLKA" +
      "WVMAGADQQLISSVTDPIRSRTFFLNVLAAPKRREYLDNLIVLTESYLSETKDHLEQKKMTGELFDYLGSLGAMKVTEARLDWLRVVRKQS";

  String dnaSeq1 = "MNLSPREKEKLLVSLAAMVARNRLARGVKLNHPEAIAIISDFVVEGAREGRSVADLMEAGAQVITRDQCMEGIAEMIHSIQVEATFPDGTKLVTVHH" +
      "PIR";

  String dnaSeq2 = "MIPGEIFPAEGDIELNAGAATITLMVANTGDRPVQVGSHYHFAETNPGLVFDRTAARGYRLDIAAGTAVRFEPGQSREVQLVPLSGARRVFGFNAKV" +
      "MGEL";

  String dnaSeq3 = "MPRLISRATYADMFGPTTGDKVRLADTDLIIEVEKDLTTYGEEVKFGGGKVIRDGMGQSQIPRSGGAMDTVITNALIVDHTGIYKADVGLRDGRIAG" +
      "IGKAGNPDTQPGVTLIIGPGTEVIAGEGKILTAGGIDTHIHFICPQQIEDALASGITTMLGGGTGPAHGTLATTCTPGPWHISRMLQSFEAFPMNLALAGKGNASLPEGL" +
      "VEQVKAGACALKLHEDWGTTPAAIDCCLTVAEDMDVQVMIHTDTLNESGFVENTLAAFKGRTIHAFHTEGAGGGHAPDILKVVSSQNVIPSSTNPTRPYTKNTVEEHLDM" +
      "LMVCHHLDNKVPEDVAFAESRIRKETIAAEDILHDMGAMAVISSDSQAMGRVGEIIIRCWQTADKMRKQRGRLAEETGANDNFRVRRYIAKYTINPAITHGLAEHVGSVE" +
      "VGKRADLVLWHPAFFGAKPEMVLMGGMIVAAQMGDPNGSIPAQPFYTRPMFGAFGKALSNSAVTFVSAAAEAEGVAGKLGLSKTVLPVKGTRTIGKASMRLNSATPQIEV" +
      "DPETYEVRADGEILTCEPAETLPLAQRYFLY";

  String dnaSeq4 = "MFDSATKPRLQRSHGQAAVAFEGARLKGLVQRGSAKALLPHVRGVPEVVFLNTSGGLTAGDTLRYGLDLDAGAKVVATTQAAERAYRAEGEAARVSV" +
      "AHRVGQGGWLDWLPQETILFDRARLHRETTVDLAEDAGCLLLEAVVLGRAAMGETLHDLHFSDMRRINRSGKPVFLEPFLQNSNLLAKGPRGALLGSARAFATLALCAQG" +
      "AEDAVGPARAALTVPGVQAAASGFDGKCVVRLLAEDGWPLRQQILQLMGALRRGAPPPRVWQT";

  String dnaSeq5 = "MTNGPLRVGIGGPVGAGKTTLTEQLCRALAGRLSMAVVTNDIYTREDAEALMRAQVLPADRIRGVETGGCPHTAIREDASINLAAIADLTRAHPDLE" +
      "LILIESGGDNLAATFSPELADLTIYVIDTAAGQDIPRKRGPGVTRSDLLVVNKTDLAPHVGVDPVLLEADTQRARGPRPYVMAQLRHGVGIDEIVAFLIREGGLEQASAPA";

  String dnaSeq6 = "MASERQALMLILLTTFFFTIKPSQASTTGGITIYWGQNIDDGTLTSTCDTGNFEIVNLAFLNAFGCGITPSWNFAGHCGDWNPCSILEPQIQYCQQK" +
      "GVKVFLSLGGAKGTYSLCSPEDAKEVANYLYQNFLSGKPGPLGSVTLEGIDFDIELGSNLYWGDLAKELDALRHQNDHYFYLSAAPQCFMPDYHLDNAIKTGLFDHVNVQ" +
      "FYNNPPCQYSPGNTQLLFNSWDDWTSNVLPNNSVFFGLPASPDAAPSGGYIPPQVLISEVLPYVKQASNYGGVMLWDRYHDVLNYHSDQIKDYVPKYAMRFVTAVSDAIY" +
      "ESVSARTHRILQKKPY";

  @Before
  public void setUp() throws Exception {

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("CUB13083"));
    metadata.put("accession_sources", Arrays.asList("genbank"));

    Seq emptyTestSeq = new Seq(91973L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata.remove("accession");
    metadata.put("accession", Arrays.asList("P50225"));

    Seq emptyTestSeq2 = new Seq(29034L, "2.8.2.1", 4000002681L, "Homo sapiens", protSeqNullFull, new ArrayList<>(),
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

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("O35403"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    Seq fullTestSeq2 = new Seq(82754L, "2.8.2.3", 4000003474L, "Mus musculus", protSeqFullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("AKJ32561"));
    metadata.put("accession_sources", Arrays.asList("genbank"));

    Seq proteinAccessionTestQuery = new Seq(89045L, null, 5000000005L,
        "Porcine reproductive and respiratory syndrome virus", protSeqAccQuery1, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAB21065"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));

    Seq dnaTestSeq1 = new Seq(84937L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq1, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata.remove("accession");
    metadata.put("accession", Arrays.asList("BAB21066"));

    Seq dnaTestSeq2 = new Seq(84938L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq2, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata.remove("accession");
    metadata.put("accession", Arrays.asList("BAB21067"));

    Seq dnaTestSeq3 = new Seq(84939L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq3, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata.remove("accession");
    metadata.put("accession", Arrays.asList("BAB21064"));

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
        Arrays.asList(emptyTestSeq, emptyTestSeq2, fullTestSeq, fullTestSeq2, proteinAccessionTestQuery,
            dnaTestSeq1, dnaTestSeq2, dnaTestSeq3, dnaTestSeq4),
        orgNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    GenbankInstaller genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_protein.gb").getFile()), "Protein", mockDb);
    genbankInstaller.init();

    genbankInstaller = new GenbankInstaller(
        new File(this.getClass().getResource("genbank_installer_test_dna.gb").getFile()), "DNA", mockDb);
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
    Seq emptyTestSeq = new Seq(91973L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullNull, new ArrayList<>(),
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

    compareSeqs("for test NullFull", testSeq, seqs.get(29034L));

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

    compareSeqs("for testFullFull", fullTestSeq2, seqs.get(82754L));

  }

  /**
   * Tests the case where the protein file doesn't have an EC_number listed and instead the query to the database must
   * be performed by accession number
   */
  @Test
  public void testProteinAccessionQuery() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<JSONObject> references = new ArrayList<>();
    JSONObject refObj = new JSONObject();
    refObj.put("src", "PMID");
    refObj.put("val", "26889041");
    references.add(refObj);

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("AKJ32561"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("product_names", Arrays.asList("envelope glycoprotein GP2"));
    metadata.put("name", "ORF2");

    Seq proteinAccessionTestQuery1 = new Seq(89045L, null, 5000000005L,
        "Porcine reproductive and respiratory syndrome virus", protSeqAccQuery1, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    references = new ArrayList<>();
    refObj = new JSONObject();
    refObj.put("src", "PMID");
    refObj.put("val", "27268727");
    references.add(refObj);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("AEJ31929"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", Arrays.asList("transcriptional regulator PadR-like family protein"));
    metadata.put("nucleotide_accession", new ArrayList());
    metadata.put("proteinExistence", new JSONObject());
    metadata.put("comment", new ArrayList());

    Seq proteinAccessionTestQuery2 = new Seq(79542L, null, 5000000006L, "uncultured microorganism", protSeqAccQuery2,
        references, MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    compareSeqs("for testProteinAccessionQuery (query by accession; database match exists)", proteinAccessionTestQuery1,
        seqs.get(89045L));

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(protSeqAccQuery2)) {
        compareSeqs("for testProteinAccessionQuery (query by accession with no database match)", proteinAccessionTestQuery2,
            seqentry.getValue());
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

    JSONObject metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAB21065"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("product_names", Arrays.asList("gamma subunit of urase"));
    metadata.put("name", "ureA");
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));


    Seq dnaTestSeq1 = new Seq(84937L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq1, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAB21066"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("product_names", Arrays.asList("beta subunit of urease"));
    metadata.put("name", "ureB");
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));

    Seq dnaTestSeq2 = new Seq(84938L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq2, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAB21067"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("product_names", Arrays.asList("alpha subunit of urease"));
    metadata.put("name", "ureC");
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));

    Seq dnaTestSeq3 = new Seq(84939L, "3.5.1.5", 4000005381L, "Rhodobacter capsulatus", dnaSeq3, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAB21064"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("name", "ureD");
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));

    Seq dnaTestSeq4 = new Seq(23849L, null, 4000005381L, "Rhodobacter capsulatus", dnaSeq4, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAB21071"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("name", "ureG");
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));
    metadata.put("proteinExistence", new JSONObject());
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", new ArrayList());
    metadata.put("comment", new ArrayList());

    Seq dnaTestSeq5 = new Seq(23894L, null, 4000005381L, "Rhodobacter capsulatus", dnaSeq5, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    metadata = new JSONObject();
    metadata.put("accession", Arrays.asList("BAA25015"));
    metadata.put("accession_sources", Arrays.asList("genbank"));
    metadata.put("nucleotide_accession", Arrays.asList("AB006984"));
    metadata.put("proteinExistence", new JSONObject());
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", Arrays.asList("class III acidic endochitinase"));
    metadata.put("comment", new ArrayList());

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
        compareSeqs("for testDnaInstall (query by ec, seq, org with no database match)", dnaTestSeq6, seqentry.getValue());
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
