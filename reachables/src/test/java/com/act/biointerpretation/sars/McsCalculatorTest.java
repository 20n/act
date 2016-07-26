package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
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

  // Three substrates containing methylpyrene
  private static final String METHYLPYRINE_INCHI_1 =
      "InChI=1S/C18H14O/c1-11-2-3-12-7-9-16-14(10-19)5-4-13-6-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
  private static final String METHYLPYRINE_INCHI_2 =
      "InChI=1S/C18H14O/c1-11-2-3-12-4-5-13-6-7-14(10-19)16-9-8-15(11)17(12)18(13)16/h2-9,19H,10H2,1H3";
  private static final String METHYLPYRENE_INCHI_3 =
      "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";

  // Methylpyrene itself.
  private static final String METHYLPYRENE_INCHI =
      "InChI=1S/C17H12O/c18-10-14-7-6-13-5-4-11-2-1-3-12-8-9-15(14)17(13)16(11)12/h1-9,18H,10H2";

  private Molecule methylPyreneMol1;
  private Molecule methylPyreneMol2;
  private Molecule methylPyreneMol3;
  private Molecule methylPyreneMol;

  @Before
  public void init() throws MolFormatException {
    methylPyreneMol1 = MolImporter.importMol(METHYLPYRINE_INCHI_1, INCHI_IMPORT_SETTINGS);
    methylPyreneMol2 = MolImporter.importMol(METHYLPYRINE_INCHI_2, INCHI_IMPORT_SETTINGS);
    methylPyreneMol3 = MolImporter.importMol(METHYLPYRENE_INCHI_3, INCHI_IMPORT_SETTINGS);
    methylPyreneMol = MolImporter.importMol(METHYLPYRENE_INCHI, INCHI_IMPORT_SETTINGS);
  }

  @Test
  public void testMcsCalculatorSimplePairs() throws IOException {
    // Arrange
    McsCalculator calculator = new McsCalculator(McsCalculator.SAR_OPTIONS);

    // Act
    Molecule mcs12 = calculator.getMCS(Arrays.asList(methylPyreneMol1, methylPyreneMol2));
    Molecule mcs13 = calculator.getMCS(Arrays.asList(methylPyreneMol1, methylPyreneMol3));
    Molecule mcs23 = calculator.getMCS(Arrays.asList(methylPyreneMol2, methylPyreneMol3));

    // Assert
    String mcsInchi12 = MolExporter.exportToFormat(mcs12, INCHI_EXPORT_SETTINGS);
    String mcsInchi13 = MolExporter.exportToFormat(mcs13, INCHI_EXPORT_SETTINGS);
    String mcsInchi23 = MolExporter.exportToFormat(mcs23, INCHI_EXPORT_SETTINGS);

    assertEquals("MCS of one and two should be as expected.", METHYLPYRENE_INCHI, mcsInchi12);
    assertEquals("MCS of one and three should be as expected.", METHYLPYRENE_INCHI, mcsInchi13);
    assertEquals("MCS of two and three should be as expected.", METHYLPYRENE_INCHI, mcsInchi23);
  }

  @Test
  public void testSarCalculatorRingStructuresMatchDifferentBondTypes() throws IOException {
    // Arrange
    String firstSubstrateInchi =
        "InChI=1S/C7H12O2/c8-7(9)6-4-2-1-3-5-6/h6H,1-5H2,(H,8,9)";
    String secondSubstrateInchi =
        "InChI=1S/C7H10O2/c8-7(9)6-4-2-1-3-5-6/h4H,1-3,5H2,(H,8,9)";
    String expectedMcs =
        "InChI=1S/C7H10O2/c8-7(9)6-4-2-1-3-5-6/h4H,1-3,5H2,(H,8,9)";

    Molecule firstSubstrate = MolImporter.importMol(firstSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule secondSubstrate = MolImporter.importMol(secondSubstrateInchi, INCHI_IMPORT_SETTINGS);

    McsCalculator calculator = new McsCalculator(McsCalculator.SAR_OPTIONS);

    // Act
    Molecule mcs = calculator.getMCS(Arrays.asList(firstSubstrate, secondSubstrate));

    // Assert
    String mcsInchi = MolExporter.exportToFormat(mcs, INCHI_EXPORT_SETTINGS);
    assertEquals("MCS should contain the ring despite different bond types in substrates.", expectedMcs, mcsInchi);
  }

  @Test
  public void testReactionCalculatorRingStructuresMismatchDifferentBondTypes() throws IOException {
    // Arrange
    String firstSubstrateInchi =
        "InChI=1S/C7H12O2/c8-7(9)6-4-2-1-3-5-6/h6H,1-5H2,(H,8,9)";
    String secondSubstrateInchi =
        "InChI=1S/C7H10O2/c8-7(9)6-4-2-1-3-5-6/h4H,1-3,5H2,(H,8,9)";
    String expectedMcs =
        "InChI=1S/C2H4O2/c1-2(3)4/h1H3,(H,3,4)";

    Molecule firstSubstrate = MolImporter.importMol(firstSubstrateInchi, INCHI_IMPORT_SETTINGS);
    Molecule secondSubstrate = MolImporter.importMol(secondSubstrateInchi, INCHI_IMPORT_SETTINGS);

    McsCalculator calculator = new McsCalculator(McsCalculator.REACTION_BUILDING_OPTIONS);

    // Act
    Molecule mcs = calculator.getMCS(Arrays.asList(firstSubstrate, secondSubstrate));

    // Assert
    String mcsInchi = MolExporter.exportToFormat(mcs, INCHI_EXPORT_SETTINGS);
    assertEquals("MCS should not contain the ring dueto different bond types in substrates.", expectedMcs, mcsInchi);
  }

  @Test
  public void testMcsCalculatorManyMolecules() throws IOException {
    // Arrange
    List<Molecule> molecules = Arrays.asList(methylPyreneMol1, methylPyreneMol1, methylPyreneMol, methylPyreneMol2,
        methylPyreneMol3, methylPyreneMol1, methylPyreneMol3, methylPyreneMol, methylPyreneMol2);

    McsCalculator calculator = new McsCalculator(McsCalculator.SAR_OPTIONS);

    // Act
    Molecule mcs = calculator.getMCS(molecules);

    // Assert
    String mcsInchi = MolExporter.exportToFormat(mcs, INCHI_EXPORT_SETTINGS);

    assertEquals("MCS of all molecules should be as expected.", METHYLPYRENE_INCHI, mcsInchi);
  }

  @Test
  public void testMcsCalculatorRingDoesntMatchChain() throws IOException {
    // Arrange
    String benzeneInchi = "InChI=1/C6H6/c1-2-4-6-5-3-1/h1-6H";
    String chainInchi = "InChI=1S/C5H12/c1-3-5-4-2/h3-5H2,1-2H3";
    String expectedMcs = "InChI=1S//";

    Molecule benzene = MolImporter.importMol(benzeneInchi, INCHI_IMPORT_SETTINGS);
    Molecule chain = MolImporter.importMol(chainInchi, INCHI_IMPORT_SETTINGS);
    List<Molecule> inputMolecules = Arrays.asList(benzene, chain);

    McsCalculator calculator = new McsCalculator(McsCalculator.SAR_OPTIONS);

    // Act
    Molecule mcs = calculator.getMCS(inputMolecules);
    String mcsInchi = MolExporter.exportToFormat(mcs, INCHI_EXPORT_SETTINGS);

    // Assert
    assertEquals("Mcs should be empty.", expectedMcs, mcsInchi);
  }
}
