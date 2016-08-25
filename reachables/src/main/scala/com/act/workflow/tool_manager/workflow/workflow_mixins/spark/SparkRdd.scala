package com.act.workflow.tool_manager.workflow.workflow_mixins.spark

import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.{Matrix, Vectors, Vector => SparkVector}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

trait SparkRdd {

  /**
    * Defines a spark context
    *
    * @param sparkAppName The name of the spark application
    * @param sparkMaster  What the master of the spark context is
    *
    * @return
    */
  def sparkDefineContext(sparkAppName: String, sparkMaster: String = "local"): SparkContext = {
    val conf = new SparkConf().setAppName(sparkAppName).setMaster(sparkMaster)
    new SparkContext(conf)
  }

  /**
    * Stops a supplied spark context
    *
    * @param spark Current spark context
    */
  def sparkStopContext(spark: SparkContext): Unit = {
    spark.stop()
  }

  /**
    * Takes in a list of aligned protein strings and outputs a row
    * matrix containing those clusters after the've been encoded using a characterMap.
    * Also filters the columns to remove highly ungapped regions.
    *
    * @param spark                             Spark context that has been established
    * @param proteinAlignments                 List of protein alignments
    * @param characterMap                      The mapping between characters to their integer location.  One hot encodes
    * @param percentOfRowsThatAreNotZeroToKeep The percent of rows that need to be nonzero before we keep that column
    *
    * @return A row matrix constructed under the above circumstances
    */
  def sparkOneHotEncodeProteinAlignments(spark: SparkContext)
                                        (proteinAlignments: List[String],
                           characterMap: Map[Char, Int],
                           percentOfRowsThatAreNotZeroToKeep: Double): RowMatrix = {
    require(percentOfRowsThatAreNotZeroToKeep >= 0 && percentOfRowsThatAreNotZeroToKeep <= 100,
      s"Percent of row that is nonzero prior to keeping must be between 0 and 100.  " +
        s"Supplied value was $percentOfRowsThatAreNotZeroToKeep")

    // Assumes characterMap starts at 0.
    val oneHotCount = characterMap.values.max + 1

    // Precreate the array we will fill in
    val oneHotEncodingArray = Array.ofDim[Double](proteinAlignments.head.length * oneHotCount, proteinAlignments.length)

    // For each protein
    for (proteinIndex <- proteinAlignments.indices) {
      // For each character within that protein
      for (proteinCharacterIndex <- proteinAlignments(proteinIndex).indices) {

        /*
          A one hot encoded vector is just an array of size alphabet, so if our alphabet is A,B,C we'd have an array
          that is [0, 0, 0] initially.  Then, we assign one value to each of those members (A = 0, B = 1, C = 2).
          Then, if we see that character we encode the index of the array of previously all 0 array as 1.
          A = [1, 0, 0]
          B = [0, 1, 0]
          C = [0, 0, 1]

          Thus, shift value is just the index of the one-hot encoding.
          Each member of the alphabet shifts where we place the 1 by a number, from 0 to len(alphabet) - 1.
         */
        val shiftValue: Int = characterMap(proteinAlignments(proteinIndex)(proteinCharacterIndex))

        // Shift value of -1 designates a gap, and thus the entire array should be 0.
        if (shiftValue >= 0) {
          /*
            Initial array: [0,0,0, 0,0,0]
            AB means that the first character (Protein Character Index = 0) is 0 and the second character
            (Protein Character Index = 2) is 1
            If our alphabet size is 3, we've designated a 3 wide array for each character.

            Thus, our first math would be:
              0*3 + 0 = 0, so our array looks like
              Array Update: [1,0,0, 0,0,0]
            Our second round would be:
              1*3 + 1 = 4, so our array looks like
              Array Update: [1,0,0, 0,1,0]
              Which matches our expectation of the one-hot encoding.
          */
          val aIndex: Int = proteinCharacterIndex * oneHotCount + shiftValue
          oneHotEncodingArray(aIndex)(proteinIndex) = 1.0
        }
      }
    }

    /*
      Prepare and Filter RDD
     */
    val gapThreshold = proteinAlignments.length * (percentOfRowsThatAreNotZeroToKeep / 100.0)

    // Because we one hot encode with the value 1, the sum of a column divided
    // by its length is also the percent of rows that have a value.
    val filteredA = oneHotEncodingArray filter (column => column.sum[Double] > gapThreshold)

    // Convert array to a sequence of Spark's vectors
    val vectorize: Seq[SparkVector] = filteredA map Vectors.dense toSeq

    // Turn into a RowMatrix so we can use it downstream
    val rows = spark.makeRDD[SparkVector](vectorize)
    new RowMatrix(rows)
  }


  /**
    * Performs PCA on a given matrix
    *
    * @param inputMatrix        The given matrix described above
    * @param spark              spark context that has been established.
    * @param numberOfComponents How many principle components should be pulled out
    *
    * @return An RDD that has been converted and now holds the principle components of the matrix
    */
  def sparkPca(spark: SparkContext)(inputMatrix: RowMatrix, numberOfComponents: Int = 2): RDD[SparkVector] = {
    require(inputMatrix.numCols() > numberOfComponents,
      s"Number of components in the PCA greater than number of columns that exist in the " +
        s"input matrix. Number of components asked for = $numberOfComponents, number of columns = ${inputMatrix.numCols()}")

    // Pull out the principle components
    val principleComponents = inputMatrix.computePrincipalComponents(numberOfComponents)
    toRDD(spark)(principleComponents)
  }

  /**
    * Utility function to convert from a matrix to an RDD
    *
    * @param matrix       The matrix to convert to RDD
    * @param sparkContext The spark context to use
    *
    * @return
    */
  private def toRDD(sparkContext: SparkContext)(matrix: Matrix): RDD[SparkVector] = {
    val columns = matrix.toArray.grouped(matrix.numRows)
    val rows = columns.toSeq.transpose
    val vectors = rows.map(row => Vectors.dense(row.toArray))
    sparkContext.makeRDD(vectors)
  }

  /**
    * Performs Kmeans clustering on an input RDD
    *
    * @param inputRdd           The input matrix to cluster
    * @param numberOfClusters   How many clusters to make
    * @param numberOfIterations The number of clustering iterations to perform
    *
    * @return A list of cluster assignments for each row.
    */
  def sparkKmeansCluster(inputRdd: RDD[SparkVector], numberOfClusters: Int, numberOfIterations: Int = 200): List[Int] = {
    val clusters = KMeans.train(inputRdd, numberOfClusters, numberOfIterations)

    val predictions: RDD[Int] = clusters.predict(inputRdd)
    predictions.toLocalIterator.toList
  }
}
