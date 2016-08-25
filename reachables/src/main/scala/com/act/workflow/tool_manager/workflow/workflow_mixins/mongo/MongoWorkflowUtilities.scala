package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.mongodb.DBObject
import com.mongodb.casbah.Imports.{BasicDBList, BasicDBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.mutable

trait MongoWorkflowUtilities {
  private val logger = LogManager.getLogger(getClass.getName)

  // Mongo field that should always exist
  private val ID = "_id"

  // General use
  private val EXISTS = dollarString("exists")
  private val OR = dollarString("or")
  private val AND = dollarString("and")
  private val IN = dollarString("in")
  private val REGEX = dollarString("regex")

  // Aggregation
  private val MATCH = dollarString("match")
  private val UNWIND = dollarString("unwind")
  private val GROUP = dollarString("group")
  private val PUSH = dollarString("push")


  /*
    Related to instantiating Mongo
   */

  /**
    * Instantiates a connection with the MongoDB in act.server
    *
    * @param db   The name of the database to connect to. Default marvin
    * @param host The host to connect to. Default localhost
    * @param port The port to listen at. Default 27017 (Mongo default)
    *
    * @return Created Mongo database connection.
    */
  def connectToMongoDatabase(db: String = "marvin", host: String = "localhost", port: Int = 27017): MongoDB = {
    logger.info("Setting up Mongo database connection")

    // Instantiate Mongo host.
    new MongoDB(host, port, db)
  }


  /*
    Mongo string utilities
   */

  /**
    * Unwinding a list creates a value that can be found by the name <PreviousListName>.<ValueName>.
    * This function standardizes that naming procedure for use in querying unwound variables within lists.
    *
    * A document containing a field "lists" that looks like this:
    * "lists" : [{val1 : 1, val2: 2}, {val1: 3, val2: 3}] is unwound to form:
    *
    * lists.val1 : [1, 3]
    * lists.val2 : [2, 4]
    *
    * Thus, this naming pattern modification allows to easily access these newly created values.
    *
    * @param listName  The name of the DBListObject that was unwound
    * @param valueName The name of a value found within that list
    *
    * @return String containing the formatted names.
    */
  def formatUnwoundName(listName: String, valueName: String): String = {
    s"$listName.$valueName"
  }

  /**
    * Creates a new query that checks if something exists
    * True: Exists
    * False: Doesn't exist
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/query/exists/
    *
    * @return DBObject that matches the above conditions
    */
  def getMongoExists: BasicDBObject = {
    new BasicDBObject(EXISTS, true)
  }


  /*
    General Mongo functionality
   */

  /**
    * Creates a new query that checks if something doesn't exist
    * True: Doesn't exist
    * False: Exists
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/query/exists/
    *
    * @return DB Object that matches the above conditions
    */
  def getMongoDoesntExist: BasicDBObject = {
    new BasicDBObject(EXISTS, false)
  }

  /**
    * Truth value that returns true if any members of the truthValueList evaluate to true
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/query/or/
    *
    * @param truthValueList A list of DBObjects to check truth conditions against
    *
    * @return DBObject containing this query
    */
  def defineMongoOr(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(OR, truthValueList)
  }

  /**
    * Truth value that returns true if all members of the truthValueList evaluate to true
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/query/and/
    *
    * @param truthValueList A list of DBObjects to check truth conditions against
    *
    * @return DBObject containing this query
    */
  def defineMongoAnd(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(AND, truthValueList)
  }

  /**
    * Query that returns true if any of the values in the queryList are equal to the field it is assigned to.
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/query/in/
    *
    * @param queryList A list of values that the field could equal
    *
    * @return DBObject containing this query
    */
  def defineMongoIn(queryList: BasicDBList): BasicDBObject = {
    new BasicDBObject(IN, queryList)
  }

  /**
    * Allows the use of REGEX to match field values.
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/query/regex/
    *
    * @param regex A regex string that will be matched against
    *
    * @return DBObject containing this query
    */
  def defineMongoRegex(regex: String): BasicDBObject = {
    new BasicDBObject(REGEX, regex)
  }

  /**
    * A normal query against the reactions database.
    *
    * Reference: https://docs.mongodb.com/manual/reference/method/db.collection.find/
    *
    * @param mongo  Connection to a MongoDB
    * @param key    The key to match documents against
    * @param filter A filter of the returned components of the document
    *
    * @return An iterator over the returned documents
    */
  def mongoQueryReactions(mongo: MongoDB)(key: BasicDBObject, filter: BasicDBObject): Iterator[DBObject] = {
    logger.debug(s"Querying reaction database with the query $key.  Filtering values to obtain $filter")
    mongo.getIteratorOverReactions(key, false, filter).toIterator
  }

  def mongoQueryChemicals(mongo: MongoDB)(key: BasicDBObject, filter: BasicDBObject): Iterator[DBObject] = {
    logger.debug(s"Querying reaction database with the query $key.  Filtering values to obtain $filter")
    mongo.getIteratorOverChemicals(key, false, filter).toIterator
  }

  /**
    * A normal query against the sequences database.
    *
    * Reference: https://docs.mongodb.com/manual/reference/method/db.collection.find/
    *
    * @param mongo  Connection to a MongoDB
    * @param key    The key to match documents against
    * @param filter A filter of the returned components of the document
    *
    * @return An iterator over the returned documents
    */
  def mongoQuerySequences(mongo: MongoDB)(key: BasicDBObject, filter: BasicDBObject): Iterator[DBObject] = {
    logger.debug(s"Querying sequence database with the query $key.  Filtering values to obtain $filter")
    mongo.getDbIteratorOverSeq(key, false, filter).toIterator
  }

  /**
    * Filters all documents that cause thingsToMatch to be true.
    *
    * Operation:
    * Checks thingsToMatch against each document and collects documents that evaluate to true
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/aggregation/match/
    *
    * @param thingsToMatch Conditional to evaluate true/false against
    *
    * @return DBObject constructing this request.
    */
  def defineMongoMatch(thingsToMatch: BasicDBObject): BasicDBObject = {
    new BasicDBObject(MATCH, thingsToMatch)
  }

  /*
   Mongo aggregation handling.
   */

  /**
    * Takes a a list within the Mongo document and unwinds it.  Unwinding a list creates the pattern shown below:
    *
    * A document containing a field "lists" that looks like this:
    *
    * Operation:
    * "lists" : [{val1 : 1, val2: 2}, {val1: 3, val2: 3}] ->  {lists.val1 : [1, 3], lists.val2 : [2, 4]}
    *
    * Reference: https://docs.mongodb.com/manual/reference/operator/aggregation/unwind/
    *
    * @param listName The name of the list
    *
    * @return A formatted query that will do the above operation
    */
  def defineMongoUnwind(listName: String): BasicDBObject = {
    new BasicDBObject(UNWIND, dollarString(listName))
  }

  /**
    * Groups documents together by some given value.
    * Requires an ID field and then accumulates any other fields indicating in the accumulator.
    * In our example, we create an array via PUSH and use that to name a new field outputListName.
    *
    * Operation:
    * Converts previous document -> ID, List of values in previous field.
    *
    * References:
    * $push -> https://docs.mongodb.com/manual/reference/operator/aggregation/push/
    * $group -> https://docs.mongodb.com/manual/reference/operator/aggregation/group/
    *
    * @param nameOfGroupingValue The name of the field which should be pushed into an array
    * @param outputListName      The name of the list that refers to the array created around nameOfGroupingValue
    *
    * @return DBObject to perform the group query
    */
  def defineMongoGroup(nameOfGroupingValue: String, outputListName: String): BasicDBObject = {
    // Create an array for the expression
    val pushing = new BasicDBObject(PUSH, dollarString(nameOfGroupingValue))

    // Name the output array
    val groupMap = new BasicDBObject(outputListName, pushing)

    // The new document always requires an ID, so we just use the prior ID.
    groupMap.append(ID, dollarString(ID))

    // Finally, we group everything together
    new BasicDBObject(GROUP, groupMap)
  }

  /**
    * Many Mongo queries require a dollar sign in front of the keyword.  Example: $exists
    *
    * The dollar sign is also used during aggregation to reference intermediate documents. Example: $_id
    *
    * Thus, this function changes f("String") -> "$String"
    *
    * @param inputString The string to be converted into dollar format
    *
    * @return Modified string
    */
  private def dollarString(inputString: String): String = {
    // Escape one dollar and do the input as well
    s"$$$inputString"
  }

  /**
    * Aggregate and process documents over the reactions DB with a given pipeline
    *
    * Reference: https://docs.mongodb.com/manual/aggregation/
    *
    * @param mongo    Connection to a MongoDB
    * @param pipeline A list of objects to apply sequentially to process the data.
    *
    * @return An iterator over all the returned documents
    */
  def mongoApplyPipelineReactions(mongo: MongoDB, pipeline: List[DBObject]): Iterator[DBObject] = {
    mongo.applyPipelineOverReactions(pipeline)
  }

  /**
    * Aggregate and process documents over the sequences DB with a given pipeline
    *
    * Reference: https://docs.mongodb.com/manual/aggregation/
    *
    * @param mongo    Connection to a MongoDB
    * @param pipeline A list of objects to apply sequentially to process the data.
    *
    * @return An iterator over all the returned documents
    */
  def mongoApplyPipelineSequences(mongo: MongoDB, pipeline: List[DBObject]): Iterator[DBObject] = {
    mongo.applyPipelineOverSequences(pipeline)
  }


  /*
    Mongo object utility functions
   */

  /**
    * Takes in a List of DBObjects and converts it to a BasicDBList
    *
    * @param scalaList The initial list
    *
    * @return A BasicDBList representation of the scalaList
    */
  def convertListToMongoDbList(scalaList: List[BasicDBObject]): BasicDBList = {
    val copyList = new BasicDBList
    copyList.addAll(scalaList)
    copyList
  }

  /**
    * Takes in an iterator over DBObjects and creates a set out of them.
    *
    * @param iterator DBObject iterator
    *
    * @return Set of DBObjects
    */
  def mongoDbIteratorToSet(iterator: Iterator[DBObject]): Set[DBObject] = {
    val buffer = mutable.Set[DBObject]()
    for (value <- iterator) {
      buffer add value
    }
    buffer.toSet
  }

  /**
    * Overload of the Iterable version, but converts iterator to a stream for processing
    *
    * @param iterator Iterator DBObject
    * @param fields   List of fields in the document
    *
    * @return The map of map of documents.
    *         The first map is keyed by the ID of the document,
    *         while maps contained within are keyed by the fields of that document.
    */
  def mongoReturnQueryToMap(iterator: Iterator[DBObject], fields: List[String]): Map[Long, Map[String, AnyRef]] = {
    mongoReturnQueryToMap(iterator.toStream, fields)
  }

  /**
    * Converts an iterable into a Map of Maps.
    * The first Map is keyed on the document ID and the second map on the document fields.
    *
    * @param iterator Iterable of DBObjects
    * @param fields List of fields in the document
    *
    * @return The map of map of documents.
    *         The first map is keyed by the ID of the document,
    *         while maps contained within are keyed by the fields of that document.
    */
  def mongoReturnQueryToMap(iterator: Iterable[DBObject], fields: List[String]): Map[Long, Map[String, AnyRef]] = {
    // For each field name, pull out the values of that document and add it to a list, and make a list of those.
    val filteredFields = fields.filter(!_.equals(ID))

    // Map each field as the key and the information in the document to what it goes to.
    def defineFields(document: DBObject): Map[String, AnyRef] = {
      filteredFields map (field => field -> document.get(field)) toMap
    }

    // Each document mapped by the ID mapped to a map of fields
    val mapOfMaps = iterator map (document => document.get(ID).asInstanceOf[Int].toLong -> defineFields(document)) toMap

    // Exit if all values are empty, so error check here as we convert to a map.
    mapOfMaps.size match {
      case n if n <= 0 =>
        throw new Exception(s"No values found matching any of the key supplied.")
      case default =>
        logger.info(s"Successfully found $default documents matching your query.")
    }

    mapOfMaps
  }
}
