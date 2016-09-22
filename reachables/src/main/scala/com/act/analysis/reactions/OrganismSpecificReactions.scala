package com.act.analysis.reactions

import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.SequenceKeywords
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByRo
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.QueryByReactionId
import com.mongodb.{BasicDBList, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

object OrganismSpecificReactions {
  val logger = LogManager.getLogger(getClass)

  def findOrganismSpecificReactions(organismRegexString: String = "sapien"): List[RoReactions] = {
    val eros = new ErosCorpus()
    eros.loadValidationCorpus()

    val organismRegex: Option[String] = Option(organismRegexString)
    val mongoConnection = query.connectToMongoDatabase()


    val validRos: ParSeq[RoReactions] = eros.getRoIds.asScala.toList.par.flatMap(ro => {
      val reactionIds = query.queryReactionsForReactionIdsByRo(List(ro.toString), mongoConnection)

      val returnFields = List(SequenceKeywords.ID.toString,
        SequenceKeywords.SEQ.toString,
        SequenceKeywords.ECNUM.toString,
        s"${SequenceKeywords.METADATA.toString}.${SequenceKeywords.NAME.toString}",
        SequenceKeywords.RXN_REFS.toString
      )

      val returnSequenceDocuments: Iterator[DBObject] = query.querySequencesMatchingReactionIdIterator(reactionIds.keySet.toList, mongoConnection, returnFields, organismRegex)

      val returnDocList = returnSequenceDocuments.toList

      if (returnDocList.nonEmpty) {
        val rxnList: List[Long] = returnDocList.flatMap(doc =>
          doc.get(SequenceKeywords.RXN_REFS.toString).asInstanceOf[BasicDBList].toList.asInstanceOf[List[Long]])
        Some(RoReactions(ro.toInt, rxnList.toSet.toList))
      } else {
        None
      }
    })

    validRos.seq.toList
  }

  case class RoReactions(ro: Int, reactions: List[Long])

  object query extends QueryByReactionId with QueryByRo

}