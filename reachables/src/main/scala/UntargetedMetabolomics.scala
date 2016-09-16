package com.act.lcms

import java.io.{PrintWriter, File}
import act.shared.{CmdLineParser, OptDesc}
import scala.io.Source
import act.shared.ChemicalSymbols.MonoIsotopicMass

class RetentionTime(private val time: Double) {
  // Default drift allowed is emperically picked based on observations over experimental data
  private val driftTolerated = 2.0 // seconds

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
  def ascender(a: RetentionTime, b: RetentionTime) = a.time < b.time
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
    val intensity = String.format("%.0f", integratedInt: java.lang.Double)
    s"$intensity @ ($mz, $rt)"
    // Map(MZ -> mz, RT -> rt, IntIntensity -> integratedInt, MaxIntensity -> maxInt, SNR -> snr).toString
  }
}

class UntargetedPeakSpectra(val peaks: Set[UntargetedPeak]) {
  override def toString = peaks.toString

  def toStats = {
    val topk = 500
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
      s"topK by integratedInt${rngCmt}" -> lowPks
                                            .sortWith(_.integratedInt > _.integratedInt)
                                            .map(p => List(p.mz, p.rt, p.integratedInt))
                                            .take(topk)
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

  // we use the average to combine multiple signals at the same mz, rt
  def sizedAvg(sz: Int)(a: Double, b: Double) = (a + b)/sz
  def pickMax(a: Double, b: Double) = math.max(a, b)
  // we use min to aggregate signals across replicates
  def pickMin(a: Double, b: Double) = math.min(a, b)
  // we use ratio to identify differentially expressed peaks
  def ratio(a: Double, b: Double) = if (b == 0.0) Double.MaxValue else a/b

  // the lists coming in for the inputs are if for this `peak @ mz, rt` there are *many* peaks in the
  // original data in the aggregated hypothesis trace! This is slightly crazy case and will only happen
  // when the peak structure is very zagged. We average the values
  def peakClusterToOne(mz: MonoIsotopicMass, rt: RetentionTime)(s: Set[UntargetedPeak]) = {
    combinePeaks(s.toList, mz, rt, pickMax)// sizedAvg(s.size))
  }

  // aggregate characteristic for peaks for the same molecule (eluting at the same mz, and time)
  def uniformAcross(peaks: List[Set[UntargetedPeak]],
    mz: MonoIsotopicMass,
    rt: RetentionTime): UntargetedPeak = {

    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr
    val handlePeakCluster = peakClusterToOne(mz, rt) _
    combinePeaks(peaks.map(handlePeakCluster), mz, rt, pickMin)
  }

  // identify if the peaks in hyp are outliers compared to the controls
  // we assume these peaks are for the same molecule (eluting at the same mz, and time)
  def isOutlier(peaks: List[Set[UntargetedPeak]],
    mz: MonoIsotopicMass,
    rt: RetentionTime): Option[UntargetedPeak] = {

    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr
    val handlePeakCluster = peakClusterToOne(mz, rt) _
    val ratioedPeak = combinePeaks(peaks.map(handlePeakCluster), mz, rt, ratio)
    checkOutlier(ratioedPeak)
  }

  def isOutlierOLLLLLLLLLLLLLLLLLD(hyp: List[UntargetedPeak],
    ctrl: List[UntargetedPeak],
    mz: MonoIsotopicMass,
    rt: RetentionTime): Option[UntargetedPeak] = {

    val handlePeakCluster = peakClusterToOne(mz, rt) _
    val hypPeak = handlePeakCluster(hyp.toSet)
    val ctrlPeak = handlePeakCluster(ctrl.toSet)

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

  type MzRtPeaks = ((MonoIsotopicMass, RetentionTime), List[Set[UntargetedPeak]])

  def findOutlierForOneMzRt(mzRtPeaks: MzRtPeaks): Option[UntargetedPeak] = {
    val ((mz, rt), peaks) = mzRtPeaks
    isOutlier(peaks, mz, rt)
  }

  def unifyReplicatesForOneMzRt(mzRtPeaks: MzRtPeaks): Option[UntargetedPeak] = {
    val ((mz, rt), peaks) = mzRtPeaks
    Some(uniformAcross(peaks, mz, rt))
  }

  def extractOutliers(hypothesis: LCMSExperiment, control: LCMSExperiment): LCMSExperiment = {
    val exprVsControl = List(hypothesis, control)
    metricOverCommonPeaks(exprVsControl, findOutlierForOneMzRt)
  }

  def unifyReplicates(replicates: List[LCMSExperiment]): LCMSExperiment = {
    metricOverCommonPeaks(replicates, unifyReplicatesForOneMzRt)
  }
  
  def metricOverCommonPeaks(exprs: List[LCMSExperiment], peakCmpFn: MzRtPeaks => Option[UntargetedPeak]): LCMSExperiment = {
    val peakSetsForAllReplicates = exprs.map{ expr => expr.peakSpectra.peaks }
    val peaksKeyedByMzAndRt = findAlignedPeaks(peakSetsForAllReplicates)
    val peaksByMzAndRtNonEmpty = peaksKeyedByMzAndRt.filter{ case(_, lstSets) => lstSets.forall(_.size != 0) }

    val sharedPeaks: Set[UntargetedPeak] = peaksByMzAndRtNonEmpty
      .toSet
      .map(peakCmpFn)
      .filter(_.isDefined)
      .map{ case Some(p) => p }
    val provenance = new ComputedData(sources = exprs.map(_.origin))
    new LCMSExperiment(provenance, new UntargetedPeakSpectra(sharedPeaks))
  }

  // This does not serious boilerplating to move stuff around!
  // Input: Type `List[Set[raw peaks]]` represents:
  //    -- Each list element is an LCMS trace, and the set corresponds to all peaks found in that trace
  // Output: Type `Map[ (mz, rt) -> List[Set[raw peaks]] ]`
  //    -- For each unique mz, rt pair, it is a split of the original data (in the same expr order)
  //       to those peaks in that experiment that have the corresponding (mz, rt).
  //    -- If there are no peaks at that mz, rt in that experiment then it'll be an empty set at that list loc
  def findAlignedPeaks(exprData: List[Set[UntargetedPeak]]): 
    Map[(MonoIsotopicMass, RetentionTime), List[Set[UntargetedPeak]]] = {
    
    // first group each peakset in the list of exprs into a map(mz -> peakset)
    val exprToMzPeaks: List[Map[MonoIsotopicMass, Set[UntargetedPeak]]] = exprData.map(_.groupBy(_.mz))
    // then take the mz's out a layer and map each mz -> list(peakset)
    val allMzs = exprToMzPeaks.flatMap(mp => mp.keys).distinct.sortBy(_.initMass)
    val peaksAtMz: Map[MonoIsotopicMass, List[Set[UntargetedPeak]]] = {
      // for each experiment, get the `mz` if it is there in that experiment, or else empty Set()
      allMzs.map(mz => mz -> exprToMzPeaks.map(mzPeaks => mzPeaks.getOrElse(mz, Set())))
    }.toMap

    // now for each mz, find all experiments and all retention times within them where this mz appears
    val mzRtToPeaks = for (
      mz <- peaksAtMz.keys;
      // for each unique mz, find all peaks in each experiment at that mz
      // and then pull up the optimal covering set of retention times for that mz
      rt <- optimalRts(peaksAtMz(mz))
    ) yield {
      // now filter down to all peaks at that mz, rt
      val peaksAtThisMz: List[Set[UntargetedPeak]] = peaksAtMz(mz)
      val peaksAtThisMzRt: List[Set[UntargetedPeak]] = peaksAtThisMz.map(s => s.filter(isAtMzRt(mz, rt)))
      (mz, rt) -> peaksAtThisMzRt
    }

    mzRtToPeaks.toMap
  }

  def isAtMzRt(mz: MonoIsotopicMass, rt: RetentionTime)(p: UntargetedPeak): Boolean = {
    p.mz.equals(mz) && p.rt.equals(rt)
  }

  def optimalRts(peaksForThisMz: List[Set[UntargetedPeak]]): List[RetentionTime] = {
    val peaksToRtForThisMz: List[List[RetentionTime]] = peaksForThisMz.map(_.toList.map(_.rt))

    // we are most interested in keeping peaks that show up across experiments.
    // so if there is a likelihood of a peak choice that maximizes presence across experiments
    // then we pick that, as opposed to maximizing selections within the experiment

    // to do that, we combine all retention times together in one list
    val rtsAcrossAllExpr: List[RetentionTime] = peaksToRtForThisMz.flatten.sortWith(RetentionTime.ascender)
    // for each element in the list, calculate the number of other elements it is equal to O(n^2)
    val numElemsEqual: List[Int] = rtsAcrossAllExpr.map(t => rtsAcrossAllExpr.count(r => r.equals(t)))
    // order the retention times according to how many elements before and after they cover
    val rtsInMaxCoverOrder = rtsAcrossAllExpr.zip(numElemsEqual).sortWith(_._2 > _._2)
    // now start from head and pick retention times eliminating candidates as you go down the list
    def pickCoverElemsAux(remain: List[(RetentionTime, Int)], acc: List[RetentionTime]): List[RetentionTime] = { 
      remain match {
        case List() => acc
        case hd :: tail => {
          val elim = tail.filter(!_._1.equals(hd._1))
          pickCoverElemsAux(elim, hd._1 :: acc)
        }
      }
    }
    def pickCoverElems(l: List[(RetentionTime, Int)]) = {
      // call aux, but reverse the resulting list coz we append to head when moving to acc
      pickCoverElemsAux(l, List()).reverse
    }
    val mostCoveringRTs: List[RetentionTime] = pickCoverElems(rtsInMaxCoverOrder)
    
    mostCoveringRTs
  }

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
      ("wt-wt", wt, wt, 0, 0), // debugging this case!

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
      ("wt1-df1", List(wt1), List(df1), 330, 375), // 374 @ 5.0, 337 @ 2.0
      ("wt1-dm1", List(wt1), List(dm1), 260, 300), // 299 @ 5.0, 268 @ 2.0
      
      // peaks that are differentially expressed in diseased samples compared to the wild type
      ("wt-dm", wt, dm, 45, 60), // 59 @ 5.0, 45 @ 2.0
      ("wt-df", wt, df, 60, 80), // 77 @ 5.0, 69 @ 2.0
      
      // next two: what is in one diseases samples and not in the other?
      ("dm-df", dm, df, 80, 95), // 92 @ 5.0, 82 @ 2.0
      ("df-dm", df, dm, 60, 85), // 81 @ 5.0, 68 @ 2.0

      // Check what is commonly over/under expressed in diseased samples
      // Woa! This is not really a test case. This is the final analysis!
      ("wt-dmdf", wt, dmdf, 35, 50) // 48 @ 5.0, 35 @ 2.0
      
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
