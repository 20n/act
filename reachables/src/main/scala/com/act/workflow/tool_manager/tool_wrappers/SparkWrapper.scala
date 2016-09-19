package com.act.workflow.tool_manager.tool_wrappers

import java.io.File

import com.act.workflow.tool_manager.jobs.ShellJob
import org.apache.log4j.LogManager

object SparkWrapper extends ToolWrapper {
  private val LOGGER = LogManager.getLogger(getClass)
  def runClassPath(assembledJar: String, sparkMaster: String)
                  (classPath: String, classArgs: List[String])
                  (memory: String = "1G"): ShellJob = {

    // Check if class path exists.
    try {
      Class.forName(classPath)
    } catch {
      case e: Exception => LOGGER.warn(s"Spark class $classPath is not available " +
        s"in the current file and is, therefore, likely not available in the assembled JAR.")
    }

    val fullArgs: List[String] = List(
      "--driver-class-path", assembledJar,
      "--class", classPath,
      "--master", sparkMaster,
      "--deploy-mode", "client",
      "--executor-memory", memory,
      assembledJar) ::: classArgs

    constructJob("Spark Submit", Option("spark-submit"), args = fullArgs)
  }

  def assembleJarAtRuntime(assembledJarLocation: String, useCached: Boolean = false): ShellJob = {
    if (useCached && new File(assembledJarLocation).exists()) {
      // Placeholder
      val job = constructJob("Assembled JAR in Cache", None, args = List(""))
      job.doNotWriteOutputStream()
      return job
    }

    // Assemble JAR, don't run tests
    val job = constructJob("Creating JAR for spark use", Option("sbt"), args = List("assembly"))
    job.doNotWriteOutputStream()
    job
  }
}
