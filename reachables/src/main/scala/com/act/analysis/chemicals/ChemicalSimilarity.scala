package com.act.analysis.chemicals

import breeze.linalg.*
import chemaxon.descriptors.{CFParameters, ChemicalFingerprint, SimilarityCalculator, SimilarityCalculatorFactory}
import chemaxon.struc.Molecule
import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.similarity.similarity

import scala.collection.concurrent.TrieMap

/**
  * Concurrency safe cached molecule
  */
object ChemicalSimilarity {
  def calculatorSettings: Option[String] = _calculatorSettings

  private def calculatorSettings_=(value: String): Unit = _calculatorSettings = Option(value)

  var _calculatorSettings: Option[String] = None

  /**
  * @param userCalculatorSettings The settings with which to apply the similarity calculation with
  *                           The current default value was chosen based on the Chemaxon tutorial example.
  */
  def init(userCalculatorSettings: String = "TVERSKY;0.33;0.99"): Unit = {
    require(calculatorSettings.isEmpty, "Chemical similarity calculator was already initialized.")
    calculatorSettings = userCalculatorSettings
  }

  /**
    * For two molecules, use a calculator to determine their closeness.
    *
    * @param query              Molecule to use as the query molecule.
    * @param target             Molecule you are targeting to see how similar it is to the query.
    *
    * @return Similarity value between 0 and 1.
    */
  private def calculateSimilarity(query: Either[String, Molecule], target: Either[String, Molecule]): Double = {
    val simCalc = getSimCalculator(query)
    simCalc.getSimilarity(MoleculeConversions.chemicalToIntArray(target))
  }

  def calculateSimilarity(query: String, target: String): Double = {
    calculateSimilarity(Left(query), Left(target))
  }

  def calculateSimilarity(query: Molecule, target: String): Double = {
    calculateSimilarity(Right(query), Left(target))
  }

  def calculateSimilarity(query: String, target: Molecule): Double = {
    calculateSimilarity(Left(query), Right(target))
  }

  def calculateSimilarity(query: Molecule, target: Molecule): Double = {
    calculateSimilarity(Right(query), Right(target))
  }

  /**
    * For two molecules, use a calculator to determine how far away they are
    *
    * @param query              Molecule to use as the query molecule.
    * @param target             Molecule you are targeting to see how similar it is to the query.
    *
    * @return Dissimilarity value between 0 and 1.
    */
  private def calculateDissimilarity(query: Either[String, Molecule], target: Either[String, Molecule]): Double = {
    val simCalc = getSimCalculator(query)
    simCalc.getDissimilarity(MoleculeConversions.chemicalToIntArray(target))
  }

  def calculateDissimilarity(query: String, target: String): Double = {
    calculateDissimilarity(Left(query), Left(target))
  }

  def calculateDissimilarity(query: Molecule, target: String): Double = {
    calculateDissimilarity(Right(query), Left(target))
  }

  def calculateDissimilarity(query: String, target: Molecule): Double = {
    calculateDissimilarity(Left(query), Right(target))
  }

  def calculateDissimilarity(query: Molecule, target: Molecule): Double = {
    calculateDissimilarity(Right(query), Right(target))
  }

  /**
    * Given settings, retrieves a Similarity calculator for those settings and that query molecule.
    *
    * @param queryMolecule      Molecule to query
    * @return                   A Similarity calculator.
    */
  private def getSimCalculator(queryMolecule: Either[String, Molecule]): SimilarityCalculator[Array[Int]] ={
    require(calculatorSettings.isDefined, "Please run ChemicalSimilarity.init() prior to doing comparisons.  " +
      "If you'd like to use a non-default calculator, you can supply those parameters there as well.")
    val simCalc = SimilarityCalculatorFactory.create(calculatorSettings.get)
    simCalc.setQueryFingerprint(MoleculeConversions.chemicalToIntArray(queryMolecule))

    simCalc
  }
}
