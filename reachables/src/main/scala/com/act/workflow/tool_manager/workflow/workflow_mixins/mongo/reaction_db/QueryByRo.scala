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
import com.mongodb.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

trait QueryByRo extends MongoWorkflowUtilities {
  /**
    * Query reactions based on RO Values, return only the ID
    *
    * @param roValues A list of RO values, containing one or more
    * @param mongoConnection Connection to Mongo database
    *
    * @return A map of maps containing documents -> fields.
    *         The field map is empty in this case because reaction ID is available as the primary key.
    */
  def queryReactionsForReactionIdsByRo(roValues: List[String],
                                       mongoConnection: MongoDB): Map[Long, Map[String, AnyRef]] = {
    val methodLogger = LogManager.getLogger("queryReactionsForReactionIdsByRo")

    queryReactionsForValuesByRo(roValues, mongoConnection, List(ReactionKeywords.ID.toString))
  }

  /**
    * Query reactions based on RO Values
    *
    * @param roValues A list of RO values, containing one or more
    * @param mongoConnection Connection to Mongo database
    * @param returnFilterFields The fields you are looking for.
    *
    * @return A map of maps containing documents -> fields.
    *         The field map is keyed on the document ID, the second set of maps are keyed by their field names.
    */
  def queryReactionsForValuesByRo(roValues: List[String],
                                  mongoConnection: MongoDB,
                                  returnFilterFields: List[String]): Map[Long, Map[String, AnyRef]] = {
      val dbReactionIdsIterator = queryReactionsByRo(roValues, mongoConnection, returnFilterFields)
      mongoReturnQueryToMap(dbReactionIdsIterator, returnFilterFields)
  }

  def queryReactionsByRo(roValues: List[String], mongoConnection: MongoDB, returnFilterFields: List[String]): Iterator[DBObject] = {
    val methodLogger = LogManager.getLogger("queryReactionsForValuesByRo")
    if (roValues.length <= 0) throw new RuntimeException("Number of RO values supplied was 0.")

    /*
      Query Database for Reaction IDs based on a given RO

      Map RO values to a list of mechanistic validator things we will want to see
    */

    val roObjects =
      roValues.map(r => new BasicDBObject(s"${ReactionKeywords.MECHANISTIC_VALIDATOR.toString}.$r", getMongoExists))
    val queryRoValue = convertListToMongoDbList(roObjects)

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = defineMongoOr(queryRoValue)

    // Create the return filter by adding all fields onto
    val reactionReturnFilter = new BasicDBObject()
    returnFilterFields.map(field => reactionReturnFilter.append(field, 1))

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection)(reactionIdQuery, reactionReturnFilter)

    dbReactionIdsIterator
  }
}
