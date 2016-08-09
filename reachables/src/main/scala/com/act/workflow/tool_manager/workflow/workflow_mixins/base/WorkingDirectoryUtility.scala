package com.act.workflow.tool_manager.workflow.workflow_mixins.base

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

  def verifyInputFile(inputFile: String, workingDirectory: String = null): Boolean = {
    val filePath = new File(workingDirectory, inputFile)
    verifyInputFile(filePath)
  }

  def verifyInputFile(inputFile : File): Boolean = {
    val methodLogger = LogManager.getLogger("verifyInputFilePath")
    if (!inputFile.exists()) {
      methodLogger.error(s"The input file ${inputFile.getAbsolutePath} does not exist.")
      return false
    }

    if (inputFile.isDirectory) {
      methodLogger.error(s"The input file ${inputFile.getAbsolutePath} is a directory, not a file as required.")
      return false
    }

    true
  }
}
