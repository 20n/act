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

package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoWorkflowUtilities, SequenceKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}

trait QueryBySequenceId extends MongoWorkflowUtilities {
  def querySequencesBySequenceId(sequenceIds: List[Long], mongoConnection: MongoDB,
                                 returnFilterFields: List[String]): Iterator[DBObject] = {
    val sequenceIdList = new BasicDBList
    sequenceIds.map(sequenceId => sequenceIdList.add(sequenceId.asInstanceOf[AnyRef]))

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = createDbObject(SequenceKeywords.ID, defineMongoIn(sequenceIdList))

    val sequenceIdReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      sequenceIdReturnFilter.append(field, 1)
    }

    val sequenceReturnIterator: Iterator[DBObject] = mongoQuerySequences(mongoConnection)(seqKey, sequenceIdReturnFilter)

    sequenceReturnIterator
  }
}
