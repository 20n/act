package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import com.mongodb.casbah.Imports.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

trait QueryByEcNumber extends MongoWorkflowUtilities with ReactionDatabaseKeywords {

  /**
    * Returns just the reaction Ids of the documents matching the EC number.
    *
    * @param roughEcnum EcNumber that can be regex matched
    * @param mongoConnection Connection to MongoDB
    * @return Map of documents containing a map of their fields.
    */
  def queryReactionsForReactionIdsByEcNumber(roughEcnum: String,
                                             mongoConnection: MongoDB): Map[String, Map[String, AnyRef]] = {
    queryReactionsForValuesByEcNumber(roughEcnum, mongoConnection, List(REACTION_DB_KEYWORD_ID))
  }

  /**
    * For a given ECNumber, returns an arbitrary set of values about the document.
    *
    * @param roughEcnum EcNumber that can be regex matched
    * @param mongoConnection Connection to MongoDB
    * @param returnFilterFields Which fields of the document should be returned.
    * @return Map of documents containing a map of their fields.
    */
  def queryReactionsForValuesByEcNumber(roughEcnum: String,
                                        mongoConnection: MongoDB,
                                        returnFilterFields: List[String]): Map[String, Map[String, AnyRef]]= {
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
    *
    *
    * @param roughEcnum
    * @param mongoConnection
    * @return
    */
  def queryReactionsForKmValuesByEcNumber(roughEcnum: String,
                                          mongoConnection: MongoDB): Map[String, Map[String, AnyRef]] = {
    // Returns a list of ID, KM value pairs.
    aggregateReactionsByEcNumberWithKm(roughEcnum, mongoConnection)
  }

  /**
    * Aggregates all the KM values for a given document into a list.
    *
    * @param roughEcnum Regex of ecnumbers
    * @param mongoConnection Connection to MongoDB
    * @return Map of maps containing ID and KM values for that document.
    */
  def aggregateReactionsByEcNumberWithKm(roughEcnum: String,
                                         mongoConnection: MongoDB): Map[String, Map[String, AnyRef]] = {
    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = defineMongoRegex(ecnumRegex)
    val reactionIdQuery = new BasicDBObject(REACTION_DB_KEYWORD_ECNUM, regex)

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
      defineMongoUnwind(REACTION_DB_KEYWORD_PROTEINS),
      defineMongoGroup(formatUnwoundName(REACTION_DB_KEYWORD_PROTEINS, REACTION_DB_KEYWORD_KM), REACTION_DB_KEYWORD_KM),
      defineMongoUnwind(REACTION_DB_KEYWORD_KM),
      defineMongoGroup(formatUnwoundName(REACTION_DB_KEYWORD_KM, REACTION_DB_KEYWORD_VALUE), REACTION_DB_KEYWORD_VALUE),
      defineMongoUnwind(REACTION_DB_KEYWORD_VALUE)
    )

    // Convert the iterator to a list and return
    val finalDocumentIterator = mongoApplyPipelineReactions(mongoConnection, pipeline)
    mongoReturnQueryToMap(finalDocumentIterator, List(REACTION_DB_KEYWORD_ID, REACTION_DB_KEYWORD_VALUE))
  }
}
