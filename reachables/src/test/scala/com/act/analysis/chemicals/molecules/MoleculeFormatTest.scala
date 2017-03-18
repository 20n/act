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

    // check all options with "inchi" in their name, but exclude "inchikey" format
    val inchiFormats = MoleculeFormat.listPossibleFormats().
      filter(_.toString.contains("inchi")).
      filterNot(_.toString.contains("inchikey"))

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

  "MoleculeFormat" should "should have valid inchikey options for exporting molecules." in {
    val testInchiHexane = "InChI=1S/C6H14/c1-3-5-6-4-2/h3-6H2,1-2H3"
    val testMolecule = MoleculeImporter.importMolecule(testInchiHexane)

    // check all options with "inchikey" in their name
    val inchiKeyFormats = MoleculeFormat.listPossibleFormats().filter(_.toString.contains("inchikey"))

    // Prevent test from passing if someone refactors the names
    inchiKeyFormats.nonEmpty should be(true)
    inchiKeyFormats.foreach(format => {
      noException should be thrownBy MoleculeExporter.exportMolecule(testMolecule, MoleculeFormat.getName(format.toString))
    })
  }
}
