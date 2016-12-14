package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.reaction.Reactor
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.rsmiles.single_sar_construction.SingleSarReactionsPipeline.SubstrateProduct
import com.act.biointerpretation.sars.SerializableReactor
import org.scalatest.{FlatSpec, Matchers}

/**
  * Tests for matching an RO to a substrate->product pair.
  */
class AbstractReactionSarSearcherTest extends FlatSpec with Matchers{
    val DUMMY_REACTION_ID = 1
    val DUMMY_SUBSTRATE_ID = 2
    val DUMMY_PRODUCT_ID = 3

    val infoToProjector = new  AbstractReactionSarSearcher

    "ReactionInfoToProjector" should "match RO" in {
      // Build chemicals
      val substrateSmarts = "CCCCC=O"
      val productSmarts = "CCCCC-[OH]"
      val substrateProduct = SubstrateProduct(substrateSmarts, productSmarts)
      println(s"Testing substrate: $substrateSmarts, product: $productSmarts")

      // Search for reactor
      val maybeReactor : Option[SerializableReactor] =
        infoToProjector.searchForReactor(substrateProduct)

      // Ensure that a reactor is built for this aldehyde -> alcohol reaction
      maybeReactor.isDefined should be(true)
      println("Found the RO matching this reaction.")

      // Make sure the reactor works as expected
      val reactor : Reactor = maybeReactor.get.getReactor
      val substrateMolecule : Molecule = MoleculeImporter.importMolecule(substrateSmarts, MoleculeFormat.smarts)
      val productMolecule : Molecule = MoleculeImporter.importMolecule(productSmarts, MoleculeFormat.smarts)
      reactor.setReactants(Array(substrateMolecule))
      val projector : ReactionProjector = new ReactionProjector()
      projector.reactUntilProducesProduct(reactor, productMolecule) // throws an exception if this fails
      println("Reactor produced correct product when ran on initial substrate.")
    }

  "ReactionInfoToProjector" should "match no RO" in {
    val substrateSmarts = "CCCCCCC=O"
    val productSmarts = "CCCCC-[OH]"
    val substrateProduct = SubstrateProduct(substrateSmarts, productSmarts)
    println(s"Testing substrate: $substrateSmarts, product: $productSmarts")

    val maybeReactor : Option[SerializableReactor] =
      infoToProjector.searchForReactor(substrateProduct)

    maybeReactor.isDefined should be(false)
    println("Correctly found no RO for this reaction.")
  }

  "ReactionInfoToProjector" should "include the explicit hydrogens" in {
    // Build chemicals
    val substrateSmarts = "C[CH2][CH2][CH2][CH]=O"
    val productSmarts = "CCCCC-[OH]"
    val substrateProduct = SubstrateProduct(substrateSmarts, productSmarts)
    println(s"Testing substrate: $substrateSmarts, product: $productSmarts")


    // Search for reactor
    val maybeReactor : Option[SerializableReactor]  =
    infoToProjector.searchForReactor(substrateProduct)

    // Ensure that a reactor is built for this aldehyde -> alcohol reaction
    maybeReactor.isDefined should be(true)
    println("Found the RO matching this reaction.")

    // Make sure the reactor works as expected
    val reactor : Reactor = maybeReactor.get.getReactor
    val projector : ReactionProjector = new ReactionProjector()

    var substrateMolecule : Molecule = MoleculeImporter.importMolecule(substrateSmarts, MoleculeFormat.smarts)
    var productMolecule : Molecule = MoleculeImporter.importMolecule(productSmarts, MoleculeFormat.smarts)
    reactor.setReactants(Array(substrateMolecule))
    projector.reactUntilProducesProduct(reactor, productMolecule) // throws an exception if this fails
    println("Reactor produced correct product when ran on initial substrate.")

    // Make sure the reactor works if we extend on the R group
    val validSubstrateExtension : String = "[N]C[C][C][C][C]=O"
    val validProductExtension : String = "[N]C[C][C][C][C]-O"
    substrateMolecule = MoleculeImporter.importMolecule(validSubstrateExtension, MoleculeFormat.smarts)
    productMolecule = MoleculeImporter.importMolecule(validProductExtension, MoleculeFormat.smarts)
    reactor.setReactants(Array(substrateMolecule))
    projector.reactUntilProducesProduct(reactor, productMolecule) // throws an exception if this fails
    println("Reactor produced correct product when run on correct extension of substrate.")

    // Make sure the reactor does not work if we extend the substrate elsewhere
    val invalidSubstrateExtension : String = "C[C](N)[C][C][C]=O"
    substrateMolecule = MoleculeImporter.importMolecule(invalidSubstrateExtension, MoleculeFormat.smarts)
    reactor.setReactants(Array(substrateMolecule))
    reactor.react() should be(null)
    println("Reactor correctly produced null product when run on invaild extension of substrate.")
  }

}
