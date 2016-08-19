package com.act.biointerpretation

import java.io.{File, IOException}
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
  private val OPTION_LCMS_INPUT = "l"
  private val OPTION_STARTING_POINT = "S"
  private val OPTION_ENDING_POINT = "E"

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
        required(false).
        hasArg.
        longOpt("substrates").
        desc("A filepath to a file containing the substrate inchis on which to project the ROs. If no path is " +
          "supplied, but steps are run which require substrates, the workflow searches for the file of name " +
          "raw_substrates in the working directory.  "),

      CliOption.builder(OPTION_LCMS_INPUT).
        required(false).
        hasArg.
        longOpt("lcms-input-file").
        desc("A filepath to a file containing the LCMS results as a json IonAnalysisInterchangeModel object. If no " +
          "path is supplied, but steps are run which require an LCMS file, the workflow searches for a file of name" +
          "lcms_results in the working directory."),

      CliOption.builder(OPTION_RO_IDS).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("ro_ids").
        desc("A filepath to a file containing the RO ids to use, one per line, or a comma separated list of RO IDs."),

      CliOption.builder(OPTION_MASS_THRESHOLD).
        required(false).
        hasArg.
        longOpt("mass-threshold").
        desc("The maximum mass of a substrate to be processed, in daltons."),

      CliOption.builder(OPTION_STARTING_POINT).
        required(false).
        hasArg.
        longOpt("starting-point")
        .desc("What point of the workflow to start at, since some steps may already be complete. Choices are " +
          "EXPANSION, LCMS, CLUSTERING, SAR_SCORING, PRODUCT_SCORING, and MESH_RESULTS. The workflow assumes any " +
          "pre-computed steps have been put into the working directory, where they would have been generated had " +
          "the workflow been ran from the start. For example, precomputed prediction corpuses should be located at " +
          "workingDir/predictions.1, workingDir/predictions.2, etc."),

      CliOption.builder(OPTION_ENDING_POINT).
        required(false).
        hasArg.
        longOpt("ending-point")
        .desc("What point of the workflow to end at. Handled similarly to starting point. Default is to run until " +
          "the last step, namely meshing the results."),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  object WorkflowSteps extends Enumeration {
    type WorkflowSteps = Value

    val EXPANSION = Value("EXPANSION")
    val LCMS = Value("LCMS")
    val CLUSTERING = Value("CLUSTERING")
    val SAR_SCORING = Value("SAR_SCORING")
    val PRODUCT_SCORING = Value("PRODUCT_SCORING")
    val MESH_RESULTS = Value("MESH_RESULTS")
  }

  // Implement this with the job structure you want to run to define a workflow
  override def defineWorkflow(cl: CommandLine): Job = {

    /**
      * Handle command line args and create files
      */
    val workingDir = cl.getOptionValue(OPTION_WORKING_DIRECTORY, null)
    val directory: File = new File(workingDir)
    directory.mkdirs()

    var rawSubstratesFile: File = null
    if (cl.hasOption(OPTION_SUBSTRATES)) {
      rawSubstratesFile = new File(cl.getOptionValue(OPTION_SUBSTRATES))
    } else {
      logger.info("Defaulting to look for substrates at workingDir/raw_substrates if needed.")
      rawSubstratesFile = new File(workingDir, "raw_substrates")
    }

    /**
      * Parse command line option for RO IDs as either a list of Ids, or a file.
      */
    var roIds: List[Integer] = null
    var roIdFile: File = null
    val roOptions: Array[String] = cl.getOptionValues(OPTION_RO_IDS)
    if (roOptions.length == 1) {
      // Try to read RO ID argument as file if possible
      try {
        roIdFile = new File(cl.getOptionValue(OPTION_RO_IDS))
        val erosCorpus = new ErosCorpus()
        erosCorpus.loadValidationCorpus()
        roIds = erosCorpus.getRoIdListFromFile(roIdFile).asScala.toList
      } catch {
        // If not a file, assume a list.
        case e: IOException => {
          roIds = roOptions.toList.map(arg => new Integer(arg))
        }
      }
    } else {
      // If there are multiple arguments, assume it's a list of IDs.
      roIds = roOptions.toList.map(arg => new Integer(arg))
    }
    logger.info(s"ROs to use: $roIds")

    val filteredSubstratesFile = new File(workingDir, "filtered_substrates")

    val predictionsFilename = "predictions"
    val predictionsFiles = buildFilesForRos(workingDir, predictionsFilename, roIds)

    val sarTreeFilename = "sartree"
    val sarTreeFiles = buildFilesForRos(workingDir, sarTreeFilename, roIds)

    val scoredSarsFilename = "scored_sars"
    val scoredSarsFiles = buildFilesForRos(workingDir, scoredSarsFilename, roIds)

    val scoredProductsFilename = "scored_products"
    val scoredProductsFiles = buildFilesForRos(workingDir, scoredProductsFilename, roIds)

    var lcmsFile: File = null
    if (cl.hasOption(OPTION_LCMS_INPUT)) {
      lcmsFile = new File(cl.getOptionValue(OPTION_LCMS_INPUT))
    } else {
      logger.info("Defaulting to look for lcms results at workingDir/lcms_results if needed.")
      lcmsFile = new File(workingDir, "lcms_results")
    }

    val allSarsFile = new File(workingDir, "all_scored_sars")
    val allProductsFile = new File(workingDir, "all_scored_products")

    var maxMass = Integer.MAX_VALUE
    if (cl.hasOption(OPTION_MASS_THRESHOLD)) {
      maxMass = Integer.parseInt(cl.getOptionValue(OPTION_MASS_THRESHOLD))
    }

    /**
      * Define all jobs
      */
    def addExpansionJobList()(): Unit = {
      verifyInputFile(rawSubstratesFile)
      verifyInputFile(roIdFile)

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
    }

    def addLcmsJob()(): Unit = {
      logger.info("Running LCMS job.")

      // TODO: eventually link this up with LCMS scoring job, once it is completely automated
      // Build a dummy LCMS job that crashes if you try to run it.
      val lcmsJob = JavaJobWrapper.wrapJavaFunction( "DUMMY_LCMS_JOB",
        new JavaRunnable {
          override def run(): Unit = {
            throw new NotImplementedError("LCMS JOB NOT YET IMPLEMENTED!")
          }
        }
      )
      headerJob.thenRun(lcmsJob)
    }

    def addClusteringJobs()(): Unit = {
      logger.info("Adding clustering jobs.")
      // TODO: when Michael adds the capability, change this workflow to run the clustering jobs and LCMS job in parallel
      // Build one job per RO for clustering
      val clusteringRunnables =
        roIds.map(roId =>
          LibMcsClustering.getClusterer(
            predictionsFiles(roId),
            sarTreeFiles(roId)))
      val batchSize = 1; // To limit memory usage
      addJavaRunnableBatch("cluster", clusteringRunnables, batchSize)
    }

    def addSarScoringJobs()(): Unit = {
      logger.info("Adding sar scoring job.")
      verifyInputFile(lcmsFile)

      // Build one job per RO for sar scoring, using a random set of LCMS hits instead of actual data.
      val sarScoringRunnables = roIds.map(roId =>
        LibMcsClustering.getSarScorer(
          predictionsFiles(roId),
          sarTreeFiles(roId),
          lcmsFile,
          scoredSarsFiles(roId),
          LCMS_MISS_PENALTY,
          SUBTREE_THRESHOLD)
      )
      addJavaRunnableBatch("sarScoring", sarScoringRunnables)
    }

    def addProductScoringJobs()(): Unit = {
      logger.info("Adding  product scoring job.")
      verifyInputFile(lcmsFile)

      val productScoringRunnables = roIds.map(roId =>
        ProductScorer.getProductScorer(
          predictionsFiles(roId),
          scoredSarsFiles(roId),
          lcmsFile,
          scoredProductsFiles(roId)
        ))
      addJavaRunnableBatch("productScoring", productScoringRunnables);

    }

    def addMeshResultsJob()(): Unit = {
      logger.info("Adding mesh results job.")
      val meshJob = ScalaJobWrapper.wrapScalaFunction("mesh_results",
        meshResults(
          scoredSarsFiles.values.toList,
          scoredProductsFiles.values.toList,
          allSarsFile,
          allProductsFile) _)
      headerJob.thenRun(meshJob)
    }

    /**
      * Decide which steps to run based on the StartingPoint supplied, or run the entire workflow by default
      */
    def getStepFromCommandline(optionVal: String, defaultStep: WorkflowSteps.Value): WorkflowSteps.Value = {
      if (cl.hasOption(optionVal)) {
        try {
          return WorkflowSteps.withName(cl.getOptionValue(optionVal))
        } catch {
          case e: NoSuchElementException =>
            logger.error(s"Workflow  point must be among ${WorkflowSteps.values.map(value => value.toString()).toList} " +
              s"; Input: ${cl.getOptionValue(optionVal)}; ${e.getMessage()}")
            System.exit(1)
        }
      }
      defaultStep
    }

    val firstStep = getStepFromCommandline(OPTION_STARTING_POINT, WorkflowSteps.EXPANSION)
    val lastStep = getStepFromCommandline(OPTION_ENDING_POINT, WorkflowSteps.MESH_RESULTS)
    logger.info(s"First step: $firstStep. Last step: $lastStep")

    /**
      * Pick out the correct steps to run, and add them to the workflow.
      */
    val jobList: List[(WorkflowSteps.Value, () => Unit)] = List(
      (WorkflowSteps.EXPANSION, addExpansionJobList() _),
      (WorkflowSteps.LCMS, addLcmsJob() _),
      (WorkflowSteps.CLUSTERING, addClusteringJobs() _),
      (WorkflowSteps.SAR_SCORING, addSarScoringJobs() _),
      (WorkflowSteps.PRODUCT_SCORING, addProductScoringJobs() _),
      (WorkflowSteps.MESH_RESULTS, addMeshResultsJob() _))

    // Get subset of jobs to run.
    // The first element of each entry in the list is the step it corresponds to.
    val startIndex = jobList.indexWhere(a => a._1.equals(firstStep))
    val endIndex = jobList.indexWhere(a => a._1.equals(lastStep))
    val jobsToRun = jobList.slice(startIndex, endIndex + 1)

    // The second element of each entry in the list is the function to call.
    jobsToRun.foreach(jobListEntry => jobListEntry._2())

    headerJob
  }


  /**
    * Defines a job that meshes the results from the sar scoring and product scoring into single files,
    * containing sars/products ranked over all ROs.
    * TODO: sort the summary product file. Annoying because the scores are hidden in the predictions' names.
    *
    * @param scoredSarFiles     Input sars files.
    * @param scoredProductFiles Input product files.
    * @param sarOut             Output summary sar file, sorted.
    * @param productOut         Output summary product file, unsorted.
    */
  def meshResults(scoredSarFiles: List[File], scoredProductFiles: List[File],
                  sarOut: File, productOut: File)(): Unit = {
    val sarLists = scoredSarFiles.map(file => {
      var list = new SarTreeNodeList
      list.loadFromFile(file)
      list
    })
    val meshedList = new SarTreeNodeList()
    sarLists.foreach(list => meshedList.addAll(list.getSarTreeNodes))
    meshedList.sortByDecreasingScores()
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
  def addJavaRunnableBatch(name: String, runnables: List[JavaRunnable], batchSize: Int = 20): Unit = {
    val jobs = runnables.map(runnable => JavaJobWrapper.wrapJavaFunction(name, runnable))
    headerJob.thenRunBatch(jobs, batchSize)
  }

  object StartingPoints extends Enumeration {
    type StartingPoints = Value

    val EXPANSION = Value("EXPANSION")
    val LCMS = Value("LCMS")
    val CLUSTERING = Value("CLUSTERING")
    val SCORING = Value("SCORING")
  }
}
