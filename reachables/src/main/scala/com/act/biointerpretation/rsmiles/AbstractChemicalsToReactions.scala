package com.act.biointerpretation.rsmiles

import java.io.{BufferedWriter, File, FileWriter}

import act.server.MongoDB
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.rsmiles.AbstractReactions.ReactionInformation
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import org.apache.log4j.LogManager
import spray.json._

import scala.collection.JavaConversions._

object AbstractChemicalsToReactions {
  val logger = LogManager.getLogger(getClass)

  def calculateAbstractSubstrates(db: String = "marvin" , host: String = "localhost", port: Int = 27017)
                                 (outputSubstrateFile: File, outputReactionCorpus: File, substrateCount: Int)
                                 (): Unit = {
    val db = Mongo.connectToMongoDatabase()
    val abstractChemicals = AbstractChemicals.getAbstractChemicals(db)
    val abstractReactions = AbstractReactions.getAbstractReactions(db)(abstractChemicals, substrateCount)
    logger.info(s"Found ${abstractReactions.size} matching reactions with $substrateCount substrates. Writing them to disk.")
    writeSubstrateStringsForSubstrateCount(db)(abstractReactions.seq.toList, outputSubstrateFile)
    writeAbstractReactionsToJsonCorpus(abstractReactions.seq.toList, outputReactionCorpus)
  }

  def writeAbstractReactionsToJsonCorpus(abstractReactions: List[ReactionInformation], outputReactionsLocation: File): Unit ={
    val outputFile = new BufferedWriter(new FileWriter(outputReactionsLocation))
    outputFile.write(abstractReactions.toJson.prettyPrint)
    outputFile.close()
  }

  // Keeps track of other concurrent jobs that have previously determined the number of substrates in a reaction.

  def writeSubstrateStringsForSubstrateCount(mongoDb: MongoDB)(reactions: List[ReactionInformation], outputFile: File): Unit = {
    require(!outputFile.isDirectory, "The file you designated to output your files " +
      "to is a directory and therefore is not a valid path.")
    val substrates: Seq[String] = reactions.flatMap(_.getSubstrates.map(_.getString)).seq
    new L2InchiCorpus(substrates).writeToFile(outputFile)
  }

  object Mongo extends MongoWorkflowUtilities {}
}
