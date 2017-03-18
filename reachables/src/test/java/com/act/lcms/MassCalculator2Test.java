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

package com.act.lcms;

import com.act.utils.TSVParser;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MassCalculator2Test {
  public static final String TEST_CASE_RESOURCE = "mass_calculator_test_cases.txt";
  public static final Double ACCEPTABLE_MASS_DELTA_THRESHOLD = 0.00054858; // one electron's mass in Daltons.

  @Test
  public void testMC2MatchesMC1WithinMeaningfulTolerance() throws Exception {
    List<Map<String, String>> rows;
    try (InputStream is = MassCalculator2Test.class.getResourceAsStream(TEST_CASE_RESOURCE)) {
      TSVParser parser = new TSVParser();
      parser.parse(is);
      rows = parser.getResults();
    }

    int testCase = 1;
    for (Map<String, String> row : rows) {
      String inchi = row.get("InChI");
      Double expectedMass = Double.valueOf(row.get("Mass"));
      Integer expectedCharge = Integer.valueOf(row.get("Charge"));

      Pair<Double, Integer> actualMassAndCharge = MassCalculator2.calculateMassAndCharge(inchi);


      Double threshold = ACCEPTABLE_MASS_DELTA_THRESHOLD;
      if (actualMassAndCharge.getRight() < 0) {
        // Widen the window for added electrons' masses included in Chemaxon's calculations for negative ions.
        threshold += ACCEPTABLE_MASS_DELTA_THRESHOLD * -1.0 * actualMassAndCharge.getRight().doubleValue();
      } else if (actualMassAndCharge.getRight() > 0) {
        // Positively charged molecules have the missing electrons' masses subtracted
        threshold += ACCEPTABLE_MASS_DELTA_THRESHOLD * actualMassAndCharge.getRight().doubleValue();
      }

      assertEquals(String.format("Case %d: mass for %s is within delta threshold: %.6f vs. %.6f",
          testCase, inchi, expectedMass, actualMassAndCharge.getLeft()),
          expectedMass, actualMassAndCharge.getLeft(), threshold);
      assertEquals(String.format("Case %d: charge %s matches expected", testCase, inchi),
          expectedCharge, actualMassAndCharge.getRight());
      testCase++;
    }
  }
}
