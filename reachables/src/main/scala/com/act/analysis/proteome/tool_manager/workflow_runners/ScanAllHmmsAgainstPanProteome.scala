package com.act.analysis.proteome.tool_manager.workflow_runners

import java.io.File

import com.act.analysis.proteome.tool_manager.workflow.ScanHmmAgainstProteomes

object ScanAllHmmsAgainstPanProteome {
  def main(args: Array[String]) {
    val hmmDirectory = "/Volumes/shared-data/Michael/Pfams/pfam"

    // Setup file pathings
    val proteomesDirectory = "/Volumes/shared-data/Michael/PanProteome/"
    val outputDirectory = "/Users/michaellampe/PanProteomeAnalysis/HmmHits/"
    val workflow = new ScanHmmAgainstProteomes(new File(hmmDirectory),
                                               new File(proteomesDirectory, "pan_proteome.fasta"),
                                               new File(outputDirectory))
    workflow.startWorkflowBlocking()
  }
}
