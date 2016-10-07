package com.act.biointerpretation.rsmiles.chemicals.concrete_chemicals

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.MoleculeFormat
import com.act.biointerpretation.mechanisminspection.ErosCorpus
import com.act.biointerpretation.rsmiles.chemicals.Information.{ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.rsmiles.processing.ReactionProcessing
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment
import com.act.biointerpretation.rsmiles.sar_construction.ReactionRoAssignment.RoAssignments
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.QueryByRo
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import scala.collection.parallel.immutable.{ParMap, ParSeq}


object ConcreteReactions extends QueryByRo {
  val logger = LogManager.getLogger(getClass)

  def groupConcreteReactionsByRo(db: String = "marvin", host: String = "localhost", port: Int = 27017)
                                (moleculeFormat: MoleculeFormat.MoleculeFormatType, substrateCount: Int, outputFile: File)
                                (): Unit = {
    val mongoDb = connectToMongoDatabase()
    val eros = new ErosCorpus()
    eros.loadValidationCorpus()

    val roIds: List[Int] = eros.getRoIds.asScala.toList.map(_.toInt)

    // Preapply the settings.
    val reactionInformationById: (Int) => List[ReactionInformation] =
      getReactionInformationForSingleRo(mongoDb, moleculeFormat, substrateCount)

    logger.info("Getting previously defined RO assignments from database for each RO.")
    val progressionCounter = new AtomicInteger()
    val assignments: List[RoAssignments] = roIds.par.map(roId => {
      logger.info(s"Started processing RO $roId.")
      val reactionInformation = reactionInformationById(roId)
      logger.info(s"Finished processing RO $roId.  Completed ${progressionCounter.incrementAndGet()} assignments out of ${roIds.length} total.")
      new RoAssignments(roId, reactionInformation)
    }).seq.toList

    logger.info("Writing RO reaction assignments to JSON file.")
    ReactionRoAssignment.writeRoAssignmentsToJson(outputFile, assignments)
  }

  def getReactionInformationForSingleRo(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType, substrateCount: Int)
                                       (ro: Int): List[ReactionInformation] = {
    val desiredFields = List(ReactionKeywords.ID.toString, ReactionKeywords.ENZ_SUMMARY.toString)
    val reactionsForRo: Iterator[DBObject] = queryReactionsByRo(List(ro.toString), mongoDb, desiredFields)

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      ReactionProcessing.constructDbReaction(mongoDb, moleculeFormat)(ParMap(), substrateCount) _

    val reactions: ParSeq[ReactionInformation] = reactionsForRo.toStream.par.flatMap(reaction => reactionConstructor(reaction))
    reactions.seq.toList
  }

  /**
    * Grabs all the abstract reactions for a given substrate count from a given DB.
    * Then converts them into ReactionInformations
    *
    * @param mongoDb           Database instance to use
    * @param moleculeFormat    Format to convert the molecules to
    * @param concreteChemicals A list of abstract chemicals previously constructed
    * @param substrateCount    The number of substrates we should filter for
    *
    * @return A list containing reactions that
    *         have been constructed into the reaction information format.
    */
  def getConcreteReactions(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType, substrateCount: Int)
                          (concreteChemicals: ParMap[Long, ChemicalInformation]): ParSeq[ReactionInformation] = {
    require(substrateCount > 0, s"A reaction must have at least one substrate.  " +
      s"You are looking for reactions with $substrateCount substrates.")

    logger.info("Finding reactions that contain abstract chemicals.")

    /*
      Query Reaction DB for reactions w/ these chemicals
     */
    val chemicalList = new BasicDBList
    concreteChemicals.seq.keySet.foreach(cId => chemicalList.add(cId.asInstanceOf[AnyRef]))

    // Matches a reaction if either the Products or Substrates array contains an abstract element.
    val abstractSubstrateOrProduct = new BasicDBList
    abstractSubstrateOrProduct.add(
      // Matches products that are in the abstract chemical list
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}.${ReactionKeywords.PUBCHEM}",
        Mongo.defineMongoIn(chemicalList)))

    // Matches substrates that are in the abstract chemical list
    abstractSubstrateOrProduct.add(
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}.${ReactionKeywords.PUBCHEM}",
        Mongo.defineMongoIn(chemicalList)))

    /*
      We want to match if they are either a substrate or a product to
      get all reactions that could be defined as abstract.
     */
    val query = Mongo.defineMongoOr(abstractSubstrateOrProduct)

    // Filter so we get both the substrates and products
    val filter = new BasicDBObject(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}", 1)
    filter.append(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}", 1)

    // This will likely timeout if we don't indicate notimeout == true, so this is important.
    // The timeout can be seen by getting variable length responses.
    val abstractReactions: ParSeq[DBObject] =
      Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true).toList.par

    logger.info(s"Finished finding reactions that contain abstract chemicals. Found ${abstractReactions.length}.")

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      ReactionProcessing.constructDbReaction(mongoDb, moleculeFormat)(concreteChemicals, substrateCount)

    val processCounter = new AtomicInteger()
    val singleSubstrateReactions: ParSeq[ReactionInformation] = abstractReactions.flatMap(rxn => {
      val reaction = reactionConstructor(rxn)

      if (processCounter.incrementAndGet() % 10000 == 0) {
        logger.info(s"Total of ${processCounter.get} reactions have finished processing " +
          s"so far for $substrateCount substrate${if (substrateCount > 1) "s" else ""}.")
      }

      reaction
    })

    singleSubstrateReactions
  }

  object Mongo extends MongoWorkflowUtilities {}

}
