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

package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import act.server.MongoDB
import act.shared.Reaction
import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.biointerpretation.test.util.MockedMongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicals
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class ReactionsToSubstratesAndProductsTest extends FlatSpec with Matchers with TimeLimitedTests with BeforeAndAfterEach {
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 15 seconds
  val chemicals: Map[java.lang.Long, String] = Map(
    // Hexane
    java.lang.Long.valueOf(0L) -> "InChI=1S/C6H14/c1-3-5-6-4-2/h3-6H2,1-2H3",
    // Trimethyl Borane
    java.lang.Long.valueOf(1L) -> "InChI=1S/C3H9B/c1-4(2)3/h1-3H3",
    java.lang.Long.valueOf(2L) -> "InChI=1S/C9H6N2O2/c1-7-2-3-8(10-5-12)4-9(7)11-6-13/h2-4H,1H3"
  )
  val reactions: List[Reaction] = List(
    mockedReaction(0L, List(0L), List(1L)).reaction,
    mockedReaction(1L, List(2L), List(0L, 1L)).reaction,
    mockedReaction(2L, List(2L, 0L), List(1L)).reaction,
    mockedReaction(3L, List(0L), List(1L), 2, 1).reaction,
    mockedReaction(4L, List(0L), List(1L), 1, 2).reaction
  )
  var mockDb: Option[MongoDB] = None

  override def beforeEach(): Unit = {
    val mockAPI = new MockedMongoDB
    mockAPI.installMocks(
      reactions.asJava, List(0L, 1L, 2L).map(java.lang.Long.valueOf).asJava, null, null, chemicals.asJava)
    mockDb = Option(mockAPI.getMockMongoDB)
  }

  override def afterEach(): Unit = {
    QueryChemicals.clearCache()
    MoleculeImporter.clearCache()
    mockDb = None
  }

  sealed case class mockedReaction(id: Long, substrates: List[Long], products: List[Long],
                                   substrateCoefficients: Int = 1, productCoefficients: Int = 1) {
    val reaction = new Reaction(id, substrates.map(java.lang.Long.valueOf).toArray,
      products.map(java.lang.Long.valueOf).toArray, null, null, null, null, null, null, null, null)
    reaction.setAllSubstrateCoefficients(substrates.map(
      s => (java.lang.Long.valueOf(s), java.lang.Integer.valueOf(substrateCoefficients))).toMap.asJava)
    reaction.setAllProductCoefficients(products.map(
      p => (java.lang.Long.valueOf(p), java.lang.Integer.valueOf(productCoefficients))).toMap.asJava)
  }

  "ReactionToSubstratesAndProducts" should "retrieve the InChIs of a single substrate and product reaction." in {
    val inchis: List[Option[ReactionsToSubstratesAndProducts.InchiReaction]] =
      ReactionsToSubstratesAndProducts.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(0L))

    val returnElement = inchis.head.get

    returnElement.id should be(0L)
    returnElement.substrates should be(List(chemicals(0L)))
    returnElement.products should be(List(chemicals(1L)))
  }

  "ReactionToSubstratesAndProducts" should "retrieve the InChIs of a single substrate and two product reaction." in {
    val inchis: List[Option[ReactionsToSubstratesAndProducts.InchiReaction]] = ReactionsToSubstratesAndProducts.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(1L))

    val returnElement = inchis.head.get

    returnElement.id should be(1L)
    returnElement.substrates should be(List(chemicals(2L)))
    returnElement.products should be(List(chemicals(0L), chemicals(1L)))
  }

  "ReactionToSubstratesAndProducts" should "retrieve the InChIs of a multiple substrate and single product reaction." in {
    val inchis: List[Option[ReactionsToSubstratesAndProducts.InchiReaction]] =
      ReactionsToSubstratesAndProducts.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(2L))

    val returnElement = inchis.head.get

    returnElement.id should be(2L)
    returnElement.substrates should be(List(chemicals(2L), chemicals(0L)))
    returnElement.products should be(List(chemicals(1L)))
  }

  "ReactionToSubstratesAndProducts" should
    "should correctly unpack reactions with multiple of the same substrate (Coefficient > 1)." in {
    val inchis: List[Option[ReactionsToSubstratesAndProducts.InchiReaction]] =
      ReactionsToSubstratesAndProducts.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(3L))

    val returnElement = inchis.head.get

    returnElement.id should be(3L)
    returnElement.substrates should be(List(chemicals(0L), chemicals(0L)))
    returnElement.products should be(List(chemicals(1L)))
  }

  "ReactionToSubstratesAndProducts" should
    "should correctly unpack reactions with multiple of the same product (Coefficient > 1)." in {
    val inchis: List[Option[ReactionsToSubstratesAndProducts.InchiReaction]] =
      ReactionsToSubstratesAndProducts.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(4L))

    val returnElement = inchis.head.get

    returnElement.id should be(4L)
    returnElement.substrates should be(List(chemicals(0L)))
    returnElement.products should be(List(chemicals(1L), chemicals(1L)))
  }
}
