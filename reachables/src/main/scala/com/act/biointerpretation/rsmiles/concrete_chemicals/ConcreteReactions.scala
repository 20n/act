package com.act.biointerpretation.rsmiles.concrete_chemicals

import java.io.File

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.biointerpretation.rsmiles.processing.ReactionProcessing
import com.act.biointerpretation.rsmiles.processing.ReactionProcessing.ReactionInformation
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment.RoAssignments
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ReactionKeywords
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByRo
import com.mongodb.DBObject

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.{ParMap, ParSeq}



object ConcreteReactions extends QueryByRo {
  def getConcreteReactions(db: String = "marvin" , host: String = "localhost", port: Int = 27017)
                          (moleculeFormat: MoleculeFormat.MoleculeFormatType,
                           substrateCount: Int, outputFile: File)(): Unit = {
    val mongoDb = connectToMongoDatabase()
    val eros = new ErosCorpus()
    eros.loadValidationCorpus()

    val roIds: List[Int] = eros.getRoIds.asScala.toList.map(_.toInt)

    val reactionInformationById: (Int) => List[ReactionInformation] =
      getReactionInformationForSingleRo(mongoDb, moleculeFormat, substrateCount)

    val assignments: List[RoAssignments] = roIds.par.map(roId => {
      val reactionInformation = reactionInformationById(roId)
      new RoAssignments(roId, reactionInformation)
    }).seq.toList

    ReactionRoAssignment.writeRoAssignmentsToJson(outputFile, assignments)
  }

  def getReactionInformationForSingleRo(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType, substrateCount: Int)(ro: Int): List[ReactionInformation] = {
    val desiredFields = List(ReactionKeywords.ID.toString, ReactionKeywords.ENZ_SUMMARY.toString)
    val reactionsForRo: Iterator[DBObject] = queryReactionsByRo(List(ro.toString), mongoDb, desiredFields)

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      ReactionProcessing.constructDbReaction(mongoDb, moleculeFormat)(ParMap(), substrateCount)_

    val reactions: ParSeq[ReactionInformation] = reactionsForRo.toStream.par.flatMap(reaction => reactionConstructor(reaction))
    reactions.seq.toList
  }
}
