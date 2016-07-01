package com.act.biointerpretation.mechanisminspection;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactionProjectorTest {

  static final String SUBSTRATE_1 = "InChI=1S/C10H6O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6H";
  static final String SUBSTRATE_2 = "InChI=1S/C4H6O4/c5-3(6)1-2-4(7)8/h1-2H2,(H,5,6)(H,7,8)";
  static final String SUBSTRATE_3_SPECIFIC = "InChI=1S/C7H9N/c8-6-7-4-2-1-3-5-7/h1-5H,6,8H2";
  static final String SUBSTRATE_3_AMBIGUOUS = "InChI=1S/C6H8N2/c7-8-6-4-2-1-3-5-6/h1-5,8H,7H2";

  static final String PRODUCT_1 = "InChI=1S/C10H8O2/c11-9-5-6-10(12)8-4-2-1-3-7(8)9/h1-6,11-12H";
  static final String PRODUCT_2 = "InChI=1S/C4H4O4/c5-3(6)1-2-4(7)8/h1-2H,(H,5,6)(H,7,8)/b2-1+";
  static final String PRODUCT_3_SPECIFIC = "InChI=1S/C7H9NO3S/c9-12(10,11)8-6-7-4-2-1-3-5-7/h1-5,8H,6H2,(H,9,10,11)";
  static final String PRODUCT_3_AMBIGUOUS_OPTION_1 =
      "InChI=1S/C6H8N2O3S/c9-12(10,11)8-7-6-4-2-1-3-5-6/h1-5,7-8H,(H,9,10,11)";

  static final String NO_COEFFICIENT_RO =
      "[C:1](=[O:7])1[C,c:2]=[C,c:3][C:4](=[O:8])[c:5][c:6]1.[C:9](H)[C:10](H).[N:11](H)>>[c:1]([OH:7])1[c:2][c:3]" +
          "[c:4]([OH:8])[c:5][c:6]1.[C:9]=[C:10].[N:11]S(=O)(=O)[OH]";

  @Test
  public void testReactionProjectorWorksOnMultipleSubstrateReactionsWithoutClearOrdering() throws Exception {

    Set<String> expectedProducts = new HashSet<>();
    expectedProducts.add(PRODUCT_1);
    expectedProducts.add(PRODUCT_2);
    expectedProducts.add(PRODUCT_3_SPECIFIC);

    String[] correctCombination = new String[]{
        SUBSTRATE_1,
        SUBSTRATE_2,
        SUBSTRATE_3_SPECIFIC
    };

    String[] permutation1 = new String[]{
        SUBSTRATE_1,
        SUBSTRATE_3_SPECIFIC,
        SUBSTRATE_2
    };

    String[] permutation2 = new String[]{
        SUBSTRATE_3_SPECIFIC,
        SUBSTRATE_1,
        SUBSTRATE_2
    };

    String[][] testCombinations = new String[][]{
        correctCombination,
        permutation1,
        permutation2
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
      reactor.setReactionString(NO_COEFFICIENT_RO);

      Map<Molecule[], List<Molecule[]>> productsMap = ReactionProjector.getRoProjectionMap(molSubstrates, reactor);

      Assert.assertEquals("The products map should contain exactly one entry.", 1, productsMap.size());
      Assert.assertTrue("The products map should contain only the correct substrate combination as a key.",
          productsMap.keySet().contains(correctCombination));

      List<Molecule[]> productSet = productsMap.get(correctCombination);

      Assert.assertEquals("The product set should contain only one product array.", 1, productSet.size());

      Molecule[] productArray = productSet.get(0);
      Set<String> productInchis = getInchiSet(productArray);

      Assert.assertEquals("The expected products has to match the actual products produced by the ReactionProjector",
          expectedProducts, productInchis);
    }
  }

  @Test
  public void testReactionWithMultiplePossibleOutputsReturnsBoth() throws Exception {

    Set<String> expectedProducts = new HashSet<>();
    expectedProducts.add(PRODUCT_1);
    expectedProducts.add(PRODUCT_2);
    expectedProducts.add(PRODUCT_3_AMBIGUOUS_OPTION_1);

    String[] substratesCombination = new String[]{
        SUBSTRATE_1,
        SUBSTRATE_2,
        SUBSTRATE_3_AMBIGUOUS
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
    reactor.setReactionString(NO_COEFFICIENT_RO);

    Map<Molecule[], List<Molecule[]>> productsMap = ReactionProjector.getRoProjectionMap(molSubstrates, reactor);

    Assert.assertEquals("The products map should contain exactly one entry.", 1, productsMap.size());
    Assert.assertTrue("The products map should contain only the given substrate combination as a key.",
        productsMap.keySet().contains(substratesCombination));

    List<Molecule[]> productSet = productsMap.get(substratesCombination);
    Assert.assertEquals("Product set should contain exactly two predictions.", productSet.size(), 2);

    Set<String> actualSet1 = getInchiSet(productSet.get(0));
    Set<String> actualSet2 = getInchiSet(productSet.get(1));

    Assert.assertEquals("The first actual product set has to match one set of expected products.",
        expectedProducts, actualSet1);
    Assert.assertEquals("The second actual product set has to match one set of expected products.",
        expectedProducts, actualSet2);
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

    Set<String> expectedProducts = new HashSet<>();
    expectedProducts.add(product1);
    expectedProducts.add(product2);
    expectedProducts.add(product2);

    String[] substratesCombination = new String[]{
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

    Map<Molecule[], List<Molecule[]>> productsMap = ReactionProjector.getRoProjectionMap(molSubstrates, reactor);

    Assert.assertEquals("The products map should have exactly one entry,", 1, productsMap.size());
    Assert.assertTrue("The products map should contain only the given substrate combination as a key.",
        productsMap.keySet().contains(substratesCombination));

    List<Molecule[]> productSet = productsMap.get(substratesCombination);

    Assert.assertEquals("The product set should contain only one product array.", 1, productSet.size());

    Molecule[] productArray = productSet.get(0);
    Set<String> productInchis = getInchiSet(productArray);

    Assert.assertEquals("The expected products has to match the actual products produced by the ReactionProjector",
        expectedProducts, productInchis);

    // Test a coefficient-dependent RO that should not match the substrates.
    reactor = new Reactor();
    reactor.setReactionString(nonMatchingTestRO);

    Map<Molecule[], List<Molecule[]>> products = ReactionProjector.getRoProjectionMap(molSubstrates, reactor);

    Assert.assertTrue("The products map should be empty", products.isEmpty());

  }

  private static Set<String> getInchiSet(Molecule[] molecules) throws IOException {
    Set<String> inchiSet = new HashSet<>();
    for (Molecule product : molecules) {
      inchiSet.add(MolExporter.exportToObject(product, "inchi:AuxNone").toString());
    }
    return inchiSet;
  }
}
