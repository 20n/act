package act.installer.sequence;

import act.server.MongoDB;
import com.act.utils.parser.GenbankInterpreter;
import org.biojava.bio.AnnotationType;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;


public class GenbankSeqEntryTest {
  ArrayList<GenbankSeqEntry> proteinSeqEntries;
  ArrayList<GenbankSeqEntry> dnaSeqEntries;
  ArrayList<String> sequences;

  @Before
  public void setUp() throws Exception {
    MongoDB db = new MongoDB("localhost", 27017, "marvin");
    dnaSeqEntries = new ArrayList<>();
    proteinSeqEntries = new ArrayList<>();
    sequences = new ArrayList<>();

    GenbankInterpreter giProtein = new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein.gb").getFile()), "Protein");
    giProtein.init();
    sequences.add(giProtein.sequences.get(0).getSequenceAsString());
    proteinSeqEntries.add(new GenbankSeqEntry(giProtein.sequences.get(0), db));

    giProtein = new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein_2.gb").getFile()), "Protein");
    giProtein.init();
    sequences.add(giProtein.sequences.get(0).getSequenceAsString());
    proteinSeqEntries.add(new GenbankSeqEntry(giProtein.sequences.get(0), db));


    GenbankInterpreter giDna = new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_dna.gb").getFile()), "DNA");
    giDna.init();

    List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = giDna.sequences.get(0).getFeatures();
    String organism = null;

    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
      if (feature.getType().equals("source") && feature.getQualifiers().containsKey("organism")) {
        organism = feature.getQualifiers().get("organism").get(0).getValue();
      }
    }

    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
      if (feature.getType().equals("CDS") && feature.getQualifiers().containsKey("EC_number"))
        dnaSeqEntries.add(new GenbankSeqEntry(feature.getQualifiers(), db, organism));
    }
  }

//  @Test
//  public void testMetadata() {
//
//  }

  @Test
  public void testAccession() {
    assertEquals("tests whether accession ID is extracted accurately", "CUB13083", proteinSeqEntries.get(0).getAccessions());
    assertEquals("tests whether accession ID is extracted accurately", "P50225", proteinSeqEntries.get(1).getAccessions());

  }

  @Test
  public void testGeneName() {
    assertEquals("tests whether gene name is extracted accurately", null, proteinSeqEntries.get(0).getGeneName());
    assertEquals("tests whether gene name is extracted accurately", "ST1A1_HUMAN", proteinSeqEntries.get(1).getGeneName());
  }

  @Test
  public void testGeneSynonyms() {
    List <String> list = new ArrayList<>();
    assertEquals("tests whether gene synonyms are extracted accurately", list, proteinSeqEntries.get(0).getGeneSynonyms());

    list = Arrays.asList("STP", "STP1");
    assertEquals("tests whether gene synonyms are extracted accurately", list, proteinSeqEntries.get(1).getGeneSynonyms());
  }

  @Test
  public void testProductName() {
    assertEquals("tests whether product names are extracted accurately", "Arylamine N-acetyltransferase", proteinSeqEntries.get(0).getProductName());
    assertEquals("tests whether product names are extracted accurately", "Sulfotransferase 1A1", proteinSeqEntries.get(1).getProductName());
  }

  @Test
  public void testOrgId() {
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000000648L, proteinSeqEntries.get(0).getOrgId());
    assertEquals("tests whether organism ids are extracted accurately", (Long) 4000002681L, proteinSeqEntries.get(1).getOrgId());
  }

  @Test
  public void testOrg() {
    assertEquals("tests whether organism names are extracted accurately", "Bacillus cereus", proteinSeqEntries.get(0).getOrg());
    assertEquals("tests whether organism names are extracted accurately", "Homo sapiens", proteinSeqEntries.get(1).getOrg());
  }

  @Test
  public void testSeq() {
    assertEquals("tests whether sequences are extracted accurately", sequences.get(0), proteinSeqEntries.get(0).getSeq());
    assertEquals("tests whether sequences are extracted accurately", sequences.get(1), proteinSeqEntries.get(1).getSeq());
  }

  @Test
  public void testEc() {
    assertEquals("tests whether ec_numbers are extracted accurately", "2.3.1.5", proteinSeqEntries.get(0).getEc());
    assertEquals("tests whether ec_numbers are extracted accurately", "2.8.2.1", proteinSeqEntries.get(1).getEc());
  }


}
