package com.act.analysis.chemicals

import chemaxon.descriptors.{CFParameters, ChemicalFingerprint, SimilarityCalculatorFactory}
import chemaxon.struc.Molecule

import scala.collection.concurrent.TrieMap

/**
  * Concurrency safe cached molecule
  */
object ChemicalSimilarity {


  // If a chemical's Array[Int] form has been calculated, cache it for reuse later (Really speeds up SAR tree traversal).
  private val chemicalCache = TrieMap[Molecule, Array[Int]]()

  // Set all on the same line so this default declaration stays together.
  private var _cfp = new CFParameters()
  _cfp.setLength(2048)
  _cfp.setBondCount(15)
  _cfp.setBitCount(4)

  /**
    * For two molecules, use a calculator to determine their closeness.
    *
    * @param calculatorSettings The settings with which to apply the similarity calculation with
    *                           The current default value was chosen based on the Chemaxon tutorial example.
    * @param query              Molecule to use as the query molecule.
    * @param target             Molecule you are targeting to see how similar it is to the query.
    *
    * @return Similarity value between 0 and 1.
    */
  def calculateSimilarity(calculatorSettings: String = "TVERSKY;0.33;0.99")
                         (query: Molecule, target: Molecule): Double = {
    val simCalc = SimilarityCalculatorFactory.create(calculatorSettings)

    simCalc.setQueryFingerprint(moleculeToIntArray(query))
    simCalc.getSimilarity(moleculeToIntArray(target))
  }

  /**
    * Utility to convert from a molecule to an Array[Int] using its chemical footprint.
    *
    * @param mol Input molecule
    *
    * @return Array representing the input molecule.
    */
  private def moleculeToIntArray(mol: Molecule): Array[Int] = {
    val intArray: Option[Array[Int]] = chemicalCache.get(mol)

    // If it doesn't exist, we generate it
    if (intArray.isEmpty) {
      val qfp = new ChemicalFingerprint(cfp)

      // Chemaxon uses some internal modules that can cause concurrency issues on only this method call.
      this.synchronized {
        qfp.generate(mol)
      }

      val result = qfp.toFloatArray map (x => x.toInt)
      chemicalCache.putIfAbsent(mol, result)

      // Return the result so we don't ask the cache for more than we need (Don't put then ask for it right away).
      return result
    }

    intArray.get
  }

  def cfp: CFParameters = {
    this.synchronized {
      _cfp
    }
  }

  def cfp_=(value: CFParameters): Unit = {
    this.synchronized {
      _cfp = value
    }
  }
}
