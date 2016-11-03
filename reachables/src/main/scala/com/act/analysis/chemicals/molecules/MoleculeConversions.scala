package com.act.analysis.chemicals.molecules

import chemaxon.descriptors.{CFParameters, ChemicalFingerprint}
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import org.apache.log4j.LogManager

import scala.collection.concurrent.TrieMap

object MoleculeConversions {
  private val logger = LogManager.getLogger(getClass.getName)

  // If a chemical's Array[Int] form has been calculated, cache it for reuse later (Really speeds up SAR tree traversal).
  private val chemicalCache = TrieMap[Molecule, Array[Int]]()

  // Set all on the same line so this default declaration stays together.
  private val _cfp = new CFParameters()
  _cfp.setLength(2048)
  _cfp.setBondCount(15)
  _cfp.setBitCount(4)

  def toIntArray(mol: Molecule): Array[Int] = helperToIntArray(mol)

  /**
    * Utility to convert from a molecule to an Array[Int] using its chemical footprint.
    *
    * @param mol Input molecule
    *
    * @return Array representing the input molecule.
    */
  def helperToIntArray(mol: Molecule): Array[Int] = {
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

  private def cfp: CFParameters = {
    _cfp
  }

  def toIntArray(mol: String): Array[Int] = helperToIntArray(mol)

  implicit def stringToMolecule(s: String): Molecule = {
    try {
      // Is InChI
      MoleculeImporter.importMolecule(s)
    } catch {
      case e: MolFormatException =>
        logger.debug("Unable to convert String to InChI, trying to convert to Smiles.")
        MoleculeImporter.importMolecule(s, MoleculeFormat.smiles)
    }
  }
}

