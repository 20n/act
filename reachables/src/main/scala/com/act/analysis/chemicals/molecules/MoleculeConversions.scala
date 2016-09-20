package com.act.analysis.chemicals.molecules

import chemaxon.descriptors.{CFParameters, ChemicalFingerprint}
import chemaxon.struc.Molecule
import org.apache.log4j.LogManager

import scala.collection.concurrent.TrieMap

object MoleculeConversions {
  private val logger = LogManager.getLogger(getClass)

  // If a chemical's Array[Int] form has been calculated, cache it for reuse later (Really speeds up SAR tree traversal).
  // We use a TrieMap because it is a thread-safe implementation of a hashmap,
  // which is important if we want to use this object in parallel.
  private val chemicalCache = TrieMap[Molecule, Array[Int]]()
  private def fingerPrintParams: CFParameters = _cfp

  // These parameters were found after consulting the two sources below.
  //
  // Tune these values are your peril, both in terms of how long you wait and what your results are.
  //
  // Resources:
  // General Docs: https://docs.chemaxon.com/display/docs/Chemical+Hashed+Fingerprint
  // Algorithm Related: http://www.daylight.com/dayhtml/doc/theory/theory.finger.html
  private val _cfp = new CFParameters()

  /*
    We pick 1024 because it is recommended by chemaxon docs.

    The effects are:
      Longer and more patterns hold more information on the molecule.

      Due to the higher number of patterns to be explored, structure import will be slower.

      Increases the number of patterns considered. As a result of that, the fingerprint darkness rises (up to a limit).
      The number of bit collisions increase.

      While the number of bit collisions is not too high,
      the stored information increases, which is beneficial for the efficiency of screening.
   */
  _cfp.setLength(1024)

  /*
    The maximum length of atoms in the linear paths that are considered during the fragmentation of the molecule.
    (The length of cyclic patterns is limited to a fixed ring size.)

    We pick 8 because it is > benzene and recommended by chemaxon docs cited above.

    The effects are:
      The fingerprint darkness rises.

      The coded information derived from a pattern increases.

      The effect on darkness is similar to the case of the maximum pattern length:
      it also increases. And also, while the bit collision number is not too high,
      the stored information increases, which is beneficial for the efficiency of the screening.
   */
  _cfp.setBondCount(8)

  /*
    After detecting a pattern,some bits of the bit string are set to "1".
    The number of bits used to code patterns is constant

    Two is recommended by the chemaxon docs.

    The effects are:
      The fingerprint darkness rises.

      The coded information derived from a pattern increases.

      The effect on darkness is similar to the case of the maximum pattern length:
      it also increases. And also, while the bit collision number is not too high,
      the stored information increases, which is beneficial for the efficiency of the screening.
   */
  _cfp.setBitCount(2)

  def toIntArray(mol: String): Array[Int] = helperToIntArray(mol)
  def toIntArray(mol: Molecule): Array[Int] = helperToIntArray(mol)

  /**
    * Utility to convert from a molecule to an Array[Int] using its chemical footprint.
    *
    * @param mol Input molecule
    *
    * @return Array representing the input molecule.
    */
  private def helperToIntArray(mol: Molecule): Array[Int] = {
    val intArray: Option[Array[Int]] = chemicalCache.get(mol)

    // If it doesn't exist, we generate it
    if (intArray.isEmpty) {
      val queryFingerPrint = new ChemicalFingerprint(fingerPrintParams)

      // Chemaxon uses some internal modules that can cause concurrency issues on only this method call.
      this.synchronized {
        queryFingerPrint.generate(mol)
      }

      val result = queryFingerPrint.toFloatArray map (x => x.toInt)
      chemicalCache.put(mol, result)

      // Return the result so we don't ask the cache for more than we need (Don't put then ask for it right away).
      return result
    }

    intArray.get
  }

  implicit def stringToMolecule(s: String): Molecule = {
    MoleculeImporter.importMolecule(s, List(MoleculeFormat.inchi, MoleculeFormat.smarts))
  }
}
