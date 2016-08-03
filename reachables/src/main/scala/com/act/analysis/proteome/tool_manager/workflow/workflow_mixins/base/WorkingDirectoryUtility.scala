package com.act.analysis.proteome.tool_manager.workflow.workflow_mixins.base

import java.io.File

import org.apache.commons.cli.CommandLine
import org.apache.logging.log4j.LogManager

trait WorkingDirectoryUtility {
  def defineOutputFilePath(cl: CommandLine, optionName: String, identifier: String, defaultValue: String, workingDirectory: String): String = {
    val methodLogger = LogManager.getLogger("workingDirectoryFilePathDefinition")
    // Spaces tend to be bad for file names
    val filteredIdentifier = identifier.replace(" ", "_")

    // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
    val fileNameHead = cl.getOptionValue(optionName, defaultValue)
    val fileName = s"${fileNameHead}_$filteredIdentifier"

    val finalFilePath = new File(workingDirectory, fileName).getAbsolutePath
    methodLogger.info(s"The final file path for file $optionName was $finalFilePath")
    finalFilePath
  }

  def verifyInputFilePath(inputFile: String, workingDirectory: String = null): Boolean = {
    val methodLogger = LogManager.getLogger("verifyInputFilePath")
    val filePath = new File(workingDirectory, inputFile)

    if (!filePath.exists()) {
      methodLogger.error(s"The input file ${filePath.getAbsolutePath} does not exist.")
      return false
    }

    if (filePath.isDirectory) {
      methodLogger.error(s"The input file ${filePath.getAbsolutePath} is a directory, not a file as required.")
      return false
    }

    true
  }
}
