package com.act.analysis.chemicals.molecules

import org.scalatest.{FlatSpec, Matchers}

class MoleculeFormatTest extends FlatSpec with Matchers {
  "MoleculeFormat" should "should have import and export strings for all values." in {
    // We implicitly test that all the values can be hit by
    // enumerating over the values and testing if they match to an export and an import.
    noException should be thrownBy MoleculeFormat.values.map(MoleculeFormat.getImportString)
    noException should be thrownBy MoleculeFormat.values.map(MoleculeFormat.getExportString)
  }
}