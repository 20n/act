/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
