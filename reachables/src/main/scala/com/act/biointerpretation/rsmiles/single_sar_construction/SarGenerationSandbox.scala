/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.rsmiles.single_sar_construction

import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, AbstractChemicalInfo, ReactionInformation}
import com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals.AbstractChemicals
import com.act.biointerpretation.rsmiles.single_sar_construction.SingleSarReactionsPipeline.SubstrateProduct

/**
  * This class was used to test the behavior of the SAR generation. It no longer serves any good purpose, but I'll leave it
  * in for completeness.
  */
object SarGenerationSandbox {

  def main(args: Array[String]): Unit = {
    // substrate chemical from DB
    val abstractSubstrate = "C([R])C(=O)CC([O-])=O"
    val substrateId = 182675 // Real id from DB
    // variation on that chemical
    val abstractProduct = "C([R])C(N)CC([O-])=O"
    val fakeProductId = 12222 // This isn't actually from the DB

    val chemicalProcessor : SingleSarChemicals = new SingleSarChemicals(null)

    val substrate : AbstractChemicalInfo =  chemicalProcessor.calculateConcreteSubstrateAndProduct(substrateId, abstractSubstrate).get
    val product : AbstractChemicalInfo =  chemicalProcessor.calculateConcreteSubstrateAndProduct(fakeProductId, abstractProduct).get

    print(s"Processed substrate: ${substrate.asSubstrate}")
    print(s"Processed product: ${product.asProduct}")

    val reactionInfo = SubstrateProduct(abstractSubstrate, abstractProduct)

    val reactionToSar: AbstractReactionSarSearcher = new AbstractReactionSarSearcher()

    val sar = reactionToSar.searchForReactor(reactionInfo)

    if (sar.isDefined) {
      val serReactor = sar.get
      val reactor = serReactor.getReactor

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
