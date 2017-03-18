/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.reachables

import act.server.MongoDB
import act.shared.Reaction
import act.shared.Reaction.RxnDataSource
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, SequenceKeywords}
import com.mongodb.BasicDBObject
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scalaz.Memo

object ReachRxnDescs {
  // Only needed during cascades information dump So load post-reachables
  // computation. Not when reading reactions.  Also, only needed for
  // reactions that eventually make it the reachables computation; not
  // everything.

  lazy val db: MongoDB = new MongoDB(cascades.DEFAULT_DB._1, cascades.DEFAULT_DB._2, cascades.DEFAULT_DB._3)

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

  val rxnIsSpontaneous = Memo.mutableHashMapMemo[Long, Option[Boolean]] { rid =>
    if (meta(rid).isDefined) {
      val referenceOrganisms: Boolean = meta(rid).get.getReferences.toList.
        flatMap(x => Option(x.snd())).exists(_.equals("isSpontaneous"))
      Option(referenceOrganisms)
    } else {
      None
    }
  }

  // TODO: cache organism names instead of looking them up in the DB every time.  Use caffeine after a rebase.
  val rxnOrganismNames = Memo.mutableHashMapMemo[Long, Option[Set[String]]] { rid =>
    if (meta(rid).isDefined) {
      // The entry looks like as follows:
      // OrganismId:<Number>
      // We take the second element always as that is the Id
      val referenceOrganisms: List[String] = meta(rid).get.getReferences.toList.
        flatMap(x => Option(x.snd())).
        filter(_.startsWith("OrganismId")).
        map(x => x.split(":")(1).toLong).
        map(id => db.getOrganismNameFromId(id))

      val organisms: List[String] = meta(rid).get.getProteinData.
        map(x => if (x.has("organism")) Option(x.getLong("organism")) else None).
        filter(_.isDefined).map(_.get).
        map(id => db.getOrganismNameFromId(id)).toList
      Option((organisms ::: referenceOrganisms).toSet)
    } else {
      None
    }
  }

  val rxnLiteratureReference = Memo.mutableHashMapMemo[Long, Option[Set[String]]] { rid =>
    if (meta(rid).isDefined) {
      Option(meta(rid).get.getReferences(Reaction.RefDataSource.PMID).toSet)
    } else {
      None
    }
  }

  val rxnSequence = Memo.mutableHashMapMemo[Long, Option[Set[Long]]] { rid =>
    if (meta(rid).isDefined) {
      val sequences: Set[JSONArray] = meta(rid).get.getProteinData.
        map(x => if (x.has("sequences")) Option(x.getJSONArray("sequences")) else None).
        filter(_.isDefined).map(_.get).toSet



      val sequencesScala: ListBuffer[Long] = ListBuffer[Long]()
      sequences.foreach(s => {
        for (i <- Range(0, s.length)) {
          sequencesScala.append(s.getLong(i))
        }
      })

      // Non null elements
      val q = new BasicDBObject(SequenceKeywords.ID.toString, new BasicDBObject(MongoKeywords.IN.toString, sequencesScala.toSet.asJavaCollection))
      q.put(SequenceKeywords.SEQ.toString, new BasicDBObject(MongoKeywords.NOT_EQUAL.toString, null))
      val nonNullSequences = db.getSeqIterator(q)

      Option(nonNullSequences.map(_.getUUID.toLong: Long).toSet)
    } else {
      None
    }
  }

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
