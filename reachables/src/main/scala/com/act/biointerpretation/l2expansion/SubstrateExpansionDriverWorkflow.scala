package com.act.biointerpretation.l2expansion

import java.io.File

import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{ScalaJobWrapper, SparkWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager
import scala.collection.JavaConverters._

class SubstrateExpansionDriverWorkflow extends Workflow {

  val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to run untargeted metabolomics pipeline. This runs all steps in the pipeline," +
    "beginning with L2Expansion, followed by LCMS analysis, and then structure clustering and SAR scoring."


  val OPTION_LICENSE_FILE = "l"
  val OPTION_SUBSTRATES_LIST = "i"
  val OPTION_OUTPUT_DIRECTORY = "o"
  val OPTION_NUMBER_OF_REPEATS = "n"
  val OPTION_SPARK_MASTER = "m"

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_LICENSE_FILE).
        required(true).
        hasArg.
        longOpt("license-file")
        .desc("A path to the Chemaxon license file to load, mainly for checking license validity"),

      CliOption.builder(OPTION_SUBSTRATES_LIST).
        required(true).
        hasArg.
        longOpt("substrates-list").
        desc("A list of substrate InChIs onto which to project ROs"),

      CliOption.builder(OPTION_OUTPUT_DIRECTORY).
        required(true).
        hasArg.
        longOpt("output-directory").
        desc("A directory in which to write per-RO result files"),

      CliOption.builder(OPTION_NUMBER_OF_REPEATS).
        required(true).
        hasArg.
        longOpt("output-directory").
        desc("A directory in which to write per-RO result files"),

      CliOption.builder(OPTION_SPARK_MASTER).
        hasArg.
        longOpt("spark-master").
        desc("Spark master for the spark cluster."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {
    val repeatRange = Range(0, cl.getOptionValue(OPTION_NUMBER_OF_REPEATS).toInt)

    val substrateListFile = new File(cl.getOptionValue(OPTION_SUBSTRATES_LIST))
    require(substrateListFile.exists(),
      s"Substrate list file supplied does not exist.  Supplied ${substrateListFile.getAbsolutePath}")

    val chemaxonLicenseFile = new File(cl.getOptionValue(OPTION_LICENSE_FILE))
    require(chemaxonLicenseFile.exists(),
      s"Chemaxon license file supplied does not exist.  Supplied ${chemaxonLicenseFile.getAbsolutePath}")

    val workingDirectory = new File(cl.getOptionValue(OPTION_OUTPUT_DIRECTORY))
    if (!workingDirectory.exists()) workingDirectory.mkdirs()


    val singleSubstrateRoProjectorClassPath = "com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector"
    val sparkMaster = cl.getOptionValue(OPTION_SPARK_MASTER, "spark://10.0.20.19:7077")
    // Tries to assemble JAR for spark export.  Step 1 towards Skynet is self-assembly of jar files.

    // Make sure to assemble jar first
    headerJob.thenRun(SparkWrapper.sbtAssembly())

    repeatRange.foreach(iteration => {
      val iterationOutputDirectory = new File(workingDirectory, s"$iteration.ExpansionOf.${substrateListFile.getName}")
      if (!iterationOutputDirectory.exists()) iterationOutputDirectory.mkdirs()

      val outputInchiIdentifier = "uniqueInchisIteration"
      val outputUniqueInchiFile = new File(workingDirectory, s"$outputInchiIdentifier.$iteration.txt")

      val substrateList =
        if (iteration == 0)
          substrateListFile
        else
          new File(workingDirectory, s"$outputInchiIdentifier.${iteration - 1}.txt")

      val roProjectionArgs = List(
        "--substrates-list", substrateList.getAbsolutePath,
        "-o", iterationOutputDirectory.getAbsolutePath,
        "-l", chemaxonLicenseFile.getAbsolutePath,
        "-s"
      )

      val expansion = SparkWrapper.runClassPath(
        singleSubstrateRoProjectorClassPath,
        sparkMaster,
        roProjectionArgs
      )

      val processing: () => Unit = () => {
        // Each RO has its own file.
        val allFilesInOutputDir: List[File] = iterationOutputDirectory.list().
          map(x => new File(x)).toList.
          filter(x => x.exists() && x.isFile)
        logger.info(s"Found ${allFilesInOutputDir.length} projection files.")

        val allInchis: Set[String] = allFilesInOutputDir.flatMap(inputFile => {
          val predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputFile)

          val uniqueSubstrates = predictionCorpus.getUniqueSubstrateInchis.asScala.toSet
          val uniqueProducts = predictionCorpus.getUniqueProductInchis.asScala.toSet

          logger.info(s"Found $uniqueSubstrates unique substrates and $uniqueProducts unique products.  " +
            s"Combining and writing them to a file.")
          uniqueProducts ++ uniqueSubstrates
        }).toSet

        val inchis = new L2InchiCorpus(allInchis.asJava)
        inchis.writeToFile(outputUniqueInchiFile)
      }

      val convertPredictionToUniqueInchis = ScalaJobWrapper.wrapScalaFunction(s"Condense $iteration into unique molecules.", processing)

      headerJob.thenRun(expansion)
      headerJob.thenRun(convertPredictionToUniqueInchis)
    })

    headerJob
  }
}
