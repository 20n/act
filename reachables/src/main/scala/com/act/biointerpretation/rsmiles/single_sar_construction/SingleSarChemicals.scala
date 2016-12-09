package com.act.biointerpretation.rsmiles.single_sar_construction

import act.server.MongoDB
import chemaxon.calculations.hydrogenize.Hydrogenize
import chemaxon.formats.MolFormatException
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.analysis.chemicals.molecules.MoleculeFormat.Cleaning
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.desalting.Desalter
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, ChemicalToSubstrateProduct}
import com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals.AbstractReactions._
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ChemicalKeywords
import com.mongodb.DBObject
import org.apache.log4j.LogManager

import scala.collection.mutable
import scala.collection.parallel.immutable.{ParMap, ParSeq}

/**
  * Created by gil on 12/8/16.
  */
class SingleSarChemicals(mongoDb: MongoDB) {
  val logger = LogManager.getLogger(getClass)

  private val REGEX_TO_SEARCH = "\\[R[0-9]*\\]"
  private val REGEX_TO_REPLACE = "\\[[^\\[]*R(\\]|[^euh][^\\]]*\\])"
  private val CARBON_REPLACEMENT = "\\[C\\]"

  val cleanSmartsFormat = new MoleculeFormat.MoleculeFormatType(MoleculeFormat.smarts,
    List(Cleaning.neutralize, Cleaning.clean2d, Cleaning.aromatize))

  val desalter: Desalter = new Desalter(new ReactionProjector())
  desalter.initReactors()

  // There are many, many repeated abstract smiles in the DB
  // This cache ensures we only process each one once
  val smilesCache : mutable.Map[String, Option[ChemicalToSubstrateProduct]] =
    new mutable.HashMap[String, Option[ChemicalToSubstrateProduct]]()

  def getAbstractChemicals() : Map[Int, ChemicalToSubstrateProduct] = {
    logger.info("Finding abstract chemicals.")
    /*
      Mongo DB Query

      Query: All elements that contain "[R]" or "[R#]", for some number #, in their SMILES
      TODO: try incorporating elements containing R in their inchi, which don't have a smiles, by replacing R with Cl.
     */
    var query = Mongo.createDbObject(ChemicalKeywords.SMILES, Mongo.defineMongoRegex(REGEX_TO_SEARCH))
    val filter = Mongo.createDbObject(ChemicalKeywords.SMILES, 1)
    val result: Seq[DBObject] = Mongo.mongoQueryChemicals(mongoDb)(query, filter, notimeout = true).toStream

    /*
       Convert from DB Object => Smarts and return that.
       Flatmap as Parse Db object returns None if an error occurs (Just filter out the junk)
    */

    var counter = 0
    val goodChemicalIds: Map[Int, ChemicalToSubstrateProduct] = result.flatMap(dbObj => {
      counter = counter + 1
      if (counter % 1000 == 0) {
        println(s"Processed $counter chemicals.")
      }
      getIdAndStrings(dbObj)
    }).toMap

    logger.info(s"Finished finding abstract chemicals. Found ${goodChemicalIds.size}")

    goodChemicalIds
  }

  def getIdAndStrings(dbChemical : DBObject) : Option[(Int, ChemicalToSubstrateProduct)] = {

    val chemicalId = dbChemical.get("_id").asInstanceOf[Long].toInt
    val smiles = dbChemical.get("SMILES").asInstanceOf[String]

    val result : Option[ChemicalToSubstrateProduct] = calculateConcreteSubstrateAndProduct(chemicalId, smiles)

    if (result.isDefined) {
      return Some((chemicalId, result.get))
    }
    None
  }

  /**
    * Calculates the concrete smiles that should be used for the given chemical in order to atom map an abstract
    * reaction. Since this is different for a substrate and a product, we calculate both versions here for later use
    * in either side of a reaction.
    * @param chemicalId The ID of the chemical.
    * @param chemicalSmiles The smiles of the chemical from the DB.
    * @return An object grouping the chemical Id to the modified smiles to be used if this chemical is a substrate
    *         or product of an abstract reaction.
    */
  def calculateConcreteSubstrateAndProduct(chemicalId : Int, chemicalSmiles : String): Option[ChemicalToSubstrateProduct] = {
    val cachedResult = smilesCache.get(chemicalSmiles)
    if (cachedResult.isDefined) {
      return cachedResult.get
    }

    try {
      val substrateMolecule = MoleculeImporter.importMolecule(chemicalSmiles, cleanSmartsFormat) // first import uses cleaned smarts
      Hydrogenize.convertImplicitHToExplicit(substrateMolecule)
      val hydrogenizedSubstrate = MoleculeExporter.exportAsSmarts(substrateMolecule)

      val replacedSubstrate = replaceRWithC(hydrogenizedSubstrate)
      val replacedProduct = replaceRWithC(chemicalSmiles)

      val replacedSubstrateMolecule = MoleculeImporter.importMolecule(replacedSubstrate, MoleculeFormat.smarts)
      val replacedProductMolecule = MoleculeImporter.importMolecule(replacedProduct, cleanSmartsFormat) // first import uses cleaned smarts

      val desaltedSubstrateList = desalter.desaltMoleculeForAbstractReaction(replacedSubstrateMolecule.clone()) // clone to avoid destroying molecule
      val desaltedProductList = desalter.desaltMoleculeForAbstractReaction(replacedProductMolecule.clone()) // clone to avoid destroying molecule

      if (desaltedSubstrateList.size() != 1 || desaltedProductList.size() != 1) {
        // TODO: handle multiple fragments
        println(s"Found multiple fragments in chemical $chemicalSmiles. Don't handle this case yet. Exiting!")
        return None
      }

      // For now we only deal with situations with exactly one product and substrate fragment
      val desaltedSubstrate = MoleculeExporter.exportAsSmarts(desaltedSubstrateList.get(0))
      val desaltedProduct = MoleculeExporter.exportAsSmarts(desaltedProductList.get(0))

      val result = new ChemicalToSubstrateProduct(chemicalId, desaltedSubstrate, desaltedProduct)
      smilesCache.put(chemicalSmiles, Some(result))
      Some(result)
    } catch {
      case e : MolFormatException => {
        smilesCache.put(chemicalSmiles, None)
        None
      }
    }
  }

  def replaceRWithC(chemical: String): String = {
    chemical.replaceAll(REGEX_TO_REPLACE, CARBON_REPLACEMENT)
  }


}
