package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import com.mongodb.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

trait QueryByRo extends MongoWorkflowUtilities with ReactionDatabaseKeywords {
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

    queryReactionsForValuesByRo(roValues, mongoConnection, List(REACTION_DB_KEYWORD_ID))
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
    val methodLogger = LogManager.getLogger("queryReactionsForValuesByRo")
    if (roValues.length <= 0) throw new RuntimeException("Number of RO values supplied was 0.")

    /*
      Query Database for Reaction IDs based on a given RO

      Map RO values to a list of mechanistic validator things we will want to see
    */

    val roObjects = roValues.map(r => new BasicDBObject(s"$REACTION_DB_KEYWORD_MECHANISTIC_VALIDATOR.$r", getMongoExists))
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

    mongoReturnQueryToMap(dbReactionIdsIterator, returnFilterFields)
  }
}
