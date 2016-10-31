package com.act.lcms.v2

import java.util.function.Predicate

import com.act.lcms.MS1.MetlinIonMass
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConversions._


class LcmsIonCalculatorTest extends FlatSpec with Matchers {


  val ionCalculator = new LcmsIonCalculator

  "LcmsIonCalculator" should "correctly compute m/z values" in {

    val commonIonFilter = new Predicate[MetlinIonMass] {
      val commonIons = List("M+H", "M+Na", "M+K")
      def test(ion: MetlinIonMass) = commonIons.contains(ion.getName)
    }

    val testMass = 853.33089
    val ions = ionCalculator.getIonsFromMass(testMass, commonIonFilter).toList
    ions should have size 3
    ions.find(_.getIonType.getName.equals("M+H")).get.getMzValue.doubleValue() should be equals(854.338166 +- 0.001)
    ions.find(_.getIonType.getName.equals("M+Na")).get.getMzValue.doubleValue() should be equals(876.320108 +- 0.001)
    ions.find(_.getIonType.getName.equals("M+K")).get.getMzValue.doubleValue() should be equals(892.294048 +- 0.001)
  }
}
