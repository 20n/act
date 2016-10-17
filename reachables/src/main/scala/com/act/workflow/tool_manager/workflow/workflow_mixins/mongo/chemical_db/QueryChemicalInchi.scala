package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities}
import com.mongodb.{BasicDBList, BasicDBObject}

import scala.collection.JavaConverters._

trait QueryChemicalInchi extends MongoWorkflowUtilities {

  def getChemicalsInchiById(mongoConnection: MongoDB)(chemicalId: Int): Option[String] ={
    val queryResult = mongoQueryChemicals(mongoConnection)(new BasicDBObject(ChemicalKeywords.ID.toString, chemicalId),
      new BasicDBObject(ChemicalKeywords.INCHI.toString, true))

    if (queryResult.hasNext) {
      Option(queryResult.next().get(ChemicalKeywords.INCHI.toString).toString)
    } else {
      None
    }
  }

  def getMoleculeById(mongoConnection: MongoDB)(chemicalId: Int, moleculeFormat: MoleculeFormat.Value = MoleculeFormat.inchi): Option[Molecule] = {
    val inchi = getChemicalsInchiById(mongoConnection)(chemicalId)

    if (inchi.isDefined) {
      try {
        return Option(MoleculeImporter.importMolecule(inchi.get, moleculeFormat))
      } catch {
        case e: MolFormatException =>
      }
    }

    None
  }

  def getChemicalsInchisByIds(mongoConnection: MongoDB)(chemicalIds: List[Int]): List[Option[String]] = {
    val chemicalList = chemicalIds.map(id => new BasicDBObject(ChemicalKeywords.ID.toString, id))
    val queryList = new BasicDBList()
    queryList.addAll(chemicalList.asJava)

    val query = defineMongoOr(queryList)

    val queryResult = mongoQueryChemicals(mongoConnection)(query, new BasicDBObject(ChemicalKeywords.INCHI.toString, true))

    // Convert all inchis into Strings
    queryResult.asInstanceOf[BasicDBList].asScala.map(i => Option(i.asInstanceOf[BasicDBObject].get(ChemicalKeywords.INCHI.toString).toString)).toList
  }
}
