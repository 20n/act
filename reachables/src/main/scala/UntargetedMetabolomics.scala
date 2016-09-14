package com.act.lcms

import java.io.{PrintWriter, File}
import act.shared.{CmdLineParser, OptDesc}
import scala.io.Source
import act.shared.ChemicalSymbols.MonoIsotopicMass

class RetentionTime(private val time: Double) {
  // This class is modeled after MonoIsotopicMass and tolerates differences upto a certain drift
  // i.e., makes peaks within `driftTolerated` look identical. Equality comparisons over doubles. :o !!
  // To really accomplish that, we use a combination scheme of hashing and equality:
  // HashCode: We deliberately create collisions by aligning to boundaries
  // Equality: We check for time differences within drift

  // Default drift allowed is emperically picked based on observations over experimental data
  private val driftTolerated = 3.0 // seconds

  // This function is a helper to `equals`
  // It tests whether two values are within the range of experimental drift we allow
  private def withinDriftWindow(a: Double, b: Double) = (math abs (a - b)) < driftTolerated

  // we allow for times to drift by driftTolerated, and so equals matches times that only that apart
  override def equals(that: Any) = that match { 
    case that: RetentionTime => withinDriftWindow(this.time, that.time)
    case _ => false
  }

  override def toString(): String = {
    val timeToTwoDecimal = (math round (time * 100.0)) / 100.0
    timeToTwoDecimal.toString
  }

  def isIn(low: Double, high: Double): Boolean = time >= low && time <= high
}

sealed trait Provenance
class RawData(val source: String) extends Provenance
class ComputedData(val sources: List[Provenance]) extends Provenance

class LCMSExperiment(val origin: Provenance, val peakSpectra: UntargetedPeakSpectra) {
  override def toString = peakSpectra.toString
  def toStats = peakSpectra.toStats
  def toStatsStr = peakSpectra.toStatsStr
}

class UntargetedPeak(
  val mz: MonoIsotopicMass,
  val rt: RetentionTime,
  val integratedInt: Double,
  val maxInt: Double,
  val snr: Double
) {
  override def toString = {
    Map(MZ -> mz, RT -> rt, IntIntensity -> integratedInt, MaxIntensity -> maxInt, SNR -> snr).toString
  }
}

class UntargetedPeakSpectra(val peaks: Set[UntargetedPeak]) {
  override def toString = peaks.toString

  def toStats = {
    val topk = 100
    val filterMzRt = false
    def mzRtInRange(p: UntargetedPeak) = {
      if (filterMzRt)
        p.rt.isIn(20, 200) && p.mz.isIn(50, 500)
      else
        true
    }
    val lowPks = peaks.toList.filter(mzRtInRange)
    Map(
      "num peaks" -> peaks.size,
      "topK by snr with mz:[50,500] rt:[20,200]" -> lowPks.sortWith(_.snr > _.snr).map(p => List(p.mz, p.rt, p.snr)).take(topk),
      "topK by maxInt with mz:[50,500] rt:[20,200]" -> lowPks.sortWith(_.maxInt > _.maxInt).map(p => List(p.mz, p.rt, p.maxInt)).take(topk),
      "topK by integratedInt with mz:[50,500] rt:[20,200]" -> lowPks.sortWith(_.integratedInt > _.integratedInt).map(p => List(p.mz, p.rt, p.integratedInt)).take(topk)
    )
  }

  def toStatsStr = {
    toStats.toList.map{ 
      case (k: String, i: Int) => k + ":\t" + i
      case (k: String, vl: List[List[Double]]) => k + "\n" + vl.map(_.mkString("\t")).mkString("\n")
    }.mkString("\n\n")
  }
}

sealed class XCMSCol(val id: String) {
  override def toString = id
}
object MZ extends XCMSCol("mz")
object RT extends XCMSCol("rt")
object IntIntensity extends XCMSCol("into")
object MaxIntensity extends XCMSCol("maxo")
object SNR extends XCMSCol("sn")

object UntargetedPeakSpectra {

  val hdrsXCMS = List(MZ, RT, IntIntensity, MaxIntensity, SNR)
  def fromXCMSCentwave(file: String): UntargetedPeakSpectra = {
    // example to process (with header):
    // mz  mzmin mzmax rt  rtmin rtmax into  intb  maxo  sn  sample
    // 244.98272  244.97964  244.985247  2.56099  1.91800  2.98900  130.32171 129.46491  253.17785  252 1
    // these are truncated, 14-15 digits (including before and after decimal)
    // Such are the files in /mnt/shared-data/Vijay/perlstein_xcms_centwave_optimized_output
    val lines = Source.fromFile(file).getLines.toList.map(_.split("\t").toList)
    val hdr::tail = lines
    val identifiedHdrs = hdr.map(hid => hdrsXCMS.find(_.id.equals(hid)))
    val withHdrs = tail.map(l => identifiedHdrs.zip(l))
    def keepOnlyRecognizedCols(line: List[(Option[XCMSCol], String)]): Map[XCMSCol, Double] = {
      line.filter(_._1.isDefined).map{ case (Some(hdr), value) => (hdr, value.toDouble) }.toMap
    }
    val relevantLines = withHdrs.map(keepOnlyRecognizedCols)

    def peaksFromXCMSCentwave(row: Map[XCMSCol, Double]) = {
      new UntargetedPeak(
        new MonoIsotopicMass(row(MZ)),
        new RetentionTime(row(RT)),
        row(IntIntensity),
        row(MaxIntensity),
        row(SNR))
    }
    val peaks = relevantLines.map(peaksFromXCMSCentwave).toSet

    new UntargetedPeakSpectra(peaks)
  }

}

class UntargetedMetabolomics(val controls: List[LCMSExperiment], val hypotheses: List[LCMSExperiment]) {

  def analyze(): LCMSExperiment = {
    val unifiedControls = unifyReplicates(controls)
    val unifiedHypotheses = unifyReplicates(hypotheses)
    extractOutliers(unifiedHypotheses, unifiedControls)
  }

  def extractOutliers(hypothesis: LCMSExperiment, control: LCMSExperiment): LCMSExperiment = {

    val exprVsControl = List(hypothesis, control)

    val (alignedPeaks, alignedToOriginalPeaks) = getAlignedPeaks(exprVsControl)

    val peaksWithCharacteristics: Set[Option[UntargetedPeak]] = alignedPeaks.map{
      peak => {
        val (mz, rt) = peak
        // for each `peak @ mz,rt`, we now pull the original peaks from the hypothesis and control sets
        // each `peak @ mz,rt` collapses (potentially) many original `UntargetedPeak`s and we get all
        // of those back, both for the hypothesis case (originalPeaks(0)) and control case (originalPeak(1))
        val originalPeaks: List[List[UntargetedPeak]] = alignedToOriginalPeaks(peak)
        assert( originalPeaks.size == 2)
        val judgedPeaks = outlierCharacteristics(originalPeaks(0), originalPeaks(1))
        judgedPeaks match {
          case None => None
          case Some((integr, max, snr)) => {
            println(s"peak: $peak")
            Some(new UntargetedPeak(mz, rt, integr, max, snr))
          }
        }
      }
    }

    // outliers are those that are not none
    val outlierPeaks = peaksWithCharacteristics.filter(_.isDefined).map{ case Some(p) => p }.toSet

    val provenance = new ComputedData(sources = exprVsControl.map(_.origin))
    new LCMSExperiment(provenance, new UntargetedPeakSpectra(outlierPeaks))
  }

  // aggregate characteristic for peaks for the same molecule (eluting at the same mz, and time)
  def outlierCharacteristics(hyp: List[UntargetedPeak], ctrl: List[UntargetedPeak]): Option[(Double, Double, Double)] = {
    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr
    def aggFn(a: Double, b: Double) = if (b == 0.0) Double.MaxValue else a/b

    // the lists coming in for the inputs are if for this `peak @ mz, rt` there are *many* peaks in the
    // original data in the aggregated hypothesis trace! This is slightly crazy case and will only happen
    // when the peak structure is very zagged. We average the values
    def average(closeBy: List[Double]) = closeBy.sum / closeBy.size
    val together = average _

    val aggregateIntegratedInts = aggFn(together(hyp.map(_.integratedInt)), together(ctrl.map(_.integratedInt)))
    val aggregateMaxInts = aggFn(together(hyp.map(_.maxInt)), together(ctrl.map(_.maxInt)))
    val aggregateSnrs = aggFn(together(hyp.map(_.snr)), together(ctrl.map(_.snr)))

    def cosh(x: Double) = (math.exp(x) + math.exp(-x)) / 2.0

    // 10*(cosh(x-1) - 1) is a nice function that is has properties we would need:
    // (x, y) 
    //    = (1.0, 0)
    //    = (0.5, 1.25)
    //    = (1.5, 1.25)
    //    = (2.0, 5.50)
    // We could use any function that is hyperbolic and is 0 at 1 and rises sharply
    // upwards on both sides of 1
    def valleyShape(x: Double) = 10.0 * (cosh(x - 1) - 1)

    // check:
    // signal in control identical to hypothesis: aggFn = 1.0 => valleyShape = 0
    // signal in hypothesis much lower or much higher than control: aggFn < 0.8 || aggFn > 1.2 => valleyShape > 1.0
    if (valleyShape(aggregateIntegratedInts) > 1.0) {
      println(s"Calculating outlying chars:\nhypo = $hyp\nctrl = $ctrl")
      Some((aggregateIntegratedInts, aggregateMaxInts, aggregateSnrs))
    } else {
      None
    }
  }

  def unifyReplicates(replicates: List[LCMSExperiment]): LCMSExperiment = {

    val (alignedPeaks, alignedToOriginalPeaks) = getAlignedPeaks(replicates)

    val sharedPeaksWithCharacteristics: Set[UntargetedPeak] = alignedPeaks.map{
      peak => {
        val (mz, rt) = peak
        val originalPeaks: List[List[UntargetedPeak]] = alignedToOriginalPeaks(peak)
        val (integratedIntensity, maxIntensity, snr) = aggregateCharacteristics(originalPeaks.flatten)
        new UntargetedPeak(mz, rt, integratedIntensity, maxIntensity, snr)
      }
    }

    val provenance = new ComputedData(sources = replicates.map(_.origin))
    new LCMSExperiment(provenance, new UntargetedPeakSpectra(sharedPeaksWithCharacteristics))
  }

  // aggregate characteristic for peaks for the same molecule (eluting at the same mz, and time)
  def aggregateCharacteristics(peaks: List[UntargetedPeak]): (Double, Double, Double) = {
    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr

    def aggFn(a: Double, b: Double) = math.min(a, b)

    val (integratedInts, maxInts, snrs) = peaks.map(p => (p.integratedInt, p.maxInt, p.snr)).unzip3
    val aggregateIntegratedInts = integratedInts.reduce(aggFn)
    val aggregateMaxInts = maxInts.reduce(aggFn)
    val aggregateSnrs = snrs.reduce(aggFn)

    (aggregateIntegratedInts, aggregateMaxInts, aggregateSnrs)
  }

  type PeakAt = (MonoIsotopicMass, RetentionTime)

  def intersect(peaksA: Set[PeakAt], peaksB: Set[PeakAt]) = {
    // We have MonoIsotopicMass and RetentionTime defined such
    // that they define equals and hashCode properly to equate 
    // elements that should look identical. 
    // * Equals:
    //    MonoIsotopicMass answers equals to values if they match 
    //      upto a certain decimal position.
    //    RetentionTime answers equals to values if they are
    //      within a certain drift apart.
    // * HashCode:
    //    MonoIsotopicMass hashes values to the same if they match
    //      upto a certain decimal position.
    //    RetentionTime hashes values to the same if they are "approximately"
    //      drift apart. This may cause collisions on values that are
    //      not exactly equal, but equality will diambiguate.
    peaksA.intersect(peaksB)
  }

  def getAlignedPeaks(traces: List[LCMSExperiment]) = {
    // get every (mz, rt) found in every replicate
    val peaks: List[Map[UntargetedPeak, PeakAt]] = traces.map(r => r.peakSpectra.peaks.map(peakKv).toMap)

    // also keep them as a 2D list of lists, so that we can reverse map them later
    val peaksAs2D: List[List[(UntargetedPeak, PeakAt)]] = peaks.map(_.toList)

    // only find peaks that are common across all traces, so we do
    // a pairwise intersect of the peaks. Note that both (mz, rt)
    // would need to "match" for the peaks to stay. And "match"ing
    // is defined up to the semantic tolerances as encoded in
    // MonoIsotopicMass and RetentionTime
    val alignedPeaks: Set[PeakAt] = {
      val uniquePeaksInEachSet = peaks.map(_.values.toSet)
      val uniquePeaksAcrossSets = uniquePeaksInEachSet.reduce(intersect)
      println(s"unique peaks in each set: ${uniquePeaksInEachSet.map(_.size)} and intersected across: ${uniquePeaksAcrossSets.size} as compared to total peaks: ${peaks.map(_.keys.toSet).map(_.size)}")
      uniquePeaksAcrossSets
    }

    val alignedToOriginalPeaks: Map[PeakAt, List[List[UntargetedPeak]]] = alignedPeaks.map(
      mzRt => mzRt -> peaksAs2D.map(filterToPeaksAtThisMzRT(mzRt))
    ).toMap

    (alignedPeaks, alignedToOriginalPeaks)
  }

  def filterToPeaksAtThisMzRT(mzRt: PeakAt)(originalPeaks: List[(UntargetedPeak, PeakAt)]): List[UntargetedPeak] = {
    for ((origPeak, origMzRt) <- originalPeaks if origMzRt.equals(mzRt)) yield origPeak
  }

  def peakKv(peak: UntargetedPeak): (UntargetedPeak, PeakAt) = peak -> (peak.mz, peak.rt)


}

object UntargetedMetabolomics {

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List(optOutFile, optControls, optHypotheses, optRunTests)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    // read the command line options
    val runTests = cmdLine get optRunTests
    val controls = cmdLine getMany optControls
    val hypotheses = cmdLine getMany optHypotheses

    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }

    if (cmdLine has optRunTests) {
      val nasSharedDir = cmdLine get optRunTests
      runPerlsteinLabTests(new File(nasSharedDir))
    }

    def mkLCMSExpr(kv: String) = {
      val spl = kv.split("=")
      val (name, file) = (spl(0), spl(1))
      val srcOrigin = new RawData(source = name)
      new LCMSExperiment(srcOrigin, UntargetedPeakSpectra.fromXCMSCentwave(file))
    }

    // do the thing!
    (controls, hypotheses) match {
      case (null, _) => println(s"No controls!")
      case (_, null) => println(s"No hypotheses!")
      case (cnt, hyp) => {
        val controls = cnt.map(mkLCMSExpr).toList
        val hypotheses = hyp.map(mkLCMSExpr).toList
        val experiment = new UntargetedMetabolomics(controls = controls, hypotheses = hypotheses)
        val analysisRslt = experiment.analyze()

        val statsStr = analysisRslt.toStatsStr
        println(s"stats = $statsStr")
      }
    }
  }

  val optControls = new OptDesc(
                    param = "c",
                    longParam = "controls",
                    name = "{name=file}*",
                    desc = """Controls: Comma separated list of name=file pairs""".stripMargin,
                    isReqd = false, hasArgs = true)

  val optHypotheses = new OptDesc(
                    param = "e",
                    longParam = "experiments",
                    name = "{name=file}*",
                    desc = """Experiments: Comma separated list of name=file pairs""".stripMargin,
                    isReqd = false, hasArgs = true)

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outfile",
                    name = "filename",
                    desc = "The file to which ...",
                    isReqd = false, hasArg = true)

  val optRunTests = new OptDesc(
                    param = "t",
                    longParam = "run-tests-from",
                    name = "dir path",
                    desc = """Run regression tests. It needs the path of the shared dir on the NAS,
                             |e.g., /mnt/shared-data/ because it pulls some sample data from there
                             |to test over.""".stripMargin,
                    isReqd = false, hasArg = true)

  def runPerlsteinLabTests(sharedDirLoc: File) {
    // this data was collected with XCMS Centwave "optimzed" parameters: peak width 1-50 and ppm 20 (@vijay-20n?)
    def dataForWell(dataset: String)(repl: Int) = s"Plate_plate2016_09_08_${dataset}${repl}_0908201601.tsv"
    val pLabXCMSLoc = s"${sharedDirLoc.getPath}/Vijay/perlstein_xcms_centwave_optimized_output/"
    def fullLoc(well: String) = pLabXCMSLoc + well
    def readSpectra(f: String) = {
      val src = new RawData(source = f)
      new LCMSExperiment(src, UntargetedPeakSpectra.fromXCMSCentwave(f))
    }

    val wt = (1 to 3).toList.map(dataForWell("B")).map(fullLoc)
    val df = (1 to 3).toList.map(dataForWell("A")).map(fullLoc)
    val dm = (1 to 3).toList.map(dataForWell("C")).map(fullLoc)

    val (wt1, wt2, wt3) = (wt(0), wt(1), wt(2))
    val (df1, df2, df3) = (df(0), df(1), df(2))
    val (dm1, dm2, dm3) = (dm(0), dm(1), dm(2))

    // wt{1,2,3} = wildtype replicates 1, 2, 3
    // d{M,F}{1,2,3} = disease line {M,F} replicates 1, 2, 3
    // each test is specified as (controls, hypothesis, num_peaks_min, num_peaks_max) inclusive both
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
      ("wt1-df1", List(wt1), List(df1), 1, 500),
      ("wt1-df1", List(wt1), List(dm1), 1, 500),
      
      // peaks that are differentially expressed in diseased samples compared to the wild type
      ("wt-dm", wt, dm, 1, 200),
      ("wt-df", wt, df, 1, 200),
      
      // next two: what is in one diseases samples and not in the other?
      ("dm-df", dm, df, 1, 200),
      ("df-dm", df, dm, 1, 200)
    )

    val verbose = true
    cases.foreach{ case (testID, controlsF, hypothesesF, peakMinCnt, peakMaxCnt) => {

      println(s"Testing $testID")
      controlsF.foreach{   c => println(s"Cntrl: $c") }
      hypothesesF.foreach{ c => println(s"Hypth: $c") }

      val controls = controlsF.map(readSpectra)
      val hypotheses = hypothesesF.map(readSpectra)
      val experiment = new UntargetedMetabolomics(controls = controls, hypotheses = hypotheses)
      val analysisRslt = experiment.analyze()
      if (verbose) {
        val statsStr = analysisRslt.toStatsStr
        println(s"stats = $statsStr")
      }
      val numPeaks = analysisRslt.toStats("num peaks").asInstanceOf[Int]
      if (!(numPeaks >= peakMinCnt && numPeaks <= peakMaxCnt)) {
        println(s"Failed test ${testID}, unexpected peak count: $numPeaks != [$peakMinCnt, $peakMaxCnt]")
        assert(false)
      }

    }}
  }

}
