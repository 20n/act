package com.act.similarity

import java.io.File

import chemaxon.calculations.clean.Cleaner
import chemaxon.formats.MolImporter
import chemaxon.license.LicenseManager
import chemaxon.marvin.alignment.{AlignmentMolecule, AlignmentMoleculeFactory, AlignmentProperties, PairwiseAlignment, PairwiseSimilarity3D}
import chemaxon.struc.Molecule
import com.act.analysis.logp.TSVWriter
import com.act.lcms.db.io.parser.TSVParser
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.JavaConverters._
import scala.io.Source


object compute {
  val ALIGNMENT_MOLECULE_FACTORY = new AlignmentMoleculeFactory()

  def run(license: String, inchi1: String, inchi2: String): Map[String, Double] = {
    LicenseManager.setLicense(license)

    val queryMol: Molecule = MolImporter.importMol(inchi1)
    Cleaner.clean(queryMol, 3)
    val queryFragment = findLargestFragment(queryMol.convertToFrags)
    val am: AlignmentMolecule = ALIGNMENT_MOLECULE_FACTORY.create(
      queryFragment, AlignmentProperties.DegreeOfFreedomType.TRANSLATE_ROTATE)
    val alignment = new PairwiseAlignment
    alignment.setQuery(am)
    val pairwise3d = new PairwiseSimilarity3D()
    pairwise3d.setQuery(queryFragment)

    val targetMol: Molecule = MolImporter.importMol(inchi2)
    Cleaner.clean(targetMol, 3)
    val targetFragment = findLargestFragment(targetMol.convertToFrags())
    val targetAm: AlignmentMolecule = ALIGNMENT_MOLECULE_FACTORY.create(
      targetFragment, AlignmentProperties.DegreeOfFreedomType.TRANSLATE_ROTATE)

    val alignment_score = alignment.similarity(targetAm)
    var threed_score: Double = 0.0
    var threed_tanimoto: Double = 0.0
    try {
      threed_score = pairwise3d.similarity(targetFragment)
      threed_tanimoto = pairwise3d.getShapeTanimoto
    } catch {
      case e : Exception => println(s"Caught exception: ${e.getMessage}")
    }

    Map(
      "alignment_score" -> alignment_score, "alignment_tanimoto" -> alignment.getShapeTanimoto,
      "3d_score" -> threed_score, "3d_tanimoto" -> threed_tanimoto
    )
  }

  def findLargestFragment(fragments: Array[Molecule]): Molecule = {
    fragments.foldLeft(null: Molecule) { (a, m) => if (a == null || a.getAtomCount < m.getAtomCount) m else a}
  }
}

object similarity {
  def main(args: Array[String]) {
    if (args.length != 4) {
      System.err.println("Usage: license_file query_inchi target_tsv output_tsv")
      System.exit(-1)
    }

    val license_file = args(0)
    val query_inchi = args(1) // TODO: make this take a TSV
    val target_tsv = args(2)

    // With help from http://stackoverflow.com/questions/1284423/read-entire-file-in-scala
    val license_source = Source.fromFile(license_file)
    val license_text = try license_source.mkString finally license_source.close()

    // Try to set the license right away to ensure we grabbed the text correctly.
    LicenseManager.setLicense(license_text)

    val tsv_parser = new TSVParser
    tsv_parser.parse(new File(target_tsv))

    val id_inchi_pairs = tsv_parser.getResults.asScala.map(m => (m.get("id"), m.get("inchi")))

    val conf = new SparkConf().setAppName("Spark Similarity Computation")
    conf.getAll.foreach(x => println(s"${x._1}: ${x._2}"))
    val spark = new SparkContext(conf)

    val chems: RDD[(String, String)] = spark.makeRDD(id_inchi_pairs, Math.min(100, id_inchi_pairs.size))

    val resultsRDD: RDD[(String, Map[String, Double])] =
      chems.map(t => (t._1, compute.run(license_text, query_inchi, t._2)))

    val results = resultsRDD.collect()

    val header: List[String] = List("id", "alignment_score", "alignment_tanimoto", "3d_score", "3d_tanimoto")
    val tsvWriter = new TSVWriter[String, String](header.asJava)
    tsvWriter.open(new File(args(3)))

    try {
      results.foreach(v => {
        val row: Map[String, String] = Map("id" -> v._1,
          "alignment_score" -> v._2("alignment_score").toString,
          "alignment_tanimoto" -> v._2("alignment_tanimoto").toString,
          "3d_score" -> v._2("3d_score").toString,
          "3d_tanimoto" -> v._2("3d_tanimoto").toString
        )
        tsvWriter.append(row.asJava)
      })
    } finally {
      tsvWriter.close
    }
  }

}
