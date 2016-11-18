package com.act.biointerpretation.l2expansion.sparkprojectors

import com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers.{ReadFromDatabase, WriteToDatabase}

object DatabaseSparkRoProjector extends ReadFromDatabase with WriteToDatabase {
  val runningClass = getClass

  val OPTION_READ_DB_NAME: String = "rd"
  val OPTION_READ_DB_PORT: String = "rp"
  val OPTION_READ_DB_HOST: String = "rh"
  val OPTION_READ_DB_COLLECTION: String = "rc"

  val OPTION_WRITE_DB_NAME: String = "wd"
  val OPTION_WRITE_DB_PORT: String = "wp"
  val OPTION_WRITE_DB_HOST: String = "wh"
  val OPTION_WRITE_DB_COLLECTION: String = "wc"
}
