package com.act.lcms

import java.io.PrintWriter
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
  private val driftTolerated = 5.0 // seconds

  // This function is a helper to `equals`
  // It tests whether two values are within the range of experimental drift we allow
  def withinDriftWindow(a: Double, b: Double) = {
    (math abs (a - b)) < driftTolerated
  }

  // we allow for times to drift by driftTolerated, and so equals matches times that only that apart
  override def equals(that: Any) = that match { 
    case that: RetentionTime => withinDriftWindow(this.time, that.time)
    case _ => false
  }

  // This function is the helper to hashcode. It aligns time values to boundaries, and will
  // deliberately create collisions that when the fallback to `equals` kicks in, will resolve
  // The way to understand is through example. Suppose as e.g., `driftTolerated = 5.0 seconds`, and:
  //    Case 1) `timeA = 23.4 seconds` and `timeB = 13.5 seconds` -- 9.9 seconds apart
  //    Case 2) `timeA = 23.4 seconds` and `timeB = 18.5 seconds` -- 4.9 seconds apart
  //    Case 3) `timeA = 23.4 seconds` and `timeB = 28.1 seconds` -- 5.1 seconds apart
  //    Case 4) `timeA = 23.4 seconds` and `timeB = 33.5 seconds` -- 10.1 seconds apart
  // The function below will align to `10.0` in this case, i.e., will round to nearest 10s.
  // Examining the cases above, we will see rounded values to be `timeA = 2.0` in all cases
  // And Case {1, 2, 3, 4} will be rounded to `timeB = {1.0, 2.0, 3.0, 3.0}` respectively
  // Which means only "Case 2)" will map both timeA and timeB together. This is exactly what
  // we'd want to happen.
  // Now for the "deliberate collisions". Note that in the case `timeA = 23.4 and timeB = 16.2`
  // both would align to `2.0` and we would have a hash collision. But that's ok since
  // equals will disambiguate these as they are `7.2` apart which greater than the `5.0` drift allowed.
  def alignToBoundary() = {
    val boundaryStep: Double = 2.0 * driftTolerated
    (math round (time / boundaryStep)) * boundaryStep
  }

  // note that this will deliberately create collisions. as explained in `alignToBoundary` comment
  override def hashCode() = alignToBoundary().hashCode

  override def toString(): String = this.time.toString
}

sealed trait Provenance
class RawData(val source: String) extends Provenance
class ComputedData(val sources: List[Provenance]) extends Provenance

class LCMSExperiment(val origin: Provenance, val peakSpectra: UntargetedPeakSpectra) {
  override def toString = peakSpectra.toString
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
}

sealed class XCMSCol(val id: String)
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
          case Some((integr,max,snr)) => Some(new UntargetedPeak(mz, rt, integr, max, snr))
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
    def together(closeBy: List[Double]) = {
      println(s"Woah! Multiple peaks close by: $closeBy")
      closeBy.sum / closeBy.size
    }

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
    if (valleyShape(aggregateIntegratedInts) > 1.0)
      Some((aggregateIntegratedInts, aggregateMaxInts, aggregateSnrs))
    else
      None
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
    val alignedPeaks: Set[PeakAt] = peaks.map(_.values.toSet).reduce(intersect)

    def getOriginalPeaks(mzRt: PeakAt): List[List[UntargetedPeak]] = {
      peaksAs2D.map(peaksInSingleExpr => getPeaksAtThisMzRt(mzRt, peaksInSingleExpr))
    }

    val alignedToOriginalPeaks: Map[PeakAt, List[List[UntargetedPeak]]] = alignedPeaks.map(
      peak => peak -> getOriginalPeaks(peak)
    ).toMap

    (alignedPeaks, alignedToOriginalPeaks)
  }

  def getPeaksAtThisMzRt(mzRt: PeakAt, originalPeaks: List[(UntargetedPeak, PeakAt)]): List[UntargetedPeak] = {
    for ((origPeak, origMzRt) <- originalPeaks if origMzRt.equals(mzRt)) yield origPeak
  }

  def peakKv(peak: UntargetedPeak): (UntargetedPeak, PeakAt) = peak -> (peak.mz, peak.rt)


}

object UntargetedMetabolomics {

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List(optOutFile, optControls, optHypotheses, optRunTests)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    def mkLCMSExpr(kv: String) = {
      val spl = kv.split("=")
      val (name, file) = (spl(0), spl(1))
      val srcOrigin = new RawData(source = name)
      new LCMSExperiment(srcOrigin, UntargetedPeakSpectra.fromXCMSCentwave(file))
    }

    // read the command line options
    val runTests = cmdLine has optRunTests
    val controls = (cmdLine getMany optControls).map(mkLCMSExpr)
    val hypotheses = (cmdLine getMany optHypotheses).map(mkLCMSExpr)
    val experiment = new UntargetedMetabolomics(controls = controls, hypotheses = hypotheses)

    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }

    if (runTests)
      runUnitTests()

    // do the thing!
    val analysisRslt = experiment.analyze()

    out.write(analysisRslt.toString)
  }

  val optControls = new OptDesc(
                    param = "c",
                    longParam = "controls",
                    name = "{name=file}*",
                    desc = """Controls: Comma separated list of name=file pairs""".stripMargin,
                    isReqd = true, hasArgs = true)

  val optHypotheses = new OptDesc(
                    param = "e",
                    longParam = "experiments",
                    name = "{name=file}*",
                    desc = """Experiments: Comma separated list of name=file pairs""".stripMargin,
                    isReqd = true, hasArgs = true)

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outfile",
                    name = "filename",
                    desc = "The file to which ...",
                    isReqd = false, hasArg = true)

  val optRunTests = new OptDesc(
                    param = "t",
                    longParam = "run-tests",
                    name = "run regression tests",
                    desc = """Run regression tests.""",
                    isReqd = false, hasArg = false)

  def runUnitTests() {
  }

}
