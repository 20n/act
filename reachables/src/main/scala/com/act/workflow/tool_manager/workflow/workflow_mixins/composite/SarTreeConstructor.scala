package com.act.workflow.tool_manager.workflow.workflow_mixins.composite

import java.io.File

import com.act.analysis.proteome.files.SparkAlignedFastaFileParser
import com.act.biointerpretation.sarinference.SarTree
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.chemical_db.ChemicalDatabaseKeywords
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.reaction_db.ReactionDatabaseKeywords
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.sequence_db.QueryByReactionId
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConversions._
import scala.collection.mutable

trait SarTreeConstructor extends QueryByReactionId with ReactionDatabaseKeywords with ChemicalDatabaseKeywords {
  val logger = LogManager.getLogger(getClass)
  private val NEW_PROTEIN_INDICATOR = ">"

  def constructSarTreesFromAlignedFasta(alignedFastaFile: File)(): Unit = {
    val clusterToSequenceId = SparkAlignedFastaFileParser.parseFile(alignedFastaFile)

    /*
db_id = line["proteins"].split("DB_ID: ")[1].strip()
result = seq.find_one({"_id": int(db_id)}, {"rxn_refs": 1})
x = result["rxn_refs"]
reaction_list.append(x[0])

substrates = rxn.find_one({"_id": int(x[0])}, {"enz_summary.substrates": 1})
chem_id = substrates["enz_summary"]["substrates"][0]["pubchem"]

inchi = chem.find_one({"_id": chem_id}, {"InChI": 1})
if "FAKE" in inchi["InChI"]:
    inchi_list.append("")
else:
    inchi_list.append(inchi["InChI"])
    cluster_list[cluster].add(inchi["InChI"])

    inchi_set.add(inchi["InChI"])
 */
    val mongoConnection = connectToMongoDatabase()

    // For each cluster
    for (key <- clusterToSequenceId.keys) {
      // Get all the sequences and their reactions
      val returnValues: Iterator[DBObject] =
        querySequencesMatchingReactionIdIterator(clusterToSequenceId(key).toList, mongoConnection, List(SEQUENCE_DB_KEYWORD_RXN_REFS))

      val rxnRefSet = mutable.Set[Long]()
      for (doc <- returnValues) {
        val rxnRefs = doc.get(SEQUENCE_DB_KEYWORD_RXN_REFS).asInstanceOf[BasicDBList]
        for (rxn <- rxnRefs.listIterator().toIterator) {
          rxnRefSet.add(rxn.asInstanceOf[Long])
        }
      }

      // With each of those reactions, get the substrate's chem ids
      val substrateSet = mutable.Set[Long]()
      for (reaction <- rxnRefSet) {
        val key = new BasicDBObject(REACTION_DB_KEYWORD_ID, reaction)
        val filter = new BasicDBObject(s"$REACTION_DB_KEYWORD_ENZ_SUMMARY.$REACTION_DB_KEYWORD_SUBSTRATES", 1)
        val iterator: Iterator[DBObject] = mongoQueryReactions(mongoConnection, key, filter)

        for (substrate: DBObject <- iterator) {
          val enzSummary = substrate.get(REACTION_DB_KEYWORD_ENZ_SUMMARY).asInstanceOf[BasicDBObject]
          val substrateList = enzSummary.get(REACTION_DB_KEYWORD_SUBSTRATES).asInstanceOf[BasicDBList]
          if (substrateList == null) {
            logger.error(s"Number of substrates for reaction is 0.  Reaction is $reaction")
          } else {
            for (substrate <- substrateList.listIterator().toIterator) {
              println(substrate)
              val chemId = substrate.asInstanceOf[BasicDBObject].get(REACTION_DB_KEYWORD_PUBCHEM).asInstanceOf[Long]
              substrateSet.add(chemId)
            }
          }
        }
      }

      // With all the substrates in hand, we now need to find the inchis!
      val inchiSet = mutable.Set[String]()
      for (substrate <- substrateSet) {
        val key = new BasicDBObject(CHEMICAL_DB_KEYWORD_ID, substrate)
        val filter = new BasicDBObject(CHEMICAL_DB_KEYWORD_INCHI, 1)
        val iterator: Iterator[DBObject] = mongoQueryChemicals(mongoConnection, key, filter)

        // Only except 1 item from iterator
        for (chemical: DBObject <- iterator) {
          val inchi = chemical.get(CHEMICAL_DB_KEYWORD_INCHI).asInstanceOf[String]
          if (!inchi.contains("FAKE")) {
            inchiSet.add(inchi)
          }
        }
      }

      // Construct SAR tree
      val clusterSarTree = new SarTree()
    }
  }
}
