package com.act.lcms.v2

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConversions._

class LcmsElementTest extends FlatSpec with Matchers {

  val C = new LcmsElement("C")
  val H = new LcmsElement("H")
  val N = new LcmsElement("N")
  val O = new LcmsElement("O")
  val P = new LcmsElement("P")
  val S = new LcmsElement("S")

  val commonElements = List(C, H, N, O, P, S)

  "LcmsElement" should "compute the correct atomic number and mass for basic elements" in {
    val expectedMasses = Map(
      C -> 12.000000,
      H -> 1.007825,
      O -> 15.994915,
      N -> 14.003074,
      P -> 30.973762,
      S -> 31.972071
    )
    val expectedAtomicNumbers = Map(
      C -> 6,
      H -> 1,
      O -> 8,
      N -> 7,
      P -> 15,
      S -> 16
    )

    commonElements.foreach {
      case e =>
        withClue(s"For element ${e.getSymbol}") {
          e.getMass.doubleValue() should equal (expectedMasses.getOrElse(e, -1.0) +- 0.0001)
          e.getAtomicNumber shouldEqual expectedAtomicNumbers.getOrElse(e, -1)
        }
    }
  }

  "LcmsElement" should "compute correct isotopic distributions for carbon" in {

    val expectedCarbonIsotopicDistribution = List((12.0, 100.0), (13.0033, 1.0816)).sortBy(_._1)
    val computedCarbonIsotopes = C.getElementIsotopes.toList.sortBy(_.getIsotopicMass)
    val testData = expectedCarbonIsotopicDistribution zip computedCarbonIsotopes

    testData.foreach {
      case ((mass, abundance), isotope) =>
        mass should equal (isotope.getIsotopicMass.doubleValue() +- 0.0001)
        abundance should equal (isotope.getAbundance.doubleValue() +- 0.0001)
    }
  }

}
