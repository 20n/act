package com.act.biointerpretation.rsmiles

import java.io.File

import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.workflow.tool_manager.jobs.Job
import com.act.workflow.tool_manager.tool_wrappers.{ScalaJobWrapper, SparkWrapper}
import com.act.workflow.tool_manager.workflow.Workflow
import org.apache.commons.cli.{CommandLine, Options, Option => CliOption}
import org.apache.log4j.LogManager


class AbstractChemicalsToL3ProjectionWorkflow extends Workflow {

  val OPTION_USE_CACHED_RESULTS = "c"
  val OPTION_DATABASE =           "d"
  val OPTION_METABOLITE_FILE =    "f"
  val OPTION_CHEMAXON_LICENSE =   "l"
  val OPTION_SPARK_MASTER =       "m"
  val OPTION_SUBSTRATE_COUNTS =   "s"
  val OPTION_VALID_CHEMICAL_TYPE ="v"
  val OPTION_WORKING_DIRECTORY =  "w"

  private val LOGGER = LogManager.getLogger(getClass)

  override def getCommandLineOptions: Options = {
    val options = List[CliOption.Builder](

      CliOption.builder(OPTION_DATABASE).
        hasArg.
        longOpt("database").
        desc("The database to connect to.  This is where we will find the abstract chemicals and reactions.  " +
          "By default uses the \"marvin\" database."),

      CliOption.builder(OPTION_WORKING_DIRECTORY).
        hasArg.
        longOpt("working-directory").
        required.
        desc("The directory in which to run and create all intermediate files. This directory will be created if it " +
          "does not already exist."),

      CliOption.builder(OPTION_SUBSTRATE_COUNTS).
        hasArgs.
        valueSeparator(',').
        longOpt("substrate-counts").
        required.
        desc("A list of numbers.  This list will inform which reactions will be written to a file by " +
          "filtering the reactions by substrate.  For example, \"1,2\" would mean that 1 and 2 " +
          "substrate reactions will be written to a file."),

      CliOption.builder(OPTION_CHEMAXON_LICENSE).
        longOpt("chemaxon-license-file").
        hasArg.
        required.
        desc("Location of the \"license_Start-up.cxl\" file."),

      CliOption.builder(OPTION_METABOLITE_FILE)
        .hasArg
        .longOpt("metabolite-file")
        .required
        .desc("The absolute path to the metabolites file."),

      CliOption.builder(OPTION_SPARK_MASTER).
        longOpt("spark-master").
        desc("Where to look for the spark master connection. " +
          "Uses \"spark://10.0.20.19:7077\" by default."),

      CliOption.builder(OPTION_USE_CACHED_RESULTS).
        longOpt("use-cached-results").
        desc("If this flag is enabled, we will check if the file that would be " +
          "made currently exists and use that file wherever possible."),

      CliOption.builder(OPTION_VALID_CHEMICAL_TYPE).
        longOpt("valid-chemical-types").
        hasArg.
        desc("A molecule string format. Currently valid types are inchi, stdInchi, smiles, and smarts.  " +
          s"By default, uses noStereoAromatizedSmarts which " +
          s"is the format '${MoleculeFormat.getExportString(MoleculeFormat.noStereoAromatizedSmarts)}'.  " +
          s"Possible values are: \n${MoleculeFormat.listPossibleFormats().mkString("\n")}"),

      CliOption.builder("h").argName("help").desc("Prints this help message").longOpt("help")
    )

    val opts: Options = new Options()
    for (opt <- options) {
      opts.addOption(opt.build)
    }
    opts
  }

  override def defineWorkflow(cl: CommandLine): Job = {
    /*
      Setup Files
     */
    val chemaxonLicense = new File(cl.getOptionValue(OPTION_CHEMAXON_LICENSE))
    require(chemaxonLicense.exists() && chemaxonLicense.isFile,
      s"Chemaxon license does not exist as the supplied location. " +
        s"File path supplied was ${chemaxonLicense.getAbsolutePath}")

    val outputDirectory = new File(cl.getOptionValue(OPTION_WORKING_DIRECTORY))
    require(!outputDirectory.isFile, "Working directory must be a directory, not a file.")
    if (!outputDirectory.exists()) outputDirectory.mkdirs()

    val metaboliteFile = new File(cl.getOptionValue(OPTION_METABOLITE_FILE))
    require(metaboliteFile.exists() && metaboliteFile.isFile,
      s"Metabolite file must exist. File path supplied was ${metaboliteFile.getAbsolutePath}")

    /*
     Setup database
     */
    val database = cl.getOptionValue(OPTION_DATABASE, "marvin")


    /*
     Setup Spark
     */
    val singleSubstrateRoProjectorClassPath = "com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector"
    val sparkMaster = cl.getOptionValue(OPTION_SPARK_MASTER, "spark://10.0.20.19:7077")
    // Tries to assemble JAR for spark export.  Step 1 towards Skynet is self-assembly of jar files.
    headerJob.thenRun(SparkWrapper.sbtAssembly(useCached = cl.hasOption(OPTION_USE_CACHED_RESULTS)))

    /*
      Format currently used for the molecular transitions
     */
    val moleculeFormatString =
      cl.getOptionValue(OPTION_VALID_CHEMICAL_TYPE, MoleculeFormat.noStereoAromatizedSmarts.toString)
    val moleculeFormat = MoleculeFormat.withName(moleculeFormatString)

    /*
      Setup the options for which substrate counts
      we'll be looking at and partially applies the abstract reaction function.
     */
    val substrateCounts: List[Int] = cl.getOptionValues(OPTION_SUBSTRATE_COUNTS).map(_.toInt).toList
    val individualSubstrateFunction = AbstractChemicalsToReactions.calculateAbstractSubstrates(moleculeFormat)(database)_

    // Create all the jobs for all the substrates
    val jobs = substrateCounts.map(count => {
      val uniqueId = s"db.$database.subCount.$count.format.$moleculeFormatString"

      /*
        Step 1: Abstract chemicals => Abstract reactions substrate list
       */
      val substratesOutputFileName = s"$uniqueId.Substrates.txt"
      val reactionsOutputFileName = s"$uniqueId.txt"

      val substrateListOutputFile = new File(outputDirectory, substratesOutputFileName)
      val reactionListOutputFile = new File(outputDirectory, reactionsOutputFileName)

      val appliedFunction: () => Unit = individualSubstrateFunction(substrateListOutputFile, reactionListOutputFile, count)

      val abstractChemicalsToSubstrateListJob = if (cl.hasOption(OPTION_USE_CACHED_RESULTS) && substrateListOutputFile.exists()) {
        LOGGER.info(s"Using cached file ${substrateListOutputFile.getAbsolutePath}")
        ScalaJobWrapper.wrapScalaFunction("Using cached substrate list", () => Unit)
      } else {
        ScalaJobWrapper.wrapScalaFunction("Abstract chemicals to substrate list", appliedFunction)
      }

      /*
        Step 2: Spark submit substrate list => RO projection
       */
      val projectionDir = new File(outputDirectory, "ProjectionResults")
      if (!projectionDir.exists()) projectionDir.mkdirs()

      val roProjectionsOutputFileDirectory = new File(projectionDir, s"$uniqueId.AbstractReactionRoProjections")
      if (!roProjectionsOutputFileDirectory.exists()) roProjectionsOutputFileDirectory.mkdirs()

      val roProjectionArgs = List(
        "--substrates-list", substrateListOutputFile.getAbsolutePath,
        "-o", roProjectionsOutputFileDirectory.getAbsolutePath,
        "-l", chemaxonLicense.getAbsolutePath,
        "-v", moleculeFormat.toString
      )

      // We assume files in = previous run
      val hasCachedResultsAbstractRoProjection = roProjectionsOutputFileDirectory.list().length > 0
      val sparkRoProjection = if (cl.hasOption(OPTION_USE_CACHED_RESULTS) && hasCachedResultsAbstractRoProjection) {
        ScalaJobWrapper.wrapScalaFunction("Using cached spark abstract reaction RO projections", () => Unit)
      } else {
        SparkWrapper.runClassPath(
          singleSubstrateRoProjectorClassPath,
          sparkMaster,
          roProjectionArgs,
          memory = "8G",
          cores = 1
        )
      }

      abstractChemicalsToSubstrateListJob.thenRun(sparkRoProjection)

      /*
        Step 3: Spark submit match projections to input reactions
       */
      val roAssignmentDirectory = new File(outputDirectory, "RoAssignment")
      if (!roAssignmentDirectory.exists()) roAssignmentDirectory.mkdirs()

      val roAssignmentOutputFileName = new File(roAssignmentDirectory, s"$uniqueId.RoAssignments.json")

      val reactionAssignJob = if (cl.hasOption(OPTION_USE_CACHED_RESULTS) && roAssignmentOutputFileName.exists()) {
        ScalaJobWrapper.wrapScalaFunction("Using cached ro assignments", () => Unit)
      } else {
        val reactionAssigner = ReactionRoAssignment.assignRoToReactions(roProjectionsOutputFileDirectory, reactionListOutputFile, roAssignmentOutputFileName) _
        ScalaJobWrapper.wrapScalaFunction("Ro Assignment to Reactions", reactionAssigner)
      }

      abstractChemicalsToSubstrateListJob.thenRun(reactionAssignJob)
      /*
        Step 4: Construct SARs from matching reactions
       */
      val sarCorpusDirectory = new File(outputDirectory, "SarCorpus")
      if (!sarCorpusDirectory.exists()) sarCorpusDirectory.mkdirs()
      val sarCorpusOutputFileName = s"$uniqueId.sarCorpusOutput.json"
      val sarCorpusOutputFile = new File(sarCorpusDirectory, sarCorpusOutputFileName)
      val constructSars = ConstructSarsFromAbstractReactions.sarConstructor(
        roAssignmentOutputFileName, sarCorpusOutputFile, moleculeFormat) _



      val constructedSarJob =
        if (cl.hasOption(OPTION_USE_CACHED_RESULTS) && sarCorpusOutputFile.exists()) {
          ScalaJobWrapper.wrapScalaFunction("Using cached SAR corpus.", () => Unit)
      } else {
        ScalaJobWrapper.wrapScalaFunction("Sar Constructor", constructSars)
      }

      abstractChemicalsToSubstrateListJob.thenRun(constructedSarJob)

      /*
        Step 5: Project RO + SAR over L3

        Don't cache this step as it is the last one and would make everything pointless otherwise.
       */
      val l3ProjectionOutputDirectory = new File(outputDirectory, "L3Projections")
      if (!l3ProjectionOutputDirectory.exists()) l3ProjectionOutputDirectory.mkdirs()
      val l3ProjectionArgs = List(
        "--substrates-list", metaboliteFile.getAbsolutePath,
        "-o", l3ProjectionOutputDirectory.getAbsolutePath,
        "-l", chemaxonLicense.getAbsolutePath,
        "-v", moleculeFormat.toString,
        "-c", sarCorpusOutputFile.getAbsolutePath
      )

      val l3RoPlusSarProjection = SparkWrapper.runClassPath(
        singleSubstrateRoProjectorClassPath,
        sparkMaster,
        l3ProjectionArgs,
        memory = "4G",
        cores = 1
      )
      abstractChemicalsToSubstrateListJob.thenRun(l3RoPlusSarProjection)


      abstractChemicalsToSubstrateListJob
    })

    headerJob.thenRunBatch(jobs)
  }
}
