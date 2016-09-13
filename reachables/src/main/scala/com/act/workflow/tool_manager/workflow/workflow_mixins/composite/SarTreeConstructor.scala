package com.act.workflow.tool_manager.workflow.workflow_mixins.composite

import java.io.File

import act.server.MongoDB
import chemaxon.clustering.LibraryMCS
import chemaxon.struc.Molecule
import com.act.analysis.chemicals.ChemicalSimilarity
import com.act.analysis.proteome.files.AlignedFastaFileParser
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.sarinference.{SarTree, SarTreeNode}
import com.act.utils.TSVWriter
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ReactionKeywords
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db.SequenceIdToRxnInchis
import com.act.workflow.tool_manager.workflow.workflow_mixins.spark.SparkRdd
import org.apache.spark.mllib.linalg.{Vector => SparkVector}
import org.apache.spark.rdd.RDD

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.parallel.immutable.{ParMap, ParSeq}


trait SarTreeConstructor extends SequenceIdToRxnInchis with SparkRdd {

  /**
    * Takes in an aligned protein file and an inchi file.
    * From this, scores each inchi based on the sequence clustering of the input aligned protein file.
    *
    * The values below with defaults were found via trial and error, change at your own peril.
    *
    * @param alignedProteinFile                Previously aligned protein sequence file
    * @param inchisToScore                     InchiCorpus style inchi file
    * @param principleComponentCount           The number of principle components to use for clustering
    * @param kMeansClusterNumber               The number of clusters to construct from the sequences using kMeans
    * @param kMeansNumberOfIterations          The number of iterations kMeans should run for
    * @param percentOfRowsThatAreNotZeroToKeep The percent of the rows in a column that should be nonzero
    *                                          for us to still retain that column
    */
  def constructSarTreesFromAlignedFasta(alignedProteinFile: File,
                                        inchisToScore: File,
                                        outputFile: File,
                                        principleComponentCount: Int = 5,
                                        kMeansClusterNumber: Int = 5,
                                        kMeansNumberOfIterations: Int = 200,
                                        percentOfRowsThatAreNotZeroToKeep: Double = 30.0)(): Unit = {

    val corpy = new L2InchiCorpus()
    corpy.loadCorpus(inchisToScore)
    val inchis = corpy.getInchiList

    if (inchis.isEmpty) throw new RuntimeException("After filtering for InChIs that were marked as hits, " +
      "we found no InChIs leftover.  Please ensure that your L2PredictionCorpus Projector Name " +
      "contains HIT if the LCMS marked it as a hit and that you have input a serialized L2PredictionCorpus.")

    /*
      Parse and cluster aligned fasta file
     */
    // String 1 is header, string 2 is sequence
    val parsedFile: List[(String, String)] = AlignedFastaFileParser.parseFile(alignedProteinFile)
    val headers: List[String] = parsedFile map (_._1)
    val sequences: List[String] = parsedFile map (_._2)

    /*
      Use spark to cluster the sequences.
      Spark has flexible linear algebra and machine learning libraries that we can leverage.
      For example, it allows us to do PCA, Kmeans, and manipulate arrays easily.
     */
    val sparkContext = sparkDefineContext("Sequence Clustering", "local")

    val sparkRdd =
      sparkOneHotEncodeProteinAlignments(sparkContext)(sequences, AlignedFastaFileParser.characterMap, percentOfRowsThatAreNotZeroToKeep)
    val principleComponents: RDD[SparkVector] = sparkPca(sparkContext)(sparkRdd, principleComponentCount)

    // Ordered list of clusters that will map onto the sequence
    val clusters: List[Int] = sparkKmeansCluster(principleComponents, kMeansClusterNumber, kMeansNumberOfIterations)

    // Shutdown spark as we no longer need it.
    sparkStopContext(sparkContext)

    /*
      Score inchis based on the above clustering
     */

    // Map the cluster to the sequence database ID
    /*
      1) Group by the cluster
      2) Unwrap the header by mapping each value in it
      3) For each value in it, split it based on the header format below to just extract the DB_ID

      Header format:
      >NAME: None | EC: 3.1.3.4 | DB_ID: 101128
     */
    val clusterMap: Map[Int, List[Long]] = (clusters zip headers) groupBy (_._1) map {
      case (cluster, header) => (cluster, header map (_._2.split("DB_ID: ")(1) toLong))
    }

    /*
     Use the same Mongo connection for each SAR creation

     We choose to use the reaction products here because we are looking at LCMS results,
     which contain the end-result of a reaction, not the starting point.
     Therefore, it is more biologically relevant to look at the products and to
     find similar products than to look at the substrates and expect to find them in a sample.
     */
    val sarCreator: ((List[Long]) => SarTree) =
      createSarTreeFromSequencesIds(connectToMongoDatabase(), ReactionKeywords.PRODUCTS.toString) _

    // Collect all the SAR trees that were successfully created.
    val clusteredSars: Map[Int, SarTree] = clusterMap mapValues sarCreator

    // Score inchis by the clusters
    val inchisCorpus = new L2InchiCorpus(inchis)
    val results = scoreInchiCorpus(clusteredSars, inchisCorpus)

    sortInDescendingOrderAndWriteToTsv(results, outputFile)
  }

  def sortInDescendingOrderAndWriteToTsv(inchiScores: Map[String, Double], outputFile: File): Unit = {
    // Sort ascending
    val writtenMap = ListMap(inchiScores.toSeq.sortBy(-_._2): _*)

    // Write to file, use TSV because InChIs don't play well with csvs
    val Inchi = "InChI"
    val RawScore = "Raw Score"
    val RawLogScore = "Raw Log Score"
    val NormalizedScore = "Normalized Score"
    val NormalizedLogScore = "Normalized Log Score"
    val Rank = "Rank"
    val writer =
      new TSVWriter[String, String](List(Inchi, RawScore, RawLogScore, NormalizedScore, NormalizedLogScore, Rank))
    writer.open(outputFile)

    val largestScore: Double = writtenMap.values.max
    val largestLogScore: Double = Math.log(largestScore)
    var counter = 1
    for ((key, value) <- writtenMap) {
      // Normalize based on largest score to 100
      val logValue = Math.log(value)

      val row = Map(
        Inchi -> key,
        RawScore -> f"$value%.6f",
        RawLogScore -> f"$logValue%.6f",
        NormalizedScore -> f"${100.0 * value / largestScore}%.6f",
        NormalizedLogScore -> f"${100.0 * logValue / largestLogScore}%.6f",
        Rank -> s"$counter"
      )

      writer.append(row.asJava)
      counter += 1
    }
    writer.close()
  }
  /**
    * Takes in a set of sequence IDs and creates a Sar Tree from the
    *
    * @param mongoConnection         Connection to the Mongo database
    * @param chemicalKeywordToLookAt Which keyword to look at in the reaction DB
    * @param sequenceIds             A list of all the sequence Ids
    *
    * @return
    */
  def createSarTreeFromSequencesIds(mongoConnection: MongoDB, chemicalKeywordToLookAt: String)
                                   (sequenceIds: List[Long]): SarTree = {
    val inchis = sequencesIdsToInchis(mongoConnection)(sequenceIds.toSet, chemicalKeywordToLookAt)
    val clusterSarTree = new SarTree()
    clusterSarTree.buildByClustering(new LibraryMCS(), new L2InchiCorpus(inchis).getMolecules)
    clusterSarTree
  }

  /**
    * Takes in every cluster then scores and sums them.
    *
    * @param sarTreeClusters A map of all the clusters and their respective sar tree
    * @param inchiCorpus     The inchi corpus we are scoring
    *
    * @return A map of Inchi -> Score, where the score is a single Double.
    */
  def scoreInchiCorpus(sarTreeClusters: Map[Int, SarTree], inchiCorpus: L2InchiCorpus): Map[String, Double] = {
    // Score each cluster and reduce the scoring down into the sum of all the clusters
    val combinedInchiScore: ParSeq[ParMap[String, Double]] = sarTreeClusters.par.map({ case (key, value) =>
      logger.info(s"Started scoring corpus $key.")
      val scores = scoreCorpusAgainstSarTree(value, inchiCorpus)
      logger.info(s"Finished scoring corpus $key")
      scores
    }) toSeq

    // All keys are the same so we are safe to use just the first to merge on
    val combined = combinedInchiScore.head.keys map { key =>
      // Key + some aggregation of all the inchi scores
      (key, combinedInchiScore.flatMap(_.get(key)).sum)
    } toMap

    // Convert back to non parallel form.
    combined.seq
  }

  /**
    * Score a corpus of inchis against a sar tree
    *
    * @param sarTree     Sar tree to score inchis against
    * @param inchiCorpus The inchi corpus we are scoring.
    *
    * @return
    */
  def scoreCorpusAgainstSarTree(sarTree: SarTree, inchiCorpus: L2InchiCorpus): ParMap[String, Double] = {
    val inchiToMoleculeMap: Map[String, Molecule] = (inchiCorpus.getInchiList zip inchiCorpus.getMolecules) toMap

    val inchiScorer: Molecule => Double = scoreInchiAgainstSarTree(sarTree, sarTree.getRootNodes.toList)_

    val scoredInchis: ParMap[String, Double] = inchiToMoleculeMap.par.map {
      case (key, value) => (key, inchiScorer(value))
    }

    scoredInchis
  }


  /**
    * Score individual inchi between 0 and 100.
    * 100 means it is a substrate, 0 means it didn't match anything.
    * Numbers between this indicate different levels of depth achieved.
    *
    * @param sarTree          The input SarTree to check against
    * @param currentLevelList The remaining SarTreeNodes that haven't been invalidated.
    * @param molecule         Which molecule to check against the Sar Tree
    *
    * @return
    */
  def scoreInchiAgainstSarTree(sarTree: SarTree, currentLevelList: List[SarTreeNode], molecule: Molecule): Double = {
    val nodesMatchingSar = currentLevelList filter (_.getSar.test(List[Molecule](molecule)))

    // Arbitrary score value
    val baseAdd = 10.0

    // No matches
    nodesMatchingSar.isEmpty match {
      case true => baseAdd
      case false =>
        // See how any remaining nodes score upon further traversal.
        val deeperScores: List[Double] = nodesMatchingSar map (node =>
          // Leaf Node
          if (sarTree.getChildren(node).isEmpty) {
            // Get really excited if we see an exact match
            if (node.getSubstructure.equals(molecule)) {
              baseAdd * baseAdd * baseAdd
            } else {
              // TODO Add a heuristic in to filter out REALLY REALLY large and general substrates.
              // Slightly penalize if overshoot substrate
              -baseAdd
            }
          } else {
            // Nodes still remain, see how deep prior to hitting a nothing
            scoreInchiAgainstSarTree(sarTree, sarTree.getChildren(node).toList, molecule)
          })
        baseAdd + deeperScores.sum
    }
  }
}
