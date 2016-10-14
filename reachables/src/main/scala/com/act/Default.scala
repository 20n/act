package com.act

import java.io.File

import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.biointerpretation.rsmiles.chemicals.concrete_chemicals.ConcreteChemicalsToReactions
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment
import com.act.workflow.tool_manager.jobs.management.JobManager
import com.act.workflow.tool_manager.tool_wrappers.{ScalaJobWrapper, SparkWrapper}

object Default {

  def main(args: Array[String]) {

    val substrateListOutputFile = new File("/home/michael/TestValidator/substrateList")
    val reactionListOutputFile = new File("/home/michael/TestValidator/reactionList")
    val projectionOutputFile = new File("/home/michael/TestValidator/ResultsDir/")
    val roAssignmentOutputFile = new File("/home/michael/TestValidator/roAssignment")

    if (!projectionOutputFile.exists()) {
      projectionOutputFile.mkdirs()
    }


    val individualSubstrateFunction = ConcreteChemicalsToReactions.calculateConcreteSubstrates(MoleculeFormat.stdInchi)() _
    val appliedFunction: () => Unit = individualSubstrateFunction(substrateListOutputFile, reactionListOutputFile, 1)

    val singleSubstrateRoProjectorClassPath = "com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector"
    val sparkMaster = "spark://spark-master:7077"

    val roProjectionArgs = List(
      "--substrates-list", substrateListOutputFile.getAbsolutePath,
      "-o", projectionOutputFile.getAbsolutePath,
      "-l", "/mnt/shared-data/3rdPartySoftware/Chemaxon/license_Start-up.cxl",
      "-v", MoleculeFormat.stdInchi.toString
    )

    val roProjection = SparkWrapper.runClassPath("target/scala-2.10/reachables-assembly-0.1.jar",
      singleSubstrateRoProjectorClassPath)(
      sparkMaster,
      roProjectionArgs)(
      memory = "12G")


    val reactionAssigner =
      ReactionRoAssignment.assignRoToReactions(
        projectionOutputFile,
        reactionListOutputFile, roAssignmentOutputFile) _



    val startJob = ScalaJobWrapper.wrapScalaFunction("getConcreteChemicals", appliedFunction)
    startJob.thenRun(roProjection)
    startJob.thenRun(ScalaJobWrapper.wrapScalaFunction("Ro Assignment to Reactions", reactionAssigner))
    JobManager.startJobAndAwaitUntilWorkflowComplete(startJob)
  }
}