package com.act.analysis.chemicals

import chemaxon.formats.MolExporter
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule

import scala.collection.concurrent.TrieMap

object MoleculeExporter {
  private val moleculeCache = TrieMap[Molecule, String]()

  @throws[MolExportException]
  def exportAsSmarts(mol: Molecule): String = {
    exportMolecule(mol, ChemicalSetting.Smarts)
  }

  private def exportMolecule(mol: Molecule, setting: ChemicalSetting.MoleculeType): String = {
    val smartsFormat = moleculeCache.get(mol)

    if (smartsFormat.isEmpty) {
      val newFormat = MolExporter.exportToFormat(mol, setting)
      moleculeCache.put(mol, newFormat)
      return newFormat
    }

    smartsFormat.get
  }

  object ChemicalSetting extends Enumeration {
    type MoleculeType = String
    val Inchi = "inchi"
    val Smiles = "smiles"
    val Smarts = "smarts"
  }

}
