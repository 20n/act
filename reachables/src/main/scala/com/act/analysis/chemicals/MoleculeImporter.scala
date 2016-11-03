package com.act.analysis.chemicals

import act.shared.Chemical
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.struc.Molecule

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

object MoleculeImporter {
  // Have a cache for each format.
  private val moleculeCache = TrieMap[ChemicalFormat.MoleculeType, TrieMap[String, Molecule]]()

  // For java
  @throws[MolFormatException]
  def importMolecule(chemical: Chemical): Molecule = {
    importMolecule(toMolecule(chemical))
  }

  // Overload for easy java interop.
  @throws[MolFormatException]
  def importMolecule(mol: String): Molecule = {
    importMolecule(mol, ChemicalFormat.Inchi)
  }

  private implicit def toMolecule(chemical: Chemical): String = chemical.getInChI

  @throws[MolFormatException]
  def importMolecule(mol: String, formats: java.util.List[String]): Molecule = {
    importMolecule(mol, formats.asScala.toList)
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, formats: List[ChemicalFormat.MoleculeType]): Molecule = {
    val resultingInchis: List[Molecule] = formats.flatMap(format => {
      try {
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
  def importMolecule(mol: String, format: ChemicalFormat.MoleculeType): Molecule = {
    val formatCache = moleculeCache.get(format)
    if (formatCache.isEmpty){
      moleculeCache.put(format, new TrieMap[String, Molecule])
    }

    val molecule = moleculeCache(format).get(mol)

    if (molecule.isEmpty) {
      val newMolecule = MolImporter.importMol(mol, format)
      moleculeCache(format).put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

  object ChemicalFormat extends Enumeration {
    type MoleculeType = String
    val Inchi = "inchi"
    val Smiles = "smiles"
    val Smarts = "smarts"
  }
}
