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

import act.shared.Chemical
import chemaxon.formats.{MolFormatException, MolImporter}
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeFormat.MoleculeFormatType
import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

object MoleculeImporter {
  private val LOGGER = LogManager.getLogger(getClass)
  private var maxCacheSize = 10000L
  // Have a cache for each format.
  private val moleculeCache = TrieMap[MoleculeFormat.MoleculeFormatType, Cache[String, Molecule]]()

  def clearCache(): Unit = {
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

  private def buildCache(moleculeFormatType: MoleculeFormatType): Cache[String, Molecule] = {
    val caffeine = Caffeine.newBuilder().asInstanceOf[Caffeine[String, Molecule]]
    caffeine.maximumSize(maxCacheSize)

    // If you want to debug how the cache is doing
    caffeine.recordStats()
    caffeine.build[String, Molecule]()
  }

  // For java
  @throws[MolFormatException]
  def importMolecule(chemical: Chemical): Molecule = {
    importMolecule(toMolecule(chemical))
  }

  // Overload for easy java interop.
  @throws[MolFormatException]
  def importMolecule(mol: String): Molecule = {
    importMolecule(mol, MoleculeFormat.inchi)
  }

  private implicit def toMolecule(chemical: Chemical): String = chemical.getInChI

  @throws[MolFormatException]
  def importMolecule(mol: String, formats: List[MoleculeFormat.MoleculeFormatType]): Molecule = {
    val resultingInchis: List[Molecule] = formats.flatMap(format => {
      try {
        // Inchis must start with "InChI="
        if (format.toString.toLowerCase.contains("inchi") && (!mol.startsWith("InChI=")))
          throw new MolFormatException("InChIs must start with the value 'InChI='")
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
  def importMolecule(mol: String, format: MoleculeFormat.MoleculeFormatType): Molecule = {
    val formatCache = moleculeCache.get(format)
    if (formatCache.isEmpty) {
      moleculeCache.put(format, buildCache(format))
    }

    val molecule: Option[Molecule] = Option(moleculeCache(format).getIfPresent(mol))

    if (molecule.isEmpty) {
      val newMolecule = MolImporter.importMol(mol, MoleculeFormat.getImportString(format))

      // Note: All these functions work in place on the molecule...
      val cleaningApplyFunction = MoleculeFormat.Cleaning.applyCleaningOnMolecule(newMolecule)_
      format.cleaningOptions.foreach(cleaningApplyFunction)

      moleculeCache(format).put(mol, newMolecule)
      return newMolecule
    }

    molecule.get
  }

  @throws[MolFormatException]
  def importMolecule(mol: String, format: java.util.List[MoleculeFormat.MoleculeFormatType]): Molecule = {
    importMolecule(mol, format.asScala.toList)
  }

  @throws[FakeInchiException]
  def assertNotFakeInchi(inchi: String) {
    if (inchi != null && (inchi.contains("FAKE") || inchi.contains("R"))) {
      throw new FakeInchiException(inchi)
    }
  }

  class FakeInchiException(val inchi: String) extends Exception(s"Found FAKE inchi: $inchi")
}
