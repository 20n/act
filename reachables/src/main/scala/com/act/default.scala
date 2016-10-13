package com.act

import com.act.analysis.surfactant.SurfactantAnalysis

object default {
  def main(args: Array[String]) {
    val v = SurfactantAnalysis.performAnalysis("InChI=1S/C22H22O9/c1-28-12-4-2-11(3-5-12)15-10-29-16-8-13(6-7-14(16)18(15)24)30-22-21(27)20(26)19(25)17(9-23)31-22/h2-8,10,17,19-23,25-27H,9H2,1H3/t17-,19-,20+,21-,22-/m1/s1", false)
    println(v)
  }
}
