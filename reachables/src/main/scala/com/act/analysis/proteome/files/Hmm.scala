package com.act.analysis.proteome.files

/**
  * Created by michaellampe on 7/6/16.
  */
trait Hmm {

  object HmmHeaderDesignations extends Enumeration {
    type HmmHeaderDesignation = Value
    val Name = Value("NAME")
    val Pfam = Value("ACC")
    val Description = Value("DESC")
    val Length = Value("LENG")
  }

}
