package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db

import act.server.MongoDB
import act.shared.Chemical
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities}

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
    val queryResult: List[Option[String]] = getChemicalsStringsByIds(mongoConnection)(List(chemicalId), moleculeFormat)

    if (queryResult.isEmpty) {
      throw new NoSuchElementException(s"No Chemical with ID of $chemicalId was found in the database.")
    }

    if (queryResult.length > 1) {
      throw new RuntimeException(s"More than one element (Total ${queryResult.length}) found to match $chemicalId.")
    }

    queryResult.head
  }

  /**
    * Get a single Molecule by ID
    */
  def getMoleculesById(mongoConnection: MongoDB)
                      (chemicalIds: List[Long],
                       moleculeFormat: MoleculeFormat.Value = MoleculeFormat.inchi): List[Option[Molecule]] = {
    val chemicalStrings = getChemicalsStringsByIds(mongoConnection)(chemicalIds)

    if (chemicalStrings.isEmpty) {
      return List()
    }

    chemicalStrings.par.map(maybeString => maybeImportString(maybeString, moleculeFormat)).toList
  }

  // From a list of Chemical IDs, returns the string representations from teh database if available
  def getChemicalsStringsByIds(mongoConnection: MongoDB)
                              (chemicalIds: List[Long],
                               moleculeFormat: MoleculeFormat.Value = MoleculeFormat.inchi): List[Option[String]] = {
    val queryResult: Option[java.util.Iterator[Chemical]] =
      Option(mongoConnection.getChemicalsbyIds(chemicalIds.map(java.lang.Long.valueOf).asJava, true))

    if (queryResult.isEmpty) {
      return List()
    }

    queryResult.get.asScala.map(getFormatString(moleculeFormat, _)).toList
  }

  /**
    * Returns the appropriate string, if exists.
    */
  private def getFormatString(moleculeFormat: MoleculeFormat.Value, result: Chemical): Option[String] = {
    if (Option(result).isEmpty) {
      return None
    }

    if (determineFormat(moleculeFormat).equals(ChemicalKeywords.INCHI.toString)) {
      val returnString = Option(result.getInChI)
      if (returnString.isDefined) {
        if (returnString.get.startsWith("InChI=/FAKE/")) {
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
  private def determineFormat(moleculeFormat: MoleculeFormat.Value): String = {
    if (MoleculeFormat.getImportString(moleculeFormat).contains("inchi")) {
      ChemicalKeywords.INCHI.toString
    } else {
      ChemicalKeywords.SMILES.toString
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
