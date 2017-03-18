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

import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.rsmiles.processing.ReactionProcessing
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.log4j.LogManager

import scala.collection.parallel.immutable.{ParMap, ParSeq}


object AbstractReactions {
  val logger = LogManager.getLogger(getClass)

  /**
    * Grabs all the abstract reactions for a given substrate count from a given DB.
    * Then converts them into ReactionInformations
    *
    * @param mongoDb           Database instance to use
    * @param moleculeFormat    Format to convert the molecules to
    * @param abstractChemicals A list of abstract chemicals previously constructed
    * @param substrateCount    The number of substrates we should filter for
    *
    * @return A list containing reactions that
    *         have been constructed into the reaction information format.
    */
  def getAbstractReactions(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType, substrateCount: Int)
                          (abstractChemicals: ParMap[Long, ChemicalInformation]): ParSeq[ReactionInformation] = {
    require(substrateCount > 0, s"A reaction must have at least one substrate.  " +
      s"You are looking for reactions with $substrateCount substrates.")

    logger.info("Finding reactions that contain abstract chemicals.")

    /*
      Query Reaction DB for reactions w/ these chemicals
     */
    val chemicalList = new BasicDBList
    abstractChemicals.seq.keySet.foreach(cId => chemicalList.add(cId.asInstanceOf[AnyRef]))

    // Matches a reaction if either the Products or Substrates array contains an abstract element.
    val abstractSubstrateOrProduct = new BasicDBList
    // TODO the Mongo "In" statements below could be expensive.  Possible optimization route.
    abstractSubstrateOrProduct.add(
      // Matches products that are in the abstract chemical list
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}.${ReactionKeywords.PUBCHEM}",
        Mongo.defineMongoIn(chemicalList)))

    // Matches substrates that are in the abstract chemical list
    abstractSubstrateOrProduct.add(
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}.${ReactionKeywords.PUBCHEM}",
        Mongo.defineMongoIn(chemicalList)))

    /*
      We want to match if they are either a substrate or a product to
      get all reactions that could be defined as abstract.
     */
    val query = Mongo.defineMongoOr(abstractSubstrateOrProduct)

    // Filter so we get both the substrates and products
    val filter = new BasicDBObject(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}", 1)
    filter.append(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}", 1)

    // This will likely timeout if we don't indicate notimeout == true, so this is important.
    // The timeout can be seen by getting variable length responses.
    val abstractReactions: ParSeq[DBObject] =
      Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true).toList.par

    logger.info(s"Finished finding reactions that contain abstract chemicals. Found ${abstractReactions.length}.")

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      ReactionProcessing.constructDbReaction(mongoDb, moleculeFormat)(abstractChemicals, substrateCount)

    val processCounter = new AtomicInteger()
    val singleSubstrateReactions: ParSeq[ReactionInformation] = abstractReactions.flatMap(rxn => {
      val reaction = reactionConstructor(rxn)

      if (processCounter.incrementAndGet() % 10000 == 0) {
        logger.info(s"Total of ${processCounter.get} reactions have finished processing " +
          s"so far for $substrateCount substrate${if (substrateCount > 1) "s" else ""}.")
      }

      reaction
    })

    singleSubstrateReactions
  }

  object Mongo extends MongoWorkflowUtilities {}

}
