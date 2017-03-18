/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import java.io.File

import com.act.biointerpretation.l2expansion.L2InchiCorpus
import org.apache.commons.cli.{CommandLine, Option => CliOption}

import scala.collection.JavaConverters._

trait ReadFromSubstrateFile extends BasicProjectorInput {
  val OPTION_SUBSTRATES_LISTS: String

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
