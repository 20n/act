package com.act.analysis.chemicals.molecules


// Format information be found at https://docs.chemaxon.com/display/docs/Molecule+Formats
object MoleculeFormat extends Enumeration {
  private val inchiString = "inchi"
  private val stdInchiString = "stdInchi"
  private val noAuxInchiString = "noAuxInchi"
  private val strictInchiString = "strictInchi"
  private val smilesString = "smiles"
  private val smartsString = "smarts"
  private val noStereoSmartsString = "noStereoSmarts"
  private val noStereoAromatizedSmartsString = "noStereoAromatizedSmarts"

  val inchi = Value(inchiString)
  val stdInchi = Value(stdInchiString)
  val noAuxInchi = Value(noAuxInchiString)
  val strictInchi = Value(strictInchiString)
  val smiles = Value(smilesString)
  val smarts = Value(smartsString)
  val noStereoSmarts = Value(noStereoSmartsString)
  val noStereoAromatizedSmarts = Value(noStereoAromatizedSmartsString)

  private val exportMap: Map[Value, String] = Map(
    inchi -> inchiString,
    noAuxInchi -> s"$inchiString:AuxNone",
    stdInchi -> s"$inchiString:AuxNon,SAbs,Woff",
    strictInchi -> s"$inchiString:AuxNon,SAbs,Woff,DoNotAddH",
    smiles -> smilesString,
    smarts -> smartsString,
    noStereoSmarts -> s"$smartsString:0",
    noStereoAromatizedSmarts -> s"$smartsString:a0"
  )

  // Don't add H according to usual valences: all H are explicit
  private val importMap: Map[Value, String] = Map(
    inchi -> inchiString,
    stdInchi -> inchiString,
    noAuxInchi -> inchiString,
    strictInchi -> inchiString,
    smiles -> smilesString,
    smarts -> smartsString,
    noStereoSmarts -> smartsString,
    noStereoAromatizedSmarts -> smartsString
  )

  def getExportString(chemicalFormat: MoleculeFormat.Value): String = {
    exportMap(chemicalFormat)
  }

  def getImportString(chemicalFormat: MoleculeFormat.Value): String = {
    importMap(chemicalFormat)
  }
}
