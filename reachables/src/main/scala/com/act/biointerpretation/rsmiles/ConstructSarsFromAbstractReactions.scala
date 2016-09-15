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
import org.apache.log4j.LogManager
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

object ConstructSarsFromAbstractReactions {

  private val LOGGER = LogManager.getLogger(getClass)

  def sarConstructor(roAssignmentFile: File, outputFile: File, moleculeFormat: MoleculeFormat.MoleculeFormatType)(): Unit = {
    sarConstructor(roAssignmentFile, outputFile, List(moleculeFormat))
  }

  def sarConstructor(roAssignmentFile: File, outputFile: File, moleculeFormats: List[MoleculeFormat.MoleculeFormatType])() {
    val roAssignments: List[ReactionRoAssignment.RoAssignments] =
      scala.io.Source.fromFile(roAssignmentFile).getLines().mkString.parseJson.convertTo[List[RoAssignments]]

    val roCorpus: ErosCorpus = new ErosCorpus
    roCorpus.loadValidationCorpus()

    val assignmentSarMapper: (ReactionRoAssignment.RoAssignments) => Option[CharacterizedGroup] =
      assignCharacterizedGroupForRo(moleculeFormats, roCorpus) _

    // Generate SARs for each RO + Substrates
    val characterizedGroups: ParSeq[CharacterizedGroup] = roAssignments.par.flatMap(
      assignment => assignmentSarMapper(assignment))

    // Place the characterized groups into the corpus
    val sarCorpus = new SarCorpus()

    characterizedGroups.foreach(group => sarCorpus.addCharacterizedGroup(group))

    LOGGER.info(s"Writing Sar Corpus to ${outputFile.getAbsolutePath}.")
    sarCorpus.printToJsonFile(outputFile)
  }

  def assignCharacterizedGroupForRo(moleculeFormats: List[MoleculeFormat.MoleculeFormatType], roCorpus: ErosCorpus)
                                   (assignment: ReactionRoAssignment.RoAssignments): Option[CharacterizedGroup] = {
    val ro = assignment.ro

    // Convert strings to their molecule versions
    val moleculeStrings: Set[String] = assignment.reactions.map(reaction => {
      if (reaction.getSubstrates.length > 1) {
        throw new RuntimeException("The SAR constructed currently only handles single substrate reactions.")
      }

      reaction.getSubstrates.head.getString
    }).toSet

    val molecules: List[Molecule] = moleculeStrings.par.map(MoleculeImporter.importMolecule(_, moleculeFormats)).seq.toList

    // Bild the SAR
    val clusterSarTree = new SarTree()
    clusterSarTree.buildByClustering(new LibraryMCS(), molecules.asJava)
    val sarNodes = clusterSarTree.getRootNodes.asScala.map(node => node.getSar)

    if (sarNodes.isEmpty) {
      LOGGER.info(s"Was unable to create any SAR nodes for RO $ro.")
      return None
    }

    LOGGER.info(s"Created ${sarNodes.length} valid SARs for RO $ro.")
    val singleRo = roCorpus.getEro(ro)
    Option(new CharacterizedGroup(s"Ro $ro Group", sarNodes.asJava, new SerializableReactor(singleRo.getReactor, ro)))

  }
}
