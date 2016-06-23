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
import java.util.List;

import static org.junit.Assert.assertTrue;


public class GenbankSeqEntryTest {
  GenbankSeqEntry proteinSeqEntry;
  ArrayList<GenbankSeqEntry> dnaSeqEntries;

  @Before
  public void setUp() throws Exception {
    MongoDB db = new MongoDB("localhost", 27017, "marvin");

    GenbankInterpreter giProtein = new GenbankInterpreter(new File(this.getClass().getResource("genbank_test_protein.gb").getFile()), "Protein");
    giProtein.init();
    proteinSeqEntry = new GenbankSeqEntry(giProtein.sequences.get(0), db);


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

  @Test
  public void testMetadata() {
    assertTrue(true);
  }

//  @Test
//  public void testAccession() {
//
//  }
//
//  @Test
//  public void testGeneName() {
//
//  }
//
//  @Test
//  public void testGeneSynonyms() {
//
//  }
//
//  @Test
//  public void testProductName() {
//
//  }
//
//  @Test
//  public void testOrgId() {
//
//  }
//
//  @Test
//  public void testOrg() {
//
//  }
//
//  @Test
//  public void testSeq() {
//
//  }
//
//  @Test
//  public void testEc() {
//
//  }


}
