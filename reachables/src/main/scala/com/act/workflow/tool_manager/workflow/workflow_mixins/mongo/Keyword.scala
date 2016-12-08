package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

sealed case class Keyword(keyword: String) {
  def value: String = keyword

  override def toString: String = value
}

object MongoKeywords {
  object ID extends Keyword("_id")

  // General Use
  object EXISTS extends Keyword("$exists")
  object NOT extends Keyword("$not")
  object OR extends Keyword("$or")
  object AND extends Keyword("$and")
  object IN extends Keyword("$in")
  object REGEX extends Keyword("$regex")
  object NOT_EQUAL extends Keyword("$ne")
  object WHERE extends Keyword("$where")

  // Aggregation
  object MATCH extends Keyword("$match")
  object UNWIND extends Keyword("$unwind")
  object GROUP extends Keyword("$group")
  object PUSH extends Keyword("$push")
}

object SequenceKeywords {
  object ECNUM extends Keyword("ecnum")
  object SEQ extends Keyword("seq")
  object METADATA extends Keyword("metadata")
  object NAME extends Keyword("name")
  object ID extends Keyword("_id")
  object RXN_REFS extends Keyword("rxn_refs")
  object ORGANISM_NAME extends Keyword("org")
}

object ReactionKeywords {
  object ECNUM extends Keyword("ecnum")
  object ID extends Keyword("_id")
  object MECHANISTIC_VALIDATOR extends Keyword("mechanistic_validator_result")
  object PROTEINS extends Keyword("proteins")
  object KM extends Keyword("km")
  object VALUE extends Keyword("val")
  object SUBSTRATES extends Keyword("substrates")
  object PRODUCTS extends Keyword("products")
  object ENZ_SUMMARY extends Keyword("enz_summary")
  object PUBCHEM extends Keyword("pubchem")
  object ORGANISM extends Keyword("organism")
  object COEFFICIENT extends Keyword("coefficient")
}

object ChemicalKeywords {
  object ID extends Keyword("_id")
  object INCHI extends Keyword("InChI")
  object SMILES extends Keyword("SMILES")
}
