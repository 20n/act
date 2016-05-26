package com.act.biointerpretation.reachables;

import com.act.biointerpretation.test.util.TestUtils;
import com.act.reachables.WavefrontExpansion;
import org.hsqldb.lib.HashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WavefrontExpansionTest {

  private Map<String, Integer> testCases;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(WavefrontExpansionTest.class);
    testCases = new java.util.HashMap<>();
    testCases.put("InChI=1S/C14H17NO8/c1-6(16)15-7-2-4-8(5-3-7)22-14-11(19)9(17)10(18)12(23-14)13(20)21/h2-5,9-12,14,17-19H,1H3,(H,15,16)(H,20,21)/t9-,10-,11+,12-,14+/m0/s1", 14);
    testCases.put("InChI=1S/C12H14N2O5/c13-8-3-1-7(2-4-8)11(17)14-9(12(18)19)5-6-10(15)16/h1-4,9H,5-6,13H2,(H,14,17)(H,15,16)(H,18,19)", 12);
    testCases.put("InChI=1S/C24H39NO/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-19-22-24(26)25-23-20-17-16-18-21-23/h9-10,16-18,20-21H,2-8,11-15,19,22H2,1H3,(H,25,26)", 24);
    testCases.put("InChI=1S/C6H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 6);
  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void testCarbonCountingAccuratelyCountsCarbonsFromInchi() throws Exception {
    for (Map.Entry<String, Integer> testCase : testCases.entrySet()) {
      assertEquals(WavefrontExpansion.countCarbons(testCase.getKey()), testCase.getValue());
    }
  }
}
