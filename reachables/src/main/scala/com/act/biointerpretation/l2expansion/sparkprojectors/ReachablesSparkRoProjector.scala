package com.act.biointerpretation.l2expansion.sparkprojectors

import com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers.WriteToReachablesDatabase

object ReachablesSparkRoProjector extends WriteToReachablesDatabase {
  val runningClass = getClass

  val OPTION_READ_DB_NAME: String = "d"
  val OPTION_READ_DB_PORT: String = "p"
  val OPTION_READ_DB_HOST: String = "h"
  val OPTION_READ_DB_COLLECTION: String = "c"
}
