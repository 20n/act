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

package com.act.lcms


import act.shared.ChemicalSymbols.MonoIsotopicMass
import org.scalatest.{FlatSpec, Matchers}


class UntargetedMetabolomicsTest extends FlatSpec with Matchers {

  // The keyword "ignore" does not run the test, but informs the user when running tests that it has been disabled.
  // Replace "ignore" by "MassToFormula" (with quotes) to enable the test.
  ignore should "correctly run end to end tests" in {
    // this data was collected with XCMS Centwave "optimzed" parameters: peak width 1-50 and ppm 20 (@vijay-20n?)
    def dataForWell(dataset: String)(repl: Int) = s"Plate_plate2016_09_08_$dataset${repl}_0908201601.tsv"
    def fullLocResource(well: String) = getClass.getResource(well).getPath
    // These blobs are 2MB each x 9. Moving them out of the resources dir
    // and into the "data/" directory. Find them there, or if not write to
    // act@20n.com and ask to have these pulled out the "act-private" repo
    // Will definitely have it at commit `fb6757f` in directory
    // `reachables/src/main/resources/com/act/lcms/Plate_plate2016_09_08_`
    def fullLoc(well: String) = "data/UntargetedMetabolomicsTest/" + well
    def readSpectra(f: String) = {
      val src = new RawData(source = f)
      new RawPeaks(src, PeakSpectra.fromCalledPeaks(f))
    }

    val wt = (1 to 3).toList.map(dataForWell("B")).map(fullLoc)
    val df = (1 to 3).toList.map(dataForWell("A")).map(fullLoc)
    val dm = (1 to 3).toList.map(dataForWell("C")).map(fullLoc)
    val dmdf = df ++ dm

    val (wt1, wt2, wt3) = (wt(0), wt(1), wt(2))
    val (df1, df2, df3) = (df(0), df(1), df(2))
    val (dm1, dm2, dm3) = (dm(0), dm(1), dm(2))

    // wt{1,2,3} = wildtype replicates 1, 2, 3
    // d{M,F}{1,2,3} = disease line {M,F} replicates 1, 2, 3

    // the below test cases are RetentionTime and MonoIsotopicMass parameter dependent
    // (MonoIsotopicMass.defaultNumPlaces, RetentionTime.driftTolerated, numPeaks)
    val expPks = Map(
      "wt1-df1" -> Map((3, 1.0) -> 303, (3, 2.0) -> 337, (3, 5.0) -> 374, (2, 1.0) -> 1219),
      "wt1-dm1" -> Map((3, 1.0) -> 225, (3, 2.0) -> 268, (3, 5.0) -> 299, (2, 1.0) -> 826),
      "dm-df"   -> Map((3, 1.0) ->  73, (3, 2.0) ->  82, (3, 5.0) ->  92, (2, 1.0) -> 331),
      "df-dm"   -> Map((3, 1.0) ->  57, (3, 2.0) ->  68, (3, 5.0) ->  81, (2, 1.0) -> 340),
      "wt-dm"   -> Map((3, 1.0) ->  37, (3, 2.0) ->  45, (3, 5.0) ->  59, (2, 1.0) -> 742),
      "wt-df"   -> Map((3, 1.0) ->  58, (3, 2.0) ->  69, (3, 5.0) ->  77, (2, 1.0) -> 347)
    )

    def bnd(tcase: String) = expPks(tcase)((MonoIsotopicMass.defaultNumPlaces, RetentionTime.driftTolerated))

    val cases = List(
      // consistency check: hypothesis same as control => no peaks should be differentially identified
      ("wt1-wt1", List(wt1), List(wt1), 0, 0),
      ("dm1-dm1", List(dm1), List(dm1), 0, 0),
      ("df1-df1", List(df1), List(df1), 0, 0),

      // ensure that replicate aggregation (i.e., min) works as expected.
      // we already know from the above test that differential calling works
      // to eliminate all peaks if given the same samples. so now if replicate
      // aggregation gives non-zero sets of peaks, it has to be the min algorithm.
      ("wt-wt", wt, wt, 0, 0),
      ("dm-dm", dm, dm, 0, 0),
      ("df-df", df, df, 0, 0),

      // how well does the differential calling work over a single sample of hypothesis and control
      // ("wt1-df1", List(wt1), List(df1), bnd("wt1-df1"), bnd("wt1-df1")),
      // ("wt1-dm1", List(wt1), List(dm1), bnd("wt1-dm1"), bnd("wt1-dm1")),

      // next two: what is in one diseases samples and not in the other?
      ("dm-df", dm, df, bnd("dm-df"), bnd("dm-df")),
      ("df-dm", df, dm, bnd("df-dm"), bnd("df-dm")),

      // peaks that are differentially expressed in diseased samples compared to the wild type
      ("wt-dm", wt, dm, bnd("wt-dm"), bnd("wt-dm")),
      ("wt-df", wt, df, bnd("wt-df"), bnd("wt-df"))

    )

    cases.foreach{ case (testID, controlsF, hypothesesF, peakMinCnt, peakMaxCnt) => {

      val controls = controlsF.map(readSpectra)
      val hypotheses = hypothesesF.map(readSpectra)

      val experiment = new UntargetedMetabolomics(controls = controls, hypotheses = hypotheses)
      val analysisRslt = experiment.analyze()
      val candidateMols = MultiIonHits.convertToMolHits(rawPeaks = analysisRslt, lookForMultipleIons = true)
      val numPeaks = analysisRslt.numPeaks
      val numDifferential = numPeaks("over-expressed") + numPeaks("under-expressed")

      numDifferential should be <= peakMaxCnt
      numDifferential should be >= peakMinCnt
    }}

  }
}
