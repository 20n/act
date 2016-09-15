package com.act.biointerpretation.rsmiles

import com.act.biointerpretation.rsmiles.abstract_chemicals.{AbstractChemicals, AbstractReactions}
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment
import spray.json

object DataSerializationJsonProtocol extends json.DefaultJsonProtocol {
  implicit val cFormat = jsonFormat2(AbstractChemicals.ChemicalInformation)
  implicit val rFormat = jsonFormat3(AbstractReactions.ReactionInformation)
  implicit val roAssignmentFormat = jsonFormat2(ReactionRoAssignment.RoAssignments)
}
