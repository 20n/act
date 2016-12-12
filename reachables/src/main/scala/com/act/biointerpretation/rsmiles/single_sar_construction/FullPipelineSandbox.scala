package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.calculations.hydrogenize.Hydrogenize
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.desalting.Desalter
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ChemicalToSubstrateProduct, ReactionInformation}
import com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals.AbstractChemicals
import com.act.biointerpretation.rsmiles.single_sar_construction.SingleSarReactionsPipeline.SubstrateProduct

object FullPipelineSandbox {

  def main(args: Array[String]): Unit = {
    // substrate chemical from DB
    val abstractSubstrate = "C([R])C(=O)CC([O-])=O"
    val substrateId = 182675 // Real id from DB
    // variation on that chemical
    val abstractProduct = "C([R])C(N)CC([O-])=O"
    val fakeProductId = 12222 // This isn't actually from the DB

    val chemicalProcessor : SingleSarChemicals = new SingleSarChemicals(null)

    val substrate : ChemicalToSubstrateProduct =  chemicalProcessor.calculateConcreteSubstrateAndProduct(substrateId, abstractSubstrate).get
    val product : ChemicalToSubstrateProduct =  chemicalProcessor.calculateConcreteSubstrateAndProduct(fakeProductId, abstractProduct).get

    print(s"Processed substrate: ${substrate.asSubstrate}")
    print(s"Processed product: ${product.asProduct}")

    val reactionInfo = SubstrateProduct(abstractSubstrate, abstractProduct)

    val reactionToSar: ReactionInfoToProjector = new ReactionInfoToProjector()

    val sar = reactionToSar.searchForReactor(reactionInfo)

    if (sar.isDefined) {
      val reactor = sar.get

      printSubstrateProduct("Found sar!",
        MoleculeExporter.exportAsSmarts(reactor.getReactionReactant(0)),
        MoleculeExporter.exportAsSmarts(reactor.getReactionProduct(0)))

      val substrates = Array(
        // various forms of the original substrate: should all be +
        "[#6:2][C:5][#6:8](=[O:9])[C:10][#6:13](-[#8:14])=[O:15]",
        "[#6:2][C:5][#6:8](=[O:9])[C:10][#6:13]([OH])=[O:15]",
        "CCC(=O)CC(O)=O",
        "CCC(=O)CC([OH])=O",
        // Extension of substrate by adding a few C's where the R was: should be +
        "CCCCC(=O)CC([OH])=O",
        // Extension of substrate by adding an OH to the middle of the substrate: should be -
        "CCC(=O)C([OH])C([OH])=O"
      )

      for (substrate: String <- substrates) {
        reactor.setReactants(Array(MoleculeImporter.importMolecule(substrate, MoleculeFormat.smarts)))
        var products: Array[Molecule] = reactor.react()

        if (products == null) {
          println(s"- $substrate")
        } else {
          println(s"+ $substrate")
        }

      }
    } else {
      println("Found no sar.")
    }
    // TODO : test this pipeline

  }

  def printSubstrateProduct(tagline: String, substrate: String, product: String): Unit = {
    println(tagline)
    println("Substrate: " + substrate)
    println("Product: " + product)
  }
}
