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
    val resultingInchis: List[Molecule] = formats.flatMap(format => {
      try {
        // Inchis must start with "InChI="
        if (format.equals(MoleculeFormat.stdInchi) || format.equals(MoleculeFormat.inchi)){
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
