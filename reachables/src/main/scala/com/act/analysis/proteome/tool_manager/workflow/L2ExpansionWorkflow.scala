package com.act.analysis.proteome.tool_manager.workflow

import com.act.analysis.proteome.tool_manager.jobs.Job
import com.act.analysis.proteome.tool_manager.tool_wrappers.ShellWrapper
import org.apache.commons.cli.CommandLine
import org.apache.spark.launcher.SparkLauncher

object L2ExpansionWorkflow {

  def main(args: Array[String]): Unit = {
    println("Hello, world!")

    val spark = new SparkLauncher()
      .setSparkHome("/usr/local/software/spark-1.5.2-bin-hadoop2.6")
      .setAppResource("/home/vijay/act/reachables/target/scala-2.10/reachables-assembly-0.1.jar")
      .setMainClass("com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector")
      .setMaster("spark://10.0.20.19:7077")
      .setDeployMode("client")
      .addAppArgs("--substrates-list /home/vijay/act/reachables/output_inchis.txt -s -o /mnt/shared-data/Vijay/l2projection/ -l /mnt/shared-data/3rdPartySoftware/Chemaxon/license_Start-up.cxl")
      .launch()

    spark.waitFor()
  }

  def defineWorkflow(commandLine: CommandLine): Job = {

    val command:String = "/usr/local/software/spark-1.5.2-bin-hadoop2.6/bin/spark-submit " +
      "--driver-class-path /home/vijay/act/reachables/target/scala-2.10/reachables-assembly-0.1.jar " +
      "--class com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector " +
      "--master spark://10.0.20.19:7077 --deploy-mode client --executor-memory 4G " +
      "/home/vijay/act/reachables/target/scala-2.10/reachables-assembly-0.1.jar " +
      "--substrates-list /home/vijay/act/reachables/output_inchis.txt -s -o /mnt/shared-data/Vijay/l2projection/ " +
      "-l /mnt/shared-data/3rdPartySoftware/Chemaxon/license_Start-up.cxl"

    val spark = new SparkLauncher()
      .setSparkHome("/usr/local/software/spark-1.5.2-bin-hadoop2.6")
      .setAppResource("/home/vijay/act/reachables/target/scala-2.10/reachables-assembly-0.1.jar")
      .setMainClass("com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector")
      .setMaster("spark://10.0.20.19:7077")
      .setDeployMode("client")
      .addAppArgs("--substrates-list /home/vijay/act/reachables/output_inchis.txt -s -o /mnt/shared-data/Vijay/l2projection/ -l /mnt/shared-data/3rdPartySoftware/Chemaxon/license_Start-up.cxl")
      .launch();

    spark.waitFor();

    // Print working directory
    //val job1 = ShellWrapper.shellCommand(command.split(" ").toList)

    val job1 = ShellWrapper.shellCommand(List("ls"))


    // See which files are available
    val job2 = ShellWrapper.shellCommand(List("ls"))

    // Returns first job
    job1.thenRun(job2)
  }
}
