package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

sealed trait Keyword {
  def value: String

  override def toString: String = value
}

object MongoKeywords {

  case object ID extends Keyword {
    val value = "_id"
  }

  // General Use
  case object EXISTS extends Keyword {
    val value = "$exists"
  }

  case object NOT extends Keyword {
    val value = "$not"
  }

  case object OR extends Keyword {
    val value = "$or"
  }

  case object AND extends Keyword {
    val value = "$and"
  }

  case object IN extends Keyword {
    val value = "$in"
  }

  case object REGEX extends Keyword {
    val value = "$regex"
  }

  // Aggregation
  case object MATCH extends Keyword {
    val value = "$match"
  }

  case object UNWIND extends Keyword {
    val value = "$unwind"
  }

  case object GROUP extends Keyword {
    val value = "$group"
  }

  case object PUSH extends Keyword {
    val value = "$push"
  }
}

object SequenceKeywords {

  case object ECNUM extends Keyword {
    val value = "ecnum"
  }

  case object SEQ extends Keyword {
    val value = "seq"
  }

  case object METADATA extends Keyword {
    val value = "metadata"
  }

  case object NAME extends Keyword {
    val value = "name"
  }

  case object ID extends Keyword {
    val value = "_id"
  }

  case object RXN_REFS extends Keyword {
    val value = "rxn_refs"
  }

  case object ORGANISM_NAME extends Keyword {
    val value = "org"
  }

}

object ReactionKeywords {

  case object ECNUM extends Keyword {
    val value = "ecnum"
  }

  case object ID extends Keyword {
    val value = "_id"
  }

  case object MECHANISTIC_VALIDATOR extends Keyword {
    val value = "mechanistic_validator_result"
  }

  case object PROTEINS extends Keyword {
    val value = "proteins"
  }

  case object KM extends Keyword {
    val value = "km"
  }

  case object VALUE extends Keyword {
    val value = "val"
  }

  case object SUBSTRATES extends Keyword {
    val value = "substrates"
  }

  case object PRODUCTS extends Keyword {
    val value = "products"
  }

  case object ENZ_SUMMARY extends Keyword {
    val value = "enz_summary"
  }

  case object PUBCHEM extends Keyword {
    val value = "pubchem"
  }

  case object COEFFICIENT extends Keyword {
    val value = "coefficient"
  }

}

object ChemicalKeywords {

  case object ID extends Keyword {
    val value = "_id"
  }

  case object INCHI extends Keyword {
    val value = "InChI"
  }

  case object SMILES extends Keyword {
    val value = "SMILES"
  }
}
