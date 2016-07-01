package act.installer;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.biointerpretation.test.util.MockedMongoDBAPI;
import com.google.gwt.json.client.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GenbankInstallerTest {

  MockedMongoDBAPI mockAPI;

  @Before
  public void setUp() throws Exception {

    // no metadata and no references
    Seq emptyTestSeq = new Seq(91973L, "2.8.2.1", 4000002681L, "Homo sapiens", "MELIQTD", new ArrayList<>(), new BasicDBObject(), Seq.AccDB.genbank);

    // 3 other Seq objects

    mockAPI = new MockedMongoDBAPI();

    // will need to add othe Seq objects to arraylist
    mockAPI.installMocks(new ArrayList<Reaction>(), Arrays.asList(emptyTestSeq), new HashMap<>(), new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    // installs matched null Seq
    GenbankInstaller genbankInstaller = new GenbankInstaller(new File(this.getClass().getResource("genbank_installer_test_null_protein.gb").getFile()), "Protein", mockDb);
    genbankInstaller.init();

    // 3 other installer instances that install Seq objects


  }

  @Test
  public void test() {
    // test that mockDb contains the correct versions of the updated Seq entries

    Map<Long, Seq> seqs = mockAPI.getSeqMap();
    Seq emptyTestSeq = new Seq(91973L, "2.8.2.1", 4000002681L, "Homo sapiens", "MELIQTD", new ArrayList<>(), new BasicDBObject(), Seq.AccDB.genbank);

    compareSeqs(emptyTestSeq, seqs.get(91973L));

  }

  public void compareSeqs(Seq expectedSeq, Seq testSeq) {
    assertEquals("comparing id", expectedSeq.getUUID(), testSeq.getUUID());
    assertEquals("comparing ec", expectedSeq.get_ec(), testSeq.get_ec());
    assertEquals("comparing org_id", expectedSeq.getOrgId(), testSeq.getOrgId());
    assertEquals("comparing organism", expectedSeq.get_org_name(), testSeq.get_org_name());
    assertEquals("comparing sequence", expectedSeq.get_sequence(), testSeq.get_sequence());
    assertEquals("comparing references", expectedSeq.get_references().toString(), testSeq.get_references().toString());
    assertEquals("comparing metadata", expectedSeq.get_metadata().toString(), testSeq.get_metadata().toString());
    assertEquals("comapring src db", expectedSeq.get_srcdb(), testSeq.get_srcdb());
  }

}
