package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db

import squants.Ratio
import squants.market.Price
import squants.mass.Mass

trait SequenceDatabaseKeywords {
  // Add to these as needed
  val SEQUENCE_DB_KEYWORD_ECNUM = "ecnum"
  val SEQUENCE_DB_KEYWORD_SEQ = "seq"
  val SEQUENCE_DB_KEYWORD_METADATA = "metadata"
  val SEQUENCE_DB_KEYWORD_NAME = "name"
  val SEQUENCE_DB_KEYWORD_ID = "_id"
  val SEQUENCE_DB_KEYWORD_RXN_REFS = "rxn_refs"
  val SEQUENCE_DB_KEYWORD_ORGANISM_NAME = "org"
}
