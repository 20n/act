package com.act.analysis.proteome.sequences

/**
  * Defines a sequence of Protein and all the traits that come with it
  */
object Protein {

  def translate(sequence: String): String = {
    throw new UnsupportedOperationException("Translation is not possible for proteins.")
  }
}
