package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import act.server.MongoDB
import act.shared.Reaction
import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.biointerpretation.test.util.MockedMongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicals
import org.apache.jena.sparql.function.library.uuid
import org.biopax.paxtools.model.level3.{ConversionDirectionType, StepDirection}
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.scalatest._
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._

import scala.collection.JavaConverters._

class ReactionsToSubstratesAndProductsTest extends FlatSpec with Matchers with TimeLimitedTests with BeforeAndAfterEach{
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 15 seconds

  var mockDb: Option[MongoDB] = None

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
    mockedReaction(2L, List(2L, 0L), List(1L)).reaction
  )

  sealed case class mockedReaction(id: Long, substrates: List[Long], products: List[Long]){
    val reaction = new  Reaction (id, substrates.map(java.lang.Long.valueOf).toArray,
      products.map(java.lang.Long.valueOf).toArray, null, null, null, null, null, null, null, null)
  }

  override def beforeEach(): Unit = {
    val mockAPI = new MockedMongoDB


    mockAPI.installMocks(reactions.asJava, List(0L, 1L, 2L).map(java.lang.Long.valueOf).asJava, null, null, chemicals.asJava)

    mockDb = Option(mockAPI.getMockMongoDB)
  }

  override def afterEach(): Unit = {
    MoleculeImporter.clearCache()
    mockDb = None
  }

  "ReactionToSubstratesAndProducts" should "retrieve the InChIs of a single substrate and product reaction." in {
    val inchis: List[Option[TestObject.InchiReaction]] = TestObject.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(0L))

    val returnElement = inchis.head.get

    returnElement.id should be(0L)
    returnElement.substrates should be(List(chemicals(0L)))
    returnElement.substrates should be(List(chemicals(1L)))
  }

  "ReactionToSubstratesAndProducts" should "retrieve the InChIs of a single substrate and two product reaction." in {
    val inchis: List[Option[TestObject.InchiReaction]] = TestObject.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(1L))

    val returnElement = inchis.head.get

    returnElement.id should be(1L)
    returnElement.substrates should be(List(chemicals(2L)))
    returnElement.substrates should be(List(chemicals(0L), chemicals(1L)))
  }

  "ReactionToSubstratesAndProducts" should "retrieve the InChIs of a multiple substrate and single product reaction." in {
    val inchis: List[Option[TestObject.InchiReaction]] = TestObject.querySubstrateAndProductInchisByReactionIds(mockDb.get)(List(2L))

    val returnElement = inchis.head.get

    returnElement.id should be(2L)
    returnElement.substrates should be(List(chemicals(2L), chemicals(0L)))
    returnElement.substrates should be(List(chemicals(1L)))
  }

  object TestObject extends ReactionsToSubstratesAndProducts {}
}
