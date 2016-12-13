package com.act.biointerpretation.rsmiles.single_sar_construction

import java.io.File
import java.util

import act.server.MongoDB
import chemaxon.reaction.Reactor
import com.act.analysis.chemicals.molecules.MoleculeExporter
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{AbstractChemicalInfo, ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.sars.SerializableReactor
import com.act.utils.TSVWriter
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.log4j.LogManager

import scala.collection.mutable
import scala.collection.parallel.immutable.{ParMap, ParSeq}

object SingleSarReactionsPipeline {
  val logger = LogManager.getLogger(getClass)

  val mongoDb: String = "validator_profiling_2"
  val host: String = "localhost"
  val port: Int = 27017

  val ONE_SUBSTRATE = "this.enz_summary.substrates.length==1"
  val ONE_PRODUCT = "this.enz_summary.products.length==1"

  def main(args: Array[String]): Unit = {
    val db = Mongo.connectToMongoDatabase(mongoDb, host, port)
    val chemicalSearcher: SingleSarChemicals = new SingleSarChemicals(db)

    val chemicalList: List[AbstractChemicalInfo] = chemicalSearcher.getAbstractChemicals()

    val chemIdToSmiles : Map[Int, String] = chemicalList.map(info => info.chemicalId -> info.dbSmiles).toMap
    logger.info(s"Size of chemical id keyset: ${chemIdToSmiles.keySet.size}")

    val (substrateProducts, reactionInfos) = getAbstractReactions(db, chemIdToSmiles)

    logger.info("Got abstract reaction infos from DB. Trying to generate SARs.")

    val reactionToSar: ReactionInfoToProjector = new ReactionInfoToProjector()

    val dbSmilesToSubstrate = chemicalList.map(info => info.dbSmiles -> info.asSubstrate).toMap[String, String]
    val dbSmilesToProduct = chemicalList.map(info => info.dbSmiles -> info.asProduct).toMap[String, String]
    def getProcessedSubProd(rawSubProd: SubstrateProduct): SubstrateProduct = {
      SubstrateProduct(dbSmilesToSubstrate(rawSubProd.substrate), dbSmilesToProduct(rawSubProd.product))
    }

    val subProdToSar = substrateProducts.map(subProd => subProd ->
      reactionToSar.searchForReactor(getProcessedSubProd(subProd))).toMap[SubstrateProduct, Option[SerializableReactor]]

    logger.info("Got SARs. Grouping reaction IDs by SAR and printing output.")

    val subProdToIds = reactionInfos.map(info => reactionInfoToSubstrateProduct(info) -> info.reactionId)
      .groupBy(_._1).mapValues(seq => seq.map(_._2))

    val headers: java.util.ArrayList[String] = new util.ArrayList[String]()
    val subProdHeader = "RAW_SUBSTRATE_PRODUCT"
    val processedHeader = "PROCESSED_SUBSTRATE_PRODUCT"
    val roHeader = "RO"
    val sarHeader = "SAR"
    val reactionIdHeader = "RXN_IDS"

    headers.add(subProdHeader)
    headers.add(processedHeader)
    headers.add(roHeader)
    headers.add(sarHeader)
    headers.add(reactionIdHeader)

    val writer: TSVWriter[String, String] = new TSVWriter[String, String](headers)
    writer.open(new File("/mnt/shared-data/Gil/abstract_reactions/sars.tsv"))
    substrateProducts.foreach(subProd => {
      val row: util.Map[String, String] = new util.HashMap[String, String]()
      row.put(subProdHeader, subProd.substrate + ">>" + subProd.product)
      row.put(processedHeader, dbSmilesToSubstrate(subProd.substrate) + ">>" + dbSmilesToProduct(subProd.product))
      row.put(reactionIdHeader, subProdToIds(subProd).toString())
      if (subProdToSar(subProd).isDefined) {
        row.put(roHeader, subProdToSar(subProd).get.getRoId.toString)
        row.put(sarHeader, MoleculeExporter.exportAsSmarts(subProdToSar(subProd).get.getReactor.getReaction))
      }
      writer.append(row)
    })
    writer.close()
  }

  case class SubstrateProduct(substrate: String, product: String) {
    def getSubstrate: String = substrate

    def getProduct: String = product
  }

  def reactionInfoToSubstrateProduct(reactionInfo : ReactionInformation): SubstrateProduct = {
    SubstrateProduct(reactionInfo.getSubstrates.head.chemicalAsString, reactionInfo.getProducts.head.chemicalAsString)
  }

  /**
    * Grabs all the abstract reactions for a given substrate count from a given DB.
    * Then converts them into ReactionInformations
    *
    * @param mongoDb           Database instance to use
    * @param chemIdToSmiles A list of abstract chemicals previously constructed
    * @return A list containing reactions that
    *         have been constructed into the reaction information format.
    *         Also a set of SubstrateToProduct objects containing the DB smiles for their substrates and products
    */
  def getAbstractReactions(mongoDb: MongoDB, chemIdToSmiles: Map[Int, String]):
  (mutable.Set[SubstrateProduct], List[ReactionInformation]) = {

    logger.info("Finding reactions that contain one substrate and one product by DB query.")

    /*
      Query Reaction DB for reactions w/ these chemicals
     */
    // Matches a reaction if either the Products or Substrates array contains an abstract element.
    val abstractChemicalQuery = new BasicDBList
    abstractChemicalQuery.add(
      new BasicDBObject(s"${MongoKeywords.WHERE}", ONE_SUBSTRATE)
    )

    abstractChemicalQuery.add(
      new BasicDBObject(s"${MongoKeywords.WHERE}", ONE_PRODUCT)
    )
    /*
      We want to match if they are one substrate, one product, and both are abstract.
     */
    val query = Mongo.defineMongoAnd(abstractChemicalQuery)

    // Filter so we get both the substrates and products
    val filter = new BasicDBObject(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}", 1)
    filter.append(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}", 1)

    // This will likely timeout if we don't indicate notimeout == true, so this is important.
    // The timeout can be seen by getting variable length responses.
    val abstractReactions =
    Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true)

    //TODO: Work out the following to properly return a reactionInformation if and only if its one sub one prod and both
    // abstract.

    logger.info("Iterating over reactions to product ReactionInfo objects, for those which have an abstract " +
      "substrate and product.")

    val reactionInfos = abstractReactions.flatMap(obj => reactionConstructor(obj, chemIdToSmiles)).toList

    println(s"Size of reaction infos: ${reactionInfos.size}")

    val substrateProductSet: mutable.Set[SubstrateProduct] = new mutable.HashSet[SubstrateProduct]()

    var counter = 0
    for (reaction <- reactionInfos) {
      if (counter % 1000 == 0) {
        println(s"Processed $counter reaction infos. Distinct set of size ${substrateProductSet.size}")
      }
      substrateProductSet.add(SubstrateProduct(reaction.getSubstrates.head.chemicalAsString,
        reaction.getProducts.head.chemicalAsString))
      counter = counter + 1
    }

    println(s"Size of substrate product set: ${substrateProductSet.size}")

    (substrateProductSet, reactionInfos)
  }


  def reactionConstructor(dbObj : DBObject, chemIdToSmiles: Map[Int, String]) : Option[ReactionInformation] = {
    val substrates = getDbSubstrates(dbObj)
    val products = getDbProducts(dbObj)
    if (substrates.size != 1 || products.size != 1) {
      return None
    } else {
      val substrate = substrates.head
      val product = products.head
      if (!chemIdToSmiles.keySet.contains(substrate) || !chemIdToSmiles.keySet.contains(product)) {
        return None
      }
      val id = getDbId(dbObj)
      val reactionInfo = new ReactionInformation(id,
        List(new ChemicalInformation(substrates.head, chemIdToSmiles(substrate))),
        List(new ChemicalInformation(products.head, chemIdToSmiles(product))))
      return Some(reactionInfo)
    }
  }

  def getDbId(reaction: DBObject): Int = {
    reaction.get(s"${ReactionKeywords.ID}").asInstanceOf[Int]
  }

  def getDbSubstrates(reaction: DBObject): List[Int] = {
    val substrates = reaction.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.SUBSTRATES}").asInstanceOf[BasicDBList]
    (0 until substrates.size()).map(substrates.get(_).asInstanceOf[DBObject].get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long].toInt).toList
  }

  def getDbProducts(reaction: DBObject): List[Int] = {
    val products = reaction.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.PRODUCTS}").asInstanceOf[BasicDBList]
    (0 until products.size()).map(products.get(_).asInstanceOf[DBObject].get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long].toInt).toList
  }

  object Mongo extends MongoWorkflowUtilities {}

}
