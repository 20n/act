package com.act.workflow.tool_manager.tool_wrappers

import java.io.File

import com.act.workflow.tool_manager.jobs.ShellJob

object ClustalOmegaWrapper extends ToolWrapper {
  def alignProteinFastaFile(inputFile: File, outputFile: File): ShellJob = {
    //FATAL: Cowardly refusing to overwrite already existing file if don't use force
    constructJob("Clustal Aligner", toolFunction = None,
      args = List("-i", inputFile.getAbsolutePath, "-o", outputFile.getAbsolutePath, "--force"))
  }
}
