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
