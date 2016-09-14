package com.act.analysis.chemicals.molecules

import chemaxon.calculations.clean.Cleaner
import chemaxon.standardizer.Standardizer
import chemaxon.struc.{Molecule, MoleculeGraph}
import org.apache.logging.log4j.LogManager

import scala.collection.parallel.immutable.ParMap

// Format information be found at https://docs.chemaxon.com/display/docs/Molecule+Formats
object MoleculeFormat extends Enumeration {

  private val cleaningSeparator = "."
  private val LOGGER = LogManager.getLogger(getClass)

  private val inchiString = "inchi"
  private val stdInchiString = "stdInchi"
  private val noAuxInchiString = "noAuxInchi"
  private val strictInchiString = "strictInchi"
  private val strictNoStereoInchiString = "strictNoStereoInchi"
  private val smilesString = "smiles"
  private val smartsString = "smarts"
  private val noStereoSmartsString = "noStereoSmarts"
  private val noStereoAromatizedSmartsString = "noStereoAromatizedSmarts"

  val inchi = MoleculeFormatType(Value(inchiString), List())
  val stdInchi = MoleculeFormatType(Value(stdInchiString), List())
  val noAuxInchi = MoleculeFormatType(Value(noAuxInchiString), List())
  val strictInchi = MoleculeFormatType(Value(strictInchiString), List())
  val strictNoStereoInchi = MoleculeFormatType(Value(strictNoStereoInchiString), List())
  val smiles = MoleculeFormatType(Value(smilesString), List())
  val smarts = MoleculeFormatType(Value(smartsString), List())
  val noStereoSmarts = MoleculeFormatType(Value(noStereoSmartsString), List())
  val noStereoAromatizedSmarts = MoleculeFormatType(Value(noStereoAromatizedSmartsString), List())

  private val exportMap: Map[String, String] = Map(
    inchiString -> "inchi",
    noAuxInchiString -> s"inchi:AuxNone",
    stdInchiString -> s"inchi:AuxNone,SAbs,Woff",
    strictInchiString -> s"inchi:AuxNone,SAbs,Woff,DoNotAddH",
    strictNoStereoInchiString -> s"inchi:AuxNone,SNon,Woff,DoNotAddH",
    smilesString -> smilesString,
    smartsString -> smartsString,
    noStereoSmartsString -> s"$smartsString:0",
    noStereoAromatizedSmartsString -> s"$smartsString:a0"
  )

  // Don't add H according to usual valences: all H are explicit
  private val importMap: Map[String, String] = Map(
    inchiString -> inchiString,
    stdInchiString -> inchiString,
    noAuxInchiString -> inchiString,
    strictInchiString -> inchiString,
    strictNoStereoInchiString -> inchiString,
    smilesString -> smilesString,
    smartsString -> smartsString,
    noStereoSmartsString -> smartsString,
    noStereoAromatizedSmartsString -> smartsString
  )

  def listPossibleFormats(): List[String] = {
    values.map(_.toString).toList
  }

  def getExportString(chemicalFormat: MoleculeFormat.Value): String = {
    exportMap(chemicalFormat.toString)
  }

  def getImportString(chemicalFormat: MoleculeFormat.Value): String = {
    importMap(chemicalFormat.toString)
  }

  def getName(s: String): MoleculeFormatType = {
    require(!s.isEmpty)

    val splitString = s.split(cleaningSeparator, 2).toList

    val cleaningOptions: List[CleaningOptions.Value] =
      if (splitString.length == 1)
        List()
      else
        splitString(1).split(",").toList.flatMap(cleaningSetting => {
          try {
            Option(CleaningOptions.withName(cleaningSetting))
          } catch {
            case e: NoSuchElementException => {
              val message = "The setting '$cleaningSetting' was not available as a cleaning format.  " +
                s"Continuing the run with only the valid settings."

              LOGGER.error(message)
              throw new NoSuchElementException (message)
            }
          }
        })

    try {
      new MoleculeFormatType(withName(splitString.head), cleaningOptions)
    } catch {
      case e: NoSuchElementException => {
        val message = s"Unable to find format value ${splitString.head}."
        LOGGER.error(message, e)
        throw new NoSuchElementException(message)
      }
    }
  }

  case class MoleculeFormatType(value: Value, cleaningOptions: List[CleaningOptions.Value]) {
    override def toString: String = {
      s"${value.toString}$cleaningSeparator${cleaningOptions.mkString(",")}"
    }
  }

  object CleaningOptions extends Enumeration {
    private val neutralizeString = "neutralize"
    private val clean2dString = "clean2d"
    private val clean3dString = "clean3d"
    private val aromatizeString = "aromatize"

    val neutralize = Value(neutralizeString)
    val clean2d = Value(clean2dString)
    val clean3d = Value(clean3dString)
    val aromatize = Value(aromatizeString)

    private val cleaningFunctions = ParMap[String, (Molecule) => Unit](
      neutralizeString -> ((molecule: Molecule) => {new Standardizer("neutralize").standardize(molecule)}),
      clean2dString -> ((molecule: Molecule) => {Cleaner.clean(molecule, 2)}),
      clean3dString -> ((molecule: Molecule) => {Cleaner.clean(molecule, 3)}),
      aromatizeString -> ((molecule: Molecule) => {molecule.aromatize(MoleculeGraph.AROM_BASIC)})
    )

    def applyCleaningOnMolecule(molecule: Molecule)(cleaningOption: CleaningOptions.Value): Unit = {
      // Get the correct option and apply it
      cleaningFunctions(cleaningOption.toString)(molecule)
    }
  }
}
