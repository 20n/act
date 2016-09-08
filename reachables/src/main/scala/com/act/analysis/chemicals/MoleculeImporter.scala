package com.act.analysis.chemicals

import act.shared.Chemical
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.struc.Molecule

import scala.collection.concurrent.TrieMap

object MoleculeImporter {
  private val moleculeCache = TrieMap[String, Molecule]()

  // For java
  @throws[MolFormatException]
  def importMolecule(chemical: Chemical): Molecule = {
    importMolecule(toMolecule(chemical))
  }

  // Overload for easy java interop.
  @throws[MolFormatException]
  def importMolecule(mol: String): Molecule = {
    importMolecule(mol, ChemicalSetting.Inchi)
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, setting: ChemicalSetting.MoleculeType): Molecule = {
    val molecule = moleculeCache.get(mol)

    if (molecule.isEmpty) {
      val newMolecule = MolImporter.importMol(mol, setting)
      moleculeCache.put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

  private implicit def toMolecule(chemical: Chemical): String = chemical.getInChI

  object ChemicalSetting extends Enumeration {
    type MoleculeType = String
    val Inchi = "inchi"
    val Smiles = "smiles"
    val Smarts = "smarts"
  }
}
