package com.act.analysis.chemicals

import act.shared.Chemical
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.struc.Molecule
import com.act.workflow.tool_manager.jobs.StatusCodes.Value

import scala.collection.concurrent.TrieMap

object MoleculeImporter {
  private object ChemicalSetting extends Enumeration {
    val Inchi = "inchi"
    val Smiles = "smiles"
  }

  private val moleculeCache = TrieMap[String, Molecule]()

  @throws[MolFormatException]
  def importMoleculeFromInchi(inchi: String): Molecule = {
    moleculeImportHelper(inchi, ChemicalSetting.Inchi)
  }

  @throws[MolFormatException]
  def importMoleculeFromSmiles(smile: String): Molecule = {
    moleculeImportHelper(smile, ChemicalSetting.Smiles)
  }

  @throws[MolFormatException]
  def importMolecule(chemical: Chemical): Molecule = {
    importMoleculeFromInchi(chemical.getInChI)
  }

  @throws[MolFormatException]
  private def moleculeImportHelper(mol: String, setting: String): Molecule = {
    val molecule = moleculeCache.get(mol)

    if (molecule.isEmpty){
      val newMolecule = MolImporter.importMol(mol, setting)
      moleculeCache.put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

}
