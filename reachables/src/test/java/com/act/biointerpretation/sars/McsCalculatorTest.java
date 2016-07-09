package com.act.biointerpretation.sars;

import act.server.MongoDB;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class McsCalculatorTest {

  private static final String FIRST_SUBSTRATE =
      "InChI=1S/C18H14O/c1-11-2-3-12-7-9-16-14(10-19)5-4-13-6-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
  private static final String SECOND_SUBSTRATE =
      "InChI=1S/C18H14O/c1-11-2-3-12-4-5-13-6-7-14(10-19)16-9-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
  private static final String THIRD_SUBSTRATE =
      "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";
  private static final String EXPECTED_MCS =
      "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";

  private static final String INCHI_IMPORT_SETTINGS = "inchi";
  private static final String INCHI_EXPORT_SETTINGS = "inchi:AuxNone";

  Molecule firstSubstrate;
  Molecule secondSubstrate;
  Molecule thirdSubstrate;

  MongoDB mockDb;

  @Before
  public void init() throws IOException {
    firstSubstrate = MolImporter.importMol(FIRST_SUBSTRATE, INCHI_IMPORT_SETTINGS);
    secondSubstrate = MolImporter.importMol(SECOND_SUBSTRATE, INCHI_IMPORT_SETTINGS);
    thirdSubstrate = MolImporter.importMol(THIRD_SUBSTRATE, INCHI_IMPORT_SETTINGS);
  }

  @Test
  public void testMcsCalculatorAllPairs() throws IOException {
    McsCalculator calculator = new McsCalculator();

    Molecule mcs12 = calculator.getMCS(Arrays.asList(firstSubstrate, secondSubstrate));
    Molecule mcs13 = calculator.getMCS(Arrays.asList(firstSubstrate, thirdSubstrate));
    Molecule mcs23 = calculator.getMCS(Arrays.asList(secondSubstrate, thirdSubstrate));

    String mcsInchi12 = MolExporter.exportToFormat(mcs12, INCHI_EXPORT_SETTINGS);
    String mcsInchi13 = MolExporter.exportToFormat(mcs13, INCHI_EXPORT_SETTINGS);
    String mcsInchi23 = MolExporter.exportToFormat(mcs23, INCHI_EXPORT_SETTINGS);

    assertEquals("MCS of one and two should be as expected.", EXPECTED_MCS, mcsInchi12);
    assertEquals("MCS of one and three should be as expected.", EXPECTED_MCS, mcsInchi13);
    assertEquals("MCS of two and three should be as expected.", EXPECTED_MCS, mcsInchi23);
  }
}
