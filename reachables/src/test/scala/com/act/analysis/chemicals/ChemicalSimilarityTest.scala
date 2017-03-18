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

package com.act.analysis.chemicals

import org.scalatest.{FlatSpec, Matchers}

class ChemicalSimilarityTest extends FlatSpec with Matchers {
  val benzeneInchi = "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H"
  val acetaminophenInchi = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"
  val fluorinePerchlorateInchi = "InChI=1S/ClFO4/c2-6-1(3,4)5"
  val tolueneSmiles = "Cc1ccccc1"

  ChemicalSimilarity.init()

  "ChemicalSimilarity" should "score the same InChI as completely similar" in {
    ChemicalSimilarity.calculateSimilarity(benzeneInchi, benzeneInchi) should be(1.0)
  }

  "ChemicalSimilarity" should "score the same InChI as not dissimilar at all." in {
    ChemicalSimilarity.calculateDissimilarity(benzeneInchi, benzeneInchi) should be(0.0)
  }

  "ChemicalSimilarity" should "score different InChIs as being somewhat similar if they are." in {
    ChemicalSimilarity.calculateSimilarity(acetaminophenInchi, benzeneInchi) should be < 1.0
    ChemicalSimilarity.calculateSimilarity(acetaminophenInchi, benzeneInchi) should be > 0.0
  }

  "ChemicalSimilarity" should "score different InChIs as being somewhat dissimilar." in {
    ChemicalSimilarity.calculateDissimilarity(acetaminophenInchi, benzeneInchi) should be > 0.0
  }

  "ChemicalSimilarity" should "score completely unsimilar InChIs as being completely different." in {
    ChemicalSimilarity.calculateSimilarity(fluorinePerchlorateInchi, benzeneInchi) should be(0.0)
  }

  "ChemicalSimilarity" should "score completely different InChIs as being completely different." in {
    ChemicalSimilarity.calculateDissimilarity(fluorinePerchlorateInchi, benzeneInchi) should be(1.0)
  }

  "ChemicalSimilarity" should "be able to accept Smiles that have been." in {
    ChemicalSimilarity.calculateSimilarity(tolueneSmiles, tolueneSmiles) should be(1.0)
  }
}
