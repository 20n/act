package com.act.analysis.chemicals.molecules

object MoleculeFormat extends Enumeration {
  private val inchiString = "inchi"
  private val stdInchiString = "stdInchi"
  private val smilesString = "smiles"
  private val smartsString = "smarts"

  val inchi = Value(inchiString)
  val stdInchi = Value(stdInchiString)
  val smiles = Value(smilesString)
  val smarts = Value(smartsString)

  private val exportMap: Map[Value, String] = Map(
    inchi -> inchiString,
    stdInchi -> "inchi:SAbs,AuxNone,Woff",
    smiles -> smilesString,
    smarts -> smartsString
  )

  private val importMap: Map[Value, String] = Map(
    inchi -> inchiString,
    stdInchi -> inchiString,
    smiles -> smilesString,
    smarts -> smartsString
  )

  def getExportString(chemicalFormat: MoleculeFormat.Value): String = {
    exportMap(chemicalFormat)
  }

  def getImportString(chemicalFormat: MoleculeFormat.Value): String = {
    importMap(chemicalFormat)
  }
}
