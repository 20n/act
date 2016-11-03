package com.act.analysis.proteome.files

trait Hmm {
  object HmmHeaderDesignations  {
    sealed abstract class Designation(val value: String) {
      override def toString: String = value
    }

    case object Name extends Designation("NAME")
    case object Pfam extends Designation("ACC")
    case object Description extends Designation("DESC")
    case object Length extends Designation("LENG")

    def withName(name: String): Option[Designation] = {
      names.find(n => n.value.equals(name))
    }
    private val names: List[Designation] = List(Name, Pfam, Description, Length)
  }
}
