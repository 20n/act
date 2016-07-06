package com.act.analysis.proteome.sequences


/**
  * Defines a sequence of DNA and all the traits that come with it
  */
object DNA {
  private val characterConversions = Map(('a', 'u'), ('c', 'g'), ('t', 'a'), ('g', 'c'))

  def translate(sequence: String): String = {
    require(sequence.length > 0, message = "Sequence must be of a length of at least 1.")

    val charArray = sequence.toList
    charArray.map(x => characterConversions(x)).mkString
  }
}
