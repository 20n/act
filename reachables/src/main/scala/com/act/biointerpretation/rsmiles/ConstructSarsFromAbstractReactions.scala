package com.act.biointerpretation.rsmiles

import java.io.File

import chemaxon.clustering.LibraryMCS
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import com.act.biointerpretation.rsmiles.ReactionRoAssignment.RoAssignments
import com.act.biointerpretation.sarinference.SarTree
import com.act.biointerpretation.sars.{CharacterizedGroup, SarCorpus, SerializableReactor}
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

object ConstructSarsFromAbstractReactions {

  def sarConstructor(roAssignmentFile: File, outputFile: File, moleculeFormat: MoleculeFormat.Value)(): Unit = {
    sarConstructor(roAssignmentFile, outputFile, List(moleculeFormat))
  }


  def sarConstructor(roAssignmentFile: File, outputFile: File, moleculeFormats: List[MoleculeFormat.Value])() {
    val roAssignments: List[RoAssignments] = scala.io.Source.fromFile(roAssignmentFile).getLines().mkString.parseJson.convertTo[List[RoAssignments]]

    val roCorpus: ErosCorpus = new ErosCorpus
    roCorpus.loadValidationCorpus()






    val characterizedGroups: ParSeq[CharacterizedGroup] = roAssignments.par.flatMap(assignment => {
      val ro = assignment.ro

      // Convert strings to their molecule versions
      val moleculeStrings: Set[String] = assignment.reactions.map(reaction => {
        if (reaction.getSubstrates.length > 1) {
          throw new RuntimeException("The SAR constructed currently only handles single substrate reactions.")
        }

        reaction.getSubstrates.head.getString
      }).toSet

      val molecules: List[Molecule] = moleculeStrings.par.map(MoleculeImporter.importMolecule(_, moleculeFormats)).seq.toList
      if (molecules.isEmpty || molecules.length == 1) {
        None
      } else {
        // Build all pieces of SAR generator
        val clusterSarTree = new SarTree()
        clusterSarTree.buildByClustering(new LibraryMCS(), molecules.asJava)
        val sarNodes = clusterSarTree.getRootNodes.asScala.map(node => node.getSar)

        val singleRo = roCorpus.getEro(ro)
        Option(new CharacterizedGroup(s"Ro $ro Group", sarNodes.asJava, new SerializableReactor(singleRo.getReactor, ro)))
      }
    })



    val sarCorpus = new SarCorpus()
    characterizedGroups.foreach(group => sarCorpus.addCharacterizedGroup(group))

    sarCorpus.printToJsonFile(outputFile)
  }
}
