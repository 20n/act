package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.mongo_db_keywords.ReactionDatabaseKeywords
import com.mongodb.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

trait QueryByRo extends MongoWorkflowUtilities with ReactionDatabaseKeywords {
  def queryReactionsForReactionIdsByRo(roValues: List[String], mongoConnection: MongoDB): List[AnyRef] = {
    val methodLogger = LogManager.getLogger("queryReactionsForReactionIdsByRo")

    val queryResult = queryReactionsForValuesByRo(roValues, mongoConnection, List(REACTION_DB_KEYWORD_ID))
    // Head = Keyword location, given that length is only 1.
    queryResult.head
  }

  def queryReactionsForValuesByRo(roValues: List[String],
                                  mongoConnection: MongoDB,
                                  returnFilterFields: List[String]): List[List[AnyRef]] = {
    val methodLogger = LogManager.getLogger("queryReactionsForValuesByRo")

    /*
      Query Database for Reaction IDs based on a given RO

      Map RO values to a list of mechanistic validator things we will want to see
    */

    val roObjects = roValues.map(x =>
      new BasicDBObject(s"$REACTION_DB_KEYWORD_MECHANISTIC_VALIDATOR.$x", getMongoExists))
    val queryRoValue = convertListToMongoDbList(roObjects)

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = defineMongoOr(queryRoValue)

    // Create the return filter by adding all fields onto
    val reactionReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      reactionReturnFilter.append(field, 1)
    }

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection, reactionIdQuery, reactionReturnFilter)
    val dbReactionReturnValues = mongoDbIteratorToSet(dbReactionIdsIterator)

    // For each field name, pull out the values of that document and add it to a list, and make a list of those.
    val returnValues =
      returnFilterFields.map(
        fieldName => dbReactionReturnValues.map(
          outputDocument => outputDocument.get(fieldName)
        ).toList
      )

    // Exit if none of the values are nonempty.
    returnValues match {
      case n if n.exists(_.nonEmpty) =>
        methodLogger.error("No values found matching any of the Ecnum supplied")
        throw new Exception(s"No values found matching any of the Ecnum supplied.")
      case default =>
        methodLogger.info(s"Found $default values matching the Ecnum.")
    }

    returnValues
  }
}
