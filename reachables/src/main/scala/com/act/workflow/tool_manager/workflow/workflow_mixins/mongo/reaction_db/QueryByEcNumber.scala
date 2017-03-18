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

package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.casbah.Imports.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

trait QueryByEcNumber extends MongoWorkflowUtilities {

  /**
    * Returns just the reaction Ids of the documents matching the EC number.
    *
    * @param roughEcnum EcNumber that can be regex matched
    * @param mongoConnection Connection to MongoDB
    *
    * @return A map of maps containing documents -> fields.
    *         The field map is empty in this case because reaction ID is available as the primary key.
    */
  def queryReactionsForReactionIdsByEcNumber(roughEcnum: String,
                                             mongoConnection: MongoDB): Map[Long, Map[String, AnyRef]] = {
    queryReactionsForValuesByEcNumber(roughEcnum, mongoConnection, List(ReactionKeywords.ID.toString))
  }

  /**
    * Returns an arbitrary set of values in the reaction documents matching that EC number.
    *
    * @param roughEcnum EcNumber that can be regex matched
    * @param mongoConnection Connection to MongoDB
    * @param returnFilterFields Which fields of the document should be returned.
    *
    * @return A map of maps containing documents -> fields.
    *         The field map is keyed on the document ID, the second set of maps are keyed by their field names.
    */
  def queryReactionsForValuesByEcNumber(roughEcnum: String,
                                        mongoConnection: MongoDB,
                                        returnFilterFields: List[String]): Map[Long, Map[String, AnyRef]] = {
    val methodLogger = LogManager.getLogger("queryReactionsForValuesByEcNumber")

    /*
      Query Database for something based on a given EC Number

      EC Numbers are formatted at X.X.X.X so we use a regex match of

      ^6\.1\.1\.1$
    */

    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = defineMongoRegex(ecnumRegex)
    val reactionIdQuery = createDbObject(ReactionKeywords.ECNUM, regex)

    // Create the return filter by adding all fields onto the return filter DB object
    val reactionIdReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      reactionIdReturnFilter.append(field, 1)
    }

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection)(reactionIdQuery, reactionIdReturnFilter)
    val dbReactionReturnValues = mongoDbIteratorToSet(dbReactionIdsIterator)

    mongoReturnQueryToMap(dbReactionReturnValues, returnFilterFields)
  }

  /**
    * Input is a value of form #.#.#.# where the value can stop at any #
    *
    * Valid inputs would therefore be 1, 1.2, 1.2.3, 1.2.3.4
    *
    * Invalid inputs would be 1., 1.2.3.4.5
    *
    * @param ecnum A supplied EC Number
    *
    * @return
    */
  def formatEcNumberAsRegex(ecnum: String): String = {
    val allValues = "[^.]+"
    val basicRegex = ListBuffer(allValues, allValues, allValues, allValues)

    val dividedInput = ecnum.split('.')

    for (i <- dividedInput.indices) {
      basicRegex(i) = dividedInput(i)
    }

    /*
      The ^ is the start of the string, $ is the end of the string.

      We use a \\. separator so that we match periods (Periods must be escaped in regex).
    */

    "^" + basicRegex.mkString(sep = "\\.") + "$"
  }

  /**
    * Aggregates all the KM values for a given document into a list.
    *
    * @param roughEcnum Regex of ecnumbers
    * @param mongoConnection Connection to MongoDB
    *
    * @return Map of documents keyed by the reaction ID
    *         with a field matching REACTION_DB_KEYWORD_VALUE containing a list of the KM values.
    */
  def aggregateReactionsByEcNumberWithKm(roughEcnum: String,
                                         mongoConnection: MongoDB): Map[Long, Map[String, AnyRef]] = {
    val methodLogger = LogManager.getLogger("aggregateReactionsByEcNumberWithKm")
    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = defineMongoRegex(ecnumRegex)
    val reactionIdQuery = createDbObject(ReactionKeywords.ECNUM, regex)

    /*
     1) Match all reactions that have this ecnum
     2) Unwind the proteins list to make it keyable
     3) Group proteins.km together into a list
     4) Unwind the proteins.km we just made
     5) Group the km.val that we have now.
     6) Unwind that list so we get a bunch of flat arrays.
    */
    val pipeline = List[DBObject](
      defineMongoMatch(reactionIdQuery),
      defineMongoUnwind(ReactionKeywords.PROTEINS),
      defineMongoGroup(formatUnwoundName(ReactionKeywords.PROTEINS, ReactionKeywords.KM), ReactionKeywords.KM),
      defineMongoUnwind(ReactionKeywords.KM),
      defineMongoGroup(formatUnwoundName(ReactionKeywords.KM, ReactionKeywords.VALUE), ReactionKeywords.VALUE),
      defineMongoUnwind(ReactionKeywords.VALUE)
    )

    methodLogger.info(s"Constructed pipeline $pipeline")
    // Convert the iterator to a list and return
    val finalDocumentIterator = mongoApplyPipelineReactions(mongoConnection, pipeline)
    mongoReturnQueryToMap(finalDocumentIterator, List(ReactionKeywords.ID.toString, ReactionKeywords.VALUE.toString))
  }
}
