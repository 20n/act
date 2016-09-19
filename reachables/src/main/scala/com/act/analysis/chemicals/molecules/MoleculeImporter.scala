package com.act.analysis.chemicals.molecules

import act.shared.Chemical
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.struc.Molecule

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
  * The goal of this object is to have a consistent access point for importing molecules that handles
  * cache consistency and concurrency.
  * Additionally, small options such as check if an InChi starts with "InChi prior to trying to import are made here.
  */
object MoleculeImporter {
  // Have a cache for each format.
  // TODO Make this a more LRU style cache so it is less memory intensive for large data sets.
  private val moleculeCache = TrieMap[MoleculeFormat.Value, TrieMap[String, Molecule]]()

  def clearCache(): Unit = {
    moleculeCache.keySet.foreach(key => moleculeCache.put(key, new TrieMap[String, Molecule]))
  }

  // For java
  @throws[MolFormatException]
  def importMolecule(chemical: Chemical): Molecule = {
    importMolecule(chemical.getInChI)
  }

  // Overload for easy java interop.
  @throws[MolFormatException]
  def importMolecule(mol: String): Molecule = {
    importMolecule(mol, MoleculeFormat.inchi)
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, formats: List[MoleculeFormat.Value]): Molecule = {
    formats.foreach(format => {
      try {
        // Inchis must start with "InChI="
        if (format.toString.toLowerCase.contains("inchi") && !mol.startsWith("InChI=")) throw new MolFormatException()
        val importedMolecule = importMolecule(mol, format)

        // Short-circuit upon first find.
        return importedMolecule
      } catch {
        case e: MolFormatException => None
      }
    })

    throw new MolFormatException(s"Could not convert your input string [$mol] into any of format [$formats].")
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, format: MoleculeFormat.Value): Molecule = {
    val formatCache = moleculeCache.get(format)
    if (formatCache.isEmpty) {
      moleculeCache.put(format, new TrieMap[String, Molecule])
    }

    val molecule = moleculeCache(format).get(mol)

    if (molecule.isEmpty) {
      val newMolecule = MolImporter.importMol(mol, MoleculeFormat.getImportString(format))
      moleculeCache(format).put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, format: java.util.List[MoleculeFormat.Value]): Molecule = {
    importMolecule(mol, format.asScala.toList)
  }
}
