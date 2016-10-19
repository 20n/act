package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db

import act.server.MongoDB
import act.shared.Chemical
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, Keyword, MongoWorkflowUtilities}

import scala.collection.JavaConverters._

trait QueryChemicals extends MongoWorkflowUtilities {

  /**
    * From a chemical ID, returns the Molecule format
    */
  def getMoleculeById(mongoConnection: MongoDB)
                     (chemicalId: Long, moleculeFormat:
                     MoleculeFormat.Value = MoleculeFormat.inchi): Option[Molecule] = {
    val moleculeString = getChemicalsStringById(mongoConnection)(chemicalId, moleculeFormat)
    maybeImportString(moleculeString, moleculeFormat)
  }

  /**
    * Takes in a single ChemicalId and outputs the MoleculeFormat from the DB if available
    */
  def getChemicalsStringById(mongoConnection: MongoDB)
                            (chemicalId: Long,
                             moleculeFormat: MoleculeFormat.Value = MoleculeFormat.inchi): Option[String] = {
    val queryResult: Map[Long, Option[String]] = getChemicalsStringsByIds(mongoConnection)(List(chemicalId), moleculeFormat)

    if (queryResult.isEmpty) {
      throw new NoSuchElementException(s"No Chemical with ID of $chemicalId was found in the database.")
    }

    if (queryResult.size > 1) {
      throw new RuntimeException(s"More than one element (Total ${queryResult.size}) found to match $chemicalId.")
    }

    queryResult.values.head
  }

  /**
    * Get a single Molecule by ID
    */
  def getMoleculesById(mongoConnection: MongoDB)
                      (chemicalIds: List[Long],
                       moleculeFormat: MoleculeFormat.Value = MoleculeFormat.inchi): Map[Long, Option[Molecule]] = {
    val chemicalStrings = getChemicalsStringsByIds(mongoConnection)(chemicalIds)

    if (chemicalStrings.isEmpty) {
      return Map()
    }

    // We expect the MoleculeImporter to be concurrency safe,
    // based on its implementation and no prior difficulties using it in a concurrent context.
    chemicalStrings.par.map(maybeString => (maybeString._1, maybeImportString(maybeString._2, moleculeFormat))).seq
  }

  // From a list of Chemical IDs, returns the string representations from teh database if available
  def getChemicalsStringsByIds(mongoConnection: MongoDB)
                              (chemicalIds: List[Long],
                               moleculeFormat: MoleculeFormat.Value = MoleculeFormat.inchi): Map[Long, Option[String]] = {
    val queryResult: Option[java.util.Iterator[Chemical]] =
      Option(mongoConnection.getChemicalsbyIds(chemicalIds.map(java.lang.Long.valueOf).asJava, true))

    if (queryResult.isEmpty) {
      return Map()
    }

    queryResult.get.asScala.map(id => (id.getUuid.toLong, getFormatString(moleculeFormat, id))).toMap
  }

  /**
    * Returns the appropriate string, if exists.
    */
  private def getFormatString(moleculeFormat: MoleculeFormat.Value, result: Chemical): Option[String] = {
    if (Option(result).isEmpty) {
      return None
    }

    if (determineFormat(moleculeFormat).equals(ChemicalKeywords.INCHI)) {
      val returnString = Option(result.getInChI)
      if (returnString.isDefined) {
        // Fake or abstract InChIs are discarded.
        if (returnString.get.startsWith("InChI=/FAKE/") || returnString.get.contains("R")) {
          return None
        }
      }

      returnString
    } else {
      Option(result.getSmiles)
    }
  }

  /**
    * Determines if to use InChI or SMILES
    */
  private def determineFormat(moleculeFormat: MoleculeFormat.Value): Keyword = {
    if (MoleculeFormat.getImportString(moleculeFormat).contains("inchi")) {
      ChemicalKeywords.INCHI
    } else {
      ChemicalKeywords.SMILES
    }
  }

  /**
    * If the string exists, import it
    */
  private def maybeImportString(moleculeString: Option[String],
                                moleculeFormat: MoleculeFormat.Value): Option[Molecule] = {
    if (moleculeString.isDefined) {
      try {
        return Option(MoleculeImporter.importMolecule(moleculeString.get, moleculeFormat))
      } catch {
        case e: MolFormatException =>
      }
    }
    None
  }
}
