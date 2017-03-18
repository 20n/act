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

  // Chemaxon technically uses smarts when we say Smiles, so we just make it explicit here.
  // We do the cleaning so that we can get rid of a lot of the junk that would make down-stream processing hard.
  val cleanSmartsFormat = new MoleculeFormat.MoleculeFormatType(MoleculeFormat.smarts,
    List(Cleaning.neutralize, Cleaning.clean2d, Cleaning.aromatize))

  def getAbstractChemicals(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType): ParMap[Long, ChemicalInformation] = {
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
    val parseDbObjectInFormat: (DBObject) => Option[(Long, ChemicalInformation)] = parseDbObject(mongoDb, moleculeFormat) _
    val goodChemicalIds: ParMap[Long, ChemicalInformation] = result.flatMap(
      dbResponse => parseDbObjectInFormat(dbResponse)).toMap

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
    val replacedSmarts = smiles.replaceAll("R[0-9]*", "C")

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
