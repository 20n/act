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

package com.act.biointerpretation.mechanisminspection;

import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ErosCorpusTest {

  @Test
  public void filterCorpusBySubstrateCount() throws Exception {
    ErosCorpus fullCorpus = new ErosCorpus();
    fullCorpus.loadValidationCorpus();

    ErosCorpus corpusOneSubstrate = new ErosCorpus();
    corpusOneSubstrate.loadValidationCorpus();

    corpusOneSubstrate.filterCorpusBySubstrateCount(1);
    assertEquals("One substrate ERO lists match in size",
        fullCorpus.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(1)).collect(Collectors.toList()).size(),
        corpusOneSubstrate.getRos().size()
    );

    assertEquals("Re-filtering substrate count filtered eros yields same list",
        corpusOneSubstrate.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(1)).collect(Collectors.toList()),
        corpusOneSubstrate.getRos()
    );


    ErosCorpus corpusTwoSubstrates = new ErosCorpus();
    corpusTwoSubstrates.loadValidationCorpus();

    corpusTwoSubstrates.filterCorpusBySubstrateCount(2);
    assertEquals("Two substrates ERO lists match in size",
        fullCorpus.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(2)).collect(Collectors.toList()).size(),
        corpusTwoSubstrates.getRos().size()
    );

    assertEquals("Re-filtering substrate count filtered eros yields same list",
        corpusTwoSubstrates.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(2)).collect(Collectors.toList()),
        corpusTwoSubstrates.getRos()
    );
  }
}
