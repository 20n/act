package com.act.analysis.chemicals

import chemaxon.descriptors.{CFParameters, ChemicalFingerprint}
import chemaxon.struc.Molecule

import scala.collection.concurrent.TrieMap

object MoleculeConversions {
  // If a chemical's Array[Int] form has been calculated, cache it for reuse later (Really speeds up SAR tree traversal).
  private val chemicalCache = TrieMap[Molecule, Array[Int]]()

  // Set all on the same line so this default declaration stays together.
  private val _cfp = new CFParameters()
  _cfp.setLength(2048)
  _cfp.setBondCount(15)
  _cfp.setBitCount(4)

  /**
    * Utility to convert from a molecule to an Array[Int] using its chemical footprint.
    *
    * @param mol Input molecule
    *
    * @return Array representing the input molecule.
    */
  def toIntArray(mol: Molecule): Array[Int] = {
    val intArray: Option[Array[Int]] = chemicalCache.get(mol)

    // If it doesn't exist, we generate it
    if (intArray.isEmpty) {
      val qfp = new ChemicalFingerprint(cfp)

      // Chemaxon uses some internal modules that can cause concurrency issues on only this method call.
      this.synchronized {
        qfp.generate(mol)
      }

      val result = qfp.toFloatArray map (x => x.toInt)
      chemicalCache.put(mol, result)

      // Return the result so we don't ask the cache for more than we need (Don't put then ask for it right away).
      return result
    }

    intArray.get
  }

  def cfp: CFParameters = {
    _cfp
  }

  private implicit def inchiToMolecule(inchi: String): Molecule = MoleculeImporter.importMolecule(inchi)
}

