package com.act.lcms.v2;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MassChargeCalculatorTest {
  private static final double MASS_ERROR_TOLERANCE = 0.0000001;

  // Use the fuzzy FP assertEquals over lists of values
  private static void assertEqualsWithFPErr(String msg, List<Double> expected, List<Double> actual) {
    if (expected.size() != actual.size()) {
      fail(msg + ": unequal list sizes");
    }

    for (int i = 0; i < expected.size(); i++) {
      assertEquals(msg, expected.get(i), actual.get(i), MASS_ERROR_TOLERANCE);
    }
  }

  private static void assertEqualsWithFPErr(String msg, Set<Double> expected, Set<Double> actual) {
    List<Double> expectedList = new ArrayList<>(expected);
    Collections.sort(expectedList);
    List<Double> actualList = new ArrayList<>(actual);
    Collections.sort(actualList);

    assertEqualsWithFPErr(msg, expectedList, actualList);
  }

  private static <T> void assertEqualsPairWithFPErr(
      String msg, List<Pair<T, Double>> expected, List<Pair<T, Double>> actual) {
    if (expected.size() != actual.size()) {
      fail(msg + ": unequal list sizes");
    }

    for (int i = 0; i < expected.size(); i++) {
      assertEquals(msg, expected.get(i).getLeft(), actual.get(i).getLeft());
      assertEquals(msg, expected.get(i).getRight(), actual.get(i).getRight(), MASS_ERROR_TOLERANCE);
    }
  }

  @Test
  public void testMakeMassChargeMap() throws Exception {
    List<MassChargeCalculator.MZSource> sources = Arrays.asList(
        new MassChargeCalculator.MZSource("InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)"),  // PABA
        new MassChargeCalculator.MZSource("InChI=1S/C7H7NO2/c1-10-7(9)6-3-2-4-8-5-6/h2-5H,1H3")); // Something crazy.

    MassChargeCalculator.MassChargeMap massChargeMap = MassChargeCalculator.makeMassChargeMap(
        sources,
        new HashSet<>(Arrays.asList("M+H", "M+Na"))
    );

    Double expectedMonoMass = 137.047679;
    List<Double> expectedIonMZs = Arrays.asList(138.054979, 160.036879); // M+H and M+Na of PABA

    // This is package private, but we'll use its ordering to speed testing.
    List<Double> actualIonMasses = massChargeMap.ionMZsSorted();
    assertEqualsWithFPErr("Unique ionic masses match expected list",
        expectedIonMZs,
        actualIonMasses
    );

    assertEqualsPairWithFPErr("M+H ion mz maps to source mass and ion name",
        Arrays.asList(Pair.of("M+H", expectedMonoMass)),
        massChargeMap.ionMZtoMonoMassAndIonName(actualIonMasses.get(0))
    );

    assertEqualsPairWithFPErr("M+Na ion mz maps to source mass and ion name",
        Arrays.asList(Pair.of("M+Na", expectedMonoMass)),
        massChargeMap.ionMZtoMonoMassAndIonName(actualIonMasses.get(1))
    );

    // Test reverse mapping.
    for (Double ionMZ : actualIonMasses) {
      assertEquals(String.format("Ionic masses for %f map to two MZSources", ionMZ),
          new HashSet<>(sources),
          massChargeMap.ionMZToMZSources(ionMZ)
      );
    }

    // Test iterators cover all values.
    assertEqualsWithFPErr("Iterable ionMZs match expected",
        new HashSet<>(expectedIonMZs),
        StreamSupport.stream(massChargeMap.ionMZIter().spliterator(), false).collect(Collectors.toSet())
    );

    assertEqualsWithFPErr("Iterable monoisotopic masses match expected",
        Collections.singleton(expectedMonoMass),
        StreamSupport.stream(massChargeMap.monoisotopicMassIter().spliterator(), false).collect(Collectors.toSet())
    );

    assertEquals("Iterable mzSources match expected",
        new HashSet<>(sources),
        StreamSupport.stream(massChargeMap.mzSourceIter().spliterator(), false).collect(Collectors.toSet())
    );
  }

  @Test
  public void testComputeMass() throws Exception {
    List<Pair<MassChargeCalculator.MZSource, Double>> testCases = Arrays.asList(
        Pair.of(new MassChargeCalculator.MZSource("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"),
            151.063329),
        Pair.of(new MassChargeCalculator.MZSource(151.063329), 151.063329),
        Pair.of(new MassChargeCalculator.MZSource(Pair.of("APAP", 151.063329)), 151.063329)
    );

    for (Pair<MassChargeCalculator.MZSource, Double> testCase : testCases) {
      Double actualMass = MassChargeCalculator.computeMass(testCase.getLeft());
      assertEquals(
          String.format("(Case %d) Actual mass is within bounds: %.6f ~ %.6f",
              testCase.getLeft().getId(), testCase.getRight(), actualMass),
          testCase.getRight(), actualMass, MASS_ERROR_TOLERANCE
      );
    }
  }

}
