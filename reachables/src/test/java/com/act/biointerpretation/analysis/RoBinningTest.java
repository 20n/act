package com.act.biointerpretation.analysis;

import act.server.NoSQLAPI;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.analysis.similarity.ROBinning;
import com.act.biointerpretation.test.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class RoBinningTest {


  TestUtils utilsObject;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ROBinning.class);
    utilsObject = new TestUtils();
  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void testHashing() throws Exception {
    ROBinning roBinning = new ROBinning();
    roBinning.init(null);

    String testInchi = "InChI=1S/C6H7N3O2/c7-8-5-1-3-6(4-2-5)9(10)11/h1-4,8H,7H2";
    Integer roId = 72;

    List<Integer> result = roBinning.processOneChemical(testInchi);
    for (Integer rosId : result) {
      if (rosId.equals(roId)) {
        System.out.println("Got it!");
      }
    }
  }
}
