package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import com.mongodb.casbah.Imports.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

trait QueryByEcNumber extends MongoWorkflowUtilities with ReactionDatabaseKeywords {
  def queryReactionsForReactionIdsByEcNumber(roughEcnum: String, mongoConnection: MongoDB): List[AnyRef] = {
    val methodLogger = LogManager.getLogger("queryDbForReactionIdsByEcNumber")

    val queryResult = queryReactionsForValuesByEcNumber(roughEcnum, mongoConnection, List(REACTION_DB_KEYWORD_ID))
    // Head = Keyword location, given that length is only 1.
    queryResult.head
  }

  def queryReactionsForKmValuesByEcNumber(roughEcnum: String, mongoConnection: MongoDB): List[AnyRef] = {
    val methodLogger = LogManager.getLogger("queryReactionsForKmValuesByEcNumber")

    val queryResult = queryReactionsForValuesByEcNumber(roughEcnum, mongoConnection,
      List(REACTION_DB_KEYWORD_ID, REACTION_DB_KEYWORD_PROTEINS))


    null
  }

  def queryReactionsForValuesByEcNumber(roughEcnum: String,
                                        mongoConnection: MongoDB,
                                        returnFilterFields: List[String]): List[List[AnyRef]] = {
    val methodLogger = LogManager.getLogger("queryReactionsForValuesByEcNumber")

    /*
      Query Database for something based on a given EC Number

      EC Numbers are formatted at X.X.X.X so we use a regex match of

      ^6\.1\.1\.1$
    */

    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = defineMongoRegex(ecnumRegex)
    val reactionIdQuery = new BasicDBObject(REACTION_DB_KEYWORD_ECNUM, regex)

    // Create the return filter by adding all fields onto
    val reactionIdReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      reactionIdReturnFilter.append(field, 1)
    }

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    val dbReactionReturnValues = mongoDbIteratorToSet(dbReactionIdsIterator)

    // For each field name, pull out the values of that document and add it to a list, and make a list of those.
    val returnValues =
      returnFilterFields.map(fieldName => dbReactionReturnValues.map(outputDocument => outputDocument.get(fieldName)).toList)

    // Exit if none of the values are nonempty.
    returnValues match {
      case n if n.exists(!_.nonEmpty) =>
        methodLogger.error("No values found matching any of the Ecnum supplied")
        throw new Exception(s"No values found matching any of the Ecnum supplied.")
      case default =>
        methodLogger.info(s"Found $default values matching the Ecnum.")
    }

    returnValues
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

  def aggregateReactionsByEcNumberWithKm(roughEcnum: String, mongoConnection: MongoDB) = {
    val pipeline = new ListBuffer[DBObject]

    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = defineMongoRegex(ecnumRegex)
    val reactionIdQuery = new BasicDBObject(REACTION_DB_KEYWORD_ECNUM, regex)

    pipeline.append(defineMongoMatch(reactionIdQuery))
    pipeline.append(defineMongoUnwind(REACTION_DB_KEYWORD_PROTEINS))
    pipeline.append(defineMongoGroup(formatUnwoundName(REACTION_DB_KEYWORD_PROTEINS, REACTION_DB_KEYWORD_KM)))
    pipeline.append(defineMongoUnwind(REACTION_DB_KEYWORD_KM))
    pipeline.append(defineMongoGroup(formatUnwoundName(REACTION_DB_KEYWORD_KM, REACTION_DB_KEYWORD_VALUE)))

    mongoApplyPipelineReactions(mongoConnection, pipeline.toList)
  }
}
