package com.act.biointerpretation.rsmiles.single_sar_construction

import java.io.File
import java.util

import act.server.MongoDB
import com.act.analysis.chemicals.molecules.MoleculeExporter
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{AbstractChemicalInfo, ChemicalInformation, ReactionInformation}
import com.act.biointerpretation.sars.SerializableReactor
import com.act.utils.{CLIUtil, TSVWriter}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{MongoKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.commons.cli.{CommandLine, Option => CliOption}
import org.apache.log4j.LogManager

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * Full pipeline for finding abstract reactions from a DB, finding ROs to match them, and generating SARs for each one
  * when possible.
  * Picks out only one substrate -> one product reactions
  */
object SingleSarReactionsPipeline {

  val OPTION_MONGO_DATABASE = "d"
  val OPTION_OUTPUT_FILE = "o"
  val OPTION_PORT = "p"
  val OPTION_HOST = "l"

  private val HELP_MESSAGE: String =
    """
      | Used to generate L3 projections based on SARs and input chemicals.
    """.stripMargin

  val OPTION_BUILDERS: util.List[CliOption.Builder] = List(
    CliOption.builder(OPTION_MONGO_DATABASE).
      argName("database").
      desc("The database to use").
      hasArg.
      required.
      longOpt("database"),

    CliOption.builder(OPTION_PORT).
      argName("port").
      desc("Which port to use").
      hasArg.
      required.
      longOpt("port"),

    CliOption.builder(OPTION_HOST).
      argName("host").
      desc("Which host to use").
      hasArg.
      required.
      longOpt("host"),

    CliOption.builder(OPTION_OUTPUT_FILE).
      argName("output-file").
      desc("Where the file should be output.").
      hasArg.
      required.
      longOpt("output-file")
  ).asJava
  
  
  val logger = LogManager.getLogger(getClass)
  
  // Mongo expression to test for one substrate-one product
  val ONE_SUBSTRATE = "this.enz_summary.substrates.length==1"
  val ONE_PRODUCT = "this.enz_summary.products.length==1"

  def main(args: Array[String]): Unit = {
    val cliUtil: CLIUtil = new CLIUtil(getClass, HELP_MESSAGE, OPTION_BUILDERS)
    val cl: CommandLine = cliUtil.parseCommandLine(args)
    
    val outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_FILE))
    
    val db = Mongo.connectToMongoDatabase(
      cl.getOptionValue(OPTION_MONGO_DATABASE), 
      cl.getOptionValue(OPTION_HOST), 
      cl.getOptionValue(OPTION_PORT).toInt)

    val chemicalSearcher: SingleSarChemicals = new SingleSarChemicals(db)
    val chemicalList: List[AbstractChemicalInfo] = chemicalSearcher.getAbstractChemicals()

    // Maps chemical Ids to their smiles as found in the DB
    val chemIdToSmiles : Map[Int, String] = chemicalList.map(info => info.chemicalId -> info.dbSmiles).toMap

    val (substrateProducts, reactionInfos) : (mutable.Set[SubstrateProduct], List[ReactionInformation]) = getAbstractReactions(db, chemIdToSmiles)

    logger.info("Got abstract reaction infos from DB. Trying to generate SARs.")

    val sarSearcher: AbstractReactionSarSearcher = new AbstractReactionSarSearcher()

    // Maps to go from raw DB smiles to the appropriately processed smiles, i.e. with R -> C, and explicit hydrogens
    val dbSmilesToSubstrate = chemicalList.map(info => info.dbSmiles -> info.asSubstrate).toMap[String, String]
    val dbSmilesToProduct = chemicalList.map(info => info.dbSmiles -> info.asProduct).toMap[String, String]
    def getProcessedSubProd(rawSubProd: SubstrateProduct): SubstrateProduct = {
      SubstrateProduct(dbSmilesToSubstrate(rawSubProd.substrate), dbSmilesToProduct(rawSubProd.product))
    }

    val subProdToSar = substrateProducts.map(subProd => subProd ->
      sarSearcher.searchForReactor(getProcessedSubProd(subProd))).toMap[SubstrateProduct, Option[SerializableReactor]]

    logger.info("Got SARs. Grouping reaction IDs by SAR and printing output.")

    // Map (sub, prod) pair to associated raection Ids
    // This collapses any two reactions which have the same R-smiles for both the product and substrate.
    // This does not collapse reactions whose desalted forms are the same, if their original R-smiles are different
    // It also does not collapse reactions whose substrates are equivalent in structure, but have different R-smiles representations in the DB
    // TODO: improve this deduplication procedure
    val subProdToIds : Map[SubstrateProduct, List[Int]] = reactionInfos.map(info => reactionInfoToSubstrateProduct(info) -> info.reactionId)
      .groupBy(_._1).mapValues(seq => seq.map(_._2))

    /**
      * Write output to file, one line per substrate->product pair, including columns for:
      * - The raw substrate/product R-smiles
      * - The processed smiles
      * - The reaction Ids
      * If an RO matched:
      * - The RO
      * - The generated SAR
      */
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
    writer.open(outputFile)
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

  /**
    * This data structure exists so we can easily deduplicate all equivalent sub/product pairs
    * Just stores two strings.
    */
  case class SubstrateProduct(substrate: String, product: String) {
    def getSubstrate: String = substrate

    def getProduct: String = product
  }

  def reactionInfoToSubstrateProduct(reactionInfo : ReactionInformation): SubstrateProduct = {
    SubstrateProduct(reactionInfo.getSubstrates.head.chemicalAsString, reactionInfo.getProducts.head.chemicalAsString)
  }

  /**
    * Grabs all the abstract reactions with one substrate and one product from the DB.
    * Returns a set of all distinct sub->product transformations found, and a list of the info associated with each
    * abstract reaction entry in the DB.
    *
    * @param mongoDb           Database instance to use
    * @param chemIdToSmiles A map from chemical ID to smiles, for only the abstract chemicals.
    * @return A list containing reactions that
    *         have been constructed into the reaction information format.
    *         Also a set of SubstrateToProduct objects containing the DB smiles for their substrates and products
    */
  def getAbstractReactions(mongoDb: MongoDB, chemIdToSmiles: Map[Int, String]):
  (mutable.Set[SubstrateProduct], List[ReactionInformation]) = {

    logger.info("Finding reactions that contain one substrate and one product by DB query.")

    /*
      We want to match if they are one substrate & one product. We'll do the abstraction check in code.
     */
    val oneSubOneProdQuery = new BasicDBList
    oneSubOneProdQuery.add(
      new BasicDBObject(s"${MongoKeywords.WHERE}", ONE_SUBSTRATE)
    )

    oneSubOneProdQuery.add(
      new BasicDBObject(s"${MongoKeywords.WHERE}", ONE_PRODUCT)
    )
    val query = Mongo.defineMongoAnd(oneSubOneProdQuery)

    // Filter so we get both the substrates and products
    val filter = new BasicDBObject(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.PRODUCTS}", 1)
    filter.append(s"${ReactionKeywords.ENZ_SUMMARY}.${ReactionKeywords.SUBSTRATES}", 1)

    // This will likely timeout if we don't indicate notimeout == true, so this is important.
    // The timeout can be seen by getting variable length responses.
    val abstractReactions =
    Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true)


    logger.info("Iterating over reactions to product ReactionInfo objects, for those which have an abstract " +
      "substrate and product.")

    // Flatmap turns the list of optionals into a list of the present ReactionInformation objects
    val reactionInfos : List[ReactionInformation] = abstractReactions.flatMap(obj => reactionConstructor(obj, chemIdToSmiles)).toList

    println(s"Number of reaction infos: ${reactionInfos.size}")

    val substrateProductSet: mutable.Set[SubstrateProduct] = new mutable.HashSet[SubstrateProduct]()

    // Iterate over the reaction infos to get the set of distinct substrate->product maps
    // This really shouldn't be a part of this method, it should be done in the caller
    // TODO: move this out, return only the reactionInfos, write separate method to generate subProdSet
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

  /**
    * Takes a reaction object from the DB, and the chemIdToSmiles map generated by SingleSarChemicals.
    * @return ReactionInformation if the substrate and product are both abstract, None if not
    */
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

  /**
    * Helper methods for parsing DB object
    */
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
