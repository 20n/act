package com.act.analysis.proteome.files

trait Hmm {
  object HmmHeaderDesignations  {
    sealed case class Designation(value: String) {
      override def toString: String = value
    }

    object Name extends Designation("NAME")
    object Pfam extends Designation("ACC")
    object Description extends Designation("DESC")
    object Length extends Designation("LENG")

    def withName(name: String): Option[Designation] = {
      names.find(n => n.value.equals(name))
    }
    private val names: List[Designation] = List(Name, Pfam, Description, Length)
  }
}
