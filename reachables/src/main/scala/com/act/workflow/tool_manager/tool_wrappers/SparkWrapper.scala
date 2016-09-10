package com.act.workflow.tool_manager.tool_wrappers

import java.io.File

import com.act.workflow.tool_manager.jobs.ShellJob

object SparkWrapper extends ToolWrapper {
  private val assembledJar = new File("target/scala-2.10/reachables-assembly-0.1.jar")
  def runClassPath(classPath: String, sparkMaster: String, classArgs: List[String], memory: String = "4G"): ShellJob = {
    //FATAL: Cowardly refusing to overwrite already existing file if don't use force
    val fullArgs: List[String] = List(
      "--driver-class-path", assembledJar.getAbsolutePath,
      "--class", classPath,
      "--master", sparkMaster,
      "--deploy-mode", "client",
      "--executor-memory", memory,
      assembledJar.getAbsolutePath) ::: classArgs

    constructJob("Spark Submit", Option("spark-submit"), args = fullArgs)
  }

  def sbtAssembly(useCached: Boolean = false): ShellJob = {
    if (useCached && assembledJar.exists()){
      // Placeholder
      return constructJob("Assembled JAR", None, args=List(""))
    }

    // Assemble JAR, don't run tests
    constructJob("Creating JAR for spark use.", Option("sbt"), args = List("assembly"))
  }
}
