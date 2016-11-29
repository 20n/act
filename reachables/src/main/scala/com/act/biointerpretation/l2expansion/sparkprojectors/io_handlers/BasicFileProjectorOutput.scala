package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import java.io.File

import org.apache.log4j.LogManager

trait BasicFileProjectorOutput extends BasicProjectorOutput {
  private val LOGGER = LogManager.getLogger(getClass)

  final def createOutputDirectory(directory: File): Unit = {
    if (directory.exists() && !directory.isDirectory) {
      LOGGER.error(s"Found file when expected directory at ${directory.getAbsolutePath}.  " +
        "Unable to set create output directories.")
      System.exit(1)
    } else {
      LOGGER.info(s"Creating output directory at ${directory.getAbsolutePath}")
      directory.mkdirs()
    }
  }
}
