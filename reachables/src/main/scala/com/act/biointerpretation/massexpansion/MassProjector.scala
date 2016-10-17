package com.act.biointerpretation.massexpansion

import chemaxon.sss.SearchConstants
import chemaxon.sss.search.{MolSearch, MolSearchOptions}
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db.ReactionsToSubstratesAndProducts
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByRo

import scala.collection.JavaConversions._

object MassProjector {
  def main(args: Array[String]) {

    val corpy = new ErosCorpus()
    corpy.loadValidationCorpus()

    val eros = corpy.getRos.toList

    val firstRoReaction = eros.head.getReactor.getReaction

    val firstRoSubstrates = firstRoReaction.getReactants.toList
    val firstRoProducts = firstRoReaction.getProducts.toList

    val searcher = new MolSearch
    searcher.setSearchOptions(new MolSearchOptions(SearchConstants.SUBSTRUCTURE))

    searcher.setQuery(firstRoSubstrates.head)

    val m = Mongo.connectToMongoDatabase()

    val reactionIds = Mongo.queryReactionsForReactionIdsByRo(List(eros.head.getId.toString), m)

    val reactions = Mongo.querySubstrateAndProductMoleculesByReactionIds(m)(reactionIds.keys.toList)
    val Inchisreactions = Mongo.querySubstrateAndProductInchisByReactionIds(m)(reactionIds.keys.toList)
  }

  object Mongo extends QueryByRo with ReactionsToSubstratesAndProducts {}
}
