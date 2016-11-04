package com.act.lcms

import act.shared.ChemicalSymbols.Atom
import act.shared.MassToFormula
import com.act.lcms.v2.{MassToRawMetaboliteMap, RawMetabolite}

import scala.collection.JavaConversions._


class MoleculeFinder {

  sealed case class NamedFormula(formula: Map[Atom, Int], name: Option[String])
  sealed case class NamedInchi(inchi: String, name: Option[String])

  trait SolveUsingSMTSolver {

    def findHitsUsingSolver(hits: PeakHits): Map[Peak, List[NamedFormula]] = {
      // TODO: parameterize the solver to only consider formulae that do not overlap with the
      // set already exhaustively covered through enumeration, e.g., if C50 H100 N20 O20 P20 S20,
      // i.e., 800million (tractable) has already been enumerated, then we only need to consider
      // cases where it is C>50 or H>100 or N>20 or O>20 or P>20 or S>20
      val m2f = new MassToFormula

      val pks: List[Peak] = hits.peakSpectra.peaks.toList

      def solveAndAddName(peak: Peak): List[NamedFormula] = {
        m2f.solve(peak.mz).map(NamedFormula(_, None))
      }

      val formulaeHits: Map[Peak, List[NamedFormula]] = pks.map(peak => (peak, solveAndAddName(peak))).toMap
      formulaeHits
    }
  }

  object FormulaHits extends SolveUsingSMTSolver {

    def toFormulaHitsUsingLargeMap(peaks: PeakHits,
                                   smallFormulaMap: MassToRawMetaboliteMap,
                                   precision: Double): FormulaHits = {

      val peakSet: Set[Peak] = peaks.peakSpectra.peaks

      def toFormula(n: RawMetabolite): NamedFormula = {
        NamedFormula(MassToFormula.getFormulaMap(n.getMolecule), Option(n.getName.orElse(null)))
      }

      def bestFormulaeMatches(peak: Peak): List[NamedFormula] = {
        val results: List[RawMetabolite] = smallFormulaMap.getSortedFromCenter(peak.mz.initMass, precision).toList
        results.map(toFormula)
      }

      val peakToFormula = (peakSet map { peak => peak -> bestFormulaeMatches(peak) }).toMap
      new FormulaHits(peaks, peakToFormula)
    }

    def toFormulaHitsUsingSolver(peaks: PeakHits) = {
      new FormulaHits(peaks, findHitsUsingSolver(peaks))
    }
  }

  class FormulaHits(val peaks: PeakHits, val toFormulae: Map[Peak, List[NamedFormula]]) extends
    PeakHits(peaks.origin, peaks.peakSpectra) {

    // we need a copy of MassToFormula with default `elements` coz we want to call its
    // hill system readable formula maker: buildChemFormulaA
    val m2f = new MassToFormula

    def toReadable(f: NamedFormula): (String, Option[String]) = {
      (m2f.buildChemFormulaA(f.formula), f.name)
    }

    def code(f: Option[List[NamedFormula]]): (Double, List[(String, Option[String])]) = {
      val forms = f.getOrElse(List())
      val hcode = forms match {
        case List() => -1
        case _ => forms.hashCode.toDouble
      }
      (hcode, forms.map(toReadable))
    }

    override def extraCodes(): Map[Double, List[(String, Option[String])]] = {
      val formulae: List[List[NamedFormula]] = toFormulae.values.toList
      // add an option in front of each element of the list above, so that we can call `code`
      val formulaeOpt: List[Option[List[NamedFormula]]] = formulae.map(l => Option(l))
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

  object StructureHits {

    def toStructureHitsUsingLargeMap(peaks: PeakHits,
                                   smallInchisMap: MassToRawMetaboliteMap,
                                   precision: Double): StructureHits = {

      val peakSet: Set[Peak] = peaks.peakSpectra.peaks

      def toInchi(n: RawMetabolite): NamedInchi = {
        NamedInchi(n.getMolecule, Option(n.getName.orElse(null)))
      }

      def bestInchisMatches(peak: Peak): List[NamedInchi] = {
        val results = smallInchisMap.getSortedFromCenter(peak.mz.initMass, precision)
        val inchis = results.toList.map(toInchi)
        inchis
      }

      val peakToInchis = (peakSet map { peak => peak -> bestInchisMatches(peak) }).toMap

      new StructureHits(peaks, peakToInchis)
    }
  }

  class StructureHits(val peaks: PeakHits, val toInChI: Map[Peak, List[NamedInchi]]) extends
    PeakHits(peaks.origin, peaks.peakSpectra) {

    def code(i: Option[List[NamedInchi]]): (Double, List[(String, Option[String])]) = {

      val inchis = i.getOrElse(List()).map(namedInchi => (namedInchi.inchi, namedInchi.name))
      val hcode = inchis match {
        case List() => -1
        case _ => inchis.hashCode.toDouble
      }
      (hcode, inchis)
    }

    override def extraCodes(): Map[Double, List[(String, Option[String])]] = {
      val inchis: List[List[NamedInchi]] = toInChI.values.toList
      // add an option in front of each element of the list above, so that we can call `code`
      val inchiOpts: List[Option[List[NamedInchi]]] = inchis.map(l => Option(l))
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
