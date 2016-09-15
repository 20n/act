package com.act.biointerpretation.rsmiles

import com.act.biointerpretation.rsmiles.abstract_chemicals.AbstractChemicals.ChemicalInformation
import com.act.biointerpretation.rsmiles.processing.ReactionProcessing.ReactionInformation
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment
import spray.json

object DataSerializationJsonProtocol extends json.DefaultJsonProtocol {
  implicit val cFormat = jsonFormat2(ChemicalInformation)
  implicit val rFormat = jsonFormat3(ReactionInformation)
  implicit val roAssignmentFormat = jsonFormat2(ReactionRoAssignment.RoAssignments)
}
