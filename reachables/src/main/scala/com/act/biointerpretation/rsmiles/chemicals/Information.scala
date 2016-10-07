package com.act.biointerpretation.rsmiles.chemicals

import java.io.Serializable

object Information {

  case class ChemicalInformation(chemicalId: Int, chemicalAsString: String) extends Serializable {
    def getChemicalId: Int = chemicalId

    def getString: String = chemicalAsString
  }

  case class ReactionInformation(reactionId: Int, substrates: List[ChemicalInformation], products: List[ChemicalInformation]) {
    def getReactionId: Int = reactionId

    def getSubstrates: List[ChemicalInformation] = substrates

    def getProducts: List[ChemicalInformation] = products
  }

}
