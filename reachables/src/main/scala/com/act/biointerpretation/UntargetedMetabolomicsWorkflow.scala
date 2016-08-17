package com.act.biointerpretation

import java.io.File
import java.util.NoSuchElementException

import com.act.biointerpretation.l2expansion.{L2ExpansionDriver, L2InchiCorpus, L2PredictionCorpus}
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.biointerpretation.sarinference.{LibMcsClustering, ProductScorer, SarTreeNodeList}
import com.act.jobs.JavaRunnable
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{JavaJobWrapper, ScalaJobWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import com.act.workflow.tool_manager.workflow.workflow_mixins.base.WorkingDirectoryUtility
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._

class UntargetedMetabolomicsWorkflow extends Workflow with WorkingDirectoryUtility {

  override val logger = LogManager.getLogger(getClass.getName)

  override val HELP_MESSAGE = "Workflow to run untargeted metabolomics pipeline. This runs all steps in the pipeline," +
    "beginning with L2Expansion, followed by LCMS analysis, and then structure clustering and SAR scoring."

  private val OPTION_WORKING_DIRECTORY = "w"
  private val OPTION_SUBSTRATES = "s"
  private val OPTION_RO_IDS = "r"
  private val OPTION_MASS_THRESHOLD = "m"
  private val OPTION_LCMS_POSITIVE_RATE = "p"
  private val OPTION_STARTING_POINT = "S"

  private val LCMS_MISS_PENALTY: Double = 1.0
  private val SUBTREE_THRESHOLD: Integer = 2

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        desc("The directory in which to run and create all intermediate files. This directory will be created if it " +
          "does not already exist."),

      CliOption.builder(OPTION_SUBSTRATES).
        required(true).
        hasArg.
        longOpt("substrates").
        desc("A filepath to a file containing the substrate inchis on which to project the ROs."),

      CliOption.builder(OPTION_RO_IDS).
        required(true).
        hasArg.
        longOpt("ro_ids").
        desc("A filepath to a file containing the RO ids to use, one per line"),

      CliOption.builder(OPTION_MASS_THRESHOLD).
        required(false).
        hasArg.
        longOpt("mass-threshold").
        desc("The maximum mass of a substrate to be processed, in daltons."),

      CliOption.builder(OPTION_LCMS_POSITIVE_RATE).
        required(false).
        hasArg.
        longOpt("positive-rate").
        desc("The positive rate of the LCMS run, randomly assigned for now.").
        required(),

      CliOption.builder(OPTION_STARTING_POINT).
        required(false).
        hasArg.
        longOpt("starting-point")
        .desc("What point of the workflow to start at, since some steps may already be complete. Choices are " +
          "EXPANSION, LCMS, CLUSTERING, SAA_SCORING, and PRODUCT_SCORING. The workflow assumes any " +
          "pre-computed steps have been put into the working directory, where they would have been generated had " +
          "the workflow been ran from the start. For example, precomputed prediction corpuses should be located at " +
          "workingDir/predictions.1, workingDir/predictions.2, etc."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  object StartingPoints extends Enumeration {
    type StartingPoints = Value

    val EXPANSION = Value("EXPANSION")
    val LCMS = Value("LCMS")
    val CLUSTERING = Value("CLUSTERING")
    val SAR_SCORING = Value("SAR_SCORING")
    val PRODUCT_SCORING = Value("PRODUCT_SCORING")
  }

  // Implement this with the job structure you want to run to define a workflow
  override def defineWorkflow(cl: CommandLine): Job = {

    /**
      * Handle command line args and create files
      */
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val directory: File = new File(workingDir)
    directory.mkdirs()

    val rawSubstratesFile = new File(cl.getOptionValue(OPTION_SUBSTRATES))
    verifyInputFile(rawSubstratesFile)

    val roIdFile = new File(cl.getOptionValue(OPTION_RO_IDS))
    verifyInputFile(roIdFile)

    val erosCorpus = new ErosCorpus()
    erosCorpus.loadValidationCorpus()
    val roIds = erosCorpus.getRoIdListFromFile(roIdFile).asScala.toList

    val filteredSubstratesFile = new File(workingDir, "filtered_substrates")

    val predictionsFilename = "predictions"
    val predictionsFiles = buildFilesForRos(workingDir, predictionsFilename, roIds)

    val sarTreeFilename = "sartree"
    val sarTreeFiles = buildFilesForRos(workingDir, sarTreeFilename, roIds)

    val scoredSarsFilename = "scored_sars"
    val scoredSarsFiles = buildFilesForRos(workingDir, scoredSarsFilename, roIds)

    val scoredProductsFilename = "scored_products"
    val scoredProductsFiles = buildFilesForRos(workingDir, scoredProductsFilename, roIds)

    val lcmsFilename = "lcms_positives"
    val lcmsFile = new File(workingDir, lcmsFilename)

    val allSarsFile = new File(workingDir, "all_scored_sars")
    val allProductsFile = new File(workingDir, "all_scored_products")

    var maxMass = Integer.MAX_VALUE
    if (cl.hasOption(OPTION_MASS_THRESHOLD)) {
      maxMass = Integer.parseInt(cl.getOptionValue(OPTION_MASS_THRESHOLD))
    }

    val positiveRate = cl.getOptionValue(OPTION_LCMS_POSITIVE_RATE).toDouble
    if (positiveRate < 0 || positiveRate > 1) {
      logger.info(s"Positive rate must be between 0 and 1. Tried to set to $positiveRate")
      System.exit(1)
    }

    /**
      * Define all jobs and different places from which workflow can start
      */
    def addJobsFromExpansionOn() {
      logger.info("Running RO expansion jobs.")
      val massFilteringRunnable = L2InchiCorpus.getRunnableSubstrateFilterer(
        rawSubstratesFile,
        filteredSubstratesFile,
        maxMass)
      headerJob.thenRun(JavaJobWrapper.wrapJavaFunction("mass filter", massFilteringRunnable))

      // Build one job per RO for L2 expansion
      val singleThreadExpansionRunnables =
        roIds.map(roId =>
          L2ExpansionDriver.getRunnableOneSubstrateRoExpander(
            List(roId).asJava,
            filteredSubstratesFile,
            predictionsFiles(roId)))
      // Run one job per RO for L2 expansion
      addJavaRunnableBatch("expansion", singleThreadExpansionRunnables)

      addJobsFromLcmsOn()
    }

    def addJobsFromLcmsOn(): Unit = {
      logger.info("Running LCMS job.")
      // TODO: when Vijay's LCMS code is ready, replace this with the real thing
      // Build a dummy LCMS job that doesn't do anything.
      val lcmsJob = JavaJobWrapper.wrapJavaFunction("lcms",
        new JavaRunnable {
          override def run(): Unit = {
            print("Running dummy LCMS job: didn't implement LCMS workflow job yet.")
          }

          override def toString(): String = {
            "DUMMY_LCMS_JOB"
          }
        }
      )
      headerJob.thenRun(lcmsJob)

      addJobsFromClusteringOn()
    }

    def addJobsFromClusteringOn(): Unit = {
      logger.info("Running clustering jobs.")
      // TODO: when Michael adds the capability, change this workflow to run the clustering jobs and LCMS job in parallel
      // Build one job per RO for clustering
      val clusteringRunnables =
        roIds.map(roId =>
          LibMcsClustering.getRunnableClusterer(
            predictionsFiles(roId),
            sarTreeFiles(roId))) // Run one job per RO for clustering
      addJavaRunnableBatch("cluster", clusteringRunnables)

      addJobsFromSarScoringOn()
    }

    def addJobsFromSarScoringOn(): Unit = {
      logger.info("Running scoring jobs.")
      // Build one job per RO for sar scoring, using a random set of LCMS hits instead of actual data.
      val sarScoringRunnables = roIds.map(roId =>
        LibMcsClustering.getRunnableRandomSarScorer(
          sarTreeFiles(roId),
          lcmsFile,
          scoredSarsFiles(roId),
          LCMS_MISS_PENALTY,
          SUBTREE_THRESHOLD)
      )

      addJavaRunnableBatch("sarScoring", sarScoringRunnables)

      addJobsFromProductScoringOn()
    }

    def addJobsFromProductScoringOn(): Unit = {
      val productScoringRunnables = roIds.map(roId =>
        ProductScorer.getRunnableProductScorer(
          predictionsFiles(roId),
          scoredSarsFiles(roId),
          lcmsFile,
          scoredProductsFiles(roId)
        ))
      addJavaRunnableBatch("productScoring", productScoringRunnables);

      val meshResultsJob = ScalaJobWrapper.wrapScalaFunction("mesh results",
        meshResults(
          scoredSarsFiles.values.toList,
          scoredProductsFiles.values.toList,
          allSarsFile,
          allProductsFile) _)
      headerJob.thenRun(meshResultsJob)
    }

    /**
      * Decide which steps to run based on the StartingPoint supplied, or run the entire workflow by default
      */
    var startingPoint = StartingPoints.EXPANSION
    if (cl.hasOption(OPTION_STARTING_POINT)) {
      try {
        startingPoint = StartingPoints.withName(cl.getOptionValue(OPTION_STARTING_POINT))
      } catch {
        case e: NoSuchElementException =>
          logger.error(s"Starting point must be among ${StartingPoints.values.map(value => value.toString()).toList} " +
            s"; Input: ${cl.getOptionValue(OPTION_STARTING_POINT)}; ${e.getMessage()}")
          System.exit(1)
      }
    }

    startingPoint match {
      case StartingPoints.EXPANSION => addJobsFromExpansionOn()
      case StartingPoints.LCMS => addJobsFromLcmsOn()
      case StartingPoints.CLUSTERING => addJobsFromClusteringOn()
      case StartingPoints.SAR_SCORING => addJobsFromSarScoringOn()
      case StartingPoints.PRODUCT_SCORING => addJobsFromProductScoringOn()
    }

    headerJob
  }


  def meshResults(scoredSarFiles: List[File], scoredProductFiles: List[File],
                  sarOut: File, productOut: File)(): Unit = {
    val sarLists = scoredSarFiles.map(file => {
      var list = new SarTreeNodeList
      list.loadFromFile(file)
      list
    })
    val meshedList = new SarTreeNodeList()
    sarLists.foreach(list => meshedList.addAll(list.getSarTreeNodes))
    meshedList.writeToFile(sarOut)

    var productCorpuses = scoredProductFiles.map(file => L2PredictionCorpus.readPredictionsFromJsonFile(file))
    val meshedCorpus = new L2PredictionCorpus
    productCorpuses.foreach(corpus => meshedCorpus.addAll(corpus.getCorpus))
    meshedCorpus.writePredictionsToJsonFile(productOut)
  }

  /**
    * Build a file for each RO, with the RO id as a suffix
    *
    * @param workingDir The directory in which to create the files.
    * @param fileName   The prefix of the file name.
    * @param roIds      The ROs to create files for.
    *
    * @return A map from RO id to the corresponding file.
    */
  def buildFilesForRos(workingDir: String, fileName: String, roIds: List[Integer]): Map[Integer, File] = {
    Map() ++ roIds.map(r => (r, new File(workingDir, fileName + "." + r.toString)))
  }

  /**
    * Adds a list of JavaRunnables to the job manager as a batch.
    */
  def addJavaRunnableBatch(name: String, runnables: List[JavaRunnable]): Unit = {
    val jobs = runnables.map(runnable => JavaJobWrapper.wrapJavaFunction(name, runnable))
    headerJob.thenRunBatch(jobs)
  }

  object StartingPoints extends Enumeration {
    type StartingPoints = Value

    val EXPANSION = Value("EXPANSION")
    val LCMS = Value("LCMS")
    val CLUSTERING = Value("CLUSTERING")
    val SCORING = Value("SCORING")
  }
}
