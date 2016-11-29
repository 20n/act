package com.act.biointerpretation.l2expansion.sparkprojectors

import com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers.{ReadFromSubstrateFile, WriteToJson}

object FileSparkRoProjector extends BasicSparkROProjector with ReadFromSubstrateFile with WriteToJson {
  val runningClass = getClass

  val OPTION_SUBSTRATES_LISTS = "i"
  val OPTION_OUTPUT_DIRECTORY = "o"
}
