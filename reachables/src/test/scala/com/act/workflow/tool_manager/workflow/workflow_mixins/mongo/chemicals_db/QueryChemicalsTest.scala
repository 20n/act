package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemicals_db

import java.util.NoSuchElementException

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.test.util.MockedMongoDB
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.QueryChemicals
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class QueryChemicalsTest extends FlatSpec with Matchers with TimeLimitedTests with BeforeAndAfterEach{
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 15 seconds

  var mockDb: Option[MongoDB] = None
  val chemicals: Map[java.lang.Long, String] = Map(
    // Hexane
    java.lang.Long.valueOf(0L) -> "InChI=1S/C6H14/c1-3-5-6-4-2/h3-6H2,1-2H3",
    // Trimethyl Borane
    java.lang.Long.valueOf(1L) -> "InChI=1S/C3H9B/c1-4(2)3/h1-3H3"
  )

  // Trimethyl Borane SMILES
  val trimethSmiles = "B(C)(C)C"
  val complexSmiles = "CC(=NNC1=C(C(=O)NN=C1)Br)C2=CC=CC=N2"

  override def beforeEach(): Unit = {
    val mockAPI = new MockedMongoDB

    mockAPI.installMocks(null, List(0L, 1L, 2L, 3L).map(java.lang.Long.valueOf).asJava, null, null, chemicals.asJava)

    mockAPI.getChemicalMap.get(1L).setSmiles(trimethSmiles)
    mockAPI.getChemicalMap.get(2L).setSmiles(complexSmiles)
    mockDb = Option(mockAPI.getMockMongoDB)
  }

  override def afterEach(): Unit = {
    MoleculeImporter.clearCache()
    mockDb = None
  }

  "QueryChemicals" should "return inchis that are loaded into the database when queried by ID." in {
    TestObject.getChemicalsStringById(mockDb.get)(0L) should be(Some(chemicals(0L)))
    TestObject.getChemicalsStringById(mockDb.get)(1L, MoleculeFormat.inchi) should be(Some(chemicals(1L)))
  }

  "QueryChemicals" should "return None if a chemical does not have an InChI representation." in {
    TestObject.getChemicalsStringById(mockDb.get)(2L) should be(None)
    TestObject.getChemicalsStringById(mockDb.get)(3L) should be(None)
  }

  "QueryChemicals" should "throw an error if the chemical does not exist within the database." in {
    an[NoSuchElementException] should be thrownBy TestObject.getChemicalsStringById(mockDb.get)(-2L)
  }

  "QueryChemicals" should "be able to query multiple chemical IDs at one time and " +
    "return an ordered result of InChIs." in {
    val multipleInchiQuery = TestObject.getChemicalsStringsByIds(mockDb.get)(List(0L, 1L))
    multipleInchiQuery.length should be(2)
    multipleInchiQuery(0) should be(Some(chemicals(0L)))
    multipleInchiQuery(1) should be(Some(chemicals(1L)))
  }

  "QueryChemicals" should "be able to import a molecule of the same format when implicitly supplied " in {
    TestObject.getMoleculeById(mockDb.get)(0L) should be(Some(MoleculeImporter.importMolecule(chemicals(0L))))
  }

  "QueryChemicals" should "be able to import a molecule of the same format when explicitly supplied " in {
    TestObject.getMoleculeById(mockDb.get)(0L, MoleculeFormat.inchi) should be(
      Some(MoleculeImporter.importMolecule(chemicals(0L), MoleculeFormat.inchi)))
  }

  "QueryChemicals" should "be able to import a chemical as SMILES." in {
    TestObject.getMoleculeById(mockDb.get)(2L, MoleculeFormat.smiles) should be(
      Some(MoleculeImporter.importMolecule(complexSmiles, MoleculeFormat.smiles)))
  }

  "QueryChemicals" should "be able to import a chemical as both SMILES and InChI if it has both" in {
    TestObject.getMoleculeById(mockDb.get)(1L, MoleculeFormat.smiles) should
      be(Some(MoleculeImporter.importMolecule(trimethSmiles, MoleculeFormat.smiles)))
    TestObject.getMoleculeById(mockDb.get)(1L) should be(
      Some(MoleculeImporter.importMolecule(chemicals(1L), MoleculeFormat.inchi)))
  }

  "QueryChemicals" should "return None on an invalid type even if another vlaid type exists.." in {
    TestObject.getChemicalsStringById(mockDb.get)(0L) should be(Some(chemicals(0L)))
    TestObject.getChemicalsStringById(mockDb.get)(0L, MoleculeFormat.smiles) should be(None)
  }

  "QueryChemicals" should "be able to import multiple molecules at one time" in {
    TestObject.getMoleculesById(mockDb.get)(List(0L, 1L)) should contain
    Some(MoleculeImporter.importMolecule(chemicals(0L), MoleculeFormat.inchi))
    TestObject.getMoleculesById(mockDb.get)(List(0L, 1L)) should contain
    Some(MoleculeImporter.importMolecule(chemicals(1L), MoleculeFormat.inchi))
  }

  "QueryChemicals" should "return a heterogeneous list if only a subset of the molecules can be imported" in {
    TestObject.getMoleculesById(mockDb.get)(List(0L, 3L)) should contain
    Some(MoleculeImporter.importMolecule(chemicals(0L), MoleculeFormat.inchi))
    TestObject.getMoleculesById(mockDb.get)(List(0L, 3L)) should contain (None)
  }

  object TestObject extends QueryChemicals {}

}
