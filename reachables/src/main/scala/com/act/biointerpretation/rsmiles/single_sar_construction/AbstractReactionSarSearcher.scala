package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.reaction.{ReactionException, Reactor}
import chemaxon.struc.{Molecule, RxnMolecule}
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.act.biointerpretation.rsmiles.single_sar_construction.SingleSarReactionsPipeline.SubstrateProduct
import com.act.biointerpretation.sars.SerializableReactor

import collection.JavaConverters._

/**
  * Matches SARs to abstract reactions.
  * This entails finding an RO that matches, and using the RO to atom-map the reaction.
  */
class AbstractReactionSarSearcher() {

  // Use only one-substrate one-product ROs
  val roCorpus = new ErosCorpus()
  roCorpus.loadValidationCorpus()
  roCorpus.filterCorpusBySubstrateCount(1)
  roCorpus.filterCorpusByProductCount(1)

  val reactionProjector = new ReactionProjector()

  /**
    * Tests all ROs to see if any RO in the corpus matches this reaction. If one is found, builds a reactor to
    * map the transformation.
    *
    * @param substrateProduct The SubstrateProduct information of the reaction to build a projector from.
    * @return The reactor corresponding to the full mapped transformation, if any succeeds. None otherwise.
    */
  def searchForReactor(substrateProduct: SubstrateProduct): Option[SerializableReactor] = {
    val substrateMol: Molecule = MoleculeImporter.importMolecule(substrateProduct.substrate, MoleculeFormat.smarts)
    val expectedProductMol: Molecule = MoleculeImporter.importMolecule(substrateProduct.product, MoleculeFormat.smarts)

    // Iterate over all the ROs in the corpus to see which one matches
    for (ro: Ero <- roCorpus.getRos.asScala) {
      val substrateCopy: Molecule = substrateMol.clone(); // chemaxon-defined deep copy method for molecules
      // This is needed because the molecule may be modified by chemaxon in the process of the following projections
      val maybeReactor: Option[SerializableReactor] = getReactor(substrateCopy, expectedProductMol, ro)
      if (maybeReactor.isDefined) {
        return maybeReactor
      }
    }
    None
  }

  /**
    * Tries to react the given substrate with the given RO to produce the given product.
    *
    * @return A reactor for the full mapped reaction, if successful. None otherwise.
    */
  private def getReactor(substrateToReact: Molecule, expectedProduct: Molecule, ro: Ero): Option[SerializableReactor] = {
    val reactor = ro.getReactor
    reactor.setReactants(Array(substrateToReact))

    var matchesRo: Boolean = true

    var producedProduct: Option[Molecule] = None
    try {
      // Returns None or throws ReactionException if the product doesn't match the substrate
      // Currently, this method uses a substructure-based equality test to compare the expectedProduct and the produced product
      // The details are in ReactionProjector.testEquality(). The current version seems to be too loose in certain scenarios, as
      // discussed in github issue 535.
      // TODO: fix the equality test to produce better behavior
      // Possibilities include modifying the settings on the substructure search and matching by some form of standardized inchi
      producedProduct = Some(reactionProjector.reactUntilProducesProduct(reactor, expectedProduct))
    } catch {
      case e: ReactionException => {}
    }

    if (producedProduct.isDefined) {
      return Some(new SerializableReactor(buildReactor(substrateToReact, producedProduct.get), ro.getId))
    }
    return None
  }

  /**
    * Given that a Reactor was used to transform the reacted substrate into the produced product, this method
    * returns a Reactor corresponding to that entire atom-mapped transformation.
    *
    * @param reactedSubstrate The substrate reacted.
    * @param producedProduct  The product produced.
    * @return The full Reactor.
    */
  private def buildReactor(reactedSubstrate: Molecule, producedProduct: Molecule): Reactor = {
    val rxnMolecule: RxnMolecule = new RxnMolecule()
    rxnMolecule.addComponent(reactedSubstrate, RxnMolecule.REACTANTS)
    rxnMolecule.addComponent(producedProduct, RxnMolecule.PRODUCTS)

    val fullReactor: Reactor = new Reactor
    fullReactor.setReaction(rxnMolecule)

    fullReactor
  }

}
