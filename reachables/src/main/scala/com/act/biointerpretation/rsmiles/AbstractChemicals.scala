package com.act.biointerpretation.rsmiles

import java.io.Serializable

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import chemaxon.marvin.io.MolExportException
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities}
import com.mongodb.DBObject
import org.apache.log4j.LogManager

import scala.collection.parallel.immutable.{ParMap, ParSeq}

object AbstractChemicals {
  val logger = LogManager.getLogger(getClass)

  def getAbstractChemicals(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.Value): ParMap[Long, ChemicalInformation] = {
    logger.info("Finding abstract chemicals.")
    /*
      Mongo DB Query

      Query: All elements that contain "R" in their SMILES and "FAKE" in their InChI
     */
    var query = Mongo.createDbObject(ChemicalKeywords.SMILES, Mongo.defineMongoRegex("R"))
    query = Mongo.appendKeyToDbObject(query, ChemicalKeywords.INCHI, Mongo.defineMongoRegex("FAKE"))
    val filter = Mongo.createDbObject(ChemicalKeywords.SMILES, 1)
    val result: ParSeq[DBObject] = Mongo.mongoQueryChemicals(mongoDb)(query, filter, notimeout = true).toStream.par

    /*
       Convert from DB Object => Smarts and return that.
       Flatmap as Parse Db object returns None if an error occurs (Just filter out the junk)
    */
    val parseDbObjectInFormat: (DBObject) => Option[(Long, ChemicalInformation)] = parseDbObject(moleculeFormat)_
    val goodChemicalIds: ParMap[Long, ChemicalInformation] = result.flatMap(
      dbResponse => parseDbObjectInFormat(dbResponse)).toMap

    logger.info(s"Finished finding abstract chemicals. Found ${goodChemicalIds.size}")

    goodChemicalIds
  }

  private def parseDbObject(moleculeFormat: MoleculeFormat.Value)(ob: DBObject): Option[(Long, ChemicalInformation)] = {
    /*
      Type conversions from DB objects
     */
    val chemicalId: Long = ob.get(ChemicalKeywords.ID.toString).asInstanceOf[Long]
    val smiles: String = ob.get(ChemicalKeywords.SMILES.toString).asInstanceOf[String]

    // Replace R groups for C currently.  There can be multiple R groups, where they are listed as characters.  We want to grab any of the numbers assigned there.
    val replacedSmarts = smiles.replaceAll("R[0-9]*", "C")

    /*
      Try to import the SMILES field as a Smarts representation of the molecule.
     */
    try {
      // Chemaxon technically uses smarts when we say Smiles, so we just make it explicit here.
      val mol = MoleculeImporter.importMolecule(replacedSmarts, MoleculeFormat.smarts)

      // Convert to smarts so everything is standard
      Option((chemicalId, new ChemicalInformation(chemicalId.toInt, MoleculeExporter.exportMolecule(mol, moleculeFormat))))
    } catch {
      case e: MolExportException =>
        logger.debug(s"Tried converting molecule to smarts, but failed.  Molecule's chemical ID is ${chemicalId.toInt}.")
        None
      case e: MolFormatException =>
        logger.debug(s"Tried to import SMARTS value $replacedSmarts, but failed.")
        None
    }
  }

  case class ChemicalInformation(chemicalId: Int, chemicalAsString: String) extends Serializable {
    def getChemicalId: Int = chemicalId
    def getString: String = chemicalAsString
  }

  object Mongo extends MongoWorkflowUtilities {}
}
