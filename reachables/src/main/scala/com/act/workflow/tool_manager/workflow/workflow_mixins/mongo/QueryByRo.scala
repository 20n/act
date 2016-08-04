package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.mongo_db_keywords.ReactionDatabaseKeywords
import com.mongodb.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

trait QueryByRo extends MongoWorkflowUtilities with ReactionDatabaseKeywords {
  def queryReactionsForReactionIdsByRo(roValues: List[String], mongoConnection: MongoDB): List[AnyRef] = {
    val methodLogger = LogManager.getLogger("queryReactionsForReactionIdsByRo")

    /*
      Query Database for Reaction IDs based on a given RO

      Map RO values to a list of mechanistic validator things we will want to see
    */

    val roObjects = roValues.map(x =>
      new BasicDBObject(s"$REACTION_DB_KEYWORD_MECHANISTIC_VALIDATOR.$x", getMongoExists))
    val queryRoValue = convertListToMongoDbList(roObjects)

    // Setup the query and filter for just the reaction ID
    val reactionIdQuery = defineMongoOr(queryRoValue)
    val reactionIdReturnFilter = new BasicDBObject(REACTION_DB_KEYWORD_ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    val dbReactionIds = mongoDbIteratorToSet(dbReactionIdsIterator)
    // Map reactions by their ID, which is the only value we care about here
    val reactionIds = dbReactionIds.map(x => x.get(REACTION_DB_KEYWORD_ID)).toList

    // Exit if there are no reactionIds matching the RO
    reactionIds.size match {
      case n if n < 1 =>
        methodLogger.error("No Reaction IDs found matching any of the ROs supplied")
        throw new Exception(s"No reaction IDs found for the given RO.")
      case default =>
        methodLogger.info(s"Found $default Reaction IDs matching the RO.")
    }

    reactionIds
  }
}
