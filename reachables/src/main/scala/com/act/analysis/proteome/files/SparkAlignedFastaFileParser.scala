package com.act.analysis.proteome.files

import java.io.File

import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.{SparkConf, SparkContext}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

object SparkAlignedFastaFileParser {
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

  def parseFile(openFile: File): Unit = {
    val alignedProteinSequences = ListBuffer[String]()

    val lines = scala.io.Source.fromFile(openFile).getLines()

    @tailrec
    def parser(iterator: Iterator[String]): Unit = {
      val header = iterator.next().mkString
      val proteinSplits = iterator.span(!_.startsWith(NEW_PROTEIN_INDICATOR))
      val protein: String = proteinSplits._1.mkString

      alignedProteinSequences.append(protein)

      if (proteinSplits._2.hasNext) parser(proteinSplits._2)
    }
    parser(lines)

    createSparkRdd(alignedProteinSequences.toList)
  }

  def createSparkRdd(proteinAlignments: List[String]): Unit = {
    val conf = new SparkConf().setAppName("Spark Proteins").setMaster("local")
    val spark = new SparkContext(conf)

    val a = Array.ofDim[Double](proteinAlignments.head.length * 20, proteinAlignments.length)
    println(a.length)
    println(a(0).length)

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

    val portionUngappedNeeded = 0.1
    val filteredA = a.filter(column => column.sum[Double] > proteinAlignments.length * portionUngappedNeeded).transpose
    val vectorize: Seq[Vector] = filteredA.map(x => Vectors.dense(x)).toSeq

    val rows = spark.makeRDD[Vector](vectorize)
    val sm: RowMatrix = new RowMatrix(rows)

    println(sm.computePrincipalComponents(filteredA.length))


  }
}
