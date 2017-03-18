/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.lcms.v2;


import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


public class LcmsChemicalFormulaTest {

  private static final Double MASS_TOLERANCE = 0.001;

  private static Element C = LcmsCommonElements.CARBON.getElement();
  private static Element H = LcmsCommonElements.HYDROGEN.getElement();
  private static Element N = LcmsCommonElements.NITROGEN.getElement();
  private static Element O = LcmsCommonElements.OXYGEN.getElement();
  private static Element P = LcmsCommonElements.PHOSPHORUS.getElement();
  private static Element S = LcmsCommonElements.SULFUR.getElement();
  private static Element I = LcmsCommonElements.IODINE.getElement();
  private static Element F = LcmsCommonElements.FLUORINE.getElement();
  private static Element Cl = LcmsCommonElements.CHLORINE.getElement();
  private static Element Br = LcmsCommonElements.BROMINE.getElement();

  private static ChemicalFormula acetaminophenFormula;
  private static ChemicalFormula sulfuricAcidFormula;
  private static String acetaminophenFormulaString;
  private static String sulfuricAcidFormulaString;
  static {
    Map<Element, Integer> acetaminophenElementsMap = new HashMap<Element, Integer>(){{
      put(C, 8); put(H, 9); put(N, 1); put(O, 2);
    }};
    acetaminophenFormula = new LcmsChemicalFormula(acetaminophenElementsMap);

    Map<Element, Integer> sulfuricAcidElementMap = new HashMap<Element, Integer>(){{
      // H2O4S
      put(H, 2); put(S, 1); put(O, 4);
    }};
    sulfuricAcidFormula = new LcmsChemicalFormula(sulfuricAcidElementMap);

    acetaminophenFormulaString = "C8H9NO2";
    sulfuricAcidFormulaString = "H2O4S";
  }

  @Test
  public void testChemicalFormulaToString() {
    assertEquals(acetaminophenFormulaString, acetaminophenFormula.toString());
    assertEquals(sulfuricAcidFormulaString, sulfuricAcidFormula.toString());
  }

  @Test
  public void testChemicalFormulaMonoIsotopicMass() {
    assertEquals(151.063, acetaminophenFormula.getMonoIsotopicMass(), MASS_TOLERANCE);
    assertEquals(97.967, sulfuricAcidFormula.getMonoIsotopicMass(), MASS_TOLERANCE);
  }

  @Test
  public void testChemicalFormulaFromString() {
    ChemicalFormula acetaminophenTestFormula = new LcmsChemicalFormula(acetaminophenFormulaString);
    assertEquals(acetaminophenFormula, acetaminophenTestFormula);
    ChemicalFormula sulfuricAcidTestFormula = new LcmsChemicalFormula(sulfuricAcidFormulaString);
    assertEquals(sulfuricAcidFormula, sulfuricAcidTestFormula);
    assertNotEquals(sulfuricAcidTestFormula, acetaminophenTestFormula);
  }

  /**
   * End to end formula parsing (from string) and conversion (to string) test
   * The general outline of this test is:
   * 1) parsing formulae from string
   * 2) confirming expected element counts
   * 3) converting back to string
   */
  @Test
  public void testFormulaStringConversions() {
    List<String> testCases = Arrays.asList(
        "C20BrCl2", // test correct parsing of halogens
        "BrCl2", // test correct parsing of halogens
        "BrC2",
        "CCl", // chlorine vs carbon + ordering
        "ClC", // chlorine vs carbon + ordering
        "IFClH20CBrN10P2", // test hill system ordering (case contains carbon)
        "H10ClBrN4O2", // test hill system ordering (case does not contains carbon)
        "IFClH20BrN10P2" // test hill system ordering (case does not contains carbon)
    );

    // Parse formulae from their string representation
    List<ChemicalFormula> testCasesFormulae = testCases.stream().map(LcmsChemicalFormula::new).collect(Collectors.toList());

    // Confirm element counts in the parsed formulae
    Iterator<Integer> testCasesExpectedC = Arrays.asList(20, 0, 2, 1, 1, 1, 0, 0).iterator();
    Iterator<Integer> testCasesExpectedBr = Arrays.asList(1, 1, 1, 0, 0, 1, 1, 1).iterator();
    Iterator<Integer> testCasesExpectedCl = Arrays.asList(2, 2, 0, 1, 1, 1, 1, 1).iterator();

    Iterator<ChemicalFormula> testCasesFormulaeIterator = testCasesFormulae.iterator();
    while (testCasesFormulaeIterator.hasNext()) {
      ChemicalFormula formula = testCasesFormulaeIterator.next();
      assertEquals(testCasesExpectedC.next(), formula.getElementCount(C));
      assertEquals(testCasesExpectedBr.next(), formula.getElementCount(Br));
      assertEquals(testCasesExpectedCl.next(), formula.getElementCount(Cl));
    }

    // Convert back to string and test hill ordering
    List<String> testCasesHillOrdered = testCasesFormulae.stream().map(ChemicalFormula::toString).collect(Collectors.toList());
    List<String> testCasesHillOrderedExpected = Arrays.asList(
        "C20BrCl2",
        "BrCl2",
        "C2Br",
        "CCl",
        "CCl",
        "CH20BrClFIN10P2",
        "BrClH10N4O2",
        "BrClFH20IN10P2"
    );


    assertEquals(testCasesHillOrderedExpected, testCasesHillOrdered);
  }
}
