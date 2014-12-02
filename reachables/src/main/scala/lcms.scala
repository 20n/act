package com.act.lcms

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.mllib.regression.RidgeRegressionWithSGD
import org.apache.spark.mllib.regression.LassoWithSGD
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.{Vectors, Vector}

object lcms {

  def main(args: Array[String]) {
    if (args.length < 2) {
      println("Usage: <spark verbiage> predictRT retention-training-data.csv num_iter,stepsz,find_intercept")
      System.exit(-1)
    }

    exec(args)
  }

  def exec(args: Array[String]) {
    val cmd = args(0)
    val datafile = args(1)
    val param = {
      if (args.length > 2) 
        args(2).split(",") // iterations,step,findIntercept
      else 
        Array("10000", "1", "true")
    }

    // The number of slices is the size of each unit of work assigned
    // to each worker. The number of workers is defined by the 
    // spark-submit script. Instances below:
    // --master local[1]: one worker thread on localhost
    // --master local[4]: four worker theads on localhost
    // --master spark://host:port where the master EC2 location is from:
    // ./spark-ec2 -k <kpair> -i <kfile> -s <#slaves> launch <cluster-name>

    // E.g., of spark submit are:
    // LOCAL: ~/Desktop/spark-1.0.2-bin-hadoop2/bin/spark-submit --class "com.act.lcms.lcms" --master local[1] target/scala-2.10/reachables-assembly-0.1.jar predictRT ~/Desktop/observed-retention-times/RT-observed-normalized-training-data.csv 10000,1,true -- with features(polar, logP, Mw) 
    // LinearRegressionWithSGD  MSE = 3363 (10k iter)
    //          MSE 8178 (10 iter), 5646 (100), 4280 (1k), 3267 (20k/40k)
    // LassoWithSGD             MSE = 3607 (10k iter)
    //          MSE 8236 (10 iter), 5660 (100), 4434 (1k), 3481 (20k/40k)  
    // RidgeRegressionWithSGD   MSE = 22554 (10k iter)
    //          MSE does not change for iterations 10, 100, 1k, 10k

    val slices = if (args.length > 3) args(3).toInt else 2

    val conf = new SparkConf().setAppName("Spark LCMS analytics")
    val spark = new SparkContext(conf)

    cmd match {
      case "predictRT" => {
        // Load and parse the data
        val data: RDD[String] = spark.textFile(datafile, slices).cache() 

        val trainingData = data.map(predictRTFeaturesAndLabel)

        // Build the model
        val iter = param(0).toInt
        val stepsz = param(1).toDouble
        val findIntercept = param(2).toBoolean

        // Linear works best.... (Lasso comparable: see # above)
        // val regression = new LinearRegressionWithSGD
        val regression = new LassoWithSGD 
        // val regression = new RidgeRegressionWithSGD 

        regression.setIntercept(findIntercept)
        regression.optimizer.setNumIterations(iter).setStepSize(stepsz)
        val model = regression.run(trainingData)
        
        // Evaluate model on training examples and compute training error
        val predictions = trainingData.map{ x => (x.label, model.predict(x.features)) }
        val mse = predictions.map{
          case(ob, pr) => {
            val e_sq = math.pow((ob - pr), 2)
            println("E^2 = " + e_sq + " \t\t obs=" + ob + " vs pred=" + pr)
            e_sq
          }
        }.mean()

        println("training Mean Squared Error = " + mse)
        println("Model: Intercept: " + model.intercept + " Weights: " + model.weights) 

      }
    }

    def predictRTFeaturesAndLabel(in: String) = {
      // format:
      // num,polarizability,logP,e^logP,Mw,observedRT
      // ignore col(0) -- just the row number
      // col(1,2,3,4)  -- features: 3 = e^2, so ignore dependent 3
      // col(5)        -- label
      val col = in.split(',')
      val label = col(5).toDouble
      val feat  = Array(col(1), col(2), col(4))
      // val feat  = col.slice(1,5)
      val features = Vectors.dense(feat.map(_.toDouble))
      val datum = LabeledPoint(label, features)

      datum
    }
  }

}
