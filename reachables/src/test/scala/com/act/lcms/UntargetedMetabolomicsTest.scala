package com.act.lcms


import act.shared.ChemicalSymbols.MonoIsotopicMass
import org.scalatest.{FlatSpec, Matchers}


class UntargetedMetabolomicsTest extends FlatSpec with Matchers {

  // desactivate tests for this class
  def runTests = false

  // this data was collected with XCMS Centwave "optimzed" parameters: peak width 1-50 and ppm 20 (@vijay-20n?)
  def dataForWell(dataset: String)(repl: Int) = s"Plate_plate2016_09_08_${dataset}${repl}_0908201601.tsv"
  def fullLoc(well: String) = getClass.getResource(well).getPath
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

  val verbose = false
  val outputRawPeakHits = false

  if (runTests) {
    cases.foreach{ case (testID, controlsF, hypothesesF, peakMinCnt, peakMaxCnt) => {

      val controls = controlsF.map(readSpectra)
      val hypotheses = hypothesesF.map(readSpectra)

      val experiment = new UntargetedMetabolomics(controls = controls, hypotheses = hypotheses)
      val analysisRslt = experiment.analyze()
      val candidateMols = MultiIonHits.convertToMolHits(rawPeaks = analysisRslt, lookForMultipleIons = true)
      if (verbose) {
        val peaks = analysisRslt.toJsonFormat() // differential peaks
        val molecules = candidateMols.toJsonFormat() // candidate molecules
        // outStream.println(if (outputRawPeakHits) peaks.prettyPrint else molecules.prettyPrint)
        // outStream.flush
      }
      val numPeaks = analysisRslt.numPeaks
      val numDifferential = numPeaks("over-expressed") + numPeaks("under-expressed")

      numDifferential should be <= peakMaxCnt
      numDifferential should be >= peakMinCnt
    }}
  }

}
