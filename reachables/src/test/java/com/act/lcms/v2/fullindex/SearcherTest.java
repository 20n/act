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

package com.act.lcms.v2.fullindex;

import com.act.utils.MockRocksDBAndHandles;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SearcherTest {
  public static final double FP_TOLERANCE = 0.000001;

  Searcher searcher;

  @Before
  public void setup() throws Exception {
    // Most of the work is done in the DB construction.  TODO: supply our own test data to use.
    MockRocksDBAndHandles<ColumnFamilies> fakeDB = BuilderTest.populateTestDB();
    searcher = new Searcher(fakeDB);
    searcher.init();
  }

  @Test
  public void searchIndexInRange() throws Exception {
    List<TMzI> actual = searcher.searchIndexInRange(Pair.of(100.004, 100.016), Pair.of(1.5, 3.5));

    List<Triple<Float, Double, Float>> expected = Arrays.asList(
        Triple.of(2.0F, 100.005, 10.0F),
        Triple.of(2.0F, 100.010, 20.0F),
        Triple.of(2.0F, 100.015, 30.0F),
        Triple.of(3.0F, 100.010, 100.0F),
        Triple.of(3.0F, 100.015, 200.0F)
    );

    assertEquals("Searcher returned expected number of TMzI tuples", expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      Triple<Float, Double, Float> e = expected.get(i);
      TMzI a = actual.get(i);
      assertEquals("Time matches expected", e.getLeft(), a.getTime(), FP_TOLERANCE);
      assertEquals("M/z matches expected", e.getMiddle(), a.getMz(), FP_TOLERANCE);
      assertEquals("Intensity matches expected", e.getRight(), a.getIntensity(), FP_TOLERANCE);
    }
  }
}
