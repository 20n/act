package com.act.biointerpretation.mechanisminspection;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReactionProjectorTest {
  @Test
  public void testReactionProjectorWorksOnMultipleSubstrateReactionsWithoutClearOrdering() throws Exception {
    String testRO =
        "[C:1](=[O:7])1[C,c:2]=[C,c:3][C:4](=[O:8])[c:5][c:6]1.[C:9](H)[C:10](H).[N:11](H)>>[c:1]([OH:7])1[c:2][c:3][c:4]([OH:8])[c:5][c:6]1.[C:9]=[C:10].[N:11]S(=O)(=O)[OH]";

    String substrate1 = "InChI=1S/C10H6O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6H";
    String substrate2 = "InChI=1S/C4H6O4/c5-3(6)1-2-4(7)8/h1-2H2,(H,5,6)(H,7,8)";
    String substrate3 = "InChI=1S/C7H9N/c8-6-7-4-2-1-3-5-7/h1-5H,6,8H2";

    String product1 = "InChI=1S/C10H8O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6,11-12H";
    String product2 = "InChI=1S/C4H4O4/c5-3(6)1-2-4(7)8/h1-2H,(H,5,6)(H,7,8)/b2-1+";
    String product3 = "InChI=1S/C7H9NO3S/c9-12(10,11)8-6-7-4-2-1-3-5-7/h1-5,8H,6H2,(H,9,10,11)";

    Set<String> expectedProducts = new HashSet<>();
    expectedProducts.add(product1);
    expectedProducts.add(product2);
    expectedProducts.add(product3);

    String[] substratesCombination1 = new String[] {
        substrate2,
        substrate1,
        substrate3
    };

    String[] substratesCombination2 = new String[] {
        substrate1,
        substrate3,
        substrate2
    };

    String[] substratesCombination3 = new String[] {
        substrate3,
        substrate2,
        substrate1
    };

    String[][] testCombinations = new String[][] {
        substratesCombination1,
        substratesCombination2,
        substratesCombination3
    };

    for (String[] substratesCombination : testCombinations) {
      Molecule[] molSubstrates = new Molecule[substratesCombination.length];
      int counter = 0;
      for (String substrate : substratesCombination) {
        Molecule mol = MolImporter.importMol(substrate, "inchi");
        Cleaner.clean(mol, 2);
        molSubstrates[counter] = mol;
        counter++;
      }

      Reactor reactor = new Reactor();
      reactor.setReactionString(testRO);

      Molecule[] products = ReactionProjector.projectRoOnMolecules(molSubstrates, reactor);

      Assert.assertNotNull("The products from the projector should not be null", products);

      Set<String> actualProducts = new HashSet<>();
      for (Molecule product : products) {
        actualProducts.add(MolExporter.exportToObject(product, "inchi:AuxNone").toString());
      }

      Assert.assertEquals("The expected products has to match the actual products produced by the ReactionProjector",
          expectedProducts, actualProducts);
    }
  }

  // TODO: this test is not necessarily stable, as it relies on the Reactor's output ordering.  Do something better.
  @Test
  public void testReactionWithMultiplePossibleOutputsOnlyReturnsOnePossibility() throws Exception {
    String testRO =
        "[C:1](=[O:7])1[C,c:2]=[C,c:3][C:4](=[O:8])[c:5][c:6]1.[C:9](H)[C:10](H).[N:11](H)>>[c:1]([OH:7])1[c:2][c:3][c:4]([OH:8])[c:5][c:6]1.[C:9]=[C:10].[N:11]S(=O)(=O)[OH]";

    String substrate1 = "InChI=1S/C10H6O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6H";
    String substrate2 = "InChI=1S/C4H6O4/c5-3(6)1-2-4(7)8/h1-2H2,(H,5,6)(H,7,8)";
    String substrate3 = "InChI=1S/C6H8N2/c7-8-6-4-2-1-3-5-6/h1-5,8H,7H2";

    String product1 = "InChI=1S/C10H8O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6,11-12H";
    String product2 = "InChI=1S/C4H4O4/c5-3(6)1-2-4(7)8/h1-2H,(H,5,6)(H,7,8)/b2-1+";
    String product3 = "InChI=1S/C6H8N2O3S/c9-12(10,11)8-7-6-4-2-1-3-5-6/h1-5,7-8H,(H,9,10,11)";

    Set<String> expectedProducts = new HashSet<>();
    expectedProducts.add(product1);
    expectedProducts.add(product2);
    expectedProducts.add(product3);

    String[] substratesCombination = new String[] {
        substrate2,
        substrate1,
        substrate3
    };

    Molecule[] molSubstrates = new Molecule[substratesCombination.length];
    int counter = 0;
    for (String substrate : substratesCombination) {
      Molecule mol = MolImporter.importMol(substrate, "inchi");
      Cleaner.clean(mol, 2);
      molSubstrates[counter] = mol;
      counter++;
    }

    Reactor reactor = new Reactor();
    reactor.setReactionString(testRO);

    Molecule[] products = ReactionProjector.projectRoOnMolecules(molSubstrates, reactor);

    Assert.assertNotNull("The products from the projector should not be null", products);

    Set<String> actualProducts = new HashSet<>();
    for (Molecule product : products) {
      actualProducts.add(MolExporter.exportToObject(product, "inchi:AuxNone").toString());
    }

    Assert.assertEquals("The expected products has to match the actual products produced by the ReactionProjector",
        expectedProducts, actualProducts);
  }

  @Test
  public void testCoefficientDependentReaction() throws Exception {
    String testRO =
        "[H][#8:3]-[#6:2].[H][#8:9]-[#6:8].[#6:4]-[#8:5][P:13]([#8:14])([#8:15])=[O:16]>>[H][#8:5]-[#6:4].[#6:2]-[#8:3][P:13]([#8:14])([#8:15])=[O:16].[H][#8]P(=O)([#8][H])[#8:9]-[#6:8]";
    String nonMatchingTestRO =
        "[H][#8:9]-[#6:8].[#6:4]-[#8:5][P:13]([#8:14])([#8:15])=[O:16].[H][#8]P(=O)([#8][H])[#8:11]-[#6:10]>>[H][#8:5]-[#6:4].[H][#8:11]-[#6:10].[#6:8]-[#8:9][P:13]([#8:14])([#8:15])=[O:16]";

    String substrate1 = "InChI=1S/CH4O/c1-2/h2H,1H3";
    String substrate2 = "InChI=1S/CH4O/c1-2/h2H,1H3";
    String substrate3 = "InChI=1S/CH5O4P/c1-5-6(2,3)4/h1H3,(H2,2,3,4)";

    String product1 = "InChI=1S/CH4O/c1-2/h2H,1H3";
    String product2 = "InChI=1S/CH5O4P/c1-5-6(2,3)4/h1H3,(H2,2,3,4)";
    String product3 = "InChI=1S/CH5O4P/c1-5-6(2,3)4/h1H3,(H2,2,3,4)";

    Set<String> expectedProducts = new HashSet<>();
    expectedProducts.add(product1);
    expectedProducts.add(product2);
    expectedProducts.add(product3);

    String[] substratesCombination = new String[] {
        substrate2,
        substrate1,
        substrate3
    };

    Molecule[] molSubstrates = new Molecule[substratesCombination.length];
    int counter = 0;
    for (String substrate : substratesCombination) {
      Molecule mol = MolImporter.importMol(substrate, "inchi");
      Cleaner.clean(mol, 2);
      molSubstrates[counter] = mol;
      counter++;
    }

    // Test a coefficient-dependent RO that should match the substrates.
    Reactor reactor = new Reactor();
    reactor.setReactionString(testRO);

    Molecule[] products = ReactionProjector.projectRoOnMolecules(molSubstrates, reactor);

    Assert.assertNotNull("The products from the projector should not be null", products);

    Set<String> actualProducts = new HashSet<>();
    for (Molecule product : products) {
      actualProducts.add(MolExporter.exportToObject(product, "inchi:AuxNone").toString());
    }

    Assert.assertEquals("The expected products has to match the actual products produced by the ReactionProjector",
        expectedProducts, actualProducts);

    // Test a coefficient-dependent RO that should not match the substrates.
    reactor = new Reactor();
    reactor.setReactionString(nonMatchingTestRO);

    products = ReactionProjector.projectRoOnMolecules(molSubstrates, reactor);

    Assert.assertNull("The products from the projector should be null", products);

  }

  @Test
  public void testFastProjectionOfTwoSubstrateRoOntoTwoMolecules() throws Exception {
    String testRO =
        "[C:1](=[O:7])1[C,c:2]=[C,c:3][C:4](=[O:8])[c:5][c:6]1.[C:9](H)[C:10](H)>>[c:1]([OH:7])1[c:2][c:3][c:4]([OH:8])[c:5][c:6]1.[C:9]=[C:10]";

    String substrate1 = "InChI=1S/C10H6O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6H";
    String substrate2 = "InChI=1S/C6H8N2/c7-8-6-4-2-1-3-5-6/h1-5,8H,7H2";

    String[] substratesCombination = new String[] {
        substrate1,
        substrate2
    };

    Molecule[] molSubstrates = new Molecule[substratesCombination.length];
    int counter = 0;
    for (String substrate : substratesCombination) {
      Molecule mol = MolImporter.importMol(substrate, "inchi");
      Cleaner.clean(mol, 2);
      molSubstrates[counter] = mol;
      counter++;
    }

    Reactor reactor = new Reactor();
    reactor.setReactionString(testRO);

    List<Molecule[]> products = ReactionProjector.fastProjectionOfTwoSubstrateRoOntoTwoMolecules(molSubstrates, reactor);
    for (Molecule[] prod : products) {
      int j = 0;
    }
  }
}
