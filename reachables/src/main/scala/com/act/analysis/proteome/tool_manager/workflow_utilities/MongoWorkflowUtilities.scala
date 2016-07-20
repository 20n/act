package com.act.analysis.proteome.tool_manager.workflow_utilities

import act.server.MongoDB
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.mutable

object MongoWorkflowUtilities {
  // Commonly used operators for this mongo query
  val EXISTS = new BasicDBObject("$exists", 1)
  val DOESNT_EXIST = new BasicDBObject("$exists", 0)
  val ELEMMATCH = "$elemMatch"
  private val logger = LogManager.getLogger(getClass.getName)
  private val OR = "$or"
  private val AND = "$and"
  private val IN = "$in"

  private val host = "localhost"
  private val port = 27017
  private val db = "marvin"

  def connectToDatabase(): MongoDB = {
    logger.info("Setting up Mongo database connection")

    // Instantiate Mongo host.
    new MongoDB(host, port, db)
  }

  def defineOr(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(OR, truthValueList)
  }

  def defineAnd(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(AND, truthValueList)
  }

  def defineIn(queryList: BasicDBList): BasicDBObject = {
    new BasicDBObject(IN, queryList)
  }

  def toDbList(normalList: List[BasicDBObject]): BasicDBList = {
    val copyList = new BasicDBList
    copyList.addAll(normalList)
    copyList
  }

  def mongoQueryReactions(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Iterator[DBObject] = {
    logger.info(s"Querying reaction database with the query $key.  Filtering values to obtain $filter")
    mongo.getIteratorOverReactions(key, false, filter).toIterator
  }

  def mongoQuerySequences(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Iterator[DBObject] = {
    logger.info(s"Querying sequence database with the query $key.  Filtering values to obtain $filter")
    mongo.getIteratorOverSeq(key, false, filter).toIterator
  }


  def dbIteratorToSet(iterator: Iterator[DBObject]): Set[DBObject] = {
    val buffer = mutable.Set[DBObject]()
    for (value <- iterator) {
      buffer add value
    }
    buffer.toSet
  }

}
