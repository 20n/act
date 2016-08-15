package com.act.workflow.tool_manager.workflow.workflow_mixins.base

import java.io.File

import org.apache.commons.cli.CommandLine
import org.apache.logging.log4j.LogManager

trait WorkingDirectoryUtility {
  def defineOutputFilePath(cl: CommandLine, optionName: String, identifier: String, defaultValue: String, workingDirectory: String): File = {
    val methodLogger = LogManager.getLogger("workingDirectoryFilePathDefinition")

    createWorkDirectory(new File(workingDirectory))

    // Spaces tend to be bad for file names
    val filteredIdentifier = identifier.replace(" ", "_")

    // <User chosen or default file name>_<UID> ... Add UID to end in case absolute file path is supplied
    val fileNameHead = cl.getOptionValue(optionName, defaultValue)
    val fileName = s"${fileNameHead}_$filteredIdentifier"

    val finalFile = new File(workingDirectory, fileName)
    methodLogger.info(s"The final file path for file $optionName was ${finalFile.getAbsoluteFile}")

    verifyOutputFile(finalFile)

    finalFile
  }

  def verifyOutputFile(outputFile: File): Unit = {
    // File sanity checks
    if (outputFile.isDirectory) {
      val message = s"File path should be a file, not a directory. Supplied path is ${outputFile.getAbsolutePath}"
      throw new RuntimeException(message)
    }

    if (outputFile.exists()) {
      if (!outputFile.canWrite) throw new RuntimeException(s"Can't write to designated location ${outputFile.getAbsolutePath}")
    } else {
      outputFile.createNewFile()
      if (!outputFile.canWrite) throw new RuntimeException(s"Can't write to designated location ${outputFile.getAbsolutePath}")
      outputFile.delete()
    }
  }

  def createWorkDirectory(workingDirectory: File): Unit = {
    if (!workingDirectory.exists()) {
      workingDirectory.mkdirs()
    }
  }

  def verifyInputFile(inputFile: File): Unit = {
    if (!inputFile.exists()) {
      val message = s"The input file ${inputFile.getAbsolutePath} does not exist."
      throw new RuntimeException(message)
    }

    if (inputFile.isDirectory) {
      val message = s"The input file ${inputFile.getAbsolutePath} is a directory, not a file as required."
      throw new RuntimeException(message)
    }
  }
}
