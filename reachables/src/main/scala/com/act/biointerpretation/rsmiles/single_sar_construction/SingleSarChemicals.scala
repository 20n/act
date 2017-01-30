package com.act.biointerpretation.rsmiles.single_sar_construction

import act.server.MongoDB
import chemaxon.formats.{MolImporter, MolFormatException}
import chemaxon.standardizer.Standardizer
import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.analysis.chemicals.molecules.MoleculeFormat.Cleaning
import com.act.biointerpretation.Utils.ReactionProjector
import com.act.biointerpretation.desalting.Desalter
import com.act.biointerpretation.rsmiles.chemicals.JsonInformationTypes.{ChemicalInformation, AbstractChemicalInfo}
import com.act.biointerpretation.rsmiles.chemicals.abstract_chemicals.AbstractReactions._
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ChemicalKeywords
import com.mongodb.DBObject
import org.apache.log4j.LogManager

import scala.collection.mutable
import scala.collection.parallel.immutable.{ParMap, ParSeq}

/**
  * Finds abstract chemicals in the DB and processes their R-smiles for later use in SAR generation
  */
class SingleSarChemicals(mongoDb: MongoDB) {
  val logger = LogManager.getLogger(getClass)

  private val REGEX_TO_SEARCH = "\\[R[0-9]*\\]"
  private val REGEX_TO_REPLACE = "\\[[^\\[]*R(\\]|[^euh][^\\]]*\\])"
  private val CARBON_REPLACEMENT = "\\[Au\\]"

  val cleanSmartsFormat = new MoleculeFormat.MoleculeFormatType(MoleculeFormat.smarts,
    List(Cleaning.clean2d, Cleaning.aromatize))

  val desalter: Desalter = new Desalter(new ReactionProjector())
  desalter.initReactors()

  val standardizer: Standardizer = new Standardizer("addexplicith");

  // There are many, many repeated abstract smiles in the DB
  // This cache ensures we only process each one once
  val smilesCache : mutable.Map[String, Option[AbstractChemicalInfo]] =
    new mutable.HashMap[String, Option[AbstractChemicalInfo]]()

  /**
    * Returns a list of the AbstractChemicalInfo corresponding to each abstract chemical
    * Abstract chemicals are detected by containing the regex REGEX_TO_SEARCH, and being importable by chemaxon.
    * The importability ensures that "smiles" with strings like "a nucleobase" in them are not processed further.
    */
  def getAbstractChemicals() : List[AbstractChemicalInfo] = {
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
    */
    var counter = 0
    val abtractChemicalList: List[AbstractChemicalInfo] = result.flatMap(dbObj => {
      counter = counter + 1
      if (counter % 1000 == 0) {
        println(s"Processed $counter chemicals.")
      }
      getAbstractChemicalInfo(dbObj)
    }).toList

    logger.info(s"Finished finding abstract chemicals. Found ${abtractChemicalList.size}")

    abtractChemicalList
  }

  /**
    * Gets abstract chemical info by pulling the appropriate fields from the db object and calling
    * calculateConcreteSubstrateAndProduct
    */
  def getAbstractChemicalInfo(dbChemical : DBObject) : Option[AbstractChemicalInfo] = {

    val chemicalId = dbChemical.get("_id").asInstanceOf[Long].toInt
    val smiles = dbChemical.get("SMILES").asInstanceOf[String]

    val result : Option[AbstractChemicalInfo] = calculateConcreteSubstrateAndProduct(chemicalId, smiles)

    if (result.isDefined) {
      return Some(result.get)
    }
    None
  }

  /**
    * Calculates the concrete smiles that should be used for the given chemical in order to atom map an abstract
    * reaction. Since this is different for a substrate and a product, we calculate both versions here for later use
    * in either side of a reaction.
    * The pipeline we use is as follows:
    * SUBSTRATE pipeline: import smiles, make hydrogens explicit, export to string, replace R groups with C's, import to molecule, desalt the molecule, export to smarts
    * PRODUCT pipeline: replace R groups with C's, import molecule, desalt the molecule, export to smarts
    * The difference is that, for substrates, we need to make all H's explicit except those touching where the abstract group was
    *   This ensures that the variation in concrete molecules that will match the generated substructure can only occur at the site of abstraction
    * For products, this is not a concern, and explicit hydrogens can actually hinder our ability to match products of a projection to DB products. So, we leave hydrogens implicit.
    * @param chemicalId The ID of the chemical.
    * @param chemicalSmiles The smiles of the chemical from the DB.
    * @return An object grouping the chemical Id to the modified smiles to be used if this chemical is a substrate
    *         or product of an abstract reaction.
    */
  def calculateConcreteSubstrateAndProduct(chemicalId : Int, chemicalSmiles : String): Option[AbstractChemicalInfo] = {
    // Use cache to avoid repeat calculations
    val cachedResult = smilesCache.get(chemicalSmiles)
    if (cachedResult.isDefined) {
      val cached = cachedResult.get
      if (cached.isDefined) {
        val info = cached.get
        return Some(AbstractChemicalInfo(chemicalId, info.dbSmiles, info.asSubstrate, info.asProduct))
      }
      return None
    }

    try {
      println("Original R-smiles: " + chemicalSmiles)

      //Replace the R with Gold (Au)
      val goldSmiles = replaceRWithGold(chemicalSmiles)

      println("Goldsmiles: " + goldSmiles)
      
      //Load the gold-modified substrate into ChemAxon as a concrete molecule, not as SMARTs
      val substrateMolecule = MolImporter.importMol(goldSmiles)

      // Make substrate hydrogens explicit
      standardizer.standardize(substrateMolecule)

      // Desalt the substrate
      val desaltedSubstrateList = desalter.desaltMoleculeForAbstractReaction(substrateMolecule.clone()) // clone to avoid destroying molecule
      if (desaltedSubstrateList.size() != 1) {
        // I haven't seen this case so far
        println(s"Found multiple fragments after desalting chemical $chemicalSmiles. Don't handle this case yet. Exiting!")
        return None
      }
      val desaltedSubstrate = desaltedSubstrateList.get(0)
      
      //Export the substrate as SMILES (I'ts still concrete)
      val desaltedSmiles = MoleculeExporter.exportAsSmiles(desaltedSubstrate)

      println("Hydrogenized and desalted smiles: " + desaltedSmiles)

      // Replace Gold with Carbon, which will convert it to abstract SMARTS
      val finalSmarts = replaceGoldWithCarbon(desaltedSmiles)

      println("Final SMARTS: " + finalSmarts)

      val result = new AbstractChemicalInfo(chemicalId, chemicalSmiles, finalSmarts, finalSmarts)
      smilesCache.put(chemicalSmiles, Some(result))
      return Some(result)
    } catch {
      case e : MolFormatException => {
        smilesCache.put(chemicalSmiles, None)
        return None
      }
    }
  }

  def replaceRWithGold(chemical: String): String = {
    chemical.replaceAll(REGEX_TO_REPLACE, CARBON_REPLACEMENT)
  }

  def replaceGoldWithCarbon(chemical: String): String = {
    chemical.replaceAll("Au", "C")
  }

}
