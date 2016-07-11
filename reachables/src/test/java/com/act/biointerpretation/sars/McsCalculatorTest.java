package com.act.biointerpretation.sars;

import act.server.MongoDB;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class McsCalculatorTest {


  private static final String INCHI_IMPORT_SETTINGS = "inchi";
  private static final String INCHI_EXPORT_SETTINGS = "inchi:AuxNone";

  MongoDB mockDb;

  @Test
  public void testMcsCalculatorSimplePairs() throws IOException {
    // Arrange
    String firstSubstrateInchi =
        "InChI=1S/C18H14O/c1-11-2-3-12-7-9-16-14(10-19)5-4-13-6-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
    String secondSubstrateInchi =
        "InChI=1S/C18H14O/c1-11-2-3-12-4-5-13-6-7-14(10-19)16-9-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
    String thirdSubstrateInchi =
        "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";
    String expectedMcs =
        "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";

    Molecule firstSubstrate = MolImporter.importMol(firstSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule secondSubstrate = MolImporter.importMol(secondSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule thirdSubstrate = MolImporter.importMol(thirdSubstrateInchi, INCHI_IMPORT_SETTINGS);

    McsCalculator calculator = new McsCalculator();

    // Act

    Molecule mcs12 = calculator.getMCS(Arrays.asList(firstSubstrate, secondSubstrate));
    Molecule mcs13 = calculator.getMCS(Arrays.asList(firstSubstrate, thirdSubstrate));
    Molecule mcs23 = calculator.getMCS(Arrays.asList(secondSubstrate, thirdSubstrate));

    // Assert
    String mcsInchi12 = MolExporter.exportToFormat(mcs12, INCHI_EXPORT_SETTINGS);
    String mcsInchi13 = MolExporter.exportToFormat(mcs13, INCHI_EXPORT_SETTINGS);
    String mcsInchi23 = MolExporter.exportToFormat(mcs23, INCHI_EXPORT_SETTINGS);

    assertEquals("MCS of one and two should be as expected.", expectedMcs, mcsInchi12);
    assertEquals("MCS of one and three should be as expected.", expectedMcs, mcsInchi13);
    assertEquals("MCS of two and three should be as expected.", expectedMcs, mcsInchi23);
  }

  @Test
  public void testMcsCalculatorRingStructuresDifferentBondTypes() throws IOException {
    // Arrange
    String firstSubstrateInchi =
        "InChI=1S/C7H12O6/c8-3-1-7(13,6(11)12)2-4(9)5(3)10/h3-5,8-10,13H,1-2H2,(H,11,12)/t3-,4-,5-,7+/m1/s1";
    String secondSubstrateInchi =
        "InChI=1S/C7H10O5/c8-4-1-3(7(11)12)2-5(9)6(4)10/h1,4-6,8-10H,2H2,(H,11,12)/t4-,5-,6-/m1/s1";
    String expectedMcs =
        "InChI=1S/C7H10O5/c8-4-1-3(7(11)12)2-5(9)6(4)10/h1,4-6,8-10H,2H2,(H,11,12)/t4-,5-,6-/m1/s1";

    Molecule firstSubstrate = MolImporter.importMol(firstSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule secondSubstrate = MolImporter.importMol(secondSubstrateInchi, INCHI_IMPORT_SETTINGS);

    McsCalculator calculator = new McsCalculator();

    // Act

    Molecule mcs = calculator.getMCS(Arrays.asList(firstSubstrate, secondSubstrate));

    // Assert
    String mcsInchi = MolExporter.exportToFormat(mcs, INCHI_EXPORT_SETTINGS);

    assertEquals("MCS should contain the ring despite different bond types in substrates.", expectedMcs, mcsInchi);
  }

  @Test
  public void testMcsCalculatorManyMolecules() throws IOException {
    // Arrange
    String firstSubstrateInchi =
        "InChI=1S/C18H14O/c1-11-2-3-12-7-9-16-14(10-19)5-4-13-6-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
    String secondSubstrateInchi =
        "InChI=1S/C18H14O/c1-11-2-3-12-4-5-13-6-7-14(10-19)16-9-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
    String thirdSubstrateInchi =
        "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";
    String expectedMcsInchi =
        "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";

    Molecule firstSubstrate = MolImporter.importMol(firstSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule secondSubstrate = MolImporter.importMol(secondSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule thirdSubstrate = MolImporter.importMol(thirdSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule expectedMcs = MolImporter.importMol(expectedMcsInchi, INCHI_IMPORT_SETTINGS);

    List<Molecule> molecules = Arrays.asList(firstSubstrate, firstSubstrate, expectedMcs, secondSubstrate,
        thirdSubstrate, firstSubstrate, thirdSubstrate, expectedMcs, secondSubstrate);

    McsCalculator calculator = new McsCalculator();

    // Act
    Molecule mcs = calculator.getMCS(molecules);

    // Assert
    String mcsInchi = MolExporter.exportToFormat(mcs, INCHI_EXPORT_SETTINGS);

    assertEquals("MCS of all molecules should be as expected.", expectedMcsInchi, mcsInchi);
  }

}
