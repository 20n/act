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

package com.act.workflow.tool_manager.workflow.workflow_mixins.composite

import java.io.File

import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByRo
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.QueryByReactionId
import org.apache.logging.log4j.LogManager

trait RoToSequences extends QueryByRo with QueryByReactionId {
  /**
    * Takes in a set of ROs and translates them into FASTA files with all the enzymes that do that RO
    */
  def writeFastaFileFromEnzymesMatchingRos(roValues: List[String], outputFastaFile: File,
                                           database: String, organism: Option[String] = None)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingRos")
    val mongoConnection = connectToMongoDatabase(database)

    val reactionIds = queryReactionsForReactionIdsByRo(roValues, mongoConnection)
    methodLogger.info("Discovering and writing sequences to FASTA file")
    createFastaByReactionId(reactionIds.keySet.toList, outputFastaFile, mongoConnection, organism)
  }
}
