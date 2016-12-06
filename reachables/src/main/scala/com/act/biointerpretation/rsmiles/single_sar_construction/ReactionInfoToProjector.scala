package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.reaction.{ReactionException, Reactor}
import chemaxon.struc.{Molecule, RxnMolecule}
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.ReactionInformation
import collection.JavaConverters._

class ReactionInfoToProjector() {

  val roCorpus = new ErosCorpus()
  roCorpus.loadValidationCorpus()
  roCorpus.filterCorpusBySubstrateCount(1)
  roCorpus.filterCorpusByProductCount(1)

  val reactionProjector = new ReactionProjector()

  /**
    * Tests all ROs to see if any RO in the corpus matches this reaction. If one is found, builds a reactor to
    * map the transformation.
    *
    * @param info The ReactionInformation of the reaction to build a projector from.
    * @return The reactor corresponding to the full mapped transformation, if any succeeds. None otherwise.
    */
  def searchForReactor(info: ReactionInformation): Option[Reactor] = {
    val substrate: Molecule = MoleculeImporter.importMolecule(info.substrates(0).chemicalAsString, MoleculeFormat.smarts)
    val expectedProduct: Molecule = MoleculeImporter.importMolecule(info.products(0).chemicalAsString, MoleculeFormat.smarts)

    for (ro: Ero <- roCorpus.getRos.asScala) {
      val substrateCopy: Molecule = substrate.clone(); // chemaxon-defined deep copy method for molecules
      val maybeReactor: Option[Reactor] = getReactor(substrateCopy, expectedProduct, ro)
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
  private def getReactor(substrateToReact: Molecule, expectedProduct: Molecule, ro: Ero): Option[Reactor] = {
    val reactor = ro.getReactor
    reactor.setReactants(Array(substrateToReact))

    var matchesRo: Boolean = true

    var producedProduct: Option[Molecule] = None
    try {
      producedProduct = Some(reactionProjector.reactUntilProducesProduct(reactor, expectedProduct))
    } catch {
      case e: ReactionException => {}
    }

    if (producedProduct.isDefined) {
      return Some(buildReactor(substrateToReact, producedProduct.get))
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
