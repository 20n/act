package com.act.analysis.chemicals

import act.shared.Chemical
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.struc.Molecule

import scala.collection.concurrent.TrieMap

object MoleculeImporter {
  private val moleculeCache = TrieMap[String, Molecule]()

  @throws[MolFormatException]
  def importMolecule(mol: String, setting: ChemicalSetting.MoleculeType = ChemicalSetting.Inchi): Molecule = {
    val molecule = moleculeCache.get(mol)

    if (molecule.isEmpty){
      val newMolecule = MolImporter.importMol(mol)
      moleculeCache.put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

  @throws[MolFormatException]
  private implicit def toMolecule(chemical: Chemical): String = chemical.getInChI

  object ChemicalSetting extends Enumeration {
    type MoleculeType = String
    val Inchi = "inchi"
    val Smiles = "smiles"
  }
}
