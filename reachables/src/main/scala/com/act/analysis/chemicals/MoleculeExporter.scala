package com.act.analysis.chemicals

import chemaxon.formats.MolExporter
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

object MoleculeExporter {
  private val moleculeCache = TrieMap[ChemicalSetting.MoleculeType, TrieMap[Molecule, String]]()
  private var _globalFormat = List(ChemicalSetting.Inchi)

  def setGlobalFormat(formats: List[ChemicalSetting.MoleculeType]) = {
    _globalFormat = formats
  }

  @throws[MolExportException]
  def exportAsSmarts(mol: Molecule): String = {
    exportMolecule(mol, ChemicalSetting.Smarts)
  }

  def exportAsInchi(mol: Molecule): String = {
    exportMolecule(mol, ChemicalSetting.Inchi)
  }

  @throws[MolExportException]
  def exportMoleculesAsFormats(mols: List[Molecule], formats: List[ChemicalSetting.MoleculeType]): List[String] = {
    mols.map(exportMoleculeAsFormats(_, formats))
  }

  @throws[MolExportException]
  def exportMoleculesAsFormats(mols: List[Molecule]): List[String] = {
    mols.map(exportMoleculeAsFormats(_, _globalFormat))
  }

  @throws[MolExportException]
  def exportMoleculeAsFormats(mol: Molecule, formats: List[ChemicalSetting.MoleculeType]): String = {
    formats.foreach(format => {
      try {
        return exportMolecule(mol, format)
      } catch {
        case e: MolExportException => None
      }
    })

    throw new MolExportException("Could not convert molecules into any valid formats.")
  }

  private def exportMolecule(mol: Molecule, format: ChemicalSetting.MoleculeType): String = {
    val formatCache = moleculeCache.get(format)

    if (formatCache.isEmpty) {
      moleculeCache.put(format, new TrieMap[Molecule, String])
    }

    val smartsFormat = moleculeCache(format).get(mol)

    if (smartsFormat.isEmpty) {
      val newFormat = MolExporter.exportToFormat(mol, format)
      moleculeCache(format).put(mol, newFormat)
      return newFormat
    }

    smartsFormat.get
  }

  @throws[MolExportException]
  def exportMoleculesAsFormatsJava(mols: List[Molecule], formats: List[ChemicalSetting.MoleculeType]): java.util.List[String] = {
    mols.map(exportMoleculeAsFormats(_, formats)).asJava
  }

  @throws[MolExportException]
  def exportMoleculesAsFormatsJava(mols: List[Molecule]): java.util.List[String] = {
    mols.map(exportMoleculeAsFormats(_, _globalFormat)).asJava
  }

  object ChemicalSetting extends Enumeration {
    type MoleculeType = String
    val Inchi = "inchi"
    val Smiles = "smiles"
    val Smarts = "smarts"
  }

}
