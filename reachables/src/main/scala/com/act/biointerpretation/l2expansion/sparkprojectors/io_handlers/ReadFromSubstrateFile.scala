package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import java.io.File

import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.l2expansion.sparkprojectors.BasicSparkROProjector
import org.apache.commons.cli.{CommandLine, Option => CliOption}
import org.apache.log4j.LogManager

trait ReadFromSubstrateFile extends BasicSparkROProjector {
  private val LOGGER = LogManager.getLogger(getClass)

  abstract val OPTION_SUBSTRATES_LISTS: String

  final def getValidInchiCommandLineOptions: List[CliOption.Builder] = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_SUBSTRATES_LISTS).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("substrates-list").
        desc("A list of substrate InChIs onto which to project ROs"))

    options
  }

  final def getValidInchis(cli: CommandLine): Stream[Stream[String]] = {
    inchiSourceFromFiles(getSubstrateFileList(cli))
  }

  private def getSubstrateFileList(cli: CommandLine): List[File] ={
    cli.getOptionValues(OPTION_SUBSTRATES_LISTS).toList.map(f => new File(f))
  }

  private def inchiSourceFromFiles(fileNames: List[File]): Stream[Stream[String]] = {
    val substrateCorpuses: List[L2InchiCorpus] = fileNames.map(x => {
      val inchiCorpus = new L2InchiCorpus()
      inchiCorpus.loadCorpus(x)
      inchiCorpus
    })

    inchiSourceFromCorpuses(substrateCorpuses)
  }

  private def inchiSourceFromCorpuses(inchiCorpuses: List[L2InchiCorpus]): Stream[Stream[String]] = {
    // List of all unique InChIs in each corpus
    val inchiLists: List[List[String]] = inchiCorpuses.map(_.getInchiList.asScala.distinct.toList)

    // List of combinations of InChIs
    val inchiCombinations: Stream[Stream[String]] = combinationList(inchiLists.map(_.toStream).toStream)


    // TODO Move this filtering into combinationsList so that it is lazily evaluated as we need the elements.
    LOGGER.info("Attempting to filter out combinations with invalid InChIs.  " +
      s"Starting with ${inchiCombinations.length} inchis.")
    val validInchis: Stream[Stream[String]] = inchiCombinations.filter(group => {
      try {
        group.foreach(inchi => {
          MoleculeImporter.importMolecule(inchi)
        })
        true
      } catch {
        case e: Exception => false
      }
    })
    LOGGER.info(s"Filtering removed ${inchiCombinations.length - validInchis.length}" +
      s" combinations, ${validInchis.length} remain.")

    validInchis
  }

  private def combinationList(suppliedInchiLists: Stream[Stream[String]]): Stream[Stream[String]] = {
    if (suppliedInchiLists.isEmpty) Stream(Stream.empty)
    else suppliedInchiLists.head.flatMap(i => combinationList(suppliedInchiLists.tail).map(i #:: _))
  }
}
