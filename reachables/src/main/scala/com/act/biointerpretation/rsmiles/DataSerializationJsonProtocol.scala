package com.act.biointerpretation.rsmiles

import spray.json

object DataSerializationJsonProtocol extends json.DefaultJsonProtocol {
  implicit val chemicalFormat = jsonFormat2(AbstractChemicals.ChemicalInformation)
  implicit val reactionFormat = jsonFormat3(AbstractReactions.ReactionInformation)
}
