package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.calculations.hydrogenize.Hydrogenize
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.desalting.Desalter
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals.AbstractChemicals

object FullPipelineSandbox {

  private val ABSTRACT_CHEMICAL_REGEX = "\\[[^\\[]*R(\\]|[^euh][^\\]]*\\])"
  private val CARBON_REPLACEMENT = "\\[C\\]"

  def main(args: Array[String]): Unit = {
    // substrate chemical from DB
    val abstractSubstrate = "C([R])C(=O)CC([O-])=O"
    val substrateId = 182675 // Real id from DB
    // variation on that chemical
    val abstractProduct = "C([R])C(N)CC([O-])=O"
    val fakeProductId = 12222 // This isn't actually from the DB

    printSubstrateProduct("Original", abstractSubstrate, abstractProduct)

    val substrateMolecule: Molecule = MoleculeImporter.importMolecule(abstractSubstrate, MoleculeFormat.smarts)
    val productMolecule: Molecule = MoleculeImporter.importMolecule(abstractProduct, MoleculeFormat.smarts)

    Hydrogenize.convertImplicitHToExplicit(substrateMolecule)
    val hydrogenizedSubstrate = MoleculeExporter.exportAsSmarts(substrateMolecule)

    printSubstrateProduct("Hydrogenized.", hydrogenizedSubstrate, abstractProduct)

    val replacedSubstrate = replaceRWithC(hydrogenizedSubstrate)
    val replacedProduct = replaceRWithC(abstractProduct)

    printSubstrateProduct("Replaced", replacedSubstrate, replacedProduct)

    val replacedSubstrateMolecule = MoleculeImporter.importMolecule(replacedSubstrate, MoleculeFormat.smarts)
    val replacedProductMolecule = MoleculeImporter.importMolecule(replacedProduct, MoleculeFormat.smarts)

    val desalter: Desalter = new Desalter(new ReactionProjector())
    desalter.initReactors()

    val desaltedSubstrateList = desalter.desaltMoleculeForAbstractReaction(replacedSubstrateMolecule)
    val desaltedProductList = desalter.desaltMoleculeForAbstractReaction(replacedProductMolecule)

    if (desaltedSubstrateList.size() != 1 || desaltedProductList.size() != 1) {
      // TODO: handle multiple fragments
      println("Found multiple fragments. Don't handle this case yet. Exiting!")
      return
    }

    // For now we only deal with situations with exactly one product and substrate fragment
    val desaltedSubstrate = MoleculeExporter.exportAsSmarts(desaltedSubstrateList.get(0))
    val desaltedProduct = MoleculeExporter.exportAsSmarts(desaltedProductList.get(0))

    printSubstrateProduct("Desalted", desaltedSubstrate, desaltedProduct)

    var chemicalSubstrate: ChemicalInformation = new ChemicalInformation(substrateId, desaltedSubstrate)
    var chemicalProduct: ChemicalInformation = new ChemicalInformation(fakeProductId, desaltedProduct)

    val fakeReactionId = 1 // This isn't a DB reaction
    val reactionInfo = new ReactionInformation(fakeReactionId, List(chemicalSubstrate), List(chemicalProduct))

    val reactionToSar: ReactionInfoToProjector = new ReactionInfoToProjector()

    val sar = reactionToSar.searchForReactor(reactionInfo)

    if (sar.isDefined) {
      val reactor = sar.get

      printSubstrateProduct("Found sar!",
        MoleculeExporter.exportAsSmarts(reactor.getReactionReactant(0)),
        MoleculeExporter.exportAsSmarts(reactor.getReactionProduct(0)))

      val substrates = Array(
        "[#6:2][C:5][#6:8](=[O:9])[C:10][#6:13](-[#8:14])=[O:15]",
        "[#6:2][C:5][#6:8](=[O:9])[C:10][#6:13]([OH])=[O:15]",
        "CCC(=O)CC(O)=O",
        "CCC(=O)CC([OH])=O",
        "CCCCC(=O)CC([O-])=O",
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


  def replaceRWithC(chemical: String): String = {
    chemical.replaceAll(ABSTRACT_CHEMICAL_REGEX, CARBON_REPLACEMENT)
  }


  def printSubstrateProduct(tagline: String, substrate: String, product: String): Unit = {
    println(tagline)
    println("Substrate: " + substrate)
    println("Product: " + product)
  }
}
