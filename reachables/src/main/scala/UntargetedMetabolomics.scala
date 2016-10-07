package com.act.lcms

import java.io.{File, PrintWriter}
import java.util.NavigableMap

import act.shared.{CmdLineParser, OptDesc}

import scala.io.Source
import act.shared.ChemicalSymbols.{AllAminoAcids, Atom, MonoIsotopicMass}
import com.act.lcms.MS1.MetlinIonMass
import act.shared.MassToFormula
import act.shared.ChemicalSymbols.Helpers.computeMassFromAtomicFormula
import com.act.lcms.MassCalculator.calculateMass

// @mark-20n @MichaelLampe20n: help resolve this to specific imports; please!
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.annotation.tailrec

object MagicParams {
  // peak size metric is either INT = IntegratedInt or MAX = MaxInt or SNR = SNR
  val _rankPeaksBy = "SNR" // "INT" // "MAX"
  
  // when a peak is missing, we install a negative MaxInt, SNR, IntegratedInt peak
  val _missingPeakVal = -1.0

  // we use a pretty valley fn `10cosh(x-1)-10` that is:
  //  * y=0.0 @ x=1.0
  //  * y>1.0 @ [-inf, 0.55] and [1.45, inf]
  // The threshold below is the check on `y>`, i.e., <55% under- or >45% over-expressed
  // Note that the cosh function we are using is asymmetrical. That is absolute non-ideal
  // TODO: Switch to symmetrical function that calls over- and under- in the same way.
  // Then, we'd be able to switch the engg vs wildtype to wildtype vs engg andd see
  // numerically similar results, just switched around.
  val _valleyShapeThreshold = 1.0

  // when normalization vectors are calculated, we get for each pivot (amino acid)
  // a vector in n-dimensional space (n = number of LCMS traces being normalized)
  // and we expect most of them to be "similar". We calculate similarity by vector angle
  // and only allow "rebelious" angles only as far as the parameter below
  val _normVecMaxDeviation = 20 // degrees

  // replicates should truly be combined using `min` over all technical/biological.
  // that said, the outcomes with min (as opposed to avg) are hard to intuitively interpret
  // for humans. so for debugging and exploration, we have this flag
  // that temporarily switches to averaging as the means of replicate combination.
  val _useMinForReplicateAggregation = true

  // to keep the output file for visualization smaller, we disable outputing raw mz and raw rt
  // values. this flag controls that.
  val _outputRawPeakData = true
}

object RetentionTime {
  // Default drift allowed is emperically picked based on observations over experimental data
  val driftTolerated = 1.0 // seconds

  def middle(xs: List[Double]): Double = {
    // In the cases of odd sized xs this would correspond to median
    // But in the case of even sized lists, we don't want to average since that 
    // would give us a point that is not in the original retention times making
    // provenance of that datapoint difficult to track from the original
    xs.sorted.toList(xs.size / 2)
  }
  def middle(times: List[RetentionTime]): RetentionTime = new RetentionTime(middle(times.map(_.time)))
  def isLt(a: RetentionTime, b: RetentionTime) = a.time < b.time
}

class RetentionTime(val time: Double) {
  // This function is a helper to `equals`
  // It tests whether two values are within the range of experimental drift we allow
  private def withinDriftWindow(a: Double, b: Double) = (math abs (a - b)) < RetentionTime.driftTolerated

  // we allow for times to drift by driftTolerated, and so equals matches times that only that apart
  override def equals(that: Any) = that match { 
    case that: RetentionTime => withinDriftWindow(this.time, that.time)
    case _ => false
  }

  // calculate how many decimal places we consider equal, e.g., drift = 0.1 => numDecimals = 1
  // if drift = 1.0 => numDecimals = 0, if drift = 5.0 => numDecimals = 0
  val invLogDrift = math log10 (1.0 / RetentionTime.driftTolerated)
  // when drift > 1.0 inverse log will be < 0.0, so min bound it to 0 and round it
  val numDecimalsSignificant = math round (math max (0.0, invLogDrift))

  override def toString(): String = {
    String.format(s"%3.${numDecimalsSignificant}f", this.time: java.lang.Double)
  }

  def isIn(low: Double, high: Double): Boolean = time >= low && time <= high
}

sealed trait Provenance
class RawData(val source: String) extends Provenance
class ComputedData(val sources: List[Provenance]) extends Provenance

class PeakHits(val origin: Provenance, val peakSpectra: PeakSpectra) {

  // the peak summary is what makes it to the output. we also expect other overriders of this class
  // to override the peakSummarizer when they need to augment the peak information, e.g., molecules
  // corresponding to the peak.
  def peakSummarizer(p: Peak) = p.summary

  // because the peakSummary is a Map(String -> Double) (and the corresponding json object), if for
  // a peak we need to output more than a double, we need a way of storing them elsewhere. What we do
  // is output a double (hashCode) in the peak map, basically an ID that can then be located in another
  // place in the output json. That "another" place are the `extraCodes`
  def extraCodes(): Map[Double, List[String]] = Map()

  // when we take the ratio (and install -1.0 missing peaks), the output metrics will be such:
  // (+1.0,\inf): if signals present in ALL samples and OVER expressed in hypotheses vs controls
  // (0.0, +1.0): if sinnals present in ALL samples and UNDER expressed in hypotheses vs controls 
  // (-1.0, 0.0): if signals MISSING in hypotheses, i.e., extreme UNDER expressed in hypotheses vs controls
  // (-\inf, -1.0): if signals MISSING in controls, i.e., extreme OVER expressed in hypotheses vs controls
  def signalPresentInAll(p: Peak) = p.rank > +0.0
  def signalPresentInAllOverExpr(p: Peak) = p.rank > +1.0

  assert(MagicParams._missingPeakVal == -1.0) 
  def signalMissingInHypOverExpr(p: Peak) = p.rank < -1.0

  def numPeaks: Map[String, Int] = {
    val (peaksInAll, peaksMissingInSome) = peakSpectra.peaks.partition(signalPresentInAll)
    val (overExprPresentInAll, underExprPresentInAll) = peaksInAll.partition(signalPresentInAllOverExpr)
    val (overExprMissingInSome, underExprMissingInSome) = peaksMissingInSome.partition(signalMissingInHypOverExpr)

    val sizes = Map(
      "over-expressed" -> (overExprPresentInAll.size + overExprMissingInSome.size),
      "under-expressed" -> (underExprPresentInAll.size + underExprMissingInSome.size)
    )

    sizes
  }

  def sortedPeaks = {
    def sortfn(a: Peak, b: Peak) = {
      val field: XCMSCol = IntIntensity // you can also sort by MZ or RT
      field match {
        case IntIntensity => a.integratedInt > b.integratedInt
        case MaxIntensity => a.maxInt > b.maxInt
        case SNR => a.snr > b.snr
        case MZ => MonoIsotopicMass.isLt(a.mz, b.mz)
        case RT => RetentionTime.isLt(a.rt, b.rt)
      }
    }
    peakSpectra.peaks.toList.sortWith(sortfn)
  }

  def toJsonFormat(extra: Map[String, JsValue] = Map()) = {
    def getPlates(src: Provenance): List[Map[String, String]] = {
      // because of the processing we do, we end up with three nested arrays on provenance:
      // 1. normalization
      // 2. replication aggregation
      // 3. outlier detection
      src match {
        case file: RawData => List(Map("filename" -> new File(file.source).getName.replace(".tsv",".nc")))
        case nested: ComputedData => nested.sources.map(o => getPlates(o)).flatten
      }
    }
    val peakSummaries = sortedPeaks.map(peakSummarizer)
    val plateNames = getPlates(origin)
    val nrows = 2 // always have one control set and one experimental set
    val ncols = plateNames.size / nrows
    val layout = Map("nrow" -> nrows, "ncol" -> ncols)
    val output = Map(
      "peaks" -> peakSummaries.toJson,      // List[Map[String, Double]]
      "plates" -> plateNames.toJson,        // List[Map[String, String]]
      "num_peaks" -> numPeaks.toJson,       // Map[String, Int]
      "layout" -> layout.toJson             // Map[String, Int]
    ) ++ extra

    output.toJson 
  }
}

class RawPeaks(val org: Provenance, val ps: PeakSpectra) extends PeakHits(org, ps)

class Peak(
  val mz: MonoIsotopicMass,
  val rt: RetentionTime,
  val integratedInt: Double,
  val maxInt: Double,
  val snr: Double
) {
  override def toString = {
    f"($integratedInt%.2f, $maxInt%.2f, $snr%.2f)" + s" @ mzrt($mz, $rt) "
  }

  def getHdrVal(hdr: TSVHdr): Double = hdr match {
    case HdrMZ => mz.rounded()
    case HdrRT => rt.time
    case HdrDiff => rank // use whatever metric
    case HdrRawMZ => mz.initMass // raw MZ from incoming data 
    case HdrRawRT => rt.time // raw RT from incoming data
  }

  val hdrs = {
    val core = List(HdrMZ, HdrRT, HdrDiff)
    val extr = List(HdrRawMZ, HdrRawRT)
    if (!MagicParams._outputRawPeakData) core else core ++ extr
  }

  def summary(): Map[String, Double] = {
    val taggedVals = hdrs.map(h => h.toString -> getHdrVal(h)).toMap
    val withMeta = taggedVals + 
        (HdrMZBand.toString -> 2 * MonoIsotopicMass.tolerance()) +
        (HdrRTBand.toString -> 5 * RetentionTime.driftTolerated)
    withMeta
  }

  def rank() = MagicParams._rankPeaksBy match {
    case "MAX" => this.maxInt
    case "INT" => this.integratedInt
    case "SNR" => this.snr
  }

  def scaleBy(factor: Double) = {
    // change the integrated and max intensities, but SNR stays the same. SNR is not a scaling candidate!
    new Peak(this.mz, this.rt, this.integratedInt * factor, this.maxInt * factor, this.snr)
  }
}

class PeakSpectra(val peaks: Set[Peak])

sealed trait HasId {
  val id: String
  override def toString = id
}

sealed class XCMSCol(val id: String) extends HasId
object MZ extends XCMSCol("mz")
object RT extends XCMSCol("rt")
object IntIntensity extends XCMSCol("into")
object MaxIntensity extends XCMSCol("maxo")
object SNR extends XCMSCol("sn")

sealed class TSVHdr(val id: String) extends HasId
object HdrMZ extends TSVHdr("mz")       // mz at precision considered equal (MonoIsotopicMass.numDecimalPlaces)
object HdrRT extends TSVHdr("rt") // rt at precision considered equal (RetentionTime.driftTolerated/toString)
object HdrMZBand extends TSVHdr("mz_band") // when outputing the json for viz on lcms/ we need a band param
object HdrRTBand extends TSVHdr("rt_band") // when outputing the json for viz on lcms/ we need a band param
object HdrDiff extends TSVHdr("rank_metric") // the x% over- or under-expressed compared to controls
object HdrRawMZ extends TSVHdr("raw_mz") // mz value up to infinite precision (from input data)
object HdrRawRT extends TSVHdr("raw_rt") // rt value up to infinite precision (from input data)
object HdrMolMass extends TSVHdr("mol_mass") // if we map to molecules, then the monoisotopic mass thereof
object HdrMolIon extends TSVHdr("ion")  // if we map to molecules, the ion of which the peak is observed
object HdrMolFormula extends TSVHdr("formula") // if we map to formula, the formula of the molecule
object HdrMolInChI extends TSVHdr("inchi") // if we map to InChI, the inchi of the molecule
object HdrMolHitSource extends TSVHdr("hitsrc") // if we map to formula/inchi, where the hit was found & DB ID

trait CanReadTSV {
  type H <: HasId // head types in the first row
  type V // value types in everything expect the first row

  // TODO: If com.act.utils.TSVParser was parameterized to use the header type `H` and content value type `V`
  // it could replace this custom reader. Considerations: 1) Types, 2) Only keep recognized hdrs, 3) # comments
  def readTSV(file: String, hdrs: List[H], semanticizer: String => V): List[Map[H, V]] = {
    val linesWithComments = Source.fromFile(file).getLines.toList
    val lines = linesWithComments.filter(l => l.length > 0 && l(0) != '#').map(_.split("\t").toList)
    val hdr::tail = lines
    val identifiedHdrs = hdr.map(hid => hdrs.find(_.id.equals(hid)))
    val withHdrs = tail.map(l => identifiedHdrs.zip(l))
    def keepOnlyRecognizedCols(line: List[(Option[H], String)]): Map[H, V] = {
      line.filter(_._1.isDefined).map{ case (Some(hdr), value) => (hdr, semanticizer(value)) }.toMap
    }
    val tsvData = withHdrs.map(keepOnlyRecognizedCols)
    tsvData
  }
}

object PeakSpectra extends CanReadTSV {
  type H = XCMSCol
  type V = Double

  val hdrsXCMS = List(MZ, RT, IntIntensity, MaxIntensity, SNR)

  def peaksFromRow(row: Map[XCMSCol, Double]): Peak = {
    new Peak(
      new MonoIsotopicMass(row(MZ)),
      new RetentionTime(row(RT)),
      row(IntIntensity),
      row(MaxIntensity),
      row(SNR))
  }

  def toMass(s: String): Double = s.toDouble

  def fromCalledPeaks(file: String): PeakSpectra = {
    // example TSV
    //    mz  mzmin mzmax rt  rtmin rtmax into  intb  maxo  sn  sample
    //    244.98272  244.97964  244.985247  2.56099  1.91800  2.98900  130.32171 129.46491  253.17785  252 1
    // Such are the files in /mnt/shared-data/Vijay/perlstein_xcms_centwave_optimized_output
    val rows: List[Map[XCMSCol, Double]] = readTSV(file, hdrsXCMS, toMass _)
    val peaks: Set[Peak] = rows.map(peaksFromRow).toSet
    new PeakSpectra(peaks)
  }

  val fromDeepLearnt: String => PeakSpectra = fromCalledPeaks _

}

class UntargetedMetabolomics(val controls: List[RawPeaks], val hypotheses: List[RawPeaks]) {

  def analyze(): RawPeaks = {
    val (normalizedControls, normalizedHypotheses) = normalize(controls, hypotheses)
    val unifiedControls = unifyReplicates(normalizedControls)
    val unifiedHypotheses = unifyReplicates(normalizedHypotheses)
    extractOutliers(unifiedHypotheses, unifiedControls)
  }

  def normalize(setA: List[RawPeaks], setB: List[RawPeaks]) = {
    // we normalize across all datasets, so we put them in a bin together
    // but we remember where each came from by keeping the num of experiments
    // each in A, B, i.e., |A|, |B|, so that later we can just `take` that many out
    val exprAB = List(setA, setB)
    val szA = setA.size
    val allExpr = exprAB.flatten
    val norm = normalizeCommonPeaks(allExpr)
    // now split it back into two lists of appropriate sizes
    (norm.take(szA), norm.drop(szA))
  }

  // we use the average to combine multiple signals at the same mz, rt
  def sizedAvg(num: Int)(a: Double, b: Double) = (a + b) / num
  def pickMax(a: Double, b: Double) = math.max(a, b)
  // we use min to aggregate signals across replicates
  def pickMin(a: Double, b: Double) = math.min(a, b)
  // we use ratio to identify differentially expressed peaks
  def ratio(a: Double, b: Double) = {
    // there are fields (think, snr) that can come in as both being 0.0. equate 0.0/0.0
    // in that case to 0.0. Rest of the time we do not expect either of the values to be zero.
    if (a == 0.0 && b == 0.0) {
      0.0 
    } else {
      assert(a != 0.0 && b != 0.0)
      a/b
    }
  }
  def missingPk(mz: MonoIsotopicMass, rt: RetentionTime) = {
    val defaultv = MagicParams._missingPeakVal
    new Peak(mz, rt, defaultv, defaultv, defaultv)
  }

  // the lists coming in for the inputs are if for this `peak @ mz, rt` there are *many* peaks in the
  // original data. To combine the peaks we pick their Max value.
  def peakClusterToOne(mz: MonoIsotopicMass, rt: RetentionTime)(s: Set[Peak]) = {
    combinePeaks(s.toList, mz, rt, pickMax)
  }

  // aggregate characteristic for peaks for the same molecule (eluting at the same mz, and time)
  def uniformAcross(peaks: List[Set[Peak]],
    mz: MonoIsotopicMass,
    rt: RetentionTime): Peak = {

    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr
    val handlePeakCluster = peakClusterToOne(mz, rt) _
    val replicateCombineFn = if (MagicParams._useMinForReplicateAggregation) {
      pickMin _
    } else {
      sizedAvg(peaks.size) _
    }
    combinePeaks(peaks.map(handlePeakCluster), mz, rt, replicateCombineFn)
  }

  // identify if the peaks in hyp are outliers compared to the controls
  // we assume these peaks are for the same molecule (eluting at the same mz, and time)
  def isOutlier(peaks: List[Set[Peak]],
    mz: MonoIsotopicMass,
    rt: RetentionTime): Option[Peak] = {

    // all the peaks passed in here should have the same (mz, rt) upto tolerances
    // all we have to do is aggregate their (integrated and max) intensity and snr
    val handlePeakCluster = peakClusterToOne(mz, rt) _
    val ratioedPeak = combinePeaks(peaks.map(handlePeakCluster), mz, rt, ratio)
    checkOutlier(ratioedPeak)
  }

  def combinePeaks(peaks: List[Peak], 
    mz: MonoIsotopicMass, rt: RetentionTime,
    fn: (Double, Double) => Double) = {

    val (integratedInts, maxInts, snrs) = peaks.map(p => (p.integratedInt, p.maxInt, p.snr)).unzip3
    val aggIntegratedInts = integratedInts reduce fn
    val aggMaxInts = maxInts reduce fn
    val aggSnrs = snrs reduce fn

    new Peak(mz, rt, aggIntegratedInts, aggMaxInts, aggSnrs)
  }

  def checkOutlier(peak: Peak): Option[Peak] = {
    // TODO: change this to a symmetric function. E.g., 10 (x-1) ^2 might do.
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
    val metric = peak.rank
    if (valleyShape(metric) > MagicParams._valleyShapeThreshold)
      Some(peak)
    else
      None
  }

  type MzRtPeaks = ((MonoIsotopicMass, RetentionTime), List[Set[Peak]])

  def findOutlierForOneMzRt(mzRtPeaks: MzRtPeaks): Option[Peak] = {
    val ((mz, rt), peaks) = mzRtPeaks
    isOutlier(peaks, mz, rt)
  }

  def unifyReplicatesForOneMzRt(mzRtPeaks: MzRtPeaks): Option[Peak] = {
    val ((mz, rt), peaks) = mzRtPeaks
    Some(uniformAcross(peaks, mz, rt))
  }

  def extractOutliers(hypothesis: RawPeaks, control: RawPeaks): RawPeaks = {
    val exprVsControl = List(hypothesis, control)
    metricOverCommonPeaks(exprVsControl, findOutlierForOneMzRt, addProxyPeak = true)
  }

  def unifyReplicates(replicates: List[RawPeaks]): RawPeaks = {
    metricOverCommonPeaks(replicates, unifyReplicatesForOneMzRt)
  }
  
  def metricOverCommonPeaks(exprs: List[RawPeaks],
    peakCmpFn: MzRtPeaks => Option[Peak],
    addProxyPeak: Boolean = false): RawPeaks = {
    val peakSetsForAllReplicates = exprs.map{ expr => expr.peakSpectra.peaks }
    val peaksKeyedByMzAndRt = findAlignedPeaks(peakSetsForAllReplicates)
    val peaksByMzAndRtNonEmpty = handleMissingPks(addProxyPeak)(peaksKeyedByMzAndRt)

    val sharedPeaks: Set[Peak] = peaksByMzAndRtNonEmpty
      .toSet
      .map(peakCmpFn)
      .filter(_.isDefined)
      .map{ case Some(p) => p }
    val provenance = new ComputedData(sources = exprs.map(_.origin))
    new RawPeaks(provenance, new PeakSpectra(sharedPeaks))
  }

  def handleMissingPks(addProxy: Boolean)(aligned: Map[(MonoIsotopicMass, RetentionTime), List[Set[Peak]]]) = {
    addProxy match {
      case true => {
        // replace the missing peak with proxy
        aligned.map{ case (mzrt, pksForSamples) => (mzrt, {
          val (mz, rt) = mzrt
          pksForSamples.map(s => if (s.isEmpty) { Set(missingPk(mz, rt)) } else s) }
        )}
      }
      case false => {
        // we just want to remove groups where one of the sets is missing the peak
        aligned.filter{ case(_, pksForSamples) => pksForSamples.forall(!_.isEmpty) }
      }
    }
  }

  def normalizeCommonPeaks(exprs: List[RawPeaks]): List[RawPeaks] = {
    // Important: This function has to output normalized peaks in the same order as input (hence the list in-out)
    // `normalize` below maintains that invariant

    // we do the same thing we do when we are computing a uniform metric over shared peaks across all exprs
    // we first extract the shared peaks (and then we'll look for how they differ across each set)
    val peakSetsForAllReplicates = exprs.map{ expr => expr.peakSpectra.peaks }
    val alignedPeaksKeyedByMzAndRt = findAlignedPeaks(peakSetsForAllReplicates)
    val peaksByMzAndRtNonEmpty = alignedPeaksKeyedByMzAndRt.filter{ case(_, lstSets) => lstSets.forall(_.size != 0) }

    try {
      // calculate the normalization factor, defined as ratio of peak intensities in pivot compared to each expr
      // by default, `NormalizationVector.build` normalizes using Amino Acids. (See `NormalizeUsingAminoAcids`)
      val normalizer = NormalizationVector.build(peaksByMzAndRtNonEmpty.toList)

      // now normalize each experiment to normalize across systematic changes in peak intensities
      normalizer.normalize(exprs)
    } catch {
      // if a normalization vector not found, abort and return unnormalized peaks
      case e: Exception => {
        println(s"No shared (AA) peaks common across samples. Continuing without normalization!")
        exprs
      }
    }
  }

  // This does some serious boilerplating to move stuff around!
  // Input: Type `List[Set[raw peaks]]` represents:
  //    -- Each list element is an LCMS trace, and the set corresponds to all peaks found in that trace
  // Output: Type `Map[ (mz, rt) -> List[Set[raw peaks]] ]`
  //    -- For each unique mz, rt pair, it is a split of the original data (in the same expr order)
  //       to those peaks in that experiment that have the corresponding (mz, rt).
  //    -- If there are no peaks at that mz, rt in that experiment then it'll be an empty set at that list loc
  def findAlignedPeaks(exprData: List[Set[Peak]]): Map[(MonoIsotopicMass, RetentionTime), List[Set[Peak]]] = {
    
    // first group each peakset in the list of exprs into a map(mz -> peakset)
    val exprToMzPeaks: List[Map[MonoIsotopicMass, Set[Peak]]] = exprData.map(_.groupBy(_.mz))
    // then take the mz's out a layer and map each mz -> list(peakset)
    val allMzs = exprToMzPeaks.flatMap(mp => mp.keys).distinct.sortBy(_.initMass)
    val peaksAtMz: Map[MonoIsotopicMass, List[Set[Peak]]] = {
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
      val peaksAtThisMz: List[Set[Peak]] = peaksAtMz(mz)
      // TODO: is filtering on mz redundant? Maybe not so if hashcode for mz always leads to collisions
      // this filter is done with `equals`
      val peaksAtThisMzRt: List[Set[Peak]] = peaksAtThisMz.map(s => s.filter(isAtMzRt(mz, rt)))
      (mz, rt) -> peaksAtThisMzRt
    }

    mzRtToPeaks.toMap
  }

  def isAtMzRt(mz: MonoIsotopicMass, rt: RetentionTime)(p: Peak): Boolean = {
    p.mz.equals(mz) && p.rt.equals(rt)
  }

  def optimalRts(peaksForThisMz: List[Set[Peak]]): List[RetentionTime] = {
    val peaksToRtForThisMz: List[List[RetentionTime]] = peaksForThisMz.map(_.toList.map(_.rt))

    // we are most interested in keeping peaks that show up across experiments.
    // so if there is a likelihood of a peak choice that maximizes presence across experiments
    // then we pick that, as opposed to maximizing selections within the experiment

    // to do that, we combine all retention times together in one list
    val rtsAcrossAllExpr: List[RetentionTime] = peaksToRtForThisMz.flatten.sortWith(RetentionTime.isLt)
    // for each element in the list, calculate the number of other elements it is equal to O(n^2)
    val numElemsEqual: List[Int] = rtsAcrossAllExpr.map(t => rtsAcrossAllExpr.count(r => r.equals(t)))
    // order the retention times according to how many elements before and after they cover
    // Also, to keep it as a stable sort, we ensure that when the counts are equal we order by RT
    val rtsInMaxCoverOrder = rtsAcrossAllExpr
                              .zip(numElemsEqual)
                              .sortWith{ 
                                case ((rt1, n1), (rt2, n2)) => {
                                  if (n1 == n2)
                                    RetentionTime.isLt(rt1, rt2)
                                  else
                                    n1 > n2
                                }
                              }
    // now start from head and pick retention times eliminating candidates as you go down the list
    @tailrec
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
    println(s"Timed: ${(end - start) / 1000000000.0} seconds")
    rslt
  } 

  ////
  // The two classes below `NormalizationVector` and `NormalizeUsingAminoAcids` could be refactored
  // to be independent classes. Only considerations: they rely on type `MzRtPeaks` and on the 
  // function `peakClusterToOne`. So for now, we leave them as inner classes, and can modularize code later.
  ////

  class NormalizationVector(val vec: List[Double], val pivots: Object) {
    // a normalization vector is a ordered list of multiplicative factors
  
    // we compute cosine similarity in n-dimensional space
    // which allows us to compute normal vector metrics between vectors
    // e.g., we can use the angle between the vectors and their magnitude
    // as measures of how similar the vectors are to each other
    // and if they are all similar within bounds, then use the average of
    // the vectors as the consensus vector.
  
    def +(that: NormalizationVector) = {
      val vecSum = this.vec.zip(that.vec).map(pairwise(_+_))
      val origin = List(this.pivots, "+", that.pivots)
      new NormalizationVector(vecSum, origin)
    }
  
    def -(that: NormalizationVector) = {
      val vecSum = this.vec.zip(that.vec).map(pairwise(_-_))
      val origin = List(this.pivots, "-", that.pivots)
      new NormalizationVector(vecSum, origin)
    }
  
    def pairwise(fn: (Double, Double) => Double)(v: (Double, Double)): Double = { fn(v._1, v._2) }
  
    def /(denominator: Double) = {
      val div = this.vec.map(_ / denominator)
      val origin = List(pivots, "/", denominator)
      new NormalizationVector(div, origin)
    }
  
    def dot(that: NormalizationVector): Double = {
      val prd: List[Double] = this.vec.zip(that.vec).map(pairwise(_*_))
      prd.reduce(_ + _)
    }
  
    def angle(that: NormalizationVector): Double = {
      // cos(angle) = dot product / product of lens
      val angle = math acos ((this dot that) / (this.len * that.len))
  
      // because of errors in precision, for this == that, cos-1(1.0) 
      // ends up computing a 1e-8 value, so lets round that down to 0
      if (angle < 1e-7) 0 else angle
    }
  
    def len(): Double = {
      val squares = this.vec.map(v => math pow (v, 2))
      math.sqrt(squares.reduce(_ + _))
    }
  
    // to normalize a trace with a vector, we multiply each peak within
    // each experiment with its corresponding multiplicative factor
    def normalize(exprs: List[RawPeaks]) = {
      // Important: This function has to output normalized RawPeaks in the same order as input
      // The map below over maintains taht invariant
      val exprsNormFactor = exprs.zip(vec)
      exprsNormFactor.map{ case (e, multiplier)  => {
        val normalizedPeaks = e.peakSpectra.peaks.map(_.scaleBy(multiplier))
        val provenance = new ComputedData(sources = List(e.origin))
        new RawPeaks(provenance, new PeakSpectra(normalizedPeaks))
      }}
    }
  
    override def toString = s"Multipliers = $vec Using pivots = $pivots"
  }
  
  object NormalizationVector {
  
    // build a normalization vector using a set of peaks coming in for pivots
    def build(alignedPeaks: List[MzRtPeaks]): NormalizationVector = {
      // now filter/focus attention to only the relevant peaks, e.g., aminoacid peaks
      val peaksForPivots: Set[MzRtPeaks] = alignedPeaks.filter{ 
        case ((mz, _), _) => NormalizeUsingAminoAcids.isPivotMz(mz)
      }.toSet
  
      val possibleNormalizers: Set[NormalizationVector] = peaksForPivots.map{
        case ((mz, rt), peakSets) => {
          // peakSets is a ordered list of peaks found in each spectra
          // because there might be multiple reading in each spectra for the same mz,rt
          // there can be a set of replicates readings. So we compress each set into a single peak
          val representativePeaks = peakSets.map(peakClusterToOne(mz, rt))
          // we now have a single representative peak for each spectra (at this pivot point mz)
          // use that representative peak to find the normalization factor
          val multipliers = getMultipliers(representativePeaks)
          val derivedFrom = mz
          new NormalizationVector(multipliers, mz)
        }
      }
      pickRep(possibleNormalizers)
    }
  
    def pickRep(vectors: Set[NormalizationVector]): NormalizationVector = {
      // find the vector that is least away from all others
      // i.e., find one whose average angle to all others is minimal
      // we compute for each vector its angle to all others, then take the average of
      // that angle and create a map(vector, avg_angle), we then sort on avg angle
      // such that at the head of the list we have the "representative" with min angle
      // to all others.
  
      val size = vectors.size
      // for all pairs of vectors compute the angles
      // this will be O(n^2) in the size of vectors
      val vecAvgAngles = vectors.map{ case v => v -> {
        val anglesToOthers = for (other <- vectors if other != v) yield { v angle other }
        val avgAngleToOthers = anglesToOthers.reduce(_ + _) / (size - 1)
        avgAngleToOthers
      }}.toList
      val inOrderOfSimilarityToOthers = vecAvgAngles.sortWith{ case ((v1, ang1), (v2, ang2)) => ang1 < ang2 }
      val mostRep = inOrderOfSimilarityToOthers(0)._1
      
      ensureRepNotTooCrazy(inOrderOfSimilarityToOthers)
  
      mostRep
    }
  
    val okAngleDeviation = math toRadians MagicParams._normVecMaxDeviation
  
    def ensureRepNotTooCrazy(ordVecs: List[(NormalizationVector, Double)]) {
      println(s"Norm pivots: ${ordVecs.map{ case (v, a) => v.pivots}}")
      println(s"Norm vectors: $ordVecs")
      val repAngle: Double = ordVecs(0)._2
      val rebelAngle: Double = ordVecs.last._2
      
      val tooDeviant = rebelAngle > okAngleDeviation || repAngle > okAngleDeviation
      if (tooDeviant)
        assert(false, "Vectors deviate way too much!")
    }
  
    def getMultipliers(peakSet: List[Peak]) = {
      // it does not matter which peak we pick as the normalizer, so might as well pick the first
      val valueOf1 = peakSet(0).rank
      // for each spectra now, we calculate what factor will bring it to the same scale as the first
      // e.g., if the AminoAcid Cys was present in all traces, and in the first it's intensity was 5
      // and in the 2nd, 3rd, 4th it was 10, 20, 30 respectively. Then we need to normalize by
      // 1/1, 1/2, 1/4, and 1/6 in the 1st..4th respectively. These are equivalently
      // 5/5, 5/10, 5/20, 5/30, which is `valueOf1 / (ith intensity)`
      val normFactors = peakSet.map{ p => valueOf1 / p.rank }
      normFactors
    }
  
  }
  
  object NormalizeUsingAminoAcids {
    val mH: MetlinIonMass = MS1.ionDeltas.find(_.getName.equals("M+H")) match { case Some(mh) => mh }
    val aaMasses: List[Double] = AllAminoAcids.map(_.mass.initMass)
    val aaMzs: List[MonoIsotopicMass] = aaMasses.map(m => new MonoIsotopicMass(MS1.computeIonMz(m, mH)))
    def isPivotMz(mz: MonoIsotopicMass) = aaMzs.contains(mz)
  }
}

object UntargetedMetabolomics {

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List(optOutFile, optControls, optHypotheses, optDoIons, optRestrictIons, optMultiIonsRankHigher, 
                    optToStructUsingList, optToFormulaUsingList, optToFormulaUsingNavigableMap, optToFormulaUsingSolver,
                    optGetDifferentialFromDL, optFilterRtRegions, optRunTests)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    // read the command line options
    val runTests = cmdLine get optRunTests
    val controls = cmdLine getMany optControls
    val hypotheses = cmdLine getMany optHypotheses
    val mapToMassAndIons = cmdLine has optDoIons
    val restrictedIons = cmdLine getMany optRestrictIons
    val rankMultipleIons = cmdLine has optMultiIonsRankHigher
    val mapToInChIsUsingList = cmdLine has optToStructUsingList
    val mapToFormulaUsingList = cmdLine has optToFormulaUsingList
    val mapToFormulaUsingNavigableMap = cmdLine has optToFormulaUsingNavigableMap
    val mapToFormulaUsingSolver = cmdLine has optToFormulaUsingSolver
    val inchiListFile = cmdLine get optToStructUsingList
    val formulaListFile = cmdLine get optToFormulaUsingList
    val formulaNavMapFile = cmdLine get optToFormulaUsingNavigableMap
    val dlDifferentials = cmdLine get optGetDifferentialFromDL
    val filterRtRegions = cmdLine getMany optFilterRtRegions

    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }

    if (cmdLine has optRunTests) {
      val nasSharedDir = cmdLine get optRunTests
      runPerlsteinLabTests(new File(nasSharedDir), out)
    }

    def mkLCMSExpr(kv: String) = {
      val spl = kv.split("=")
      val (shortname, file) = (spl(0), spl(1))
      // right now, we drop the shortname on the floor! That is just a tag such as "wt1"
      // http://lcms/ only needs the the plate name, e.g., Plate_jaffna3_B1_0815201601.nc
      // we get that by extracting the filename from the full path in `file`
      val srcOrigin = new RawData(source = file)
      new RawPeaks(srcOrigin, PeakSpectra.fromCalledPeaks(file))
    }

    def fromDeepLearnDiff(kv: String) = {
      val spl = kv.split("=")
      val (srcFilesJson, deepCalledPeaks) = (spl(0), spl(1))

      // Parse my outside JSON format
      val lines = scala.io.Source.fromFile(srcFilesJson).getLines().mkString
      val json = scala.util.parsing.json.JSON.parseFull(lines)

      // Ensure everything is correct in the JSON file
      assert(json.isDefined, s"Parsing JSON file as $srcFilesJson failed.  Please check validity of file.")
      val jsonMap = json.get.asInstanceOf[Map[Any, Any]]

      val experimentalKeyValue = "experimental"
      val experimentalValue = jsonMap.get(experimentalKeyValue)
      assert(experimentalValue.isDefined, s"JSON file at $srcFilesJson is expected to have field '$experimentalKeyValue'")

      val controlKeyValue = "control"
      val controlValue = jsonMap.get(controlKeyValue)
      assert(controlValue.isDefined, s"JSON file at $srcFilesJson is expected to have field '$controlKeyValue'")

      val experimental: List[String] = experimentalValue.get.asInstanceOf[List[String]]
      val controls: List[String] = controlValue.get.asInstanceOf[List[String]]

      val srcs: List[String] = experimental ::: controls

      val srcFiles = srcs.map(s => new RawData(source = s))
      val provenance = new ComputedData(sources = srcFiles)
      new RawPeaks(provenance, PeakSpectra.fromDeepLearnt(deepCalledPeaks))
    }

    val differential: RawPeaks = {
      if (cmdLine has optGetDifferentialFromDL) {
        assert(controls == null && hypotheses == null)
        fromDeepLearnDiff(dlDifferentials)
      } else {
        // deep learning only provided us with the peak calls, not the differential :(
        // compute the differentials manually ourselves
        val cnt = controls.map(mkLCMSExpr).toList
        val hyp = hypotheses.map(mkLCMSExpr).toList
        val experiment = new UntargetedMetabolomics(controls = cnt, hypotheses = hyp)
        // output the differential of the peaks
        experiment.analyze()
      }
    }
    
    val diffFiltered = if (filterRtRegions == null) {
      differential
    } else {
      val regions = filterRtRegions.map(s => { 
        val spl = s.split("-")
        val (l, h) = (spl(0), spl(1))
        (new RetentionTime(l.toDouble), new RetentionTime(h.toDouble))
      })
      RemoveGradientBoundaries.filter(differential, regions.toSet)
    }
 
    val rslt: PeakHits = if (!mapToMassAndIons) {
      diffFiltered
    } else {
      // map the peaks to candidate molecule masses
      val candidateMols = restrictedIons match {
        case null => MultiIonHits.convertToMolHits(rawPeaks = diffFiltered, lookForMultipleIons = rankMultipleIons)
        case _ => MultiIonHits.convertToMolHits(rawPeaks = diffFiltered, ionOpts = restrictedIons.toSet, lookForMultipleIons = rankMultipleIons)
      }
      candidateMols
    }

    val inchis: PeakHits = if (!mapToInChIsUsingList) {
      rslt
    } else {
      println(s"Mapping to structures using inchi list")
      // map the peaks to candidate structures if they appear in the lists (from HMDB, ROs, etc)
      new PeakToStructure().StructureHits.toStructureHitsUsingLists(rslt, inchiListFile)
    }

    val formulae: PeakHits = if (!mapToFormulaUsingList && !mapToFormulaUsingList) {
      inchis
    } else if (!mapToFormulaUsingNavigableMap) {
      println(s"Mapping to formula using list")
      new PeakToStructure().FormulaHits.toFormulaHitsUsingLists(inchis, formulaListFile)
    } else if (!mapToFormulaUsingList) {
      val builder = new SmallFormulaeCorpusBuilder()
      builder.populateMapFromFile(new File(formulaNavMapFile))
      val smallFormulaMap: NavigableMap[java.lang.Float, String] = builder.getMassToFormulaMap
      val precision: Double = 0.01
      new PeakToStructure().FormulaHits.toFormulaHitsUsingTreeMap(inchis, smallFormulaMap, precision)
    }

    val formulaeWithSolver: PeakHits = if (!mapToFormulaUsingSolver) {
      formulae
    } else {
      new PeakToStructure().FormulaHits.toFormulaHitsUsingSolver(formulae)
    }

    def codesToJson(kv: (Double, List[String])): Map[String, JsValue] = {
      val (k, v) = kv
      Map("code" -> k.toJson, "vals" -> v.toJson)
    }
    val metaForInChIs = if (!mapToInChIsUsingList) {
      List()
    } else {
      inchis.extraCodes.map(codesToJson)
    }
    val metaForFormulae = if (!mapToFormulaUsingList) {
      List()
    } else {
      formulae.extraCodes.map(codesToJson)
    }

    val extraMetaData = Map("matching_inchi_hashes" -> metaForInChIs) ++
                        Map("matching_formulae_hashes" -> metaForFormulae)
    val extraMetaJson = extraMetaData.mapValues(_.toJson)
    val json = formulaeWithSolver.toJsonFormat(extraMetaJson)

    out.println(json.prettyPrint)
    out.flush
  }

  val optControls = new OptDesc(
                    param = "c",
                    longParam = "controls",
                    name = "{name=file}*",
                    desc = "Controls: Comma separated list of name=file pairs",
                    isReqd = false, hasArgs = true)

  val optHypotheses = new OptDesc(
                    param = "e",
                    longParam = "experiments",
                    name = "{name=file}*",
                    desc = "Experiments: Comma separated list of name=file pairs",
                    isReqd = false, hasArgs = true)

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outjson",
                    name = "filename",
                    desc = "Output json of peaks, mz, rt, masses, formulae etc.",
                    isReqd = false, hasArg = true)

  val optDoIons = new OptDesc(
                    param = "I",
                    longParam = "convert-ion-mzs-to-masses",
                    name = "",
                    desc = """If flag set, each mz is translated to {(ion, mass)}, where mass is the
                             |monoisotopic mass of the molecule whose candidate ion got a hit""".stripMargin,
                    isReqd = false, hasArg = false)

  val optRestrictIons = new OptDesc(
                    param = "Z",
                    longParam = "restrict-ions",
                    name = "M",
                    desc = """Only consider limited set of ions, e.g., M+H,M+Na,M+H-H2O,M+Li,M+K.
                             |If this option is omitted system defaults to M+H,M+Na,M+K""".stripMargin,
                    isReqd = false, hasArgs = true)

  val optMultiIonsRankHigher = new OptDesc(
                    param = "M",
                    longParam = "rank-multiple-ions-higher",
                    name = "",
                    desc = "If flag set, then each if a mass has multiple ions with hits, it ranks higher",
                    isReqd = false, hasArg = false)

  val optToStructUsingList = new OptDesc(
                    param = "S",
                    longParam = "structures-using-list",
                    name = "file",
                    desc = s"""An enumerated list of InChIs (from HMDB, RO (L2n1-inf), L2). TSV file with 
                              |one inchi per line, and optionally tab separated column of monoistopic mass.
                              |TSV hdrs need be `${HdrMolInChI.toString}` and `${HdrMolMass.toString}`""".stripMargin,
                    isReqd = false, hasArg = true)

  val optToFormulaUsingList = new OptDesc(
                    param = "F",
                    longParam = "formulae-using-list",
                    name = "file",
                    desc = s"""An enumerated list of formulae (from formulae enumeration, or EnumPolyPeptides).
                              |TSV file with one formula per line, and optionally tab separated column of
                              |monoisotopic mass for that formula. If missing it is computed online. The TSV
                              |hdrs need be `${HdrMolFormula.toString}` and `${HdrMolMass.toString}`""".stripMargin,
                    isReqd = false, hasArg = true)

  val optToFormulaUsingNavigableMap = new OptDesc(
                    param = "N",
                    longParam = "structures-using-navmap",
                    name = "file",
                    // TODO: fill this
                    desc = s"""TODO""".stripMargin,
                    isReqd = false, hasArg = true)

  val optToFormulaUsingSolver = new OptDesc(
                    param = "C",
                    longParam = "map-to-formula-using-solver",
                    name = "",
                    desc = """Fallback to SMT Constraint solver to solve formula. Ideally, the lower MW
                             |formulae would have been enumerated in lists, and so would be matched using lookup
                             |but for higher MW, e.g., where CHNOPS counts are > 30 we use the solver""".stripMargin,
                    isReqd = false, hasArg = false)

  val optGetDifferentialFromDL = new OptDesc(
                    param = "D",
                    longParam = "get-differential-from-deep-learning",
                    name = "layoutJson=diffData",
                    desc = """Instead of doing the ratio_lines(min_replicates(max_sample)) manually,
                             |we can take the input differential from deep learning. The input here takes the form
                             |of jsonfile=differentialData, where jsonfile contains the original 01.nc filenames
                             |and differentialData is the called differential peaks from deep learning.""".stripMargin,
                    isReqd = false, hasArg = true)

  val optFilterRtRegions = new OptDesc(
                    param = "R",
                    longParam = "filter-rt-regions",
                    name = "[low-high,]+",
                    desc = "A set of retention time regions that should be ignored in the output",
                    isReqd = false, hasArgs = true)

  val optRunTests = new OptDesc(
                    param = "t",
                    longParam = "run-tests-from",
                    name = "dir path",
                    desc = """Run regression tests. It needs the path of the shared dir on the NAS,
                             |e.g., /mnt/shared-data/ because it pulls some sample data from there
                             |to test over.""".stripMargin,
                    isReqd = false, hasArg = true)

  // TODO: move to scalatest
  def runPerlsteinLabTests(sharedDirLoc: File, outStream: PrintWriter) {
    // this data was collected with XCMS Centwave "optimzed" parameters: peak width 1-50 and ppm 20 (@vijay-20n?)
    def dataForWell(dataset: String)(repl: Int) = s"Plate_plate2016_09_08_${dataset}${repl}_0908201601.tsv"
    val pLabXCMSLoc = s"${sharedDirLoc.getPath}/Vijay/perlstein_xcms_centwave_optimized_output/"
    def fullLoc(well: String) = pLabXCMSLoc + well
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
      
      ("wt2-wt2", List(wt1), List(wt1), 0, 0),
      ("dm2-dm2", List(dm1), List(dm1), 0, 0),
      ("df2-df2", List(df1), List(df1), 0, 0),
      
      ("wt3-wt3", List(wt1), List(wt1), 0, 0),
      ("dm3-dm3", List(dm1), List(dm1), 0, 0),
      ("df3-df3", List(df1), List(df1), 0, 0),
      
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

    val verbose = true
    val outputRawPeakHits = true
    cases.foreach{ case (testID, controlsF, hypothesesF, peakMinCnt, peakMaxCnt) => {

      println(s"Testing $testID")
      controlsF.foreach{   c => println(s"Cntrl: $c") }
      hypothesesF.foreach{ c => println(s"Hypth: $c") }

      val controls = controlsF.map(readSpectra)
      val hypotheses = hypothesesF.map(readSpectra)
      controls.foreach(_.peakSpectra.peaks.foreach(p => println(p)))
      val experiment = new UntargetedMetabolomics(controls = controls, hypotheses = hypotheses)
      val analysisRslt = experiment.analyze()
      val candidateMols = MultiIonHits.convertToMolHits(rawPeaks = analysisRslt, lookForMultipleIons = true)
      if (verbose) {
        val peaks = analysisRslt.toJsonFormat() // differential peaks
        val molecules = candidateMols.toJsonFormat() // candidate molecules
        outStream.println(if (outputRawPeakHits) peaks.prettyPrint else molecules.prettyPrint)
        outStream.flush
      }
      val numPeaks = analysisRslt.numPeaks
      val numDifferential = numPeaks("over-expressed") + numPeaks("under-expressed")
      if (!(numDifferential >= peakMinCnt && numDifferential <= peakMaxCnt)) {
        outStream.println(s"Failed test ${testID}, unexpected peak count: $numPeaks != [$peakMinCnt, $peakMaxCnt]")
        outStream.flush
        assert(false)
      }

    }}
  }
}

object RemoveGradientBoundaries {
  def filter(pks: PeakHits, rmRanges: Set[(RetentionTime, RetentionTime)]) = {
    def isInRng(p: Peak) = !rmRanges.exists{ case (l, h) => RetentionTime.isLt(l, p.rt) && RetentionTime.isLt(p.rt, h) }
    val outsideOfGradientBoundaries: Set[Peak] = pks.peakSpectra.peaks.filter(isInRng)
    val provenance = new ComputedData(sources = List(pks.origin))
    new RemoveGradientBoundaries(new RawPeaks(provenance, new PeakSpectra(outsideOfGradientBoundaries)))
  }
}

class RemoveGradientBoundaries(val peaks: PeakHits) extends PeakHits(peaks.origin, peaks.peakSpectra)

object MultiIonHits {

  def convertToMolHits(
    rawPeaks: PeakHits,
    ionOpts: Set[String] = Set("M+H", "M+Na", "M+K"),
    lookForMultipleIons: Boolean = false): MultiIonHits = {

    // helper function to take each mz to Map(mass_i -> (ion_i, mz))
    def toMolCandidates(mzPeak: Peak): Map[Peak, (MetlinIonMass, MonoIsotopicMass)] = {
      val validIons = MS1.ionDeltas.toList.filter(i => ionOpts.contains(i.getName))
      validIons.map(ion => {
        val massVal = MS1.computeMassFromIonMz(mzPeak.mz.initMass, ion)
        val mass = new MonoIsotopicMass(massVal)
        val pk = new Peak(mass, mzPeak.rt, mzPeak.integratedInt, mzPeak.maxInt, mzPeak.snr)
        val ionMz = (ion, mzPeak.mz)
        pk -> ionMz
      }).toMap
    }

    // helper function to take Map(mass_i -> (ion_i, mz)) to mass_i values that have more than one ion
    def keepOnlyMultiple(ionPeaks: Map[Peak, (MetlinIonMass, MonoIsotopicMass)]): Set[Peak] = {
      // isolate to peaks that are the same (mz, rt) and multiple of those

      // TODO: check if groupBy works if instead of toString we just use p.rt
      // We are not sure if groupBy uses hashCode (which is deliberately not
      // defined for RetentionTime) or it uses equals (which is defined)

      // this has type List[(MonoIsotopicMass, RetentionTime), Peak]
      // where we are expecting multiple peaks for the same MonoIsotopicMass
      // because we backcalculated these masses from candidate ions for a peak
      val mzRtToPeaks = ionPeaks.toList.groupBy{ case (molPk, ionMz) => (molPk.mz.toString, molPk.rt.toString) }
      val multiplePeak = mzRtToPeaks.filter{ case (_, pks) => pks.size > 1 }
      multiplePeak.map{ case (_, pkMap) => pkMap(0)._1 }.toSet[Peak]
    }

    // compute candidate molecule peaks, and corresponding map of mol_peak -> (ion, mz)
    val molPeaks: Map[Peak, (MetlinIonMass, MonoIsotopicMass)] = {
      rawPeaks.peakSpectra
        .peaks                // the Set(mz: Peak) of original mz peak hits
        .map(toMolCandidates) // convert to Set(Map(mol_mass -> (ion, mz_peak)))
        .reduce(_ ++ _)       // reduce all sets of Maps to single Map(mol_mass -> (ion, mz_peak))
    }

    val multiple: Set[Peak] = {
      if (lookForMultipleIons)
        keepOnlyMultiple(molPeaks)
      else
        molPeaks.keys.toSet
    }
    val molToMzIon = molPeaks.filterKeys(k => multiple.contains(k))

    val provenance = new ComputedData(sources = List(rawPeaks.origin))
    val moleculePeaks = new RawPeaks(provenance, new PeakSpectra(multiple))

    // return the wrapped data structure with mol peaks and map back to the (ion, mz) it came from 
    new MultiIonHits(moleculePeaks, molToMzIon)
  }
}

class MultiIonHits(val peaks: PeakHits, val toOriginalMzIon: Map[Peak, (MetlinIonMass, MonoIsotopicMass)]) extends
  PeakHits(peaks.origin, peaks.peakSpectra) {

  override def peakSummarizer(p: Peak) = {
    // we augment information from the original summarized peaks
    // call the chained `PeakHits` subclass's peakSummarizer to get prior information
    val (metlinIon, mzMonoIsotopicMass) = toOriginalMzIon(p)
    val peakForIon = new Peak(mzMonoIsotopicMass, p.rt, p.integratedInt, p.maxInt, p.snr)
    val basic: Map[String, Double] = peaks.peakSummarizer(peakForIon)
    // I would love to also write the specific ion to the output M+H or M+Na etc, but
    // the map needs to be a String -> Double, and so we could stuff the hashcode, but the string
    // corresponding to the ion name. TODO: Fix this.
    // ("ion" -> metlinIon.getName)
    basic + ("moleculeMass" -> p.mz.initMass) 
  }
}
