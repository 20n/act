package com.act.analysis.chemicals

import chemaxon.descriptors.{CFParameters, ChemicalFingerprint, SimilarityCalculator, SimilarityCalculatorFactory}
import chemaxon.struc.Molecule

import scala.collection.concurrent.TrieMap

object ChemicalSimilarity {

  private val cfp = new CFParameters()
  cfp.setLength(2048)
  cfp.setBondCount(15)
  cfp.setBitCount(4)

  // Instead of remaking for all SARs, we cache our calculators on by their query chemicals.
  private val factoryCache = TrieMap[Molecule, SimilarityCalculator[Array[Int]]]()
  private val chemicalCache = TrieMap[Molecule, Array[Int]]()

  def calculateSimilarity(query: Molecule, target: Molecule): Double = {
    val cachedSimCalc = factoryCache.get(query)

    val simCalc: SimilarityCalculator[Array[Int]] =
      if (cachedSimCalc.isDefined) cachedSimCalc.get else SimilarityCalculatorFactory.create("TVERSKY;0.33;0.99")

    // Setup calculator if it is new
    if (cachedSimCalc.isEmpty) {
      simCalc.setQueryFingerprint(moleculeToIntArray(query))
      factoryCache.putIfAbsent(query, simCalc)
    }

    simCalc.getSimilarity(moleculeToIntArray(target))
  }

  private def moleculeToIntArray(mol: Molecule): Array[Int] = {
    val intArray: Option[Array[Int]] = chemicalCache.get(mol)

    if (intArray.isEmpty) {
      val qfp = new ChemicalFingerprint(cfp)

      // Chemaxon uses some internal modules that can cause concurrent issues.
      this.synchronized {
        qfp.generate(mol)
      }

      val result = qfp.toFloatArray map (x => x.toInt)
      chemicalCache.putIfAbsent(mol, result)
      return result
    }

    intArray.get
  }
}
