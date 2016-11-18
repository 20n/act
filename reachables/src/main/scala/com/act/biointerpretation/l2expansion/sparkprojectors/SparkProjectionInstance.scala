package com.act.biointerpretation.l2expansion.sparkprojectors

import java.io.File

import chemaxon.license.LicenseManager
import chemaxon.marvin.io.MolExportException
import chemaxon.sss.SearchConstants
import chemaxon.sss.search.{MolSearch, MolSearchOptions}
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import org.apache.log4j.LogManager
import org.apache.spark.SparkFiles

import scala.collection.JavaConverters._

object SparkProjectionInstance extends Serializable {
  val defaultMoleculeFormat = MoleculeFormat.strictNoStereoInchi

  private val LOGGER = LogManager.getLogger(getClass)
  var eros = new ErosCorpus()
  eros.loadValidationCorpus()

  var localLicenseFile: Option[String] = None

  //var substructureSearch: MolSearch = new MolSearch // TODO: should this be `ThreadLocal` (or the scala-equivalent)?
  var substructureSearch: ThreadLocal[MolSearch] = new ThreadLocal[MolSearch] {
    override def initialValue(): MolSearch = {
      val search: MolSearch = new MolSearch
      val options: MolSearchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE)
      // This allows H's in RO strings to match implicit hydrogens in our target molecules.
      options.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_ENABLED)
      /* This allows for vague bond matching in ring structures.  From the Chemaxon Docs:
       *    In the query all single ring bonds are replaced by "single or aromatic" and all double ring bonds are
       *    replaced by "double or aromatic" prior to search.
       *    (https://www.chemaxon.com/jchem/doc/dev/java/api/chemaxon/sss/SearchConstants.html)
       *
       * This should allow us to handle aromatized molecules gracefully without handling non-ring single and double
       * bonds ambiguously. */
      options.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL2)
      // Few if any of our ROs concern stereo chemistry, so we can just ignore it.
      options.setStereoSearchType(SearchConstants.STEREO_IGNORE)
      /* Chemaxon's tautomer handling is weird, as sometimes it picks a non-representative tautomer as its default.
       * As such, we'll allow tautomer matches to avoid excluding viable candidates. */
      options.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON)
      search.setSearchOptions(options)
      search
    }
  }


  def project(licenseFileName: String)
             (reverse: Boolean, exhaustive: Boolean)
             (substrates: List[String]): Stream[ProjectionResult] = {

    // Load Chemaxon license file once
    if (this.localLicenseFile.isEmpty) {
      this.localLicenseFile = Option(SparkFiles.get(licenseFileName))
      LOGGER.info(s"Using license file at $localLicenseFile " +
        s"(file exists: ${new File(SparkFiles.get(licenseFileName)).exists()})")
      LicenseManager.setLicenseFile(SparkFiles.get(licenseFileName))
    }

    val getResults: Ero => Stream[ProjectionResult] = getResultsForSubstrate(substrates, reverse, exhaustive)
    val results: Stream[ProjectionResult] = this.eros.getRos.asScala.toStream.flatMap(getResults)
    results
  }

  private def getResultsForSubstrate(inputs: List[String], reverse: Boolean, exhaustive: Boolean)
                                    (ro: Ero): Stream[ProjectionResult] = {
    // We check the substrate or the products to ensure equal length based on if we are reversing the rxn or not.
    if ((!reverse && ro.getSubstrate_count != inputs.length) || (reverse && ro.getProduct_count != inputs.length)) {
      return Stream()
    }

    // Setup reactor based on ro
    val reactor = ro.getReactor
    if (reverse) reactor.setReverse(reverse)

    // Get all permutations of the input so that substrate order doesn't matter.
    //
    // TODO Having the importedMolecules above the loop may interfere with permutation's ability to correctly
    // filter out duplicate reactions (For example, List(a,b,b) should not make two combinations of (a,b,b)
    // because of the duplicates b. We should assess this as for higher number of molecule reactors this
    // may be the dominant case over the cost that could be imposed by hitting the MoleculeImporter's cache.
    val importedMolecules: List[Molecule] = inputs.map(x => MoleculeImporter.importMolecule(x, defaultMoleculeFormat))

    val resultingReactions: Stream[ProjectionResult] = importedMolecules.permutations.filter(possibleSubstrates(ro)).
      flatMap(substrateOrdering => {

        // Setup reactor
        reactor.setReactants(substrateOrdering.toArray)

        // Generate reactions
        val reactedValues: Stream[Array[Molecule]] = if (exhaustive) {
          Stream.continually(Option(reactor.react())).takeWhile(_.isDefined).flatMap(_.toStream)
        } else {
          val result = Option(reactor.react())
          if (result.isDefined) Stream(result.get) else Stream()
        }

        // Map the resulting reactions to a consistent format.
        val partiallyAppliedMapper: List[Molecule] => Option[ProjectionResult] =
          mapReactionsToResult(inputs, ro.getId.toString)

        reactedValues.flatMap(potentialProducts => partiallyAppliedMapper(potentialProducts.toList))
      }).toStream

    // Output stream
    resultingReactions
  }

  // TODO: This is so much more elegant and concise in scala; can we bring this to the ReactionProjector?
  private def possibleSubstrates(ro: Ero)(mols: List[Molecule]): Boolean = {
    val substrateQueries: Array[Molecule] = ro.getReactor.getReaction.getReactants
    if (substrateQueries == null) {
      throw new RuntimeException(s"Got null substrate queries for ero ${ro.getId}")
    }
    if (mols == null) {
      throw new RuntimeException(s"Got null molecules, but don't know where from.  ero is ${ro.getId}")
    }
    if (mols.size != substrateQueries.length) {
      return false
    }

    val molsAndQueries: Array[(Molecule, Molecule)] = substrateQueries.zip(mols)
    molsAndQueries.forall(p => matchesSubstructure(p._1, p._2))
  }

  private def matchesSubstructure(query: Molecule, target: Molecule): Boolean = {
    val q = MoleculeExporter.exportAsSmarts(query)
    val t = MoleculeExporter.exportAsSmarts(target)
    LOGGER.info(s"Running search $q on $t")
    val search = substructureSearch.get()
    search.setQuery(query)
    search.setTarget(target)
    search.findFirst() != null
  }

  private def mapReactionsToResult(substrates: List[String], roNumber: String)
                                  (potentialProducts: List[Molecule]): Option[ProjectionResult] = {
    try {
      val products = potentialProducts.map(x => MoleculeExporter.exportMolecule(x, defaultMoleculeFormat))
      Option(ProjectionResult(substrates, roNumber, products))
    } catch {
      case e: MolExportException =>
        LOGGER.warn(s"Unable to export a projected project.  Projected projects were $potentialProducts")
        None
    }
  }
}
