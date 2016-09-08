package com.act.biointerpretation.rsmiles

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import chemaxon.formats.MolFormatException
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.{MoleculeExporter, MoleculeImporter}
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.mechanisminspection.{Ero, ErosCorpus}
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.{ChemicalKeywords, MongoWorkflowUtilities, ReactionKeywords}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.parallel.immutable.{ParMap, ParRange, ParSeq}


object AbstractChemicalsToReactions {
  val logger = LogManager.getLogger(getClass)

  def main(args: Array[String]) {
    val db = Mongo.connectToMongoDatabase()
    val abstractChemicals = getAbstractChemicals(db)
    val abstractReactions = getAbstractReactions(db)(abstractChemicals)
    //val constructedReactions = constructAndProjectReactions(db)(abstractReactions, abstractChemicals)
    ParRange(1, 6, step = 1, inclusive = true).foreach(
      subCount => writeSingleSubstrateStringsToFile(db)(abstractReactions, abstractChemicals, subCount, new File("/Volumes/shared-data/Michael/Rsmiles", s"AbstractReactions$subCount.Substrates")))
  }

  def getAbstractChemicals(mongoDb: MongoDB): ParMap[Long, ChemicalInformation] = {
    logger.info("Finding abstract chemicals.")
    /*
      Mongo DB Query

      Query: All elements that contain "R" in their SMILES and "FAKE" in their InChI
     */
    var query = Mongo.createDbObject(ChemicalKeywords.SMILES, Mongo.defineMongoRegex("R"))
    query = Mongo.appendKeyToDbObject(query, ChemicalKeywords.INCHI, Mongo.defineMongoRegex("FAKE"))
    val filter = Mongo.createDbObject(ChemicalKeywords.SMILES, 1)
    val result: ParSeq[DBObject] = Mongo.mongoQueryChemicals(mongoDb)(query, filter, notimeout = true).toStream.par

    /*
       Convert from DB Object => Smarts and return that.
       Flatmap as Parse Db object returns None if an error occurs (Just filter out the junk)
    */
    val goodChemicalIds: ParMap[Long, ChemicalInformation] = result.flatMap(parseDbObjectForSmiles(_)).toMap

    logger.info(s"Finished finding abstract chemicals. Found ${goodChemicalIds.size}")

    goodChemicalIds
  }

  private def parseDbObjectForSmiles(ob: DBObject): Option[(Long, ChemicalInformation)] = {
    /*
      Type conversions from DB objects
     */
    val chemicalId: Long = ob.get(ChemicalKeywords.ID.toString).asInstanceOf[Long]
    val smiles: String = ob.get(ChemicalKeywords.SMILES.toString).asInstanceOf[String]

    // Replace R groups for C currently.
    val replacedSmarts = smiles.replaceAll("R[0-9]?", "C")

    /*
      Try to import the SMILES field as a Smarts representation of the molecule.
     */
    try {
      // Chemaxon technically uses smarts when we say Smiles, so we just make it explicit here.
      val mol = MoleculeImporter.importMolecule(replacedSmarts, MoleculeImporter.ChemicalFormat.Smarts)
      Option((chemicalId, new ChemicalInformation(chemicalId.toInt, mol, replacedSmarts)))
    } catch {
      case e: MolFormatException => None
    }
  }

  def getAbstractReactions(mongoDb: MongoDB)(abstractChemicals: ParMap[Long, ChemicalInformation]): ParSeq[DBObject] = {
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
    val matchingReactions: ParSeq[DBObject] = Mongo.mongoQueryReactions(mongoDb)(query, filter, notimeout = true).toList.par

    logger.info(s"Finished finding reactions that contain abstract chemicals. Found ${matchingReactions.length}.")

    matchingReactions
  }

  def writeSingleSubstrateStringsToFile(mongoDb: MongoDB)(abstractReactions: ParSeq[DBObject], abstractChemicals: ParMap[Long, ChemicalInformation], substrateCountFilter: Int, outputFile: File): Unit = {
    require(!outputFile.isDirectory, "The file you designated to output your files to is a directory and therefore is not a valid path.")
    require(substrateCountFilter > 0, s"A reaction must have at least one substrate.  " +
      s"You are looking for reactions with $substrateCountFilter substrates.")

    logger.info(s"Creating a file containing all abstract InChIs " +
      s"with $substrateCountFilter substrate${if (substrateCountFilter > 1) "s" else ""}.")

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      constructDbReaction(mongoDb)(abstractChemicals, substrateCountFilter) _

    val processCounter = new AtomicInteger()

    val singleSubstrateReactions: ParSeq[ReactionInformation] = abstractReactions.flatMap(rxn => {
      if (processCounter.incrementAndGet() % 10000 == 0) {
        logger.info(s"Total of ${processCounter.get} reactions have started processing " +
          s"so far for $substrateCountFilter substrate${if (substrateCountFilter > 1) "s" else ""}.")
      }

      reactionConstructor(rxn)
    }
    )
    logger.info(s"Found ${singleSubstrateReactions.length} " +
      s"reactions with $substrateCountFilter substrate${if (substrateCountFilter > 1) "s" else ""}.  Writing to file.")
    val substrates: Seq[String] = singleSubstrateReactions.flatMap(_.getSubstrates.map(_.getString)).seq
    new L2InchiCorpus(substrates).writeToFile(outputFile)
  }

  def constructAndProjectReactions(mongoDb: MongoDB)(abstractReactions: ParSeq[DBObject], abstractChemicals: ParMap[Long, ChemicalInformation]): ParSeq[Option[Int]] = {
    logger.info("Constructing reactions as substrate product pairs.")
    /*
      Construct in code reactions for each
     */


    logger.info("Projecting ROs on substrates to determine reaction ROs.")
    /*
      Project reactions
     */
    val eros = new ErosCorpus()
    eros.loadValidationCorpus()
    val projector = new ReactionProjector()
    val fullRoProjection: (ReactionInformation) => Option[Int] =
      projectReactionToDetermineRo(projector, eros.getRos.toList) _

    val reactionConstructor: (DBObject) => Option[ReactionInformation] =
      constructDbReaction(mongoDb)(abstractChemicals, 1) _

    val processedCount = new AtomicInteger()


    // For each element, we first create it, then try to do the RO mapping.
    val scores: ParSeq[Option[Int]] = abstractReactions.map(rxn => {
      val reactionInfo = reactionConstructor(rxn)
      if (reactionInfo.isDefined) {
        val projection: Option[Int] = fullRoProjection(reactionInfo.get)
        if (processedCount.incrementAndGet() % 10000 == 0) {
          logger.info(s"Total of ${processedCount.get} reactions have finished processing so far.")
        }
        projection
      } else {
        None
      }
    }
    )

    logger.info("Finished computing reactions.")

    logger.info(s"$scores")
    val hits = scores.flatten.length
    logger.info(s"Number of hits: $hits | Number of misses: ${scores.length - hits}")

    scores
  }

  private def constructDbReaction(mongoDb: MongoDB)(abstractChemicals: ParMap[Long, ChemicalInformation], substrateCountFilter: Int = -1)(ob: DBObject): Option[ReactionInformation] = {
    val substrates = ob.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.SUBSTRATES}").asInstanceOf[BasicDBList]
    val products = ob.get(s"${ReactionKeywords.ENZ_SUMMARY}").asInstanceOf[BasicDBObject].get(s"${ReactionKeywords.PRODUCTS}").asInstanceOf[BasicDBList]

    if (substrates == null | products == null) {
      return None
    }

    val substrateList = substrates.toList
    if (substrateCountFilter > 0 && substrateList.length != substrateCountFilter) {
      return None
    }

    val productList = products.toList

    // Make sure we load everything in.
    val moleculeLoader = loadMolecule(mongoDb)(abstractChemicals) _

    try {
      val substrateMoleculeList: List[ChemicalInformation] = substrateList.flatMap(x => moleculeLoader(x.asInstanceOf[DBObject]))
      val productMoleculeList: List[ChemicalInformation] = productList.flatMap(x => moleculeLoader(x.asInstanceOf[DBObject]))

      val rxnInfo = new ReactionInformation(ob.get(ReactionKeywords.ID.toString).asInstanceOf[Int], substrateMoleculeList, productMoleculeList)
      Option(rxnInfo)
    } catch {
      case e: MolFormatException => None
    }
  }

  private def loadMolecule(mongoDb: MongoDB)(abstractChemicals: ParMap[Long, ChemicalInformation])(dbObj: DBObject): List[ChemicalInformation] = {
    val hitGoodChem: Option[ChemicalInformation] = abstractChemicals.get(dbObj.get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long])
    val coefficient = dbObj.get(ReactionKeywords.COEFFICIENT.toString).asInstanceOf[Int]

    if (hitGoodChem.isDefined) {
      return List.fill(coefficient)(hitGoodChem.get)
    }

    // Try to look for real molecules if we can't find it in our abstract stack.
    val chemicalId = dbObj.get(ReactionKeywords.PUBCHEM.toString).asInstanceOf[Long]
    val query = Mongo.createDbObject(ChemicalKeywords.ID, chemicalId)
    val inchi = Mongo.mongoQueryChemicals(mongoDb)(query, null).next().get(ChemicalKeywords.INCHI.toString).asInstanceOf[String]
    val molecule = MoleculeImporter.importMolecule(inchi)
    List.fill(coefficient)(new ChemicalInformation(chemicalId.toInt, molecule, inchi))
  }

  def projectReactionToDetermineRo(projector: ReactionProjector, eros: List[Ero])(reactionInformation: ReactionInformation): Option[Int] = {
    val project = projectionArray(projector, reactionInformation.getSubstrates.map(_.getMolecule)) _

    // Full projection for RO
    val projectedProducts: List[(Int, Molecule)] = eros.flatMap(ro => project(ro)).flatten

    for (products <- reactionInformation.getProducts) {
      val productSmart = MoleculeExporter.exportAsSmarts(products.getMolecule)
      for ((ro, molecule) <- projectedProducts) {
        val moleculeSmart = MoleculeExporter.exportAsSmarts(molecule)
        if (productSmart.equals(moleculeSmart)) {
          return Option(ro)
        }
      }
    }

    None
  }

  private def projectionArray(projector: ReactionProjector, substrates: List[Molecule])(ro: Ero): Option[List[(Int, Molecule)]] = {
    val projection = projector.getAllProjectedProductSets(substrates.toArray, ro.getReactor).toList.flatten
    if (projection.nonEmpty) {
      return Option(List.fill(projection.length)(ro.getId.intValue()) zip projection)
    }

    None
  }

  class ReactionInformation(reactionId: Int, substrates: List[ChemicalInformation], products: List[ChemicalInformation]) {
    def getReactionId: Int = reactionId

    def getSubstrates: List[ChemicalInformation] = substrates

    def getProducts: List[ChemicalInformation] = products
  }

  class ChemicalInformation(chemicalId: Int, molecule: Molecule, stringVersion: String) {
    def getChemicalId: Int = chemicalId

    def getMolecule: Molecule = molecule

    def getString: String = stringVersion
  }

  object Mongo extends MongoWorkflowUtilities {}

}
