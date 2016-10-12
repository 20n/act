package com.act.lcms

import java.util.NavigableMap

import act.shared.ChemicalSymbols.Helpers.computeMassFromAtomicFormula
import com.act.lcms.MassCalculator.calculateMass
import act.shared.ChemicalSymbols.{Atom, MonoIsotopicMass}
import act.shared.MassToFormula

import scala.collection.JavaConversions._

class PeakToMolecule {

  trait ChemicalFormulae {
    type ChemicalFormula = Map[Atom, Int]
  }



  // @mark-20n says: All of this peak to structure matching scaffolding deserves its own
  //                 module. The API for this is something we should stabilize ASAP so
  //                 that we can iterate on it separately from the cross-replicate/negative
  //                 analysis and get it talking with the upcoming L2/L4-derived network analysis.
  trait LookupInEnumeratedList extends CanReadTSV {
    type T // the output type, InChI: String or Formula: ChemicalFormula
    type H = TSVHdr // headers are inchi, formula, mass
    type V = String // row values read from TSV have String as cell elements (either InChI, formula, or Mass)


    def findHits(hits: PeakHits, enumerated: Map[MonoIsotopicMass, List[(T, Option[String])]]): Map[Peak, List[(T, Option[String])]] = {
      val pks: Set[Peak] = hits.peakSpectra.peaks
      val haveHits = pks.filter(p => enumerated.contains(p.mz))
      // now that we have filtered to those that are guaranteed to have a hit, we can just
      // look them up in a map and not have it fail (instead of doing a get which return Option[T])

      // haveHits contains a set of peaks
      // each peak in this set has to be compared to other Monoisotopic mass later
      // given one peak in haveHits, how to compare it to the others in the map

      def getSortedHits(peak: Peak) = {
        def sortByMz(a: (MonoIsotopicMass, List[(T, Option[String])]),
                     b: (MonoIsotopicMass, List[(T, Option[String])])) = {
          a._1.percentCompare(peak.mz) < b._1.percentCompare(peak.mz)
        }

        enumerated.filterKeys(mass => mass.equals(peak.mz)).toList.sortWith(sortByMz).map(_._2).flatten
      }

      haveHits.map(p => p -> getSortedHits(p)).toMap
    }

    def assumeUniqT(tsv: List[Map[TSVHdr, String]], hdrForT: TSVHdr): Map[(String, Option[String]), Option[String]] = {
      val massHdr: TSVHdr = HdrMolMass
      val moleculeHdr: TSVHdr = hdrForT
      val nameHdr: TSVHdr = HdrName
      def tsvRowToKV(row: Map[TSVHdr, String]): ((String, Option[String]), Option[String]) = {
        val k = row(moleculeHdr)
        val n = if (row contains nameHdr) Some(row(nameHdr)) else None
        val v = if (row contains massHdr) Some(row(massHdr)) else None
        (k, n) -> v
      }
      tsv.map(tsvRowToKV).toMap
    }

    def grpByMass(toMass: Map[(T, Option[String]), MonoIsotopicMass]): Map[MonoIsotopicMass, List[(T, Option[String])]] = {
      val grouped: Map[MonoIsotopicMass, List[((T, Option[String]), MonoIsotopicMass)]] = toMass.toList.groupBy{ case ((t, n), m) => m }
      val mass2Ts: Map[MonoIsotopicMass, List[(T, Option[String])]] = grouped.map{ case (m, listTM) => (m, listTM.unzip._1) }
      mass2Ts
    }

    def readEnumeratedList(file: String, hdrT: TSVHdr, containsName: Boolean, semanticizer: String => T, masser: T => MonoIsotopicMass):
    Map[(T, Option[String]), MonoIsotopicMass] = {
      // get all the rows as maps from string (formula or inchi) to mass (optional, if specified)
      // if mass is not specified, each of the values in the map are `None` and the data in the
      // map is just the list of keys
      val hdrName = HdrName
      val hdrs = if (!containsName) {
        List(HdrMolMass, hdrT)
      } else {
        List(HdrMolMass, hdrName, hdrT)
      }
      val tsv: List[Map[TSVHdr, String]] = readTSV(file, hdrs, identity)
      val rows: Map[(String, Option[String]), Option[String]] = assumeUniqT(tsv, hdrT)
      val semanticized: Map[(T, Option[String]), Option[String]] = rows.map{
        case ((k, n), v) => (semanticizer(k), n) -> v }

      // if mass column does not exist then call MassCalculator
      def fillMass(kv: ((T, Option[String]), Option[String])): ((T, Option[String]), MonoIsotopicMass) = {
        val ((k, n), v) = kv
        val mass = v match {
          case None => masser(k)
          case Some(massStr) => new MonoIsotopicMass(massStr.toDouble)
        }
        (k, n) -> mass
      }
      val withMasses: Map[(T, Option[String]), MonoIsotopicMass] = semanticized map fillMass

      withMasses
    }
  }

  trait SolveUsingSMTSolver extends ChemicalFormulae {
    def findHitsUsingSolver(hits: PeakHits): Map[Peak, List[(ChemicalFormula, Option[String])]] = {
      // TODO: parameterize the solver to only consider formulae that do not overlap with the
      // set already exhaustively covered through enumeration, e.g., if C50 H100 N20 O20 P20 S20,
      // i.e., 800million (tractable) has already been enumerated, then we only need to consider
      // cases where it is C>50 or H>100 or N>20 or O>20 or P>20 or S>20
      val m2f = new MassToFormula

      val pks: List[Peak] = hits.peakSpectra.peaks.toList

      def solveAndAddName(peak: Peak): List[(ChemicalFormula, Option[String])] = {
        m2f.solve(peak.mz).map((_, None))
      }

      val formulae: List[List[(ChemicalFormula, Option[String])]] = pks map solveAndAddName
      val formulaeHits: Map[Peak, List[(ChemicalFormula, Option[String])]] = pks.zip(formulae).toMap
      formulaeHits
    }
  }

  object FormulaHits extends LookupInEnumeratedList with SolveUsingSMTSolver with ChemicalFormulae {
    type T = ChemicalFormula

    def toFormulaHitsUsingLists(peaks: PeakHits, source: String): FormulaHits = {
      // to deconstruct the chemical element composition from the formula string such as `C9H10NO2`
      def toFormula(s: String): ChemicalFormula = MassToFormula.getFormulaMap(s)
      def toMass(f: ChemicalFormula): MonoIsotopicMass = computeMassFromAtomicFormula(f)
      val sourceList: Map[(T, Option[String]), MonoIsotopicMass] = readEnumeratedList(source, HdrMolFormula, true, toFormula _, toMass _)
      toFormulaHitsUsingLists(peaks, grpByMass(sourceList))
    }

    def toFormulaHitsUsingLists(peaks: PeakHits, sourceList: Map[MonoIsotopicMass, List[(T, Option[String])]]) = {
      new FormulaHits(peaks, findHits(peaks, sourceList))
    }

    def toFormulaHitsUsingTreeMap(peaks: PeakHits,
                                  smallFormulaMap: NavigableMap[java.lang.Float, String],
                                  precision: Float): FormulaHits = {

      val peakSet: Set[Peak] = peaks.peakSpectra.peaks

      def toFormula(s: String): ChemicalFormula = MassToFormula.getFormulaMap(s)

      def bestFormulaeMatches(peak: Peak): List[(ChemicalFormula, Option[String])] = {
        val lowerBoundMass: java.lang.Float = peak.mz.rounded(6).toFloat - precision / 2
        val upperBoundMass: java.lang.Float = peak.mz.rounded(6).toFloat + precision / 2
        val results = smallFormulaMap.subMap(lowerBoundMass, true, upperBoundMass, true)

        def toNamed(s: String) = {
          (toFormula(s), None)
        }
        results.values.map(toNamed).toList
      }

      val peakToFormula = (peakSet map { peak => peak -> bestFormulaeMatches(peak) }).toMap
      val peakToFormulaWithHits = peakToFormula.filter((x) => x._2 != List())
      new FormulaHits(peaks, peakToFormulaWithHits)
    }

    def toFormulaHitsUsingSolver(peaks: PeakHits) = {
      new FormulaHits(peaks, findHitsUsingSolver(peaks))
    }
  }

  class FormulaHits(val peaks: PeakHits, val toFormulae: Map[Peak, List[(Map[Atom, Int], Option[String])]]) extends
    PeakHits(peaks.origin, peaks.peakSpectra) with ChemicalFormulae {

    // we need a copy of MassToFormula with default `elements` coz we want to call its
    // hill system readable formula maker: buildChemFormulaA
    val m2f = new MassToFormula

    def toReadable(f: (ChemicalFormula, Option[String])): (String, Option[String]) = {
      (m2f.buildChemFormulaA(f._1), f._2)
    }

    def code(f: Option[List[(ChemicalFormula, Option[String])]]): (Double, List[(String, Option[String])]) = {
      val forms = f.getOrElse(List())
      val hcode = f match {
        case None => -1
        case Some(_) => forms.hashCode.toDouble
      }
      (hcode, forms.map(toReadable))
    }

    override def extraCodes(): Map[Double, List[(String, Option[String])]] = {
      val formulae: List[List[(ChemicalFormula, Option[String])]] = toFormulae.values.toList
      // add an option in front of each element of the list above, so that we can call `code`
      val formulaeOpt: List[Option[List[(ChemicalFormula, Option[String])]]] = formulae.map(l => Some(l))
      formulaeOpt.map(code).toMap
    }

    override def peakSummarizer(p: Peak) = {
      // we augment information from the original summarized peaks
      // call the chained `PeakHits` subclass's peakSummarizer to get prior information
      val basic: Map[String, Double] = peaks.peakSummarizer(p)
      // for each peak, the data has to be string->double, so we can only put a pointer to the actual
      // formula in the peak output. We later to have to dump a mapping of hashCode -> list(formulae)
      // else where
      val found = code(toFormulae.get(p))
      val hcode = found._1
      basic + ("matching_formulae" -> hcode)
    }
  }

  object StructureHits extends LookupInEnumeratedList {
    type T = String

    def toStructureHitsUsingLists(peaks: PeakHits, source: String): StructureHits = {
      def toInChI(s: String): String = { assert(s startsWith "InChI="); s }
      def toMass(inchi: String): MonoIsotopicMass = {
        val mass = try { calculateMass(inchi).doubleValue } catch { case _: Exception => 0.0 }
        new MonoIsotopicMass(mass)
      }
      val sourceList = readEnumeratedList(source, HdrMolInChI, true, toInChI _, toMass _)
      toStructureHitsUsingLists(peaks, grpByMass(sourceList))
    }

    def toStructureHitsUsingLists(peaks: PeakHits, sourceList: Map[MonoIsotopicMass, List[(String, Option[String])]]): StructureHits = {
      new StructureHits(peaks, findHits(peaks, sourceList))
    }
  }

  class StructureHits(val peaks: PeakHits, val toInChI: Map[Peak, List[(String, Option[String])]]) extends
    PeakHits(peaks.origin, peaks.peakSpectra) {

    def code(i: Option[List[(String, Option[String])]]): (Double, List[(String, Option[String])]) = {

      val inchis = i.getOrElse(List())
      val hcode = i match {
        case None => -1
        case Some(_) => inchis.hashCode.toDouble
      }
      (hcode, inchis)
    }

    override def extraCodes(): Map[Double, List[(String, Option[String])]] = {
      val inchis: List[List[(String, Option[String])]] = toInChI.values.toList
      // add an option in front of each element of the list above, so that we can call `code`
      val inchiOpts: List[Option[List[(String, Option[String])]]] = inchis.map(l => Some(l))
      inchiOpts.map(code).toMap
    }

    override def peakSummarizer(p: Peak) = {
      // we augment information from the original summarized peaks
      // call the chained `PeakHits` subclass's peakSummarizer to get prior information
      val basic: Map[String, Double] = peaks.peakSummarizer(p)
      // for each peak, the data has to be string->double, so we can only put a pointer to the actual
      // formula in the peak output. We later to have to dump a mapping of hashCode -> list(formulae)
      // else where
      val found = code(toInChI.get(p))
      val hcode = found._1
      basic + ("matching_inchis" -> hcode)
    }
  }

}
