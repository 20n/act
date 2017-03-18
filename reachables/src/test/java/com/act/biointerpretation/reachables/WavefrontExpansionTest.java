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

package com.act.biointerpretation.reachables;

import com.act.reachables.WavefrontExpansion;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WavefrontExpansionTest {

  private Map<String, Integer> testCases;

  @Before
  public void setUp() throws Exception {
    testCases = new java.util.HashMap<>();
    testCases.put("InChI=1S/ClH7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 0);
    testCases.put("InChI=1S/CH7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 1);
    testCases.put("InChI=1S/CCl9H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 1);
    testCases.put("InChI=1S/ClC9H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 9);
    testCases.put("InChI=1S/Cl9H7NOC/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 1);
    testCases.put("InChI=1S/C14H17NO8/c1-6(16)15-7-2-4-8(5-3-7)22-14-11(19)9(17)10(18)12(23-14)13(20)21/h2-5,9-12,14,17-19H,1H3,(H,15,16)(H,20,21)/t9-,10-,11+,12-,14+/m0/s1", 14);
    testCases.put("InChI=1S/C12H14N2O5/c13-8-3-1-7(2-4-8)11(17)14-9(12(18)19)5-6-10(15)16/h1-4,9H,5-6,13H2,(H,14,17)(H,15,16)(H,18,19)", 12);
    testCases.put("InChI=1S/C24H39NO/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-19-22-24(26)25-23-20-17-16-18-21-23/h9-10,16-18,20-21H,2-8,11-15,19,22H2,1H3,(H,25,26)", 24);
    testCases.put("InChI=1S/C6H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 6);
    testCases.put("InChI=1S/Cl6H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2", 0);
    testCases.put("InChI=1S/Cd12H14N2O5/c13-8-3-1-7(2-4-8)11(17)14-9(12(18)19)5-6-10(15)16/h1-4,9H,5-6,13H2,(H,14,17)(H,15,16)(H,18,19)", 0);
  }

  @Test
  public void testCarbonCountingAccuratelyCountsCarbonsFromInchi() throws Exception {
    for (Map.Entry<String, Integer> testCase : testCases.entrySet()) {
      assertEquals(testCase.getValue(), WavefrontExpansion.countCarbons(testCase.getKey()));
    }
  }
}
