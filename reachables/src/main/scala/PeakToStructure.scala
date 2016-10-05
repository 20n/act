package com.act.lcms

import act.shared.ChemicalSymbols.Helpers.computeMassFromAtomicFormula
import act.shared.ChemicalSymbols.{Atom, MonoIsotopicMass}
import act.shared.MassToFormula
import com.act.lcms.MassCalculator.calculateMass


object PeakToStructure {
  trait LookupInEnumeratedList extends CanReadTSV {
    type T // the output type, InChI: String or Formula: ChemicalFormula
    type H = TSVHdr // headers are inchi, formula, mass
    type V = String // row values read from TSV have String as cell elements (either InChI, formula, or Mass)

    def findHits(hits: PeakHits, enumerated: Map[MonoIsotopicMass, List[T]]): Map[Peak, List[T]] = {
      val pks: Set[Peak] = hits.peakSpectra.peaks
      val haveHits = pks.filter(p => enumerated.contains(p.mz))
      // now that we have filtered to those that are guaranteed to have a hit, we can just
      // look them up in a map and not have it fail (instead of doing a get which return Option[T])
      haveHits.map(p => p -> enumerated(p.mz)).toMap
    }

    def assumeUniqT(tsv: List[Map[TSVHdr, String]], hdrForT: TSVHdr): Map[String, Option[String]] = {
      val massHdr: TSVHdr = HdrMolMass
      val moleculeHdr: TSVHdr = hdrForT
      def tsvRowToKV(row: Map[TSVHdr, String]): (String, Option[String]) = {
        val k = row(moleculeHdr)
        val v = if (row contains massHdr) Some(row(massHdr)) else None
        k -> v
      }
      tsv.map(tsvRowToKV).toMap
    }

    def grpByMass(toMass: Map[T, MonoIsotopicMass]): Map[MonoIsotopicMass, List[T]] = {
      val grouped: Map[MonoIsotopicMass, List[(T, MonoIsotopicMass)]] = toMass.toList.groupBy{ case (t, m) => m }
      val mass2Ts: Map[MonoIsotopicMass, List[T]] = grouped.map{ case (m, listTM) => (m, listTM.unzip._1) }
      mass2Ts
    }

    def readEnumeratedList(file: String, hdrT: TSVHdr, semanticizer: String => T, masser: T => MonoIsotopicMass):
    Map[T, MonoIsotopicMass] = {
      // get all the rows as maps from string (formula or inchi) to mass (optional, if specified)
      // if mass is not specified, each of the values in the map are `None` and the data in the
      // map is just the list of keys
      val hdrs = List(HdrMolMass, hdrT)
      val tsv: List[Map[TSVHdr, String]] = readTSV(file, hdrs, identity)
      val rows: Map[String, Option[String]] = assumeUniqT(tsv, hdrT)

      val semanticized: Map[T, Option[String]] = rows.map{ case (k, v) => semanticizer(k) -> v }

      // if mass column does not exist then call MassCalculator
      def fillMass(kv: (T, Option[String])): (T, MonoIsotopicMass) = {
        val (k, v) = kv
        val mass = v match {
          case None => masser(k)
          case Some(massStr) => new MonoIsotopicMass(massStr.toDouble)
        }
        k -> mass
      }
      val withMasses: Map[T, MonoIsotopicMass] = semanticized map fillMass

      withMasses
    }
  }

  trait SolveUsingSMTSolver extends ChemicalFormulae {
    def findHitsUsingSolver(hits: PeakHits): Map[Peak, List[ChemicalFormula]] = {
      // TODO: parameterize the solver to only consider formulae that do not overlap with the
      // set already exhaustively covered through enumeration, e.g., if C50 H100 N20 O20 P20 S20,
      // i.e., 800million (tractable) has already been enumerated, then we only need to consider
      // cases where it is C>50 or H>100 or N>20 or O>20 or P>20 or S>20
      val m2f = new MassToFormula

      val pks: List[Peak] = hits.peakSpectra.peaks.toList
      val formulae: List[List[ChemicalFormula]] = pks.map(p => m2f.solve(p.mz))
      val formulaeHits: Map[Peak, List[ChemicalFormula]] = pks.zip(formulae).toMap
      formulaeHits
    }
  }

  object FormulaHits extends LookupInEnumeratedList with SolveUsingSMTSolver with ChemicalFormulae {
    type T = ChemicalFormula

    def toFormulaHitsUsingLists(peaks: PeakHits, source: String): FormulaHits = {
      // to deconstruct the chemical element composition from the formula string such as `C9H10NO2`
      def toFormula(s: String): ChemicalFormula = MassToFormula.getFormulaMap(s)
      def toMass(f: ChemicalFormula): MonoIsotopicMass = computeMassFromAtomicFormula(f)
      val sourceList = readEnumeratedList(source, HdrMolFormula, toFormula _, toMass _)
      toFormulaHitsUsingLists(peaks, grpByMass(sourceList))
    }

    def toFormulaHitsUsingLists(peaks: PeakHits, sourceList: Map[MonoIsotopicMass, List[ChemicalFormula]]) = {
      new FormulaHits(peaks, findHits(peaks, sourceList))
    }

    def toFormulaHitsUsingSolver(peaks: PeakHits) = {
      new FormulaHits(peaks, findHitsUsingSolver(peaks))
    }
  }

  class FormulaHits(val peaks: PeakHits, val toFormulae: Map[Peak, List[Map[Atom, Int]]]) extends
    PeakHits(peaks.origin, peaks.peakSpectra) with ChemicalFormulae {

    // we need a copy of MassToFormula with default `elements` coz we want to call its
    // hill system readable formula maker: buildChemFormulaA
    val m2f = new MassToFormula

    def toReadable(f: ChemicalFormula): String = m2f.buildChemFormulaA(f)

    def code(f: Option[List[ChemicalFormula]]): (Double, List[String]) = {
      val forms = f.getOrElse(List())
      val hcode = f match {
        case None => -1
        case Some(_) => forms.hashCode.toDouble
      }
      (hcode, forms.map(toReadable))
    }

    override def extraCodes(): Map[Double, List[String]] = {
      val formulae: List[List[ChemicalFormula]] = toFormulae.values.toList
      // add an option in front of each element of the list above, so that we can call `code`
      val formulaeOpt: List[Option[List[ChemicalFormula]]] = formulae.map(l => Some(l))
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
      val sourceList = readEnumeratedList(source, HdrMolInChI, toInChI _, toMass _)
      toStructureHitsUsingLists(peaks, grpByMass(sourceList))
    }

    def toStructureHitsUsingLists(peaks: PeakHits, sourceList: Map[MonoIsotopicMass, List[String]]): StructureHits = {
      new StructureHits(peaks, findHits(peaks, sourceList))
    }
  }

  class StructureHits(val peaks: PeakHits, val toInChI: Map[Peak, List[String]]) extends
    PeakHits(peaks.origin, peaks.peakSpectra) {

    def code(i: Option[List[String]]): (Double, List[String]) = {

      val inchis = i.getOrElse(List())
      val hcode = i match {
        case None => -1
        case Some(_) => inchis.hashCode.toDouble
      }
      (hcode, inchis)
    }

    override def extraCodes(): Map[Double, List[String]] = {
      val inchis: List[List[String]] = toInChI.values.toList
      // add an option in front of each element of the list above, so that we can call `code`
      val inchiOpts: List[Option[List[String]]] = inchis.map(l => Some(l))
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
