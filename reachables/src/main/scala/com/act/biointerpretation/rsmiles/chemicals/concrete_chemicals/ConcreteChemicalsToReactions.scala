package com.act.biointerpretation.rsmiles.chemicals.concrete_chemicals

import java.io.{BufferedWriter, File, FileWriter}

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import com.act.biointerpretation.rsmiles.chemicals.Information.ReactionInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import org.apache.log4j.LogManager
import spray.json._

import scala.collection.JavaConversions._

object ConcreteChemicalsToReactions {
  val logger = LogManager.getLogger(getClass)

  def calculateConcreteSubstrates(moleculeFormat: MoleculeFormat.MoleculeFormatType)
                                 (db: String = "marvin", host: String = "localhost", port: Int = 27017)
                                 (outputSubstrateFile: File, outputReactionCorpus: File, substrateCount: Int)
                                 (): Unit = {
    val db = Mongo.connectToMongoDatabase()

    val concreteChemicals = ConcreteChemicals.getConcreteChemicals(db, moleculeFormat)
    val concreteReactions = ConcreteReactions.getConcreteReactions(db, moleculeFormat, substrateCount)(concreteChemicals)

    logger.info(s"Found ${concreteReactions.size} matching reactions with $substrateCount substrates. Writing both the substrates and reactions to disk.")

    writeSubstrateStringsForSubstrateCount(db)(concreteReactions.seq.toList, outputSubstrateFile)
    writeAbstractReactionsToJsonCorpus(concreteReactions.seq.toList, outputReactionCorpus)
  }

  def writeAbstractReactionsToJsonCorpus(abstractReactions: List[ReactionInformation],
                                         outputReactionsLocation: File): Unit = {
    val outputFile = new BufferedWriter(new FileWriter(outputReactionsLocation))
    outputFile.write(abstractReactions.toJson.prettyPrint)
    outputFile.close()
  }

  def writeSubstrateStringsForSubstrateCount(mongoDb: MongoDB)
                                            (reactions: List[ReactionInformation], outputFile: File): Unit = {
    require(!outputFile.isDirectory, "The file you designated to output your files to is a directory and therefore is not a valid path.")

    // We need to make sure this is a set so that we remove as many duplicates as possible.
    logger.info(s"Quickly checking for duplicate substrates to minimize the size of our substrate corpus.  Current size is ${reactions.length}")
    val substrates: Set[List[String]] = reactions.map(_.getSubstrates.map(_.getString)).seq.toSet
    logger.info(s"After removing duplicates, ${substrates.size} exist.")

    val bufferedWriter = new BufferedWriter(new FileWriter(outputFile))
    bufferedWriter.write(substrates.toJson.prettyPrint)
    bufferedWriter.close()
  }

  object Mongo extends MongoWorkflowUtilities {}

}
