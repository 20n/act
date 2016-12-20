package com.act.biointerpretation.metadata

import scala.collection.JavaConverters._

object RankPathway {
  val rankingTable: Map[Long, Int] = ProteinMetadataComparator.createProteinMetadataTable().asScala.toMap[Long, Int]

  def main(args: Array[String]) {






  }
}
