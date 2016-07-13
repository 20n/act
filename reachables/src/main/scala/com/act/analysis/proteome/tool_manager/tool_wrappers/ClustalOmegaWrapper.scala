package com.act.analysis.proteome.tool_manager.tool_wrappers

import com.act.analysis.proteome.tool_manager.jobs.ShellJob

object ClustalOmegaWrapper extends ToolWrapper {
  def alignProteinFastaFile(inputFile: String, outputFile: String): ShellJob = {
    //FATAL: Cowardly refusing to overwrite already existing file if don't use force
    constructJob("", List("-i", inputFile, "-o", outputFile, "--force"))
  }
}
