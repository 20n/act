package com.act.workflow.tool_manager.workflow.workflow_mixins.composite

import java.io.File

trait SarTreeConstructor {
  private val NEW_PROTEIN_INDICATOR = ">"

  def constructSarTreesFromAlignedFasta(alignedFastaFile: File)(): Unit = {
    val lines = scala.io.Source.fromFile(alignedFastaFile).getLines()

    // Group 2 has everything after the start parsing indicator
    val result = lines.span(!_.startsWith(NEW_PROTEIN_INDICATOR))

  }
}
