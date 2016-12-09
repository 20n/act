package com.act.biointerpretation.rsmiles.single_sar_construction

import java.io.File
import java.util
import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat}
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ChemicalToSubstrateProduct, ReactionInformation}
import com.act.biointerpretation.rsmiles.processing.ReactionProcessing
import com.act.utils.TSVWriter
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.log4j.LogManager

import scala.collection.parallel.immutable.{ParMap, ParSeq}

object SingleSarReactionsPipeline {
  val logger = LogManager.getLogger(getClass)

  val mongoDb: String = "validator_profiling_2"
  val host: String = "localhost"
  val port: Int = 27017

  def main(args: Array[String]): Unit = {
    val db = Mongo.connectToMongoDatabase(mongoDb, host, port)
    val chemicalSearcher: SingleSarChemicals = new SingleSarChemicals(db)

    val chemicalMap : Map[Int, ChemicalToSubstrateProduct] = chemicalSearcher.getAbstractChemicals()

    val reactionInfos = getAbstractReactions(db, chemicalMap)

    val reactionToSar : ReactionInfoToProjector = new ReactionInfoToProjector()

    val sars = reactionInfos.map(reactionInfo => reactionInfo -> reactionToSar.searchForReactor(reactionInfo))

    val headers : java.util.ArrayList[String] = new util.ArrayList[String]()
    headers.add("RXN_ID")
    headers.add("SAR")

    val writer : TSVWriter[String, String] = new TSVWriter[String,String](headers)
    writer.open(new File("/mnt/shared-data/Gil/abstract_reactions/sars.tsv"))
    sars.foreach(sar => {
      val row : util.Map[String,String]  = new util.HashMap[String, String]()
      row.put("RXN_ID", sar._1.reactionId.toString)
      row.put("SAR", MoleculeExporter.exportAsSmarts(sar._2.get.getReaction))
    })
    writer.close()
  }

  /**
    * Grabs all the abstract reactions for a given substrate count from a given DB.
    * Then converts them into ReactionInformations
    * @param mongoDb           Database instance to use
    * @param abstractChemicals A list of abstract chemicals previously constructed
    * @return A list containing reactions that
    *         have been constructed into the reaction information format.
    */

  def getAbstractReactions(mongoDb: MongoDB, abstractChemicals: Map[Int, ChemicalToSubstrateProduct]): ParSeq[ReactionInformation] = {

    logger.info("Finding reactions that contain one substrate, and one product, which are both abstract chemicals.")

    /*
      Query Reaction DB for reactions w/ these chemicals
     */
    val chemicalList = new BasicDBList
    abstractChemicals.seq.keySet.foreach(cId => chemicalList.add(cId.asInstanceOf[AnyRef]))

    // Matches a reaction if either the Products or Substrates array contains an abstract element.
    val abstractChemicalQuery = new BasicDBList
    // TODO the Mongo "In" statements below could be expensive.  Possible optimization route.
    abstractChemicalQuery.add(
      // Matches products that are in the abstract chemical list
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}.${ReactionKeywords.PUBCHEM}",
        Mongo.defineMongoIn(chemicalList)))

    // Matches substrates that are in the abstract chemical list
    abstractChemicalQuery.add(
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}.${ReactionKeywords.PUBCHEM}",
        Mongo.defineMongoIn(chemicalList)))

    abstractChemicalQuery.add(
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}.${MongoKeywords.LENGTH}",
        1))

    abstractChemicalQuery.add(
      new BasicDBObject(
        s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}.${MongoKeywords.LENGTH}",
        1))

    /*
      We want to match if they are one substrate, one product, and both are abstract.
     */
    val query = Mongo.defineMongoAnd(abstractChemicalQuery)

    // Filter so we get both the substrates and products
    val filter = new BasicDBObject(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}", 1)
    filter.append(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}", 1)

    // This will likely timeout if we don't indicate notimeout == true, so this is important.
    // The timeout can be seen by getting variable length responses.
    val abstractReactions: ParSeq[DBObject] =
    Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true).toList.par

    logger.info(s"Finished finding reactions that contain abstract chemicals. Found ${abstractReactions.length}.")

    val reactionConstructor: DBObject => ReactionInformation = dbObj => {
      val substrate = getDbSubstrate(dbObj)
      val product = getDbProduct(dbObj)
      val id = getDbId(dbObj)
      val reactionInfo = new ReactionInformation(id,
        List(new ChemicalInformation(substrate, abstractChemicals(substrate).asSubstrate)),
        List(new ChemicalInformation(product, abstractChemicals(product).asProduct)))
      reactionInfo
    }

    abstractReactions.map(obj => reactionConstructor(obj))
  }

  def getDbId(reaction : DBObject) : Int = {
    reaction.get(s"${ReactionKeywords.ID}").asInstanceOf[Int]
  }

  def getDbSubstrate(reaction : DBObject): Int = {
    val substrates = reaction.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.SUBSTRATES}").asInstanceOf[BasicDBList]
    substrates.get(0).asInstanceOf[DBObject].get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Int]
  }

  def getDbProduct(reaction : DBObject): Int = {
    val products = reaction.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.PRODUCTS}").asInstanceOf[BasicDBList]
    products.get(0).asInstanceOf[DBObject].get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Int]
  }

  object Mongo extends MongoWorkflowUtilities {}
}
