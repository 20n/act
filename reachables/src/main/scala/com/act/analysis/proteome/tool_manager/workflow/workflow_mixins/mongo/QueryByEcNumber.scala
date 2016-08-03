package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.mongodb.{BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.mutable.ListBuffer

trait QueryByEcNumber extends MongoWorkflowUtilities {
  /*
  Commonly used keywords for this mongo query
  */
  private val ECNUM = "ecnum"
  private val ID = "_id"

  def queryReactionsForReactionIdsByEcNumber(roughEcnum: String, mongoConnection: MongoDB): List[AnyRef] = {
    val methodLogger = LogManager.getLogger("queryDbForReactionIdsByEcNumber")

    /*
    Query Database for Reaction IDs based on a given EC Number

    EC Numbers are formatted at X.X.X.X so we use a regex match of

    ^6\.1\.1\.1$
   */
    val ecnumRegex = formatEcNumberAsRegex(roughEcnum)

    // Setup the query and filter for just the reaction ID
    val regex = defineMongoRegex(ecnumRegex)
    val reactionIdQuery = new BasicDBObject(ECNUM, regex)
    val reactionIdReturnFilter = new BasicDBObject(ID, 1)

    // Deploy DB query w/ error checking to ensure we got something
    methodLogger.info(s"Running query $reactionIdQuery against DB.  Return filter is $reactionIdReturnFilter")
    val dbReactionIdsIterator: Iterator[DBObject] =
      mongoQueryReactions(mongoConnection, reactionIdQuery, reactionIdReturnFilter)
    val dbReactionIds = mongoDbIteratorToSet(dbReactionIdsIterator)

    // Map reactions by their ID, which is the only value we care about here
    val reactionIds = dbReactionIds.map(x => x.get(ID)).toList

    // Exit if there are no reactionIds matching the Ecnum
    reactionIds.size match {
      case n if n < 1 =>
        methodLogger.error("No Reaction IDs found matching any of the Ecnum supplied")
        throw new Exception(s"No reaction IDs found for the given Ecnum.")
      case default =>
        methodLogger.info(s"Found $default Reaction IDs matching the Ecnum.")
    }

    reactionIds
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
      ^ is the start of the string, $ is the end of the string.

      We use a \\. seperator so that we match periods (Periods must be escaped in regex).
    */
    "^" + basicRegex.mkString(sep = "\\.") + "$"
  }
}
