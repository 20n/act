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

package com.act.biointerpretation.analysis;

import com.act.analysis.similarity.ROBinning;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RoBinningTest {

  @Test
  public void testAliphaticRODoesNotMatchWithAromaticCompoundAndViceVersa() throws Exception {
    Ero aromaticRO = new Ero();
    aromaticRO.setId(1);
    aromaticRO.setRo("[H][c:6]1[c:7][c:1][c:2][c:3][c:4]1>>[C:1](=[O:7])1[C,c:2]=[C,c:3][C:4](=[O:8])[C,c:5]=[C,c:6]1");

    Ero aliphaticRO = new Ero();
    aliphaticRO.setId(2);
    aliphaticRO.setRo("[C:9]([H])[C:10]([H])>>[C:9]=[C:10]");

    ErosCorpus erosCorpus = new ErosCorpus();
    List<Ero> eros = new ArrayList<>();
    eros.add(aromaticRO);
    eros.add(aliphaticRO);
    erosCorpus.setRos(eros);

    ROBinning roBinning = new ROBinning(erosCorpus, null);
    roBinning.init();

    String aromaticInchi = "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H";
    String aliphaticInchi = "InChI=1S/C4H10O/c1-4(2)3-5/h4-5H,3H2,1-2H3";

    List<Integer> aromaticResult = roBinning.processChemical(aromaticInchi);
    Assert.assertEquals("Benzene should only match one RO", 1, aromaticResult.size());
    Assert.assertEquals("Benzene should only match the aromatic RO", 1, aromaticResult.get(0).intValue());

    List<Integer> aliphaticResult = roBinning.processChemical(aliphaticInchi);
    Assert.assertEquals("Butanol should only match one RO", 1, aliphaticResult.size());
    Assert.assertEquals("Butanol should only match the aliphatic RO", 2, aliphaticResult.get(0).intValue());
  }

  @Test
  public void testROIsBeingSplitProperlyAndMatchedAgainst() throws Exception {
    Ero aromaticRO = new Ero();
    aromaticRO.setId(1);
    aromaticRO.setRo("[H][c:6]1[c:7][c:1][c:2][c:3][c:4]1.[C:9]([H])[C:10]([H])>>[C:1](=[O:7])1[C,c:2]=[C,c:3][C:4](=[O:8])[C,c:5]=[C,c:6]1.[C:9]=[C:10]");

    ErosCorpus erosCorpus = new ErosCorpus();
    List<Ero> eros = new ArrayList<>();
    eros.add(aromaticRO);
    erosCorpus.setRos(eros);

    ROBinning roBinning = new ROBinning(erosCorpus, null);
    roBinning.init();

    String aromaticInchi = "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H";
    String aliphaticInchi = "InChI=1S/C4H10O/c1-4(2)3-5/h4-5H,3H2,1-2H3";

    List<Integer> aromaticResult = roBinning.processChemical(aromaticInchi);
    Assert.assertEquals("Benzene should only match one RO", 1, aromaticResult.size());
    Assert.assertEquals("Benzene should only match the RO since one half of it contains an aromatic compound and it " +
        "does not match anything in the product side", 1, aromaticResult.get(0).intValue());

    List<Integer> aliphaticResult = roBinning.processChemical(aliphaticInchi);
    Assert.assertEquals("Butanol should only match one RO", 1, aliphaticResult.size());
    Assert.assertEquals("Butanol should only match the RO since one half of it contains an aliphatic compound and it " +
        "does not match anything in the product side", 1, aliphaticResult.get(0).intValue());
  }
}
