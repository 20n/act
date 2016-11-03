package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db

import act.server.MongoDB
import act.shared.Chemical
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeFormat.MoleculeFormatType
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, Keyword, MongoWorkflowUtilities}

import scala.collection.JavaConverters._

trait QueryChemicals extends MongoWorkflowUtilities {

  /**
    * From a chemical ID, returns the molecule for that chemical in the appropriate format
    */
  def getMoleculeById(mongoConnection: MongoDB)
                     (chemicalId: Long, moleculeFormat:
                     MoleculeFormatType = MoleculeFormat.inchi): Option[Molecule] = {
    val moleculeString = getChemicalStringById(mongoConnection)(chemicalId, moleculeFormat)
    maybeImportString(moleculeString, moleculeFormat)
  }

  /**
    * Takes in a single ChemicalId and outputs the MoleculeFormat from the DB if available
    */
  def getChemicalStringById(mongoConnection: MongoDB)
                           (chemicalId: Long,
                             moleculeFormat: MoleculeFormatType = MoleculeFormat.inchi): Option[String] = {
    val queryResult: Map[Long, Option[String]] = getChemicalStringsByIds(mongoConnection)(List(chemicalId), moleculeFormat)

    if (queryResult.isEmpty) {
      throw new NoSuchElementException(s"No Chemical with ID of $chemicalId was found in the database.")
    }

    if (queryResult.size > 1) {
      throw new RuntimeException(s"More than one element (Total ${queryResult.size}) found to match $chemicalId.")
    }

    queryResult.values.head
  }

  /**
    * Get a list of Molecules by IDs
    */
  def getMoleculesById(mongoConnection: MongoDB)
                      (chemicalIds: List[Long],
                       moleculeFormat: MoleculeFormatType = MoleculeFormat.inchi): Map[Long, Option[Molecule]] = {
    val chemicalStrings = getChemicalStringsByIds(mongoConnection)(chemicalIds)

    if (chemicalStrings.isEmpty) {
      return Map()
    }

    // We expect the MoleculeImporter to be concurrency safe,
    // based on its implementation and no prior difficulties using it in a concurrent context.
    chemicalStrings.par.map(maybeString => (maybeString._1, maybeImportString(maybeString._2, moleculeFormat))).seq
  }

  // From a list of Chemical IDs, returns the string representations from teh database if available
  def getChemicalStringsByIds(mongoConnection: MongoDB)
                             (chemicalIds: List[Long],
                               moleculeFormat: MoleculeFormatType = MoleculeFormat.inchi): Map[Long, Option[String]] = {
    val queryResult: Option[java.util.Iterator[Chemical]] =
      Option(mongoConnection.getChemicalsbyIds(chemicalIds.map(java.lang.Long.valueOf).asJava, true))

    if (queryResult.isEmpty) {
      return Map()
    }

    queryResult.get.asScala.map(id => (id.getUuid.toLong, getChemicalStringByFormat(moleculeFormat, id))).toMap
  }

  /**
    * Returns the appropriate string, if exists.
    * This assumes that we have used "determineFormat" to determine the format to get the format,
    * which ensures that it is a valid type.
    */
  private def getChemicalStringByFormat(moleculeFormat: MoleculeFormatType, result: Chemical): Option[String] = {
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
  private def determineFormat(moleculeFormat: MoleculeFormatType): Keyword = {
    if (MoleculeFormat.getImportString(moleculeFormat).contains("inchi")) {
      ChemicalKeywords.INCHI
    } else if (MoleculeFormat.getImportString(moleculeFormat).contains("smiles") ||
      MoleculeFormat.getImportString(moleculeFormat).contains("smarts")) {
      ChemicalKeywords.SMILES
    } else {
      throw new RuntimeException(s"Invalid chemical format $moleculeFormat supplied.")
    }
  }

  /**
    * If the string exists, import it
    */
  private def maybeImportString(moleculeString: Option[String],
                                moleculeFormat: MoleculeFormatType): Option[Molecule] = {
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
