package com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import chemaxon.marvin.io.MolExportException
import com.act.analysis.chemicals.molecules.MoleculeFormat.Cleaning
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.ChemicalInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities}
import com.mongodb.DBObject
import org.apache.log4j.LogManager

import scala.collection.parallel.immutable.{ParMap, ParSeq}

object AbstractChemicals {
  val logger = LogManager.getLogger(getClass)


  private val ABSTRACT_CHEMICAL_REGEX = "\\[R[0-9]*\\}"
  private val CARBON_REPLACEMENT = "\\[C\\]";

  // Chemaxon technically uses smarts when we say Smiles, so we just make it explicit here.
  // We do the cleaning so that we can get rid of a lot of the junk that would make down-stream processing hard.
  val cleanSmartsFormat = new MoleculeFormat.MoleculeFormatType(MoleculeFormat.smarts,
    List(Cleaning.neutralize, Cleaning.clean2d, Cleaning.aromatize))

  def getAbstractChemicals(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType): ParMap[Long, ChemicalInformation] = {
    logger.info("Finding abstract chemicals.")
    /*
      Mongo DB Query

      Query: All elements that contain "[R]" or "[R#]", for some number #, in their SMILES
      TODO: try incorporating elements containing R in their inchi, which don't have a smiles, by replacing R with Cl.
     */
    var query = Mongo.createDbObject(ChemicalKeywords.SMILES, Mongo.defineMongoRegex(ABSTRACT_CHEMICAL_REGEX))
    val filter = Mongo.createDbObject(ChemicalKeywords.SMILES, 1)
    val result: ParSeq[DBObject] = Mongo.mongoQueryChemicals(mongoDb)(query, filter, notimeout = true).toStream.par

    /*
       Convert from DB Object => Smarts and return that.
       Flatmap as Parse Db object returns None if an error occurs (Just filter out the junk)
    */
    val parseDbObjectInFormat: (DBObject) => Option[(Long, ChemicalInformation)] = parseDbObject(mongoDb, moleculeFormat) _
    val goodChemicalIds: ParMap[Long, ChemicalInformation] = result.flatMap(parseDbObjectInFormat(_)).toMap

    logger.info(s"Finished finding abstract chemicals. Found ${goodChemicalIds.size}")

    goodChemicalIds
  }

  private def parseDbObject(mongoDB: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType)
                           (ob: DBObject): Option[(Long, ChemicalInformation)] = {
    /*
      Type conversions from DB objects
     */
    val chemical = mongoDB.convertDBObjectToChemical(ob)

    val chemicalId = chemical.getUuid.toLong
    val smiles = chemical.getSmiles

    // All these mean that there is likely too much unrepresented complexity.
    if (smiles.toLowerCase.contains("rrna")) return None
    if (smiles.toLowerCase.contains("trna")) return None
    if (smiles.toLowerCase.contains("protein")) return None
    if (smiles.toLowerCase.contains("nucleobase")) return None

    // Replace R groups for C currently.
    // There can be multiple R groups, where they are listed as characters.  We want to grab any of the numbers assigned there.
    val replacedSmarts = smiles.replaceAll(ABSTRACT_CHEMICAL_REGEX, CARBON_REPLACEMENT)

    /*
      Try to import the SMILES field as a Smarts representation of the molecule.
     */
    try {
      val mol = MoleculeImporter.importMolecule(replacedSmarts, cleanSmartsFormat)
      // Convert to smarts so everything is standard
      Option((chemicalId, new ChemicalInformation(chemicalId.toInt, MoleculeExporter.exportMolecule(mol, moleculeFormat))))
    } catch {
      case e: MolExportException =>
        logger.warn(s"Tried converting molecule to smarts, but failed.  Molecule's chemical ID is ${chemicalId.toInt}.")
        None
      case e: MolFormatException =>
        logger.warn(s"Tried to import SMARTS value $replacedSmarts, but failed.")
        None
    }
  }

  object Mongo extends MongoWorkflowUtilities {}

}
