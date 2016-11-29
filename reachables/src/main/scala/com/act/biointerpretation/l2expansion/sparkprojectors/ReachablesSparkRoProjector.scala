package com.act.biointerpretation.l2expansion.sparkprojectors

import com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers.WriteToReachablesDatabase

object ReachablesSparkRoProjector extends BasicSparkROProjector with WriteToReachablesDatabase {
  val runningClass = getClass

  val OPTION_READ_DB_NAME: String = "md"
  val OPTION_READ_DB_PORT: String = "mp"
  val OPTION_READ_DB_HOST: String = "mh"
  val OPTION_READ_DB_COLLECTION: String = "mc"
}
