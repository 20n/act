package com.act.analysis.proteome.tool_manager.workflow;

import org.apache.spark.launcher.SparkLauncher;
import org.apache.spark.deploy.SparkSubmit;

public class LauncherTest {

  public static void main(String[] args) throws Exception {

    SparkSubmit.main(args);

//    Process spark = new SparkLauncher()
//        .setSparkHome("/usr/local/software/spark-1.5.2-bin-hadoop2.6")
//        .setAppResource("/home/vijay/act/reachables/target/scala-2.10/reachables-assembly-0.1.jar")
//        .setMainClass("com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector")
//        .setMaster("local")
//        .setDeployMode("client")
//        .addAppArgs(args)
//        .launch();
//
//    spark.waitFor();
  }
}
