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

package com.act.biointerpretation.rsmiles.processing

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ReactionInformation}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.parallel.immutable.ParMap

object ReactionProcessing {
  val logger = LogManager.getLogger(getClass)

  def constructDbReaction(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType)
                         (previousChemicals: ParMap[Long, ChemicalInformation], substrateCountFilter: Int = -1)
                         (ob: DBObject): Option[ReactionInformation] = {
    // Parse the objects from the database and ensure that they exist.
    val substrates = ob.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.SUBSTRATES}").asInstanceOf[BasicDBList]
    val products = ob.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.PRODUCTS}").asInstanceOf[BasicDBList]
    val reactionId = ob.get(ReactionKeywords.ID.toString).asInstanceOf[Int]

    if (substrates == null | products == null) return None

    val productList = products.toList
    val substrateList = substrates.toList

    // Not really a reaction if nothing is happening.
    if (substrateList.isEmpty || productList.isEmpty) return None

    // Ensure that the substrate number is the same as the number of substrates we are looking for
    if (substrateCountFilter > 0 && substrateList.length != substrateCountFilter) return None

    // Make sure we load everything in, we assign settings that carry over here.
    val moleculeLoader: (DBObject) => List[ChemicalInformation] = loadMolecule(mongoDb, moleculeFormat)(previousChemicals)

    try {
      val substrateMoleculeList: List[ChemicalInformation] = substrateList.flatMap(x => moleculeLoader(x.asInstanceOf[DBObject]))
      val productMoleculeList: List[ChemicalInformation] = productList.flatMap(x => moleculeLoader(x.asInstanceOf[DBObject]))

      /*
        Check if the Substrates == Products.
        This probably means a stereo change is occurring that our import settings could strip off the original molecule.
       */
      val uniqueSubstrates = substrateMoleculeList.map(_.getString).toSet
      val uniqueProducts = productMoleculeList.map(_.getString).toSet
      if (uniqueSubstrates.equals(uniqueProducts)) {
        logger.debug(s"Reaction with ID $reactionId has the same substrates as products. " +
          s"Likely is a stereo change. Skipping.")
        return None
      }

      Option(new ReactionInformation(reactionId, substrateMoleculeList, productMoleculeList))
    } catch {
      case e: MolFormatException => None
    }
  }

  private def loadMolecule(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType)
                          (previousChemicals: ParMap[Long, ChemicalInformation])(dbObj: DBObject): List[ChemicalInformation] = {
    val hitAbstractChem: Option[ChemicalInformation] =
      previousChemicals.get(dbObj.get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long])
    val coefficient = dbObj.get(ReactionKeywords.COEFFICIENT.toString).asInstanceOf[Int]

    // We only get past this if we are dealing with non-abstract chemicals.
    if (hitAbstractChem.isDefined) return List.fill(coefficient)(hitAbstractChem.get)

    // Try to look for real molecules if we can't find it in our abstract stack.
    val chemicalId = dbObj.get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long]
    val query = Mongo.createDbObject(ChemicalKeywords.ID, chemicalId)
    val inchi = Mongo.mongoQueryChemicals(mongoDb)(query, null).next().get(ChemicalKeywords.INCHI.toString).asInstanceOf[String]

    if (!moleculeFormat.toString.toLowerCase.contains("inchi"))
      logger.warn("Trying to import InChIs with a non InChI setting.")

    val molecule = MoleculeImporter.importMolecule(inchi, moleculeFormat)
    List.fill(coefficient)(new ChemicalInformation(chemicalId.toInt, MoleculeExporter.exportMolecule(molecule, moleculeFormat)))
  }

  object Mongo extends MongoWorkflowUtilities {}

}
