package com.act.analysis.chemicals.molecules

import act.shared.Chemical
import chemaxon.calculations.clean.Cleaner
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.standardizer.Standardizer
import chemaxon.struc.{Molecule, MoleculeGraph}

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

object MoleculeImporter {
  // Have a cache for each format.
  private val moleculeCache = TrieMap[MoleculeFormat.MoleculeFormatType, TrieMap[String, Molecule]]()

  def clearCache(): Unit = {
    moleculeCache.keySet.foreach(key => moleculeCache.put(key, new TrieMap[String, Molecule]))
  }

  // For java
  @throws[MolFormatException]
  def importMolecule(chemical: Chemical): Molecule = {
    importMolecule(toMolecule(chemical))
  }

  // Overload for easy java interop.
  @throws[MolFormatException]
  def importMolecule(mol: String): Molecule = {
    importMolecule(mol, MoleculeFormat.inchi)
  }

  private implicit def toMolecule(chemical: Chemical): String = chemical.getInChI

  @throws[MolFormatException]
  def importMolecule(mol: String, formats: List[MoleculeFormat.MoleculeFormatType]): Molecule = {
    val resultingInchis: List[Molecule] = formats.flatMap(format => {
      try {
        // Inchis must start with "InChI="
        if (format.toString.toLowerCase.contains("inchi")){
          if (!mol.startsWith("InChI=")) throw new MolFormatException()
        }
        val importedMolecule = Option(importMolecule(mol, format))
        importedMolecule
      } catch {
        case e: MolFormatException => None
      }
    })

    if (resultingInchis.isEmpty) {
      throw new MolFormatException()
    }

    if (resultingInchis.length > 1) {
      throw new MolFormatException("Multiple format types matched this string, " +
        s"so we couldn't decide which one was correct. String was $mol.")
    }

    resultingInchis.head
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, format: MoleculeFormat.MoleculeFormatType): Molecule = {
    val formatCache = moleculeCache.get(format)
    if (formatCache.isEmpty){
      moleculeCache.put(format, new TrieMap[String, Molecule])
    }

    val molecule = moleculeCache(format).get(mol)

    if (molecule.isEmpty) {
      val newMolecule = MolImporter.importMol(mol, MoleculeFormat.getImportString(format.value))

      // Do cleaning here if request.... All these functions work inplace on the molecule...
      val cleaningApplyFunction = MoleculeFormat.CleaningOptions.applyCleaningOnMolecule(newMolecule)_
      format.cleaningOptions.foreach(cleaningApplyFunction)

      moleculeCache(format).put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, format: java.util.List[MoleculeFormat.MoleculeFormatType]): Molecule = {
    importMolecule(mol, format.asScala.toList)
  }
}
