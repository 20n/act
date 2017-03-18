/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals

import java.io.{BufferedWriter, File, FileWriter}

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.ReactionInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoWorkflowUtilities
import org.apache.log4j.LogManager
import spray.json._

import scala.collection.JavaConversions._

object AbstractChemicalsToReactions {
  val logger = LogManager.getLogger(getClass)

  def calculateAbstractSubstrates(moleculeFormat: MoleculeFormat.MoleculeFormatType)
                                 (db: String = "marvin", host: String = "localhost", port: Int = 27017)
                                 (outputSubstrateFile: File, outputReactionCorpus: File, substrateCount: Int)
                                 (): Unit = {
    val db = Mongo.connectToMongoDatabase()
    val abstractChemicals = AbstractChemicals.getAbstractChemicals(db, moleculeFormat)
    val abstractReactions = AbstractReactions.getAbstractReactions(db, moleculeFormat, substrateCount)(abstractChemicals)
    logger.info(s"Found ${abstractReactions.size} matching reactions with $substrateCount substrates. " +
      s"Writing both the substrates and reactions to disk.")
    writeSubstrateStringsForSubstrateCount(db)(abstractReactions.seq.toList, outputSubstrateFile)
    writeAbstractReactionsToJsonCorpus(abstractReactions.seq.toList, outputReactionCorpus)
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
    val substrates: Set[String] = reactions.flatMap(_.getSubstrates.map(_.getString)).seq.toSet
    logger.info(s"After removing duplicates, ${substrates.size} exist.")
    new L2InchiCorpus(substrates).writeToFile(outputFile)
  }

  object Mongo extends MongoWorkflowUtilities {}

}
