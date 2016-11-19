package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import java.io.File

import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.l2expansion.sparkprojectors.BasicSparkROProjector
import org.apache.commons.cli.{CommandLine, Option => CliOption}
import org.apache.log4j.LogManager

import scala.collection.JavaConverters._

trait ReadFromSubstrateFile extends BasicSparkROProjector {
  val OPTION_SUBSTRATES_LISTS: String
  private val LOGGER = LogManager.getLogger(getClass)

  final def getInputCommandLineOptions: List[CliOption.Builder] = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_SUBSTRATES_LISTS).
        required(true).
        hasArgs.
        valueSeparator(',').
        longOpt("substrates-list").
        desc("A list of substrate InChIs onto which to project ROs"))

    options
  }

  final def getInputMolecules(cli: CommandLine): Stream[Stream[String]] = {
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
    combinationList(inchiLists.map(_.toStream).toStream)
  }
}
