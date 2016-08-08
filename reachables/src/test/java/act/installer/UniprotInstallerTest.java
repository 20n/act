package act.installer;

import act.server.MongoDB;
import act.shared.Organism;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.Utils.OrgMinimalPrefixGenerator;
import com.act.biointerpretation.test.util.MockedMongoDB;
import com.mongodb.BasicDBObject;
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


public class UniprotInstallerTest {

  private MockedMongoDB mockAPI;

  private String protSeqNullNull = "MFTQYRKTLLAGTLALTFGLAAGNSLAAGFQPAQPAGKLGAIVVDPYGNAPLTALVELDS" +
      "HVISDVKVTVHGKGEKGVPVTYTVGKESLATYDGIPIFGLYQKFANKVTVEYKENGKAMK" +
      "DDYVVQTSAIVNHYMDNRSISDLQQTKVIKVAPGFEDRLYLVNTHTFTPQGAEFHWHGEK" +
      "DKNAGILDAGPAGGALPFDIAPFTFVVDTEGEYRWWLDQDTFYDGHDMDINKRGYLMGIR" +
      "ETPRGTFTAVQGQHWYEFDMLGQILADHKLPRGFLDASHESVETVNGTVLLRVGKRDYRK" +
      "EDGLHVHTIRDQIIEVDKSGRVVDVWDLTQILDPMRDALLGALDAGAVCVNVDLAHAGQQ" +
      "AKLEPDTPYGDALGVGAGRNWAHVNSIAYDAKDDSIILSSRHQGVVKIGRDKQVKWILAP" +
      "SKGWNKALASKLLKPVDDKGNALKCDENGKCENTDFDFTYTQHTAWLSSKGTLTIFDNGD" +
      "GRGLEQPALPTMKYSRFVEYKIDEKKGTVQQVWEYGKERGYDFYSPITSVIEYQKDRDTM" +
      "FGFGGSINLFDVGQPTIGKINEIDYKTKEVKVEIDVLSDKPNQTHYRALLVRPQQMFK";

  private String protSeqFullNull = "MADLPTASIDMILCDLPYGTTANAWDKVIPFEYLWGQYERLIKPQGAIVLTATERFSADL" +
      "VQSNPALYRYKWVWIKNTVTNFVNAKNRPLSRFEEILVFSKSGTANFGNSPDTRGMNYFP" +
      "QGLLPYNKTVNSRKYENANQMHPWNAPDSYTQEWTKYPSDVLNYKSDRTGWHPTQKPVDL" +
      "FAYLIKTYTQPGEIVLDNCMGSGTTAIAAMDTDRHFIGYEISEEYWRRALDRIKHHHATQ" +
      "TELF";

  private String protSeqFullFull = "MDNKDEYLLNFKGYNFQKTLVKMEVVENIENYEIRDDDIFIVTYPKSGTIWTQQILSLIYFEGHRNRTENIETIDRAPFF" +
      "EYNIHKLDYAKMPSPRIFSSHIPYYLVPKGLKDKKAKILYMYRNPKDVLISYFHFSNLMLIFQNPDTVESFMQTFLDGDVVGSLWFDHIRGWYEHRHDFNIMFMSFEDM" +
      "KKDFRSSVLKICSFLEKELSEEDVDAVVRQATFQKMKADPRANYEHIIKDELGTRNEMGSFLRKGVVGAWKHYLTVDQSERFDKIFHRNMKNIPLKFIWDINEE";

  private String protSeqNullFull = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKI" +
      "LIQKRGGLCYELNSLLYYFLMDCGFQVYKVAGTVYDLYDNKWKPDDGHVIIVLTHNNKDY" +
      "VIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGTHILEMRKGANGESTNFLQSE" +
      "PSHEWKVGYAFTLDPIDEKKVNNIQKVIVEHKESPFNKGAITCKLTDYGHVSLTNKNYTE" +
      "TFKGTKNKRPIESKDYAHILRESFGITQVKYVGKTLERG";

  private String protSeqAccQuery = "MPSVAAVLLWHVIALLLVANLGYASSHDAKRLRAEVIYARNGAVATDDRRCSRIGKDILL" +
      "EGGHAADAAVAAALCLGVVSPASSGLGGGAFMLLRQANGESKAFDMRETAPALASKDMYG" +
      "GNTTLKAQGGLSVAVPGELAGLHEAWKQYGKLPWKRLVNPAENLARRGFKISAYLHMQMK" +
      "STESDILQDKGLRSILAPNGKLLNIGDTCYNKKLADTLRAISVFGPKAFYDGLIGHNLVK" +
      "DVQNAGGILTTKDLKNYTVNQKKPLSTNVLGLNLLAMPPPSGGPPMILLLNILDQYKLPS" +
      "GLSGALGIHREIEALKHVFAVRMNLGDPDFVNITEVVSDMLSRRFATVLKNDINDNKTFS" +
      "PTHYGGKWNQIHDHGTSHLCVIDLERNAISMTTTVNAYFGSKILSPSTGIVLNNEMDDFS" +
      "IPRNVSKDVPPPAPSNFIMPGKRPLSSMSPTIALKDGKLKAVVGASGGAFIIGGTSEVLL" +
      "NHFGKGLDPFSSVTAPRVYHQLIPNVVNYENWTTVTGDHFELGADIRKVLRSKGHVLQSL" +
      "AGGTICQFIVVENSVSSRKTKVTGIERLVAVSDPRKGGLPAGF";

  private String nucSeqAccQuery = "FCSAADLMELDMAMEPDRKAAVSHWQQQSYLDSGIHSGATTTAPSLSGKGNPEEEDVDTS" +
      "QVLYEWEQGFSQSFTQEQVADIDGQYAMTRAQRVRAAMFPETLDEGMQIPSTQFDAAHPT" +
      "NVQRLAEPSQMLKHAVVNLINYQDDAELATRAIPELTKLLNDEDQVVVNKAAVMVHQLSK" +
      "KEASRHAIMRSPQMVSAIVRTMQNTNDVETARCTAGTLHNLSHHREGLLAIFKSGGIPAL" +
      "VKMLGSPVDSVLFYAITTLHNLLLHQEGAKMAVRLAGGLQKMVALLNKTNVKFLAITTDC" +
      "LQILAYGNQESKLIILASGGPQALVNIMRTYTYEKLLWTTSRVLKVLSVCSSNKPAIVEA" +
      "GGMQALGLHLTDPSQRLVQNCLWTLRNLSDAATKQEGMEGLLGTLVQLLGSDDINVVTCA" +
      "AGILSNLTCNNYKNKMMVCQVGGIEALVRTVLRAGDREDITEPAICALRHLTSRHQEAEM" +
      "AQNAVRLHYGLPVVVKLLHPPSHWPLIKATVGLIRNLALCPANHAPLREQGAIPRLVQLL" +
      "VRAHQDTQRRTSMGGTQQQFVEGVRMEEIVEGCTGALHILARDVHNRIVIRGLNTIPLFV" +
      "QLLYSPIENIQRVAAGVLCELAQDKEAAEAIEAEGATAPLTELLHSRNEGVATYAAAVLF" +
      "RMSEDKPQDYKKRLSVELTSSLFRTEPMAWNETADLGLDIGAQGEPLGYRQDDPSYRSFH" +
      "SGGYGQDTLGMDPMMEHEMGGHHPGADYPVDGLPDLGHAQDLMDGLPPGDSNQLAWFDTD" +
      "L";


  @Before
  public void setUp() throws Exception {

    Seq nullNullTestSeq = new Seq(21389L, "2.8.2.22", 4000001398L, "Citrobacter freundii", protSeqNullNull,
        new ArrayList<>(), new BasicDBObject(), Seq.AccDB.uniprot);

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), Collections.singletonList("234890"));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("synonyms", Arrays.asList("HYT1", "HYT"));
    metadata.put("product_names", Collections.singletonList("Methyltransferase"));
    metadata.put("name", "N422");

    List<JSONObject> references = new ArrayList<>();

    JSONObject obj = new JSONObject();
    obj.put("src", "PMID");
    obj.put("val", "24435875");
    references.add(obj);

    Seq fullNullTestSeq = new Seq(93482L, "2.1.1.1", 4000008473L, "Lactobacillus casei 5b", protSeqFullNull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), Collections.singletonList("NUR84963"));

    metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1"));
    metadata.put("product_names", Collections.singletonList("Sulfotransferase 1A1"));
    metadata.put("name", "SULT1A1");

    references = new ArrayList<>();

    List<String> pmids = Arrays.asList("8363592", "8484775", "8423770", "8033246", "7864863", "7695643", "7581483",
        "8912648", "8924211", "9855620");

    for (String pmid : pmids) {
      obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    Seq fullFullTestSeq = new Seq(93766L, "2.8.2.3", 4000003474L, "Mus musculus", protSeqFullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    accessions = new JSONObject();
    accessions.put(Seq.AccType.genbank_protein.toString(), Collections.singletonList("CUB13083"));

    metadata = new JSONObject();
    metadata.put("accession", accessions);

    Seq nullFullTestSeq = new Seq(38942L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullFull, new ArrayList<>(),
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    accessions = new JSONObject();
    accessions.put(Seq.AccType.genbank_protein.toString(), Collections.singletonList("ESW35608"));

    metadata = new JSONObject();
    metadata.put("accession", accessions);

    Seq protAccessionQueryTestSeq = new Seq(23894L, null, 4000004746L, "Phaseolus vulgaris", protSeqAccQuery,
        new ArrayList<>(), MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), Collections.singletonList("H0UZN6"));
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), Collections.singletonList("AAKN02012235"));

    metadata = new JSONObject();
    metadata.put("accession", accessions);

    Seq nucAccessionQueryTestSeq = new Seq(58923L, null, 4000001225L, "Cavia porcellus", nucSeqAccQuery,
        new ArrayList<>(), MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    mockAPI = new MockedMongoDB();

    Map<Long, String> orgNames = new HashMap<>();
    orgNames.put(4000003474L, "Mus musculus");
    orgNames.put(4000000648L, "Bacillus cereus");
    orgNames.put(4000004746L, "Phaseolus vulgaris");
    orgNames.put(4000001225L, "Cavia porcellus");
    orgNames.put(4000001398L, "Citrobacter freundii");
    orgNames.put(4000008473L, "Lactobacillus casei 5b");

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

    mockAPI.installMocks(new ArrayList<>(),
        Arrays.asList(nullNullTestSeq, fullNullTestSeq, fullFullTestSeq, nullFullTestSeq, protAccessionQueryTestSeq,
            nucAccessionQueryTestSeq), orgNames, new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    // loading test file for testProteinEcSeqOrgQuery
    UniprotInstaller uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_1.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testProteinFullFull
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_2.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testProteinNullFull
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_3.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testProteinAccessionQuery with database match
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_4.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testNucleotideAccessionQuery with database match
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_5.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testProteinAccessionQuery without database match
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_6.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testNucleotideAccessionQuery without database match
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_7.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testProteinNullNull
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_8.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

    // loading test file for testProteinFullNull
    uniprotInstaller = new UniprotInstaller(
        new File(this.getClass().getResource("uniprot_installer_test_9.xml").getFile()), mockDb, minimalPrefixMapping);
    uniprotInstaller.init();

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null and the
   * information acquired from the protein file is also null.
   */
  @Test
  public void testProteinNullNull() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), new ArrayList());
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), new ArrayList());
    accessions.put(Seq.AccType.genbank_protein.toString(), new ArrayList());

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);

    Seq nullNullTestSeq = new Seq(21389L, "2.8.2.22", 4000001398L, "Citrobacter freundii", protSeqNullNull,
        new ArrayList<>(), MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    compareSeqs("for testProteinNullNull; (query by ec, seq, org; database match exists)", nullNullTestSeq,
        seqs.get(21389L));

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are null but
   * the protein file has all fields of information
   */
  @Test
  public void testProteinNullFull() {

    List<String> oldAccessions = Collections.singletonList("CUB13083");

    List<String> uniprotAccessions = Collections.singletonList("A0A0K6JCJ7");

    List<String> genbankNucleotideAccessions = Collections.singletonList("CYHI01000402");

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), oldAccessions);

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("product_names", Collections.singletonList("Arylamine N-acetyltransferase"));
    metadata.put("name", "nat_1");
    metadata.put("catalytic_activity", "An aryl sulfate + a phenol = a phenol + an aryl sulfate.");

    List<String> pmids = Collections.singletonList("8493748");

    List<JSONObject> references = new ArrayList<>();

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    Seq nullFullTestSeq = new Seq(38942L, "2.3.1.5", 4000000648L, "Bacillus cereus", protSeqNullFull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    compareSeqs("for testProteinNullFull; (query by ec, seq, org; database match exists)", nullFullTestSeq,
        seqs.get(38942L));

  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null but
   * the information acquired from the protein file is null
   */
  @Test
  public void testProteinFullNull() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), Collections.singletonList("234890"));

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("synonyms", Arrays.asList("HYT1", "HYT"));
    metadata.put("product_names", Collections.singletonList("Methyltransferase"));
    metadata.put("name", "N422");

    List<JSONObject> references = new ArrayList<>();

    JSONObject obj = new JSONObject();
    obj.put("src", "PMID");
    obj.put("val", "24435875");
    references.add(obj);

    Seq fullNullTestSeq = new Seq(93482L, "2.1.1.1", 4000008473L, "Lactobacillus casei 5b", protSeqFullNull, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    compareSeqs("for testProteinFullNull (query by ec, seq, org; database match exists)", fullNullTestSeq,
        seqs.get(93482L));
  }

  /**
   * Tests the case where the existing reference list and metadata json object in the database are not null and
   * the protein file has all fields of information
   */
  @Test
  public void testProteinFullFull() {

    List<String> uniprotAccessions = Arrays.asList("NUR84963", "O35403");

    List<String> genbankNucleotideAccessions = Collections.singletonList("AF026075");

    List<String> genbankProteinAccessions = Collections.singletonList("AAB82293");

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), genbankProteinAccessions);

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("synonyms", Arrays.asList("STP", "STP1", "ST1A1", "St3a1", "Sult3a1"));
    metadata.put("product_names", Arrays.asList("Sulfotransferase 1A1", "Amine sulfotransferase"));
    metadata.put("name", "SULT1A1");
    metadata.put("catalytic_activity",
        "3'-phosphoadenylyl sulfate + an amine = adenosine 3',5'-bisphosphate + a sulfamate.");

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

    compareSeqs("for testProteinFullFull (query by ec, seq, org; database match exists)", fullTestSeq2,
        seqs.get(93766L));
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

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), genbankProteinAccessions);

    JSONObject metadata = new JSONObject();
    metadata.put("xref", new JSONObject());
    metadata.put("accession", accessions);
    metadata.put("synonyms", Arrays.asList("ADH"));
    metadata.put("product_names", Arrays.asList("Alcohol dehydrogenase class-P"));
    metadata.put("name", "ADH1");
    metadata.put("catalytic_activity", "An alcohol + NAD(+) = an aldehyde or ketone + NADH.");

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

    Seq proteinEcSeqOrgTestQuery = new Seq(82934L, "1.1.1.1", 6L, "Arabidopsis thaliana", protSeqEcSeqOrgQuery,
        references, MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(protSeqEcSeqOrgQuery)) {
        compareSeqs("for testProteinEcSeqOrgQuery (query by ec, org, seq with no database match)",
            proteinEcSeqOrgTestQuery, seqentry.getValue());
      }
    }

  }

  /**
   * Tests the case where the protein file doesn't have an EC_number listed and instead the query to the database must
   * be performed by Genbank protein accession number, both in the case when a database match exists and when it
   * doesn't.
   */
  @Test
  public void testProteinAccessionQuery() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<String> oldAccessions = Collections.singletonList("ESW35608");

    List<String> uniprotAccessions = Collections.singletonList("V7D1Q1");

    List<String> genbankNucleotideAccessions = Collections.singletonList("CM002288");

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), oldAccessions);

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);

    Seq protAccessionQueryTestSeq = new Seq(23894L, null, 4000004746L, "Phaseolus vulgaris", protSeqAccQuery,
        new ArrayList<>(), MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    String protSeqAccessionQuery = "MAPAPSLLHYPIIVCHLLFFAELTTGMSASTERPYVSSESPIRISVSTEGANTSSSTSTS" +
        "TTGTSHLIKCAEKEKTFCVNGGECFMVKDLSNPSRYLCKCQPGFTGARCTENVPMKVQTQ" +
        "EKAEELYQKRVLTITGICIALLVVGIMCVVAYCKTKKQRQKLHDRLRQSLRSERNNMVNI" +
        "ANGPHHPNPPPENVQLVNQYVSKNVISSEHIVEREVETSFSTSHYTSTAHHSTTVTQTPS" +
        "HSWSNGHTESIISESHSVIMMSSVESSRHSSPAGGPRGRLHGLGGPRECNSFLRHARETP" +
        "DSYRDSPHSER";

    uniprotAccessions = Collections.singletonList("Q3TD94");

    List<String> genbankProteinAccessions = Collections.singletonList("BAE41710");

    genbankNucleotideAccessions = Collections.singletonList("AK170314");

    accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), genbankProteinAccessions);

    List<String> pmids = Arrays.asList("10349636", "11042159", "11076861", "11217851", "12466851", "16141073");

    List<JSONObject> references = new ArrayList<>();

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("src", "PMID");
      obj.put("val", pmid);
      references.add(obj);
    }

    metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", new ArrayList());
    metadata.put("xref", new JSONObject());
    metadata.put("name", "Nrg1");

    Seq protAccessionQueryTestSeq2 = new Seq(48922, null, 4000003474L, "Mus musculus", protSeqAccessionQuery,
        references, MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);


    compareSeqs("for testProteinAccessionQuery (query by protein accession; database match exists)",
        protAccessionQueryTestSeq, seqs.get(23894L));

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(protSeqAccessionQuery)) {
        compareSeqs("for testProteinAccessionQuery (query by protein accession with no database match)",
            protAccessionQueryTestSeq2, seqentry.getValue());
      }
    }
  }

  /**
   * Tests the case where the protein file doesn't have an EC_number listed or a genbank protein accession number
   * referenced, in which case it queries using the Genbank nucleotide accession number and sequence, both in the case
   * when a database match exists and when it doesn't.
   */
  @Test
  public void testNucleotideAccessionQuery() {
    Map<Long, Seq> seqs = mockAPI.getSeqMap();

    List<String> uniprotAccessions = Collections.singletonList("H0UZN6");

    List<String> genbankNucleotideAccessions = Collections.singletonList("AAKN02012235");

    JSONObject accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);

    JSONObject metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("name", "CTNNB1");

    List<JSONObject> references = new ArrayList<>();
    JSONObject obj = new JSONObject();
    obj.put("src", "PMID");
    obj.put("val", "21993624");
    references.add(obj);

    Seq nucAccessionQueryTestSeq = new Seq(58923L, null, 4000001225L, "Cavia porcellus", nucSeqAccQuery, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    String nucSeqAccQuery2 = "FLTADLMELDMAMEPDRKAAVSHWQQQSYLDSGIHSGATTTAPSLSGKGNPEEEDVDTTQ" +
        "VLYEWEQGFSQSFTQEQVADIDGQYAMTRAQRVRAAMFPETLDEGMQIPSTQFDAAHPTN" +
        "VQRLAEPSQMLKHAVVNLINYQDDAELATRAIPELTKLLNDEDQVVVNKAAVMVHQLSKK" +
        "EASRHAIMRSPQMVSAIVRTMQNTNDVETARCTAGTLHNLSHHREGLLAIFKSGGIPALV" +
        "KMLGSPVDSVLFYAITTLHNLLLHQEGAKMAVRLAGGLQKMVALLNKTNVKFLAITTDCL" +
        "QILAYGNQESKLIILASGGPQALVNIMRTYTYEKLLWTTSRVLKVLSVCSSNKPAIVEAG" +
        "GMQALGLHLTDPSQRLVQNCLWTLRNLSDAATKQEGMEGLLGTLVQLLGSDDINVVTCAA" +
        "GILSNLTCNNYKNKMMVCQVGGIEALVRTVLRAGDREDITEPAICALRHLTSRHQEAEMA" +
        "QNAVRLHYGLPVVVKLLHPPSHWPLIKATVGLIRNLALCPANHAPLREQGAIPRLVQLLV" +
        "RAHQDTQRRTSMGGTQQQFVEGVRMEEIVEGCTGALHILARDVHNRIVIRGLNTIPLFVQ" +
        "LLYSPIENIQRVAAGVLCELAQDKEAAEAIEAEGATAPLTELLHSRNEGVATYAAAVLFR" +
        "MSEDKPQDYKKRLSVELTSSLFRTEPMAWNETADLGLDIGAQGEPLGYRPDDPSYRSFHS" +
        "GGYGQDALGMDPMMEHEMGGHHPGADYPVDGLPDLGHAQDLMDGLPPGDSNQLAWFDTDL";

    uniprotAccessions = Collections.singletonList("H0Z303");

    genbankNucleotideAccessions = Collections.singletonList("ABQF01014180");

    accessions = new JSONObject();
    accessions.put(Seq.AccType.uniprot.toString(), uniprotAccessions);
    accessions.put(Seq.AccType.genbank_nucleotide.toString(), genbankNucleotideAccessions);
    accessions.put(Seq.AccType.genbank_protein.toString(), new ArrayList());

    metadata = new JSONObject();
    metadata.put("accession", accessions);
    metadata.put("synonyms", new ArrayList());
    metadata.put("product_names", new ArrayList());
    metadata.put("xref", new JSONObject());
    metadata.put("name", "CTNNB1");

    references = new ArrayList<>();

    obj = new JSONObject();
    obj.put("src", "PMID");
    obj.put("val", "20360741");
    references.add(obj);

    Seq nucAccessionQueryTestSeq2 = new Seq(94032L, null, 7L, "Taeniopygia guttata", nucSeqAccQuery2, references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.uniprot);

    compareSeqs("for testNucleotideAccessionQuery (query by nucleotide accession and seq; database match exists)",
        nucAccessionQueryTestSeq, seqs.get(58923L));

    for (Map.Entry<Long, Seq> seqentry : seqs.entrySet()) {
      if (seqentry.getValue().get_sequence().equals(nucSeqAccQuery2)) {
        compareSeqs("for testNucleotideAccessionQuery (query by nucleotide accession and seq with no database match)",
            nucAccessionQueryTestSeq2, seqentry.getValue());
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
