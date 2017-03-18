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

package com.act.biointerpretation.rsmiles.single_sar_construction

import org.scalatest.{FlatSpec, Matchers}

/**
  * Currently this class is not a real test.
  * It just exemplifies a number of cases in which processing a db smiles into its processed substrate & product form fails
  * because some Carbon in the processed substrate ends up with too many hydrogens.
  * TODO: fix this bug!
  * Not sure what's causing it.
  */
class SingleSarChemicalsTest extends FlatSpec with Matchers {

  "SingleSarChemicals" should "have the correct valence" in {
    val abstractChem = "[CH](=O)[R]"
    val singleSarChemicals = new SingleSarChemicals(null)
    val dummyId = 1

    val maybeInfo = singleSarChemicals.calculateConcreteSubstrateAndProduct(dummyId, abstractChem)

    maybeInfo.isDefined should be(true)
    val chemicalInfo = maybeInfo.get

    println(s"Abstract chemical: ${chemicalInfo.dbSmiles}")
    println(s"Substrate : ${chemicalInfo.getAsSubstrate}")
    println(s"Product : ${chemicalInfo.getAsProduct}")
  }

  "SingleSarChemicals" should "have the correct valence again" in {
    val abstractChem = "CC([R])"
    val singleSarChemicals = new SingleSarChemicals(null)
    val dummyId = 1

    val maybeInfo = singleSarChemicals.calculateConcreteSubstrateAndProduct(dummyId, abstractChem)

    maybeInfo.isDefined should be(true)
    val chemicalInfo = maybeInfo.get

    println(s"Abstract chemical: ${chemicalInfo.dbSmiles}")
    println(s"Substrate : ${chemicalInfo.getAsSubstrate}")
    println(s"Product : ${chemicalInfo.getAsProduct}")
  }

  "SingleSarChemicals" should "have the correct valence again again" in {
    val abstractChem = "[CH2]([R])O"
    val singleSarChemicals = new SingleSarChemicals(null)
    val dummyId = 1

    val maybeInfo = singleSarChemicals.calculateConcreteSubstrateAndProduct(dummyId, abstractChem)

    maybeInfo.isDefined should be(true)
    val chemicalInfo = maybeInfo.get

    println(s"Abstract chemical: ${chemicalInfo.dbSmiles}")
    println(s"Substrate : ${chemicalInfo.getAsSubstrate}")
    println(s"Product : ${chemicalInfo.getAsProduct}")
  }


  "SingleSarChemicals" should "have the correct valence on chlorine" in {
    val abstractChem = "[CH2]([Cl])O"
    val singleSarChemicals = new SingleSarChemicals(null)
    val dummyId = 1

    val maybeInfo = singleSarChemicals.calculateConcreteSubstrateAndProduct(dummyId, abstractChem)

    maybeInfo.isDefined should be(true)
    val chemicalInfo = maybeInfo.get

    println(s"Abstract chemical: ${chemicalInfo.dbSmiles}")
    println(s"Substrate : ${chemicalInfo.getAsSubstrate}")
    println(s"Product : ${chemicalInfo.getAsProduct}")
  }
}
