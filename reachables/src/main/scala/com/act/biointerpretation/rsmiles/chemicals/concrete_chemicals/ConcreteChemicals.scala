package com.act.biointerpretation.rsmiles.chemicals.concrete_chemicals

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import chemaxon.marvin.io.MolExportException
import com.act.analysis.chemicals.molecules.MoleculeFormat.CleaningOptions
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.rsmiles.chemicals.Information.ChemicalInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities}
import com.mongodb.DBObject
import org.apache.log4j.LogManager

import scala.collection.parallel.immutable.{ParMap, ParSeq}

object ConcreteChemicals {
  val logger = LogManager.getLogger(getClass)

  // Chemaxon technically uses smarts when we say Smiles, so we just make it explicit here.
  // We do the cleaning so that we can get rid of a lot of the junk that would make down-stream processing hard.
  val cleanSmartsFormat = new MoleculeFormat.MoleculeFormatType(MoleculeFormat.smarts.value,
    List(CleaningOptions.neutralize, CleaningOptions.clean2d, CleaningOptions.aromatize))

  def getConcreteChemicals(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType): ParMap[Long, ChemicalInformation] = {
    logger.info("Finding concrete chemicals.")
    /*
      Mongo DB Query

      Query: All elements that contain "R" in their SMILES and "FAKE" in their InChI
     */
    val query = Mongo.createDbObject(ChemicalKeywords.INCHI, Mongo.defineMongoNot(Mongo.defineMongoRegex("FAKE")))
    val filter = Mongo.createDbObject(ChemicalKeywords.INCHI, 1)
    val result: ParSeq[DBObject] = Mongo.mongoQueryChemicals(mongoDb)(query, filter, notimeout = true).toStream.par

    /*
       Convert from DB Object => Smarts and return that.
       Flatmap as Parse Db object returns None if an error occurs (Just filter out the junk)
    */
    val parseDbObjectInFormat: (DBObject) => Option[(Long, ChemicalInformation)] = parseDbObject(moleculeFormat) _
    val goodChemicalIds: ParMap[Long, ChemicalInformation] = result.flatMap(
      dbResponse => parseDbObjectInFormat(dbResponse)).toMap

    logger.info(s"Finished finding concrete chemicals. Found ${goodChemicalIds.size}")

    goodChemicalIds
  }

  private def parseDbObject(moleculeFormat: MoleculeFormat.MoleculeFormatType)(ob: DBObject): Option[(Long, ChemicalInformation)] = {
    /*
      Type conversions from DB objects
     */
    val chemicalId: Long = ob.get(ChemicalKeywords.ID.toString).asInstanceOf[Long]
    val inchi: String = ob.get(ChemicalKeywords.INCHI.toString).asInstanceOf[String]

    // All these mean that there is likely too much unrepresented complexity.
    if (inchi.toLowerCase.contains("rrna")) return None
    if (inchi.toLowerCase.contains("trna")) return None
    if (inchi.toLowerCase.contains("protein")) return None
    if (inchi.toLowerCase.contains("nucleobase")) return None

    /*
      Try to import the SMILES field as a Smarts representation of the molecule.
     */
    try {
      val mol = MoleculeImporter.importMolecule(inchi, MoleculeFormat.stdInchi)
      // Convert to smarts so everything is standard
      Option((chemicalId, new ChemicalInformation(chemicalId.toInt, MoleculeExporter.exportMolecule(mol, moleculeFormat))))
    } catch {
      case e: MolExportException =>
        logger.debug(s"Tried converting molecule to smarts, but failed.  Molecule's chemical ID is ${chemicalId.toInt}.")
        None
      case e: MolFormatException =>
        logger.debug(s"Tried to import SMARTS value $inchi, but failed.")
        None
    }
  }

  object Mongo extends MongoWorkflowUtilities {}

}
