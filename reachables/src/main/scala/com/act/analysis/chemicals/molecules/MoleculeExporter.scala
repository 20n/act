/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.analysis.chemicals.molecules

import chemaxon.formats.MolExporter
import chemaxon.marvin.io.MolExportException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeFormat.MoleculeFormatType
import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
  * Provides a consistent place to handle molecule exportation
  * in all the various formats we experience in a cache friendly manner.
  */
object MoleculeExporter {
  private val LOGGER = LogManager.getLogger(getClass)
  private var maxCacheSize = 10000L
  // By hashing also on the format we can support a molecule being converted to multiple formats in a given JVM
  private val moleculeCache = TrieMap[MoleculeFormat.MoleculeFormatType, Cache[Molecule, String]]()

  // Defaults to inchi which has aux information.
  private var defaultFormat: List[MoleculeFormat.MoleculeFormatType] = List(MoleculeFormat.inchi)

  def clearCache(): Unit ={
    moleculeCache.keySet.foreach(key => moleculeCache.put(key, buildCache(key)))
  }

  /**
    * Wipes all the current caches and changes their maximum sizes to the designated value
    *
    * @param size Maximum number of elements in the cache
    */
  def setCacheSize(size: Long): Unit = {
    LOGGER.info(s"${getClass.getCanonicalName} cache size has changed to $size " +
      s"per ${MoleculeFormatType.getClass.getCanonicalName}.")
    maxCacheSize = size
    clearCache()
  }

  private def buildCache(moleculeFormatType: MoleculeFormatType): Cache[Molecule, String] = {
    val caffeine = Caffeine.newBuilder().asInstanceOf[Caffeine[Molecule, String]]
    caffeine.maximumSize(maxCacheSize)

    // If you want to debug how the cache is doing
    caffeine.recordStats()
    caffeine.build[Molecule, String]()
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
      moleculeCache.put(format, buildCache(format))
    }

    val moleculeString: Option[String] = Option(moleculeCache(format).getIfPresent(mol))

    if (moleculeString.isEmpty) {
      val newFormat = MolExporter.exportToFormat(mol, MoleculeFormat.getExportString(format))
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
  def exportAsSmiles(mol: Molecule): String = {
    exportMolecule(mol, MoleculeFormat.smiles)
  }

  @throws[MolExportException]
  def exportAsInchiKey(mol: Molecule): String = {
    exportMolecule(mol, MoleculeFormat.inchiKey)
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

