package com.act.biointerpretation.rsmiles.chemicals

import java.io.Serializable

object JsonInformationTypes {

  @SerialVersionUID(9508372526223L)
  case class ChemicalInformation(chemicalId: Int, chemicalAsString: String) extends Serializable {
    def getChemicalId: Int = chemicalId

    def getString: String = chemicalAsString
  }

  case class ChemicalToSubstrateProduct(chemicalId: Int, dbSmiles: String, asSubstrate: String, asProduct: String) extends Serializable {
    def getChemicalId: Int = chemicalId

    def getDbSmiles: String = dbSmiles

    def getAsSubstrate: String = asSubstrate

    def getAsProduct: String = asProduct

  }

  case class ReactionInformation(reactionId: Int, substrates: List[ChemicalInformation], products: List[ChemicalInformation]) {
    def getReactionId: Int = reactionId

    def getSubstrates: List[ChemicalInformation] = substrates

    def getProducts: List[ChemicalInformation] = products
  }

}
