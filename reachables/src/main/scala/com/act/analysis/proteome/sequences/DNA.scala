package com.act.analysis.proteome.sequences


/**
  * Defines a sequence of DNA and all the traits that come with it
  */
object DNA {
  // DNA -> RNA conversion
  private val characterConversions = Map(('a', 'u'), ('c', 'g'), ('t', 'a'), ('g', 'c'))
  private val characterConversionsComplement = Map(('a', 'a'), ('g', 'g'), ('t', 'u'), ('c', 'c'))

  // Complement is default true because this is how most files are supplied
  def translate(sequence: String, complement: Boolean = true): String = {
    require(sequence.length > 0, message = "Sequence must be of a length of at least 1.")
    val charArray = sequence.toList

    // Return based on if the complement was desired or not,
    if (complement) charArray.map(x => characterConversionsComplement(x)).mkString
    else charArray.map(x => characterConversions(x)).mkString
  }
}
