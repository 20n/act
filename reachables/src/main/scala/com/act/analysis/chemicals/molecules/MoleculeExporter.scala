package com.act.analysis.chemicals.molecules

import chemaxon.formats.MolExporter
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
  * Provides a consistent place to handle molecule exportation
  * in all the various formats we experience in a cache friendly manner.
  */
object MoleculeExporter {
  // By hashing also on the format we can support a molecule being converted to multiple formats in a given JVM
  private val moleculeCache = TrieMap[MoleculeFormat.MoleculeFormatType, TrieMap[Molecule, String]]()

  // Defaults to inchi which has aux information.
  private var defaultFormat = List(MoleculeFormat.inchi)

  def clearCache(): Unit ={
    moleculeCache.keySet.foreach(key => moleculeCache.put(key, new TrieMap[Molecule, String]))
  }

  def setDefaultFormat(format: MoleculeFormat.MoleculeFormatType): Unit = {
    setDefaultFormat(List(format))
  }

  def setDefaultFormat(formats: List[MoleculeFormat.MoleculeFormatType]): Unit = {
    defaultFormat = formats
  }

  /*
    Allows for a global default to be set and used.  Default default is inchi.
   */
  @throws[MolExportException]
  def exportMoleculesDefaultFormat(mols: List[Molecule]): List[String] = {
    mols.map(exportMoleculeDefaultFormat)
  }

  @throws[MolExportException]
  def exportMoleculesDefaultFormatJava(mols: List[Molecule]): java.util.List[String] = {
    mols.map(exportMoleculeDefaultFormat).asJava
  }

  @throws[MolExportException]
  def exportMoleculeDefaultFormat(mol: Molecule): String = {
    exportMoleculeAsFormats(mol, defaultFormat)
  }

  // The basic format
  def exportMolecule(mol: Molecule, format: MoleculeFormat.MoleculeFormatType): String = {
    val formatCache = moleculeCache.get(format)

    if (formatCache.isEmpty) {
      moleculeCache.put(format, new TrieMap[Molecule, String])
    }

    val moleculeString = moleculeCache(format).get(mol)

    if (moleculeString.isEmpty) {
      val newFormat = MolExporter.exportToFormat(mol, MoleculeFormat.getExportString(format.value))
      moleculeCache(format).put(mol, newFormat)
      return newFormat
    }

    moleculeString.get
  }

  @throws[MolExportException]
  def exportAsSmarts(mol: Molecule): String = {
    exportMolecule(mol, MoleculeFormat.smarts)
  }

  @throws[MolExportException]
  def exportAsStdInchi(mol: Molecule): String = {
    exportMolecule(mol, MoleculeFormat.stdInchi)
  }

  /*
    Multiple format conversions

    These methods try to convert a molecule into one of a set of formats.
   */
  @throws[MolExportException]
  def exportMoleculeAsFormats(mol: Molecule, formats: List[MoleculeFormat.MoleculeFormatType]): String = {
    formats.foreach(format => {
      try {
        return exportMolecule(mol, format)
      } catch {
        case e: MolExportException => None
      }
    })

    throw new MolExportException(s"Could not convert molecules into any valid formats that were specified $formats")
  }

  @throws[MolExportException]
  def exportMoleculesAsFormats(mols: List[Molecule], formats: List[MoleculeFormat.MoleculeFormatType]): List[String] = {
    mols.map(exportMoleculeAsFormats(_, formats))
  }

  @throws[MolExportException]
  def exportMoleculesAsFormatsJava(mols: List[Molecule], formats: List[MoleculeFormat.MoleculeFormatType]): java.util.List[String] = {
    mols.map(exportMoleculeAsFormats(_, formats)).asJava
  }
}

