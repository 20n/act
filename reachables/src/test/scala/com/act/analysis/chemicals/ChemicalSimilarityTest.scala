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
