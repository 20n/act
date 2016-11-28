package com.act.reachables

import act.server.MongoDB
import act.shared.Reaction
import act.shared.Reaction.RxnDataSource
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConversions._
import scalaz.Memo

object ReachRxnDescs {
  // Only needed during cascades information dump So load post-reachables
  // computation. Not when reading reactions.  Also, only needed for
  // reactions that eventually make it the reachables computation; not
  // everything.

  val db: MongoDB = new MongoDB(cascades.DEFAULT_DB._1, cascades.DEFAULT_DB._2, cascades.DEFAULT_DB._3)

  val meta = Memo.mutableHashMapMemo[Long, Option[Reaction]] { rid => Option(cascades.get_reaction_by_UUID(db, rid)) }

  val rxnEasyDesc = Memo.mutableHashMapMemo[Long, Option[String]] { rid =>
    if (meta(rid).isDefined) {
      Option(meta(rid).get.getReactionName)
    } else {
      None
    }
  }

  val rxnECNumber = Memo.mutableHashMapMemo[Long, Option[String]] { rid =>
    if (meta(rid).isDefined) {
      Option(meta(rid).get.getECNum)
    } else {
      None
    }
  }

  val rxnDataSource = Memo.mutableHashMapMemo[Long, Option[RxnDataSource]] { rid =>
    if (meta(rid).isDefined) {
      Option(meta(rid).get.getDataSource)
    } else {
      None
    }
  }

  // TODO @Mark add organisms here.

}

class ReachRxn(rid: Long, reachables: Set[Long]) {
  val rxnid = rid
  val substrates = ActData.instance.rxnSubstrates.get(rid)
  val products = ActData.instance.rxnProducts.get(rid)
  val substratesCofactors = ActData.instance.rxnSubstratesCofactors.get(rid)
  val productsCofactors = ActData.instance.rxnProductsCofactors.get(rid)

  // this reaction is "reachable" if all its non-cofactor substrates
  // are in the reachables set
  val isreachable = substrates forall (s => reachables contains s)

  def describe() = ReachRxnDescs.rxnEasyDesc(rxnid)

  def getReferencedChems() = substrates ++ products // Set[Long] of all substrates and products

  override def toString() = "rxnid:" + rid

  def json() = {
    // this json is just the basic information elaborating on how
    // this rxn featured in the reachables calculations. the entire
    // gamut of information about the rxn can be located by pulling
    // up the Reaction object (or its associated json in the front end)
    // from the rxnid.
    // Please do not dump all of that information into this object
    // as this object will go into updowns for many reachables
    val json = new JSONObject
    json.put("rxnid", rxnid)
    json.put("reachable", isreachable)
    json.put("substrates", new JSONArray(substrates))
    json.put("products", new JSONArray(products))
    json
  }
}
