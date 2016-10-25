package com.act.analysis.massprojections

import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.MoleculeImporter
import com.act.biointerpretation.mechanisminspection.ErosCorpus

import scala.collection.JavaConversions._

class MassProjector(massDifferences: Map[String, Double]) {

  def this() = {
    this(Map())
  }

  // Note: New values take priority over defaults for names
  private val massDifList: Map[String, Double] = getDefaultMassValues ++ massDifferences

  def project(molecule: Molecule): Map[String, Double] ={
    val currentMass = molecule.getExactMass
    project(currentMass)
  }

  def project(molecule: String): Map[String, Double] ={
    project(MoleculeImporter.importMolecule(molecule))
  }

  def project(inputMass: Double): Map[String, Double] = {
    massDifList.map(x => {
      (x._1, x._2 + inputMass)
    })
  }

   def getDefaultMassValues: Map[String, Double] ={
    val ros = new ErosCorpus()
    ros.loadValidationCorpus()

    val massDifs: Map[String, Double] = ros.getRos.toList.flatMap(ro => {
      val rxnMolecule = ro.getReactor.getReaction
      val substrates = rxnMolecule.getReactants
      val products = rxnMolecule.getProducts

      val assignedMasses = if (substrates.length >= products.length){
        for (i <- 0 until products.length) yield products(i).getExactMass - substrates(i).getExactMass
      } else {
        List(substrates(0).getExactMass - products(0).getExactMass)
      }

      assignedMasses.indices.map(i => (s"${ro.getId}_$i", assignedMasses(i)))
    }).toMap

    massDifs
  }
}

