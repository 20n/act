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

  def setCacheSize(size: Long): Unit ={
    LOGGER.info(s"${getClass.getCanonicalName} cache size has changed to $size " +
      s"per ${MoleculeFormatType.getClass.getCanonicalName}.")
    maxCacheSize = size
  }

  private def buildCache(moleculeFormatType: MoleculeFormatType): Cache[String, Molecule] ={
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
}
