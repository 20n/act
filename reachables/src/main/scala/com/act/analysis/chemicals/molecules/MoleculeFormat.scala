package com.act.analysis.chemicals.molecules

import chemaxon.calculations.clean.Cleaner
import chemaxon.standardizer.Standardizer
import chemaxon.struc.{Molecule, MoleculeGraph}
import org.apache.logging.log4j.LogManager

import scala.collection.parallel.immutable.ParMap

/**
  * Enumerates possible import and export formats to allow for consistent use.  Also standardizes cleaning.
  */
// Format information be found at https://docs.chemaxon.com/display/docs/Molecule+Formats
object MoleculeFormat extends Enumeration {

  private val cleaningSeparator = ">"
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
  val strictNoStereoInchi = MoleculeFormatType(Value(strictNoStereoInchiString), List(Cleaning.removeStereo))
  val smiles = MoleculeFormatType(Value(smilesString), List())
  val smarts = MoleculeFormatType(Value(smartsString), List())
  val noStereoSmarts = MoleculeFormatType(Value(noStereoSmartsString), List(Cleaning.removeStereo))
  val noStereoAromatizedSmarts = MoleculeFormatType(Value(noStereoAromatizedSmartsString), List(Cleaning.removeStereo))

  private val exportMap: Map[MoleculeFormat.Value, String] = Map(
    inchi.getValue -> "inchi",
    noAuxInchi.getValue -> s"inchi:AuxNone",
    stdInchi.getValue -> s"inchi:AuxNone,SAbs,Woff",
    strictInchi.getValue -> s"inchi:AuxNone,SAbs,Woff,DoNotAddH",
    strictNoStereoInchi.getValue -> s"inchi:AuxNone,SNon,Woff,DoNotAddH",
    smiles.getValue -> smilesString,
    smarts.getValue -> smartsString,
    noStereoSmarts.getValue -> s"$smartsString:0",
    noStereoAromatizedSmarts.getValue -> s"$smartsString:a0"
  )

  // Don't add H according to usual valences: all H are explicit
  private val importMap: Map[MoleculeFormat.Value, String] = Map(
    inchi.getValue -> inchiString,
    stdInchi.getValue -> inchiString,
    noAuxInchi.getValue -> inchiString,
    strictInchi.getValue -> inchiString,
    strictNoStereoInchi.getValue -> inchiString,
    smiles.getValue -> smilesString,
    smarts.getValue -> smartsString,
    noStereoSmarts.getValue -> smartsString,
    noStereoAromatizedSmarts.getValue -> smartsString
  )

  def listPossibleFormats(): List[String] = {
    values.map(_.toString).toList
  }

  def getExportString(chemicalFormat: MoleculeFormat.MoleculeFormatType): String = {
    exportMap(chemicalFormat.getValue)
  }

  def getImportString(chemicalFormat: MoleculeFormat.MoleculeFormatType): String = {
    importMap(chemicalFormat.getValue)
  }

  def getName(s: String): MoleculeFormatType = {
    require(!s.isEmpty)

    val splitString = s.split(cleaningSeparator, 2).toList

    val cleaningOptions: List[Cleaning.Options] =
      if (splitString.length == 1)
        List()
      else
        splitString(1).split(",").toList.flatMap(cleaningSetting => {
          try {
            if (!cleaningSetting.equals("")) {
              Cleaning.withName(cleaningSetting)
            } else
              None
          } catch {
            case e: NoSuchElementException =>
              val message = s"The setting '$cleaningSetting' was not available as a cleaning format.  " +
                s"Continuing the run with only the valid settings."

              LOGGER.error(message)
              throw new NoSuchElementException (message)
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


  object MoleculeFormatType {
    def apply(moleculeFormatType: MoleculeFormatType, cleaning: List[Cleaning.Options]): MoleculeFormatType = {
      new MoleculeFormatType(moleculeFormatType.getValue, (cleaning ::: moleculeFormatType.cleaningOptions).distinct)
    }
  }


  case class MoleculeFormatType(value: Value, cleaningOptions: List[Cleaning.Options]) {
    def this(moleculeFormatType: MoleculeFormatType, cleaning: List[Cleaning.Options]) {
      // Allow for concatenating of a native type and supplied types.
      this(moleculeFormatType.getValue, (cleaning ::: moleculeFormatType.cleaningOptions).distinct)
    }

    override def toString: String = {
      s"${value.toString}$cleaningSeparator${cleaningOptions.mkString(",")}"
    }

    def getValue: Value = {
      value
    }
  }


  object Cleaning {
    private val neutralizeString = "neutralize"
    private val clean2dString = "clean2d"
    private val clean3dString = "clean3d"
    private val aromatizeString = "aromatize"
    private val removeIsotopesString = "removeIsotopes"
    private val removeStereoString = "removeStereo"

    sealed abstract class Options(val name: String) {
      def getName: String = name
    }

    case object neutralize extends Options(neutralizeString)
    case object clean2d extends Options(clean2dString)
    case object clean3d extends Options(clean3dString)
    case object aromatize extends Options(aromatizeString)
    case object removeIsotopes extends Options(removeIsotopesString)
    case object removeStereo extends Options(removeStereoString)

    private val options: List[Options] = List(neutralize, clean2d, clean3d, aromatize, removeIsotopes, removeStereo)

    private val cleaningFunctions = ParMap[String, (Molecule) => Unit](
      neutralizeString -> ((molecule: Molecule) => {new Standardizer("neutralize").standardize(molecule)}),
      clean2dString -> ((molecule: Molecule) => {Cleaner.clean(molecule, 2)}),
      clean3dString -> ((molecule: Molecule) => {Cleaner.clean(molecule, 3)}),
      aromatizeString -> ((molecule: Molecule) => {molecule.aromatize(MoleculeGraph.AROM_BASIC)}),
      removeIsotopesString -> ((molecule: Molecule) => {new Standardizer("clearisotopes").standardize(molecule)}),
      removeStereoString -> ((molecule: Molecule) => {new Standardizer("clearstereo").standardize(molecule)})
    )

    def applyCleaningOnMolecule(molecule: Molecule)(cleaningOption: Cleaning.Options): Unit = {
      // Get the correct option and apply it
      cleaningFunctions(cleaningOption.getName)(molecule)
    }

    def withName(name: String): Option[Cleaning.Options] = {
      options.find(o => o.getName.equals(name))
    }
  }
}
