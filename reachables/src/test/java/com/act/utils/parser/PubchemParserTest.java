package com.act.utils.parser;

import act.installer.PubchemParser;
import act.shared.Chemical;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PubchemParserTest {
  PubchemParser pubchemParser;

  @Before
  public void setUp() throws Exception {
    List<File> testFiles = new ArrayList<>();
    testFiles.add(new File(this.getClass().getResource("CompoundTest.xml.gz").getFile()));
    pubchemParser = new PubchemParser(null, null);
  }

  @Test
  public void testParserProcessesTheCorrectChemicals() throws Exception {
    File testFile = new File(this.getClass().getResource("CompoundTest.xml.gz").getFile());

    String expectedInchi1 = "InChI=1S/C18H27FN2/c1-2-14-11-17(20-16-5-3-4-6-16)13-21(12-14)18-9-7-15(19)8-10-18/h7-10,14,16-17,20H,2-6,11-13H2,1H3";
    String expectedSmiles1 = "CCC1CC(CN(C1)C2=CC=C(C=C2)F)NC3CCCC3";
    String expectedInchiKey1 = "ZKXZURQJNQWQEJ-UHFFFAOYSA-N";
    String expectedCanonicalName1 = "N-cyclopentyl-5-ethyl-1-(4-fluorophenyl)piperidin-3-amine";
    Long expectedPubchemId1 = 84000001L;

    Chemical testChemical1 = new Chemical(1L, expectedPubchemId1, expectedCanonicalName1, expectedSmiles1);
    testChemical1.setInchi(expectedInchi1);
    testChemical1.setInchiKey(expectedInchiKey1);

    String expectedInchi2 = "InChI=1S/C16H23FN2/c17-13-5-3-9-16(11-13)19-10-4-8-15(12-19)18-14-6-1-2-7-14/h3,5,9,11,14-15,18H,1-2,4,6-8,10,12H2";
    String expectedSmiles2 = "C1CCC(C1)NC2CCCN(C2)C3=CC(=CC=C3)F";
    String expectedInchiKey2 = "KWRDBFWMUMVAAA-UHFFFAOYSA-N";
    String expectedCanonicalName2 = "N-cyclopentyl-1-(3-fluorophenyl)piperidin-3-amine";
    Long expectedPubchemId2 = 84000002L;

    Chemical testChemical2 = new Chemical(2L, expectedPubchemId2, expectedCanonicalName2, expectedSmiles2);
    testChemical2.setInchi(expectedInchi2);
    testChemical2.setInchiKey(expectedInchiKey2);

    List<Chemical> expectedChemicals = new ArrayList<>();
    expectedChemicals.add(testChemical1);
    expectedChemicals.add(testChemical2);

    List<Chemical> results = pubchemParser.parseCompressedXMLFileAndConstructChemicals(testFile);

    assertEquals("Two chemicals should be parsed from the xml file", 2, results.size());

    int counter = 0;
    for (Chemical chemical : results) {
      Chemical expectedChemical = expectedChemicals.get(counter);
      assertEquals("Inchis parsed from the xml file should be the same as expected", expectedChemical.getInChI(), chemical.getInChI());
      assertEquals("Inchi keys parsed from the xml file should be the same as expected", expectedChemical.getInChIKey(), chemical.getInChIKey());
      assertEquals("Smiles parsed from the xml file should be the same as expected", expectedChemical.getSmiles(), chemical.getSmiles());
      assertEquals("Canonical name parsed from the xml file should be the same as expected", expectedChemical.getCanon(), chemical.getCanon());
      assertEquals("Pubchem id parsed from the xml file should be the same as expected", expectedChemical.getPubchemID(), chemical.getPubchemID());
      counter++;
    }
  }
}
