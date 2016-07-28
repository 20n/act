package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base

import java.io.File

import org.apache.commons.cli.CommandLine

trait WorkingDirectoryUtility {
  // Setup file pathing
  val OPTION_WORKING_DIRECTORY_PREFIX: String

  def defineFilePath(cl: CommandLine, optionName: String, identifier: String, defaultValue: String): String = {
    val workingDirectory = cl.getOptionValue(OPTION_WORKING_DIRECTORY_PREFIX, null)

    // Spaces tend to be bad for file names
    val filteredIdentifier = identifier.replace(" ", "_")

    // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
    val fileNameHead = cl.getOptionValue(optionName, defaultValue)
    val fileName = s"${fileNameHead}_$filteredIdentifier"

    new File(workingDirectory, fileName).getAbsolutePath
  }
}
