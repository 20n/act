package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait BasicProjectorInput {
  def getInputCommandLineOptions: List[CliOption.Builder]

  def getInputMolecules(cli: CommandLine): Stream[Stream[String]]

  final def combinationList(suppliedInchiLists: Stream[Stream[String]]): Stream[Stream[String]] = {
    /**
      * Small utility function that takes in a streams and creates combinations of members of the multiple streams.
      * For example, with two input streams of InChIs we would construct a new stream containing all the
      * possible streams of size 2, where one element comes from each initial stream.
      */
    if (suppliedInchiLists.isEmpty) Stream(Stream.empty)
    else suppliedInchiLists.head.flatMap(i => combinationList(suppliedInchiLists.tail).map(i #:: _))
  }
}
