package com.act.biointerpretation.rsmiles.sar_construction

import java.io.File

import chemaxon.clustering.LibraryMCS
import chemaxon.reaction.Reactor
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.molecules.{MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.biointerpretation.rsmiles.DataSerializationJsonProtocol._
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment.RoAssignments
import com.act.biointerpretation.sarinference.SarTree
import com.act.biointerpretation.sars.{CharacterizedGroup, Sar, SarCorpus, SerializableReactor}
import org.apache.log4j.LogManager
import spray.json._

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.ParSeq

object ConstructSarsFromPredictionCorpus {

  private val LOGGER = LogManager.getLogger(getClass)

  /**
    * Overload to allow for only a single molecule format to be used easily.
    */
  def sarConstructor(moleculeFormat: MoleculeFormat.MoleculeFormatType)(roAssignmentFile: File, outputFile: File)(): Unit = {
    sarConstructor(List(moleculeFormat))(roAssignmentFile, outputFile)
  }

  /**
    * Attempts to construct SARs for a given RO from a file containing RO assignments of reactions.
    *
    * Outputs the SARs to a file as a SarCorpus.
    *
    * @param roAssignmentFile Previously created file containing reactions assigned to ROs
    * @param outputFile       File to output results to
    * @param moleculeFormats  Which format the molecules shoudl be imported as
    */
  def sarConstructor(moleculeFormats: List[MoleculeFormat.MoleculeFormatType])(roAssignmentFile: File, outputFile: File)() {
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

  /**
    * For a given group (Ro), create SARs
    *
    * @param moleculeFormats Molecule format to import the molecules as
    * @param roCorpus        Corpus form which to pull the reactor out of
    * @param assignment      A list of reactions assigned to a single RO.  These reactions have been validated as valid.
    *
    * @return Either a characterized group or nothing, depending on if a characterized group could be constructed.
    */
  private def assignCharacterizedGroupForRo(moleculeFormats: List[MoleculeFormat.MoleculeFormatType], roCorpus: ErosCorpus)
                                           (assignment: ReactionRoAssignment.RoAssignments): Option[CharacterizedGroup] = {
    assume(moleculeFormats.nonEmpty, "No molecule format provided, please provide a format.")

    /*
      Get the unique molecule strings
     */
    val moleculeStrings: List[String] = assignment.reactions.map(reaction => {
      LOGGER.error("Currently only single substrate SARs can be created.")
      return None
    })
    val uniqueMoleculeStrings = moleculeStrings.toSet

    /*
      Import the molecules
     */
    val molecules: List[Molecule] = uniqueMoleculeStrings.par.map(
      MoleculeImporter.importMolecule(_, moleculeFormats)).seq.toList

    /*
      Build SARs from the molecules
     */
    val clusterSarTree = new SarTree()
    clusterSarTree.buildByClustering(new LibraryMCS(), molecules.asJava)
    val sars: List[Sar] = clusterSarTree.getRootNodes.asScala.map(node => node.getSar).toList

    /*
      Package into a characterized group for later use.
     */
    val ro: Int = assignment.ro
    if (sars.isEmpty) {
      LOGGER.info(s"Was unable to create any SAR nodes for RO $ro.")
      return None
    }

    LOGGER.info(s"Created ${sars.length} valid SARs for RO $ro.")

    val singleRoReactor: Reactor = roCorpus.getEro(ro).getReactor
    Option(new CharacterizedGroup(ro.toString, sars.asJava, new SerializableReactor(singleRoReactor, ro)))
  }
}
