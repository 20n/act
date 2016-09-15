package com.act.lcms

import java.io.{PrintWriter, File}
import act.shared.{CmdLineParser, OptDesc}
import scala.io.Source
import act.shared.ChemicalSymbols.MonoIsotopicMass

class RetentionTime(private val time: Double) {
  // Default drift allowed is emperically picked based on observations over experimental data
  private val driftTolerated = 5.0 // seconds

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

object RetentionTime {
  def middle(xs: List[Double]): Double = {
    // In the cases of odd sized xs this would correspond to median
    // But in the case of even sized lists, we don't want to average since that 
    // would give us a point that is not in the original retention times making
    // provenance of that datapoint difficult to track from the original
    xs.sorted.toList(xs.size/2)
  }
  def middle(times: List[RetentionTime]): RetentionTime = new RetentionTime(middle(times.map(_.time)))
  def ascender(a: RetentionTime, b: RetentionTime) = a.time > b.time
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
    val topk = 50
    val filterMzRt = false
    def mzRtInRange(p: UntargetedPeak) = {
      if (filterMzRt)
        p.rt.isIn(20, 200) && p.mz.isIn(50, 500)
      else
        true
    }
    val lowPks = peaks.toList.filter(mzRtInRange)
    val rngCmt = if (filterMzRt) ", showing mz:[50, 500] rt:[20, 200]" else ""
    Map(
      "num peaks" -> peaks.size,
      s"topK by integratedInt${rngCmt}" -> lowPks.sortWith(_.integratedInt > _.integratedInt).map(p => List(p.mz, p.rt, p.integratedInt)).take(topk)
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

  // aggregate characteristic for peaks for the same molecule (eluting at the same mz, and time)
  def uniformAcross(peaks: List[UntargetedPeak], mz: MonoIsotopicMass, rt: RetentionTime): UntargetedPeak = {

    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr

    def pickMin(a: Double, b: Double) = math.min(a, b)
    combinePeaks(peaks, mz, rt, pickMin)
  }

  // identify if the peaks in hyp are outliers compared to the controls
  // we assume these peaks are for the same molecule (eluting at the same mz, and time)
  def isOutlier(hyp: List[UntargetedPeak], ctrl: List[UntargetedPeak], mz: MonoIsotopicMass, rt: RetentionTime): Option[UntargetedPeak] = {

    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr
    def ratio(a: Double, b: Double) = if (b == 0.0) Double.MaxValue else a/b

    // the lists coming in for the inputs are if for this `peak @ mz, rt` there are *many* peaks in the
    // original data in the aggregated hypothesis trace! This is slightly crazy case and will only happen
    // when the peak structure is very zagged. We average the values
    def sizedAvg(sz: Int)(a: Double, b: Double) = (a + b)/sz

    val hypPeak = combinePeaks(hyp, mz, rt, sizedAvg(hyp.size))
    val ctrlPeak = combinePeaks(ctrl, mz, rt, sizedAvg(ctrl.size))

    val ratioedPeak = combinePeaks(List(hypPeak, ctrlPeak), mz, rt, ratio)
    checkOutlier(ratioedPeak)
  }

  def combinePeaks(peaks: List[UntargetedPeak], 
    mz: MonoIsotopicMass, rt: RetentionTime,
    fn: (Double, Double) => Double) = {

    val (integratedInts, maxInts, snrs) = peaks.map(p => (p.integratedInt, p.maxInt, p.snr)).unzip3
    val aggIntegratedInts = integratedInts reduce fn
    val aggMaxInts = maxInts reduce fn
    val aggSnrs = snrs reduce fn

    new UntargetedPeak(mz, rt, aggIntegratedInts, aggMaxInts, aggSnrs)
  }

  def checkOutlier(peak: UntargetedPeak): Option[UntargetedPeak] = {
    // 10*(cosh(x-1) - 1) is a nice function that is has properties we would need:
    // (x, y) 
    //    = (1.0, 0)
    //    = (0.5, 1.25)
    //    = (1.5, 1.25)
    //    = (2.0, 5.50)
    // We could use any function that is hyperbolic and is 0 at 1 and rises sharply
    // upwards on both sides of 1
    def cosh(x: Double) = (math.exp(x) + math.exp(-x)) / 2.0
    def valleyShape(x: Double) = 10.0 * (cosh(x - 1) - 1)

    // signal in control identical to hypothesis: metric = 1.0 => valleyShape = 0
    // signal in hypothesis lower or higher than control:  metric < 0.8 || metric > 1.2 => valleyShape > 1.0
    val metric = peak.integratedInt
    if (valleyShape(metric) > 1.0) { Some(peak) } else { None }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  def extractOutliers(hypothesis: LCMSExperiment, control: LCMSExperiment): LCMSExperiment = {

    val exprVsControl = List(hypothesis, control)

    println(s"Identifying outlying peaks...")
    val (alignedPeaks, alignedToOriginalPeaks) = getAlignedPeaks(exprVsControl)

    val peaksWithCharacteristics: Set[Option[UntargetedPeak]] = alignedPeaks.map{
      peak => {
        val (mz, rt) = peak
        // for each `peak @ mz,rt`, we now pull the original peaks from the hypothesis and control sets
        // each `peak @ mz,rt` collapses (potentially) many original `UntargetedPeak`s and we get all
        // of those back, both for the hypothesis case (originalPeaks(0)) and control case (originalPeak(1))
        val originalPeaks: List[List[UntargetedPeak]] = alignedToOriginalPeaks(peak)
        assert( originalPeaks.size == 2)
        isOutlier(originalPeaks(0), originalPeaks(1), mz, rt)
      }
    }.toSet

    // outliers are those that are not none
    val outlierPeaks = peaksWithCharacteristics.filter(_.isDefined).map{ case Some(p) => p }.toSet

    val provenance = new ComputedData(sources = exprVsControl.map(_.origin))
    new LCMSExperiment(provenance, new UntargetedPeakSpectra(outlierPeaks))
  }

  def unifyReplicates(replicates: List[LCMSExperiment]): LCMSExperiment = {

    println(s"Unifying replicates...")
    val (alignedPeaks, alignedToOriginalPeaks) = getAlignedPeaks(replicates)

    val sharedPeaksWithCharacteristics: Set[UntargetedPeak] = alignedPeaks.map{
      peak => {
        val (mz, rt) = peak
        val originalPeaks: List[List[UntargetedPeak]] = alignedToOriginalPeaks(peak)
        uniformAcross(originalPeaks.flatten, mz, rt)
      }
    }.toSet

    val provenance = new ComputedData(sources = replicates.map(_.origin))
    new LCMSExperiment(provenance, new UntargetedPeakSpectra(sharedPeaksWithCharacteristics))
  }

  type PeakAt = (MonoIsotopicMass, RetentionTime)

  def intersect(peaksA: List[PeakAt], peaksB: List[PeakAt]) = timer {
    // We have MonoIsotopicMass and RetentionTime with equals properly defined
    // MonoIsotopicMass has both equals and hashCode. RetentionTime only has
    // equals that finds things in the tolerated drigs
    // *  MonoIsotopicMass answers equals to values if they match 
    //      upto a certain decimal position.
    // *  RetentionTime answers equals to values if they are
    //      within a certain drift apart.

    val mzsInA = peaksA.map(_._1)
    val mzsInB = peaksB.map(_._1)
    // set'intersect over MonoIsotopicMass will be fine, we have hashCode defined for it
    val mzsInBoth = mzsInA.intersect(mzsInB).distinct

    println(s"intersect: mzsInBoth = ${mzsInBoth.sortBy(_.initMass)}")
    println(s"intersect: |mzsInBoth| = ${mzsInBoth.size}")

    // given an mz, get lists of peaks in both sets that have ~equal mz, and then
    // n^2 compare each of the pulled peaks to see if they also ~match on retention time
    def pullPeaksInBoth(mz: MonoIsotopicMass): Set[PeakAt] = {

      val peaksAForMz = peaksA.filter(_._1.equals(mz))
      val peaksBForMz = peaksB.filter(_._1.equals(mz))
      val rtsInA = peaksAForMz.map(_._2).toList
      val rtsInB = peaksBForMz.map(_._2).toList

      if (peaksAForMz.size > 4 || peaksBForMz.size > 4)
        println(s"|peaks{A,B}ForMz|=${peaksAForMz.size},${peaksBForMz.size} and |rtsIn{A,B}|=${rtsInA.size},${rtsInB.size}")      

      // for each Rt in A map it to matches in B
      // for each Rt in B map it to matches in A
      // filter out those that do not have any match in the other set
      // for each key -> set{others} get `middle(kv :: others)`
      // of all the values that come out, output the unique ones 
      def nonEmptyRhs(rtNMatch: (RetentionTime, List[RetentionTime])) = !rtNMatch._2.isEmpty
      val rtsInAWithMatchInB = rtsInA.zip(rtsInA.map(rA => rtsInB.filter(_.equals(rA)))).filter(nonEmptyRhs)
      val rtsInBWithMatchInA = rtsInB.zip(rtsInB.map(rB => rtsInA.filter(_.equals(rB)))).filter(nonEmptyRhs)
      val rtsWithMatchesInOther = rtsInBWithMatchInA ++ rtsInAWithMatchInB
      val rtsWithMatches = rtsWithMatchesInOther.map{ case (k, vs) => RetentionTime.middle(k :: vs) }
      def uniq(rts: List[RetentionTime]): List[RetentionTime] = rtsWithMatches.distinct
      val rtsWithMatchesUniq = uniq(rtsWithMatches)
      val common = rtsWithMatchesUniq.map(rt => (mz, rt)).toSet

      common
    }

    val inBoth = mzsInBoth.flatMap(pullPeaksInBoth)

    println(s"${peaksA.size} /-\\ ${peaksB.size} = ${inBoth.size}")
    inBoth
  }

  def getAlignedPeaks(traces: List[LCMSExperiment]) = {
    // get every (mz, rt) found in every replicate
    val peaks: List[Map[UntargetedPeak, PeakAt]] = traces.map(r => r.peakSpectra.peaks.map(peakKv).toMap)

    // also keep them as a 2D list of lists, so that we can reverse map them later
    val peaksAs2D: List[List[(UntargetedPeak, PeakAt)]] = peaks.map(_.toList)

    // only find peaks that are common across all traces, so we do
    // a pairwise intersect of the peaks. 
    val alignedPeaks: List[PeakAt] = {
      val uniquePeaksInEachSet = peaks.map(_.values.toList)
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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  def timer[T](blk: => T): T = {
    val start = System.nanoTime()
    val rslt = blk
    val end = System.nanoTime()
    println(s"Timed: ${(end - start)/1000000000.0} seconds")
    rslt
  } 

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

    val wtmin = (1 to 3).toList.map(x => s"debugmin${x}.tsv")
    val wt = (1 to 3).toList.map(dataForWell("B")).map(fullLoc)
    val df = (1 to 3).toList.map(dataForWell("A")).map(fullLoc)
    val dm = (1 to 3).toList.map(dataForWell("C")).map(fullLoc)
    val dmdf = df ++ dm

    val (wt1, wt2, wt3) = (wt(0), wt(1), wt(2))
    val (df1, df2, df3) = (df(0), df(1), df(2))
    val (dm1, dm2, dm3) = (dm(0), dm(1), dm(2))

    // wt{1,2,3} = wildtype replicates 1, 2, 3
    // d{M,F}{1,2,3} = disease line {M,F} replicates 1, 2, 3
    // each test is specified as (controls, hypothesis, num_peaks_min, num_peaks_max) inclusive both
    val cases = List(
      ("wtmin-wtmin", wtmin, wtmin, 0, 0), // debugging this case!
      ("wt-wt", wt, wt, 0, 0) // debugging this case!

      //      // consistency check: hypothesis same as control => no peaks should be differentially identified
      //      ("wt1-wt1", List(wt1), List(wt1), 0, 0),
      //      ("dm1-dm1", List(dm1), List(dm1), 0, 0),
      //      ("df1-df1", List(df1), List(df1), 0, 0),
      //      
      //      // ensure that replicate aggregation (i.e., min) works as expected. 
      //      // we already know from the above test that differential calling works 
      //      // to eliminate all peaks if given the same samples. so now if replicate
      //      // aggregation gives non-zero sets of peaks, it has to be the min algorithm.
      //      ("wt-wt", wt, wt, 0, 0),
      //      ("dm-dm", dm, dm, 0, 0),
      //      ("df-df", df, df, 0, 0),
      //      
      //      // how well does the differential calling work over a single sample of hypothesis and control
      //      ("wt1-df1", List(wt1), List(df1), 500, 520), // 515
      //      ("wt1-dm1", List(wt1), List(dm1), 450, 500), // 461
      //      
      //      // peaks that are differentially expressed in diseased samples compared to the wild type
      //      ("wt-dm", wt, dm, 1, 200), // 152
      //      ("wt-df", wt, df, 1, 200), // 181
      //      
      //      // next two: what is in one diseases samples and not in the other?
      //      ("dm-df", dm, df, 1, 200), // 146
      //      ("df-dm", df, dm, 1, 200),  // 151

      //      // Check what is commonly over/under expressed in diseased samples
      //      // Woa! This is not really a test case. This is the final analysis!
      //      ("wt-dmdf", wt, dmdf, 100, 130) // 115 RT=3.0, 123 RT=5.0 
      
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
