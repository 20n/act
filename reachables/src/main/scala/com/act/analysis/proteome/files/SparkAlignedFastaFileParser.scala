package com.act.analysis.proteome.files

import java.io.File

import org.apache.logging.log4j.LogManager
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.{Matrix, Vector, Vectors}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object SparkAlignedFastaFileParser {

  val logger = LogManager.getLogger(getClass.getName)

  val mapCharacter: Map[Char, Int] = Map[Char, Int](
    '-' -> -1,
    'R' -> 0,
    'N' -> 1,
    'D' -> 2,
    'C' -> 3,
    'Q' -> 4,
    'E' -> 5,
    'G' -> 6,
    'H' -> 7,
    'I' -> 8,
    'L' -> 9,
    'K' -> 10,
    'M' -> 11,
    'F' -> 12,
    'P' -> 13,
    'S' -> 14,
    'T' -> 15,
    'W' -> 16,
    'Y' -> 17,
    'V' -> 18,
    'A' -> 19
  )
  private val NEW_PROTEIN_INDICATOR = ">"

  def parseFile(openFile: File): Map[Int, ListBuffer[Long]] = {
    val alignedProteinSequences = ListBuffer[String]()
    val alignedProteinHeaders = ListBuffer[String]()

    val lines = scala.io.Source.fromFile(openFile).getLines()

    @tailrec
    def parser(iterator: Iterator[String]): Unit = {
      val header = iterator.next().mkString
      val proteinSplits = iterator.span(!_.startsWith(NEW_PROTEIN_INDICATOR))
      val protein: String = proteinSplits._1.mkString

      alignedProteinHeaders.append(header)
      alignedProteinSequences.append(protein)

      if (proteinSplits._2.hasNext) parser(proteinSplits._2)
    }
    parser(lines)

    /*

      Cluster sequences
     */
    val predictionsByClustering = createSparkRdd(alignedProteinSequences.toList)

    /*

      Get all the substrates for each cluster
     */
    // Map all the db ids to which cluster they are in
    val clusterMap = mutable.HashMap[Int, ListBuffer[Long]]()
    for (i <- predictionsByClustering.indices) {
      val cluster = predictionsByClustering(i)

      if (clusterMap.get(cluster).isEmpty) {
        clusterMap(cluster) = ListBuffer[Long]()
      }

      // Header format:
      // >NAME: None | EC: 3.1.3.4 | DB_ID: 101128
      val sequenceDbId = alignedProteinHeaders(i).split("DB_ID: ")(1)
      clusterMap(cluster).append(sequenceDbId.toLong)
    }

    clusterMap.toMap
  }

  def createSparkRdd(proteinAlignments: List[String]): List[Int] = {
    val conf = new SparkConf().setAppName("Spark Proteins").setMaster("local")
    conf.getAll.foreach(x => logger.info(s"Spark config pair: ${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)
    println("Hello!")

    val a = Array.ofDim[Double](proteinAlignments.head.length * 20, proteinAlignments.length)

    for (i <- proteinAlignments.indices) {
      for (j <- proteinAlignments(i).indices) {
        val shiftValue: Int = mapCharacter(proteinAlignments(i)(j))
        if (shiftValue >= 0) {
          val aIndex: Int = j * 20 + shiftValue
          // We do it like this so we only need to transpose once.
          a(aIndex)(i) = 1.0
        }
      }
    }

    // If this is 0.1, 10% of the entries must be 1, for example.
    val portionUngappedNeeded = 0.1
    // Tranpose at end to make vectorization a simple map.
    val filteredA = a.filter(column => column.sum[Double] > proteinAlignments.length * portionUngappedNeeded).transpose
    val vectorize: Seq[Vector] = filteredA.map(x => Vectors.dense(x)).toSeq

    val rows = spark.makeRDD[Vector](vectorize)
    val sm: RowMatrix = new RowMatrix(rows)

    val principleComponents = sm.computePrincipalComponents(filteredA.length)

    val pcaRdd = toRDD(principleComponents, spark)

    val numClusters = 2
    val numIterations = 20
    val clusters = KMeans.train(pcaRdd, numClusters, numIterations)

    val WSSSE = clusters.computeCost(pcaRdd)
    println("Within Set Sum of Squared Errors = " + WSSSE)

  }

  private def toRDD(m: Matrix, sparkContext: SparkContext): RDD[Vector] = {
    val columns = m.toArray.grouped(m.numRows)
    val rows = columns.toSeq.transpose // Skip this if you want a column-major RDD.
    val vectors = rows.map(row => Vectors.dense(row.toArray))
    sparkContext.makeRDD(vectors)
  }
}
