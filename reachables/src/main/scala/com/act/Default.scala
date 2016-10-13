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
    val mySubstrateListOutputFile = new File("/home/michael/TestValidator/mySubstrateList")
    val reactionListOutputFile = new File("/home/michael/TestValidator/reactionList")
    val projectionOutputFile = new File("/home/michael/TestValidator/ResultsDir/")
    val roAssignmentOutputFile = new File("/home/michael/TestValidator/ResultsDir/roAssignment")

    if (!projectionOutputFile.exists()) {
      projectionOutputFile.mkdirs()
    }


    val individualSubstrateFunction = ConcreteChemicalsToReactions.calculateConcreteSubstrates(MoleculeFormat.stdInchi)() _
    val appliedFunction: () => Unit = individualSubstrateFunction(substrateListOutputFile, reactionListOutputFile, 1)

    val singleSubstrateRoProjectorClassPath = "com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector"
    val sparkMaster = "spark://spark-master:7077"

    val roProjectionArgs = List(
      "--substrates-list", mySubstrateListOutputFile.getAbsolutePath,
      "-o", projectionOutputFile.getAbsolutePath,
      "-l", "/mnt/shared-data/3rdPartySoftware/Chemaxon/license_Start-up.cxl",
      "-v", MoleculeFormat.stdInchi.toString
    )

    val roProjection = SparkWrapper.runClassPath(
      singleSubstrateRoProjectorClassPath,
      sparkMaster,
      roProjectionArgs,
      memory = "4G"
    )

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