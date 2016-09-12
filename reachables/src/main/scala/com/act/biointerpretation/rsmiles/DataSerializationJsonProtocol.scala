package com.act.biointerpretation.rsmiles

import spray.json

object DataSerializationJsonProtocol extends json.DefaultJsonProtocol {
  implicit val cFormat = jsonFormat2(AbstractChemicals.ChemicalInformation)
  implicit val rFormat = jsonFormat3(AbstractReactions.ReactionInformation)
//  implicit val roAssignmentFormat = jsonFormat2(AssignRoToReactions.RoAssignments)
}
