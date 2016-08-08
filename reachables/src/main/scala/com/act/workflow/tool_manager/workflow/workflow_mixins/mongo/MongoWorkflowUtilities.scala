package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

import act.server.MongoDB
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.mutable

trait MongoWorkflowUtilities {
  // Commonly used operators for this mongo query
  private val logger = LogManager.getLogger(getClass.getName)
  private val EXISTS = new BasicDBObject("$exists", 1)
  private val DOESNT_EXIST = new BasicDBObject("$exists", 0)
  private val ELEMMATCH = "$elemMatch"
  private val OR = "$or"
  private val AND = "$and"
  private val IN = "$in"
  private val REGEX = "$regex"

  private val host = "localhost"
  private val port = 27017

  def getMongoExists: BasicDBObject = {
    EXISTS
  }

  def getMongoDoesntExist: BasicDBObject = {
    DOESNT_EXIST
  }

  def connectToMongoDatabase(db: String): MongoDB = {
    logger.info("Setting up Mongo database connection")

    // Instantiate Mongo host.
    new MongoDB(host, port, db)
  }

  def defineMongoOr(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(OR, truthValueList)
  }

  def defineMongoAnd(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(AND, truthValueList)
  }

  def defineMongoIn(queryList: BasicDBList): BasicDBObject = {
    new BasicDBObject(IN, queryList)
  }

  def defineMongoRegex(regex: String): BasicDBObject = {
    new BasicDBObject(REGEX, regex)
  }

  def convertListToMongoDbList(normalList: List[BasicDBObject]): BasicDBList = {
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
    mongo.getDbIteratorOverSeq(key, false, filter).toIterator
  }


  def mongoDbIteratorToSet(iterator: Iterator[DBObject]): Set[DBObject] = {
    val buffer = mutable.Set[DBObject]()
    for (value <- iterator) {
      buffer add value
    }
    buffer.toSet
  }
}
