package com.act.biointerpretation.rsmiles


import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.rsmiles.AbstractChemicals.ChemicalInformation
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.parallel.immutable.{ParMap, ParSeq}


object AbstractReactions {
  val logger = LogManager.getLogger(getClass)

  def getAbstractReactions(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType)
                          (abstractChemicals: ParMap[Long, ChemicalInformation], substrateCountFilter: Int): ParSeq[ReactionInformation] = {
    require(substrateCountFilter > 0, s"A reaction must have at least one substrate.  " +
      s"You are looking for reactions with $substrateCountFilter substrates.")

    logger.info("Finding reactions that contain abstract chemicals.")

    /*
      Query Reaction DB for reactions w/ these chemicals
     */
    val chemicalList = new BasicDBList
    abstractChemicals.seq.keySet.foreach(cId => chemicalList.add(cId.asInstanceOf[AnyRef]))

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
    val abstractReactions: ParSeq[DBObject] = Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true).toList.par

    logger.info(s"Finished finding reactions that contain abstract chemicals. Found ${abstractReactions.length}.")

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      constructDbReaction(mongoDb, moleculeFormat)(abstractChemicals, substrateCountFilter) _

    val processCounter = new AtomicInteger()
    val singleSubstrateReactions: ParSeq[ReactionInformation] = abstractReactions.flatMap(rxn => {
      if (processCounter.incrementAndGet() % 10000 == 0) {
        logger.info(s"Total of ${processCounter.get} reactions have started processing " +
          s"so far for $substrateCountFilter substrate${if (substrateCountFilter > 1) "s" else ""}.")
      }

      reactionConstructor(rxn)
    })

    singleSubstrateReactions
  }

  private def constructDbReaction(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType)
                                 (abstractChemicals: ParMap[Long, ChemicalInformation], substrateCountFilter: Int = -1)
                                 (ob: DBObject): Option[ReactionInformation] = {
    val substrates = ob.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.SUBSTRATES}").asInstanceOf[BasicDBList]
    val products = ob.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.PRODUCTS}").asInstanceOf[BasicDBList]
    val reactionId = ob.get(ReactionKeywords.ID.toString).asInstanceOf[Int]

    if (substrates == null | products == null) {
      return None
    }

    val substrateList = substrates.toList
    if (substrateCountFilter > 0 && substrateList.length != substrateCountFilter) {
      return None
    }

    val productList = products.toList

    // Make sure we load everything in.
    val moleculeLoader = loadMolecule(mongoDb, moleculeFormat)(abstractChemicals) _

    try {
      val substrateMoleculeList: List[ChemicalInformation] = substrateList.flatMap(x => moleculeLoader(x.asInstanceOf[DBObject]))
      val productMoleculeList: List[ChemicalInformation] = productList.flatMap(x => moleculeLoader(x.asInstanceOf[DBObject]))

      /*
        Check if the Substrates = Reactants.
        This probably means a stereo change is happening that we don't really care about.
       */
      val uniqueSubstrates = substrateMoleculeList.map(_.getString).toSet
      val uniqueProducts = productMoleculeList.map(_.getString).toSet
      if (uniqueSubstrates.equals(uniqueProducts)) {
        logger.debug(s"Reaction with ID $reactionId")
        return None
      }


      val rxnInfo = new ReactionInformation(reactionId, substrateMoleculeList, productMoleculeList)
      Option(rxnInfo)
    } catch {
      case e: MolFormatException => None
    }
  }

  private def loadMolecule(mongoDb: MongoDB, moleculeFormat: MoleculeFormat.MoleculeFormatType)
                          (abstractChemicals: ParMap[Long, ChemicalInformation])(dbObj: DBObject): List[ChemicalInformation] = {
    val hitGoodChem: Option[ChemicalInformation] = abstractChemicals.get(dbObj.get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long])
    val coefficient = dbObj.get(ReactionKeywords.COEFFICIENT.toString).asInstanceOf[Int]

    if (hitGoodChem.isDefined) return List.fill(coefficient)(hitGoodChem.get)

    // Try to look for real molecules if we can't find it in our abstract stack.
    val chemicalId = dbObj.get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long]
    val query = Mongo.createDbObject(ChemicalKeywords.ID, chemicalId)
    val inchi = Mongo.mongoQueryChemicals(mongoDb)(query, null).next().get(ChemicalKeywords.INCHI.toString).asInstanceOf[String]
    val molecule = MoleculeImporter.importMolecule(inchi)
    List.fill(coefficient)(new ChemicalInformation(chemicalId.toInt, MoleculeExporter.exportMolecule(molecule, moleculeFormat)))
  }

  case class ReactionInformation(reactionId: Int, substrates: List[ChemicalInformation], products: List[ChemicalInformation]) {
    def getReactionId: Int = reactionId
    def getSubstrates: List[ChemicalInformation] = substrates
    def getProducts: List[ChemicalInformation] = products
  }

  object Mongo extends MongoWorkflowUtilities {}

}
