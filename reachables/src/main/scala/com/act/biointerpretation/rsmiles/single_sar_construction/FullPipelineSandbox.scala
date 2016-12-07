package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.calculations.hydrogenize.Hydrogenize
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals.AbstractChemicals

class FullPipelineSandbox {

  def main(): Unit = {
    // substrate chemical from DB
    val abstractSubstrate = "C([R])CCC([O-])=O"
    val substrateId = 182675 // Real id from DB
    // variation on that chemical
    val abstractProduct = "C([R])C(N)CC([O-])=O"
    val fakeProductId = 12222 // This isn't actually from the DB


    val substrateMolecule : Molecule = MoleculeImporter.importMolecule(abstractSubstrate, MoleculeFormat.smarts)
    val productMolecule : Molecule = MoleculeImporter.importMolecule(abstractProduct, MoleculeFormat.smarts)

    Hydrogenize.convertImplicitHToExplicit(substrateMolecule)

    val hydrogenizedSubstrate = MoleculeExporter.exportAsSmarts(substrateMolecule)
    val replacedSubstrate = AbstractChemicals.replaceRWithC(hydrogenizedSubstrate)
    val replacedProduct = AbstractChemicals.replaceRWithC(abstractProduct)

    // TODO: Desalt the replaced substrate and product! Ask Mark about this tomorrow

    var chemicalSubstrate : ChemicalInformation = new ChemicalInformation(substrateId, hydrogenizedSubstrate)
    var chemicalProduct : ChemicalInformation = new ChemicalInformation(fakeProductId, abstractProduct)

    val fakeReactionId = 1 // This isn't a DB reaction
    val reactionInfo = new ReactionInformation(fakeReactionId, List(chemicalSubstrate), List(chemicalProduct))

    // TODO : throw in a ReactionInfoToProjector call to finish the pipeline

    // TODO : test this pipeline

  }
}
