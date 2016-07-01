package act.installer;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.biointerpretation.test.util.MockedMongoDBAPI;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.google.gwt.json.client.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class GenbankInstallerTest {

  @Before
  public void setUp() {

    // no metadata and no references
    Seq emptyTestSeq = new Seq(91973L, "2.8.2.1", 4000002681L, "Homo sapiens", "MELIQTD", new ArrayList<>(), new BasicDBObject(), Seq.AccDB.genbank);

    MockedMongoDBAPI mockAPI = new MockedMongoDBAPI();


    mockAPI.installMocks(new ArrayList<Reaction>(), Arrays.asList(emptyTestSeq), new HashMap<>(), new HashMap<>());

    MongoDB mockDb = mockAPI.getMockMongoDB();

    GenbankInstaller genbankInstaller = new GenbankInstaller(new File(this.getClass().getResource("genbank_installer_test_null_protein.gb").getFile()), "Protein", mockDb);



  }

  @Test
  public void test() {

  }

}
