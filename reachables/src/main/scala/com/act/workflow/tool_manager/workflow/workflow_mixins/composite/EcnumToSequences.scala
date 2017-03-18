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

import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByEcNumber
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.QueryByReactionId
import org.apache.logging.log4j.LogManager

trait EcnumToSequences extends QueryByEcNumber with QueryByReactionId {
  /**
    * Takes in a ecnum and translates them into FASTA files with all the enzymes that do that Ecnum
    */
  def writeFastaFileFromEnzymesMatchingEcnums(roughEcnum: String, outputFastaFile: File, database: String)(): Unit = {
    val methodLogger = LogManager.getLogger("writeFastaFileFromEnzymesMatchingEcnums")

    val mongoConnection = connectToMongoDatabase(database)

    // Documents are keyed on their IDs
    val reactionIds = queryReactionsForReactionIdsByEcNumber(roughEcnum, mongoConnection)
    methodLogger.info("Discovering and writing sequences to FASTA file")
    createFastaByReactionId(reactionIds.keySet.toList, outputFastaFile, mongoConnection)
  }
}
