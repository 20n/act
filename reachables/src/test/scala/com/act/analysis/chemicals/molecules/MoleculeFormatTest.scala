package com.act.analysis.chemicals.molecules

import com.act.analysis.chemicals.molecules
import com.act.analysis.chemicals.molecules.MoleculeFormat.MoleculeFormatType
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class MoleculeFormatTest extends FlatSpec with Matchers with BeforeAndAfterEach {
  override def beforeEach(): Unit = {
    MoleculeImporter.clearCache()
    MoleculeExporter.clearCache()
  }

  override def afterEach(): Unit = {
    MoleculeImporter.clearCache()
    MoleculeExporter.clearCache()
  }

  "MoleculeFormat" should "should have import and export strings for all values." in {
    // We implicitly test that all the values can be hit by
    // enumerating over the values and testing if they match to an export and an import.

    // Check imports
    noException should be thrownBy MoleculeFormat.listPossibleFormats().map(
      y => MoleculeFormat.getImportString(MoleculeFormatType(y, List())))

    // Check exports
    noException should be thrownBy MoleculeFormat.listPossibleFormats().map(
      y => MoleculeFormat.getExportString(MoleculeFormatType(y, List())))
  }

  "MoleculeFormat" should "should have valid inchi options for importing molecules." in {
    val testInchiTrimethylBorane = "InChI=1S/C3H9B/c1-4(2)3/h1-3H3"

    // check all options with "inchi" in their name
    val inchiFormats = MoleculeFormat.listPossibleFormats().filter(_.toString.contains("inchi"))

    // Prevent test from passing if someone refactors the names
    inchiFormats.nonEmpty should be(true)
    inchiFormats.foreach(format => {
      noException should be thrownBy MoleculeImporter.importMolecule(testInchiTrimethylBorane, MoleculeFormat.getName(format.toString))
    })
  }

  "MoleculeFormat" should "should have valid smart options for importing molecules." in {
    val testSmarts = "CB(C)C"

    // check all options with "smarts" in their name
    val smartsFormats = MoleculeFormat.listPossibleFormats().filter(_.toString.contains("smarts"))

    // Prevent test from passing if someone refactors the names
    smartsFormats.nonEmpty should be(true)
    smartsFormats.foreach(format => {
      noException should be thrownBy MoleculeImporter.importMolecule(testSmarts, MoleculeFormat.getName(format.toString))
    })
  }

  "MoleculeFormat" should "should have valid inchi options for exporting molecules." in {
    val testInchiHexane = "InChI=1S/C6H14/c1-3-5-6-4-2/h3-6H2,1-2H3"
    val testMolecule = MoleculeImporter.importMolecule(testInchiHexane)

    // check all options with "inchi" in their name
    val inchiFormats = MoleculeFormat.listPossibleFormats().filter(_.toString.contains("inchi"))

    // Prevent test from passing if someone refactors the names
    inchiFormats.nonEmpty should be(true)
    inchiFormats.foreach(format => {
      noException should be thrownBy MoleculeExporter.exportMolecule(testMolecule, MoleculeFormat.getName(format.toString))
    })
  }

  "MoleculeFormat" should "should have valid smart options for exporting molecules." in {
    val testInchiHexane = "InChI=1S/C6H14/c1-3-5-6-4-2/h3-6H2,1-2H3"
    val testMolecule = MoleculeImporter.importMolecule(testInchiHexane)

    // check all options with "smarts" in their name
    val smartsFormats = MoleculeFormat.listPossibleFormats().filter(_.toString.contains("smarts"))

    // Prevent test from passing if someone refactors the names
    smartsFormats.nonEmpty should be(true)
    smartsFormats.foreach(format => {
      noException should be thrownBy MoleculeExporter.exportMolecule(testMolecule, MoleculeFormat.getName(format.toString))
    })
  }
}
