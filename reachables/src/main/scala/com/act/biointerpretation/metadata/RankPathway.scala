package com.act.biointerpretation.metadata

import java.util.{List => JavaList}

import act.server.MongoDB
import act.shared.Reaction
import com.act.reachables.ReactionPath
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import com.mongodb.{MongoClient, ServerAddress}
import org.apache.commons.lang3.tuple.Pair
import org.json.JSONArray

import scala.collection.JavaConverters._
import scala.collection.mutable

object RankPathway {
  /* Filtering Settings */
  val MAX_PROTEINS_PER_PATH = 4
  val MAX_DESIGNS_PER_TARGET = 5

  // This is a table of all the reactions in the database w/ metadata and their associated score.
  var rankingTable: Map[Long, List[Pair[ProteinMetadata, Integer]]] = null

  // Created once, used for all pathways.
  private def createRankingTable(srcDB: String, dstDB: String): Map[Long, List[Pair[ProteinMetadata, Integer]]] = {
    val tbl = ProteinMetadataComparator.createProteinMetadataTable(srcDB, dstDB).asScala.map(v => (v._1: Long, v._2.asScala.toList)).toMap
    tbl
  }

  object Mongo extends MongoWorkflowUtilities
  
  private def chooseOneFromEach[T](input: List[List[T]]): List[List[T]] = {
    val fullList = mutable.ListBuffer[List[T]]()

    def chooseAll(remainingInput: List[List[T]], createdListSoFar: List[T] = List()): Unit = {
      val headElements: List[T] = remainingInput.head

      val tailElements: List[List[T]] = remainingInput.tail
      if (tailElements.isEmpty) {
        // Woo we are done so we add it to our list of combinations
        headElements.foreach(x => fullList.append(createdListSoFar ::: List(x)))
        return
      }

      headElements.foreach(x => chooseAll(tailElements, createdListSoFar ::: List(x)))
    }
    chooseAll(input)

    fullList.toList
  }

  private def scoringFunction(composition: List[(String, ProteinMetadata, Integer)]): Double = {
    // Current power of 2
    var count = 0
    // We reverse so that we process the target first
    val compositionSum = composition.reverse.map(c => {
      count += 1
      // Assigned score * (1/(2^Count))... exponential decay
      c._3 * (1/Math.pow(2, count))
    }).sum

    // We penalize the length by dividing by the length.
    // Therefore, shorter sequences are penalized less
    // (This works out as not averaging because we assign value is an exponentially decaying way)
    val divisor: Double = 1/composition.length.toDouble
    compositionSum * divisor
  }

  private def removeDuplicateProteins(s: List[List[(String, ProteinMetadata, Integer)]]): List[List[(String, ProteinMetadata, Integer)]] = {
    val proteinBlacklist: mutable.HashMap[ProteinMetadata, Long] = mutable.HashMap()

    s.sortBy(scoringFunction).reverse.filter(current => {
      // Modify outside scope so progressive filter continually strengthens conditions
      val isValid = current.forall(t => {
        proteinBlacklist.get(t._2) match {
          case Some(i) => i <= 2
          case None => true
        }
      })

      // This is a valid sequence, so we increment our protein uses
      if (isValid) {
        current.foreach(t => {
          val meta = t._2
          if (proteinBlacklist.contains(meta)) {
            proteinBlacklist.put(meta, proteinBlacklist(meta) + 1)
          } else {
            proteinBlacklist.put(meta, 1)
          }
        })
      }

      isValid
    })
  }

  def processSinglePath(pathway: ReactionPath, sourceDB: String, outDB: String): Option[List[List[Pair[ProteinMetadata, Integer]]]] = {
    val sourceDb = Mongo.connectToMongoDatabase(sourceDB)

    if (rankingTable == null) {
      rankingTable = createRankingTable(sourceDB, outDB)
    }

    // Error checking and input forming
    val reactionNodes = pathway.getPath.asScala.toList.filter(_.isReaction)
    if (reactionNodes.length > MAX_PROTEINS_PER_PATH || !reactionNodes.forall(x => x.sequences.size() > 0)) return None

    // Rank each node in a given pathway.  Some nodes have multiple metadata, therefore we get a list of lists.
    val rankingsForEachNode: List[List[(Long, List[Pair[ProteinMetadata, Integer]])]] =
      reactionNodes.map(node =>
        node.reactionIds.asScala.toList.map(y => (y, rankingTable.get(y))).map(v => {
          if (v._2.isDefined) {
            (v._1: Long, v._2.get.map(x => Pair.of(x.getLeft, x.getLeft.sequences.size + x.getRight.toInt: Integer)).sortBy(r => -r.getRight))
          } else {
            val dummyMetadata = getDummyMetadata(sourceDb, v._1)
            (v._1: Long, List(Pair.of(dummyMetadata, Integer.valueOf(dummyMetadata.sequences.size()))))
          }
        }))

    val rankedFullStep: List[List[(Long, Pair[ProteinMetadata, Integer])]] = rankingsForEachNode.map(r => {
      r.flatMap(x => x._2.map(c => (x._1, c)))
    })

    val proteinPaths: List[List[(Long, Pair[ProteinMetadata, Integer])]] = rankedFullStep.reverse.map(r => {
      r.sortBy(r => -r._2.getRight).take(MAX_DESIGNS_PER_TARGET)
    })

    Option(proteinPaths.map(x => x.map(y => y._2)))
  }

  def processSinglePathAsJava(pathway: ReactionPath, sourceDB: String, outDB: String): JavaList[JavaList[Pair[ProteinMetadata, Integer]]] = {
    val processSinglePathVal = processSinglePath(pathway, sourceDB, outDB)
    processSinglePathVal match {
      case Some(x) => x.map(_.asJava).asJava;
      case None => null;
    }
  }

  private def getDummyMetadata(srcDB: MongoDB, rid: Long): ProteinMetadata = {
    val p = new ProteinMetadata()

    val newId: Long = if (rid < 0) {
      Reaction.reverseID(rid)
    } else {
      rid
    }

    val reaction = srcDB.getReactionFromUUID(newId)

    var jarray: JSONArray = new JSONArray()
    try {
      val pd = reaction.getProteinData.asScala.toList

      if (pd.nonEmpty) {
        jarray = pd.head.getJSONArray("sequences")
      }
    }
    catch {
      case err: Exception =>
    }

    val returnList: java.util.List[java.lang.Long] = new java.util.ArrayList[java.lang.Long]
    var i: Int = 0
    while (i < jarray.length) {
      val sequenceId = jarray.getLong(i): java.lang.Long
      returnList.add(sequenceId)
      i += 1
    }

    p.sequences = returnList

    p
  }
}
