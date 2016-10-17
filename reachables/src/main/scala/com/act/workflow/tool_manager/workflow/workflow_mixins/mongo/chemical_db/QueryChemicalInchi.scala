package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db

import act.server.MongoDB
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities}
import com.mongodb.BasicDBObject

trait QueryChemicalInchi extends MongoWorkflowUtilities {

  def getChemicalInchiById(mongoConnection: MongoDB)(chemicalId: Int): Option[String] ={
    val queryResult = mongoQueryChemicals(mongoConnection)(new BasicDBObject(ChemicalKeywords.ID.toString, chemicalId),
      new BasicDBObject(ChemicalKeywords.INCHI.toString, true))

    Option(queryResult.next().get(ChemicalKeywords.INCHI.toString).toString)
  }

  def getMoleculeById(mongoConnection: MongoDB)(chemicalId: Int): Option[Molecule] = {
    val inchi = getChemicalInchiById(mongoConnection)(chemicalId)

    if (inchi.isDefined) {
      Option(MoleculeImporter.importMolecule(inchi.get))
    } else {
      None
    }
  }
}
