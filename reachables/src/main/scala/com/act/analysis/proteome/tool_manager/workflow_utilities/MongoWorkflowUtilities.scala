package com.act.analysis.proteome.tool_manager.workflow_utilities

import act.server.MongoDB
import com.act.analysis.proteome.tool_manager.jobs.JobManager
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}

import scala.collection.JavaConversions._
import scala.collection.mutable

object MongoWorkflowUtilities {
  // Commonly used operators for this mongo query
  val EXISTS = new BasicDBObject("$exists", 1)
  val DOESNT_EXIST = new BasicDBObject("$exists", 0)
  val ELEMMATCH = "$elemMatch"
  private val OR = "$or"
  private val AND = "$and"

  def connectToDatabase(): MongoDB = {
    JobManager.logInfo("Setting up Mongo database connection")

    // Instantiate Mongo host.
    val host = "localhost"
    val port = 27017
    val db = "marvin"
    new MongoDB(host, port, db)
  }

  def defineOr(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(OR, truthValueList)
  }

  def defineAnd(truthValueList: BasicDBList): BasicDBObject = {
    new BasicDBObject(AND, truthValueList)
  }

  def toDbList(normalList: List[BasicDBObject]): BasicDBList = {
    val copyList = new BasicDBList
    copyList.addAll(normalList)
    copyList
  }

  def mongoQueryReactions(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Set[DBObject] = {
    val ret = mongo.getIteratorOverReactions(key, false, filter)
    val buffer = mutable.Set[DBObject]()
    while (ret.hasNext) {
      val current = ret.next
      buffer add current
    }
    buffer.toSet
  }

  def mongoQuerySequences(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Set[DBObject] = {
    val ret = mongo.getIteratorOverSeq(key, false, filter)
    val buffer = mutable.Set[DBObject]()
    while (ret.hasNext) {
      val current = ret.next
      buffer add current
    }
    buffer.toSet
  }
}
