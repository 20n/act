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
object MoleculeFormat {

  private val cleaningSeparator = ">"
  private val LOGGER = LogManager.getLogger(getClass)

  sealed case class Format(name: String) {
    override def toString: String = name
  }

  private val inchiFormat = Format("inchi")
  private val stdInchiFormat = Format("stdInchi")
  private val noAuxInchiFormat = Format("noAuxInchi")
  private val strictInchiFormat = Format("strictInchi")
  private val strictNoStereoInchiFormat = Format("strictNoStereoInchi")
  private val smilesFormat = Format("smiles")
  private val smartsFormat = Format("smarts")
  private val noStereoSmartsFormat = Format("noStereoSmarts")
  private val noStereoAromatizedSmartsFormat = Format("noStereoAromatizedSmarts")

  object inchi extends MoleculeFormatType(inchiFormat, List())
  object stdInchi extends MoleculeFormatType(stdInchiFormat, List())
  object noAuxInchi extends MoleculeFormatType(noAuxInchiFormat, List())
  object strictInchi extends MoleculeFormatType(strictInchiFormat, List())
  object strictNoStereoInchi extends MoleculeFormatType(strictNoStereoInchiFormat, List(Cleaning.removeStereo))
  object smiles extends MoleculeFormatType(smilesFormat, List())
  object smarts extends MoleculeFormatType(smartsFormat, List())
  object noStereoSmarts extends MoleculeFormatType(noStereoSmartsFormat, List(Cleaning.removeStereo))
  object noStereoAromatizedSmarts extends MoleculeFormatType(noStereoAromatizedSmartsFormat, List(Cleaning.removeStereo))

  // InChI options are from https://docs.chemaxon.com/display/docs/InChi+and+InChiKey+export+options
  // We prefix InChI options with "I"
  private val InoAuxInformation = "AuxNone"
  private val IforceAbsoluteStereo = "SAbs"
  private val IforceNoStereo = "SNon"
  private val IonlyExplicitHydrogen = "DoNotAddH"
  private val IdoNotDisplayWarnings = "Woff"

  // Smarts options can be found at https://docs.chemaxon.com/display/docs/SMILES+and+SMARTS+import+and+export+options
  // We prefix smarts options with "S"
  private val SnoStereo = "0"
  private val Saromatic = "a"

  private val exportMap: Map[Format, String] = Map(
    inchiFormat -> inchiFormat.name,
    noAuxInchiFormat-> s"${inchiFormat.name}:$InoAuxInformation",
    stdInchiFormat ->
      s"${inchiFormat.name}:$InoAuxInformation,$IforceAbsoluteStereo,$IdoNotDisplayWarnings",
    strictInchiFormat ->
      s"${inchiFormat.name}:$InoAuxInformation,$IforceAbsoluteStereo,$IdoNotDisplayWarnings,$IonlyExplicitHydrogen",
    strictNoStereoInchiFormat ->
      s"${inchiFormat.name}:$InoAuxInformation,$IforceNoStereo,$IdoNotDisplayWarnings,$IonlyExplicitHydrogen",
    smilesFormat -> smilesFormat.name,
    smartsFormat -> smartsFormat.name,
    noStereoSmartsFormat -> s"${smartsFormat.name}:$SnoStereo",
    noStereoAromatizedSmartsFormat -> s"${smartsFormat.name}:$Saromatic$SnoStereo"
  )

  // Don't add H according to usual valences: all H are explicit
  private val importMap: Map[Format, String] = Map(
    inchiFormat -> inchiFormat.name,
    stdInchiFormat -> inchiFormat.name,
    noAuxInchiFormat-> inchiFormat.name,
    strictInchiFormat -> inchiFormat.name,
    strictNoStereoInchiFormat -> inchiFormat.name,
    smilesFormat -> smilesFormat.name,
    smartsFormat -> smartsFormat.name,
    noStereoSmartsFormat -> smartsFormat.name,
    noStereoAromatizedSmartsFormat -> smartsFormat.name
  )

  def listPossibleFormatStrings(): List[String] = {
    listPossibleFormats().map(_.toString)
  }

  def listPossibleFormats(): List[Format] = {
    importMap.keys.toList
  }

  def getExportString(chemicalFormat: MoleculeFormat.MoleculeFormatType): String = {
    exportMap(exportMap.keys.find(x => x.name.equals(chemicalFormat.value.name)).get)
  }

  def getImportString(chemicalFormat: MoleculeFormat.MoleculeFormatType): String = {
    importMap(importMap.keys.find(x => x.name.equals(chemicalFormat.value.name)).get)
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
      val previousType: Option[Format] = importMap.keys.find(x => x.name.equals(splitString.head))
      if (previousType.isEmpty) throw new NoSuchElementException
      new MoleculeFormatType(previousType.get, cleaningOptions)
    } catch {
      case e: NoSuchElementException =>
        val message = s"Unable to find format value ${splitString.head}."
        LOGGER.error(message, e)
        throw new NoSuchElementException(message)
    }
  }



  case class MoleculeFormatType(value: Format, cleaningOptions: List[Cleaning.Options]) {
    def this(moleculeFormatType: MoleculeFormatType, cleaning: List[Cleaning.Options]) {
      // Allow for concatenating of a native type and supplied types.
      this(moleculeFormatType.getValue, (cleaning ::: moleculeFormatType.cleaningOptions).distinct)
    }

    override def toString: String = {
      s"${value.toString}$cleaningSeparator${cleaningOptions.mkString(",")}"
    }

    def getValue: Format = {
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

    sealed case class Options(name: String) {
      def getName: String = name
    }

    object neutralize extends Options(neutralizeString)
    object clean2d extends Options(clean2dString)
    object clean3d extends Options(clean3dString)
    object aromatize extends Options(aromatizeString)
    object removeIsotopes extends Options(removeIsotopesString)
    object removeStereo extends Options(removeStereoString)

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
