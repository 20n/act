package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db

import act.server.MongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoWorkflowUtilities, SequenceKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

trait QueryBySequenceId extends MongoWorkflowUtilities {
  def querySequencesBySequenceId(sequenceIds: List[Long], mongoConnection: MongoDB,
                                 returnFilterFields: List[String]): Iterator[DBObject] = {
    val methodLogger = LogManager.getLogger("querySequencesBySequenceId")
    val sequenceIdList = new BasicDBList
    sequenceIds.map(sequenceId => sequenceIdList.add(sequenceId.asInstanceOf[AnyRef]))

    // Elem match on all rxn_to_reactant groups in that array
    val seqKey = createDbObject(SequenceKeywords.ID, defineMongoIn(sequenceIdList))

    val sequenceIdReturnFilter = new BasicDBObject()
    for (field <- returnFilterFields) {
      sequenceIdReturnFilter.append(field, 1)
    }

    methodLogger.info("Querying enzymes with the desired reactions for sequences from Mongo")
    methodLogger.info(s"Running query $seqKey against DB.  Return filter is $sequenceIdReturnFilter. ")
    val sequenceReturnIterator: Iterator[DBObject] = mongoQuerySequences(mongoConnection)(seqKey, sequenceIdReturnFilter)
    methodLogger.info("Finished sequence query.")

    sequenceReturnIterator
  }
}
