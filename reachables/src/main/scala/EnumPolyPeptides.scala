package com.act.lcms

import java.io.PrintWriter
import com.act.lcms.MS1.{MetlinIonMass}
import act.shared.{CmdLineParser, OptDesc}

sealed trait Atom { def symbol: Char; def monoIsotopicMass: Double }
case object C extends Atom { val symbol = 'C'; val monoIsotopicMass = 12.00000 }
case object H extends Atom { val symbol = 'H'; val monoIsotopicMass = 1.007825 }
case object O extends Atom { val symbol = 'O'; val monoIsotopicMass = 15.994915 }
case object N extends Atom { val symbol = 'N'; val monoIsotopicMass = 14.003074 }
case object S extends Atom { val symbol = 'S'; val monoIsotopicMass = 31.972071 }

abstract class AminoAcid {
  def name: String
  def symbol: Char
  def formula: String
  def monoIsotopicMass: Double
  def elems: Map[Atom, Int]
}

case object Gly extends AminoAcid {
  val name = "Glycine"; val symbol = 'G'; val formula = "C2H5NO2"; val monoIsotopicMass = 75.032028;
  val elems: Map[Atom, Int] = Map(C->2, H->5, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.730.html"
}
case object Ala extends AminoAcid {
  val name = "Alanine"; val symbol = 'A'; val formula = "C3H7NO2"; val monoIsotopicMass = 89.047676;
  val elems: Map[Atom, Int] = Map(C->3, H->7, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.582.html"
}
case object Pro extends AminoAcid {
  val name = "Proline"; val symbol = 'P'; val formula = "C5H9NO2"; val monoIsotopicMass = 115.063332;
  val elems: Map[Atom, Int] = Map(C->5, H->9, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.594.html"
}
case object Val extends AminoAcid {
  val name = "Valine"; val symbol = 'V'; val formula = "C5H11NO2"; val monoIsotopicMass = 117.078979;
  val elems: Map[Atom, Int] = Map(C->5, H->11, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.1148.html"
}
case object Cys extends AminoAcid {
  val name = "Cysteine"; val symbol = 'C'; val formula = "C3H7NO2S"; val monoIsotopicMass = 121.019745;
  val elems: Map[Atom, Int] = Map(C->3, H->7, O->2, N->1, S->1);
  val url = "http://www.chemspider.com/Chemical-Structure.574.html"
}
case object Ile extends AminoAcid {
  val name = "Isoleucine"; val symbol = 'I'; val formula = "C6H13NO2"; val monoIsotopicMass = 131.094635;
  val elems: Map[Atom, Int] = Map(C->6, H->13, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.769.html"
}
case object Leu extends AminoAcid {
  val name = "Leucine"; val symbol = 'L'; val formula = "C6H13NO2"; val monoIsotopicMass = 131.094635;
  val elems: Map[Atom, Int] = Map(C->6, H->13, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.834.html"
}
case object Met extends AminoAcid {
  val name = "Methionine"; val symbol = 'M'; val formula = "C5H11NO2S"; val monoIsotopicMass = 149.051056;
  val elems: Map[Atom, Int] = Map(C->5, H->11, O->2, N->1, S->1);
  val url = "http://www.chemspider.com/Chemical-Structure.853.html"
}
case object Phe extends AminoAcid {
  val name = "Phenylalanine"; val symbol = 'F'; val formula = "C9H11NO2"; val monoIsotopicMass = 165.078979;
  val elems: Map[Atom, Int] = Map(C->9, H->11, O->2, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.5910.html"
}
case object Ser extends AminoAcid {
  val name = "Serine"; val symbol = 'S'; val formula = "C3H7NO3"; val monoIsotopicMass = 105.042595;
  val elems: Map[Atom, Int] = Map(C->3, H->7, O->3, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.597.html"
}
case object Thr extends AminoAcid {
  val name = "Threonine"; val symbol = 'T'; val formula = "C4H9NO3"; val monoIsotopicMass = 119.058243;
  val elems: Map[Atom, Int] = Map(C->4, H->9, O->3, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.6051.html"
}
case object Tyr extends AminoAcid {
  val name = "Tyrosine"; val symbol = 'Y'; val formula = "C9H11NO3"; val monoIsotopicMass = 181.073898;
  val elems: Map[Atom, Int] = Map(C->9, H->11, O->3, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.5833.html"
}
case object Asp extends AminoAcid {
  val name = "Aspartate"; val symbol = 'D'; val formula = "C4H7NO4"; val monoIsotopicMass = 133.037506;
  val elems: Map[Atom, Int] = Map(C->4, H->7, O->4, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.411.html"
}
case object Glu extends AminoAcid {
  val name = "Glutamate"; val symbol = 'E'; val formula = "C5H9NO4"; val monoIsotopicMass = 147.053162;
  val elems: Map[Atom, Int] = Map(C->5, H->9, O->4, N->1, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.30572.html"
}
case object Lys extends AminoAcid {
  val name = "Lysine"; val symbol = 'K'; val formula = "C6H14N2O2"; val monoIsotopicMass = 146.10553;
  val elems: Map[Atom, Int] = Map(C->6, H->14, O->2, N->2, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.843.html"
}
case object Trp extends AminoAcid {
  val name = "Tryptophan"; val symbol = 'W'; val formula = "C11H12N2O2"; val monoIsotopicMass = 204.089874;
  val elems: Map[Atom, Int] = Map(C->11, H->12, O->2, N->2, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.1116.html"
}
case object Asn extends AminoAcid {
  val name = "Asparagine"; val symbol = 'N'; val formula = "C4H8N2O3"; val monoIsotopicMass = 132.053497;
  val elems: Map[Atom, Int] = Map(C->4, H->8, O->3, N->2, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.231.html"
}
case object Gln extends AminoAcid {
  val name = "Glutamine"; val symbol = 'Q'; val formula = "C5H10N2O3"; val monoIsotopicMass = 146.069138;
  val elems: Map[Atom, Int] = Map(C->5, H->10, O->3, N->2, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.718.html"
}
case object His extends AminoAcid {
  val name = "Histidine"; val symbol = 'H'; val formula = "C6H9N3O2"; val monoIsotopicMass = 155.069473;
  val elems: Map[Atom, Int] = Map(C->6, H->9, O->2, N->3, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.6038.html"
}
case object Arg extends AminoAcid {
  val name = "Arginine"; val symbol = 'R'; val formula = "C6H14N4O2"; val monoIsotopicMass = 174.111679;
  val elems: Map[Atom, Int] = Map(C->6, H->14, O->2, N->4, S->0);
  val url = "http://www.chemspider.com/Chemical-Structure.227.html"
}

object EnumPolyPeptides {
  val allAminoAcids = List(Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser,
                           Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg)

  def fromSymbol(sym: Char): AminoAcid = allAminoAcids.find(_.symbol.equals(sym)) match {
    case Some(aa) => aa
    case None => throw new Exception("Invalid symbol for an amino acid.")
  }

  val ionHeaders: List[(String, MS1.IonMode)] = MS1.ionDeltas.map(ion => (ion.getName, ion.getMode)).toList
  val tsvHdrs = List("Representative", "M") ++ ionHeaders.map{ case (ionName, mode) => ionName + "/" + mode }

  class PeptideMass(val representative: List[AminoAcid],
                    val mass: Double,
                    val ionMasses: List[(MetlinIonMass, Double)]) {
    override def toString() = {
      // convert the representative to a string, DPPSAT
      val reprSymbol = representative.map(_.symbol.toString).reduce(_ + _)

      // note that here we assume that the list stays ordered the same way the header was computed for `tsvHdrs`
      // both are computed from ionDeltas and so there should be no reordering of the lists
      val ionMzs = ionMasses.map{ case (ion, mz) => truncateTo6Decimals(mz).toString }
      val together = List(reprSymbol, truncateTo6Decimals(mass).toString) ++ ionMzs
      together.mkString("\t")
    }
  }

  def formulaToListAAs(formula: Map[AminoAcid, Int]): List[AminoAcid] = {
    val aminoAcidsInaRow = formula.toList.map{ case (a, n) => List.fill(n)(a) }.flatten
    // return sorted, so that it looks like ADPPST as opposed to SAPDTP
    aminoAcidsInaRow.sortWith(_.symbol < _.symbol)
  }

  def computeMonoIsotopicMass(formula: Map[AminoAcid, Int]): Double = {
    val numPeptides = formula.values.reduce(_ + _)

    // This is where the actual algorithm from #419 is being implemented
    val numWatersToRemove = numPeptides - 1
    val massOfWater = List(H, O, H).map(_.monoIsotopicMass).reduce(_ + _)
    val massToRemove = numWatersToRemove * massOfWater
    val combinedMass = formulaToListAAs(formula).map(_.monoIsotopicMass).reduce(_ + _)
    val finalMass = combinedMass - massToRemove

    finalMass
  }

  def toPeptideMassRow(formula: Map[AminoAcid, Int]): PeptideMass = {
    val monoIsotopicMass = computeMonoIsotopicMass(formula)

    new PeptideMass(formulaToListAAs(formula), monoIsotopicMass, computeMetlinIonMasses(monoIsotopicMass))
  }

  def getAminoAcidCombinations(maxLen: Int): Iterator[List[AminoAcid]] = {
    // scala stdlib has a combinations(k) where it returns lists of size `k`
    // to allow this combinations to create with repetitions we just give it
    // the max allowed repetitions

    // first, create a list by replicating the elements maxLen number of times
    // this is a list of lists
    val replicatedLists = List.fill(maxLen)(allAminoAcids)

    // flatten the list created above, so that we have one long list of as many
    // repeated amino acids as can be in the final set. by repeating them here,
    // we can then pick *without* repetition
    val pickSetNonDistinctElems = replicatedLists.flatten

    // then we just ask the standard library to give us a standard combinations
    // i.e., pick `maxLen` elements from this mega-list
    pickSetNonDistinctElems.combinations(maxLen)
  }

  def toFormula(aas: List[AminoAcid]): Map[AminoAcid, Int] = {
    // convert List[AminoAcids] to formula Map[AminoAcids -> count]
    aas.groupBy(identity).mapValues(_.size)
  }

  def getPeptideEnumerator(maxLen: Int): Iterator[PeptideMass] = {
    // we first get an iterator over all combinations with repetition of aminoacid sets
    val aminoacidGroups = getAminoAcidCombinations(maxLen)

    // now convert each List[AminoAcids] to formula Map[AminoAcids -> count] and then to PeptideMass row
    val peptideMasses = aminoacidGroups.map(l => toPeptideMassRow(toFormula(l)))

    peptideMasses
  }

  def computeMetlinIonMasses(m: Double): List[(MetlinIonMass, Double)] = {
    // we could have called `MS1.java:queryMetlin`, but that creates a completely new List(MetlinIonMass)
    // My guess is that by using the `public static final ionDeltas` as the key the scala compiler
    // should be clever enough to not create copies of the key. The only extra memory we will use here
    // should be the computed mass values for the ions (the values of the map).
    def ionMassTuple(ion: MetlinIonMass): (MetlinIonMass, Double) = (ion -> MS1.computeIonMz(m, ion))
    val ionMasses: List[(MetlinIonMass, Double)] = MS1.ionDeltas.map(ionMassTuple).toList
    ionMasses
  }

  def main(args: Array[String]) {

    val className = this.getClass.getCanonicalName
    val opts = List(optOutFile, optMaxLen)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    // read the command line options
    val maxPeptideLength = cmdLine.get(optMaxLen).toInt
    val outTsvFile = new PrintWriter(cmdLine.get(optOutFile))
    def writeFlush(line: String) = {
      outTsvFile.write(line + "\n")
      outTsvFile.flush
    }

    // do the actual work
    writeFlush(tsvHdrs mkString "\t")
    (1 to maxPeptideLength).foreach { peptideLen =>
      val allMasses = getPeptideEnumerator(peptideLen)
      allMasses.foreach(x => writeFlush(x.toString))
    }
    outTsvFile.close

    // run unit test to make sure code is still sane
    // TODO: move this to tests framework.
    runAllUnitTests
  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "output-file",
                    name = "output TSV file",
                    desc = "The file to which the enumerated output of polypeptide masses will be written to",
                    isReqd = true, hasArg = true)

  val optMaxLen = new OptDesc(
                    param = "n",
                    longParam = "max-peptide-length",
                    name = "max length of peptides",
                    desc = List("Maximum length, in num of amino acids, of polypeptides to consider. ",
                                "Note that this grows with C(19+n, n), i.e., close to exponential. ",
                                "Also, note that above lengths 16 the polypeptide will be `>950Da` in size",
                                "and hence beyond the size range current LCMS instrument can detect.").mkString,
                    isReqd = true, hasArg = true)

  // TODO: move this into the tests directory
  def runAllUnitTests() {
    checkNChooseK
    checkEnumerationSizeCorrect
    checkSpecificPeptides
    checkAllAminoAcidMasses
  }

  val sixDecimals = math pow (10, 6)
  def truncateTo6Decimals(n: Double): Double = (math floor n * sixDecimals) / sixDecimals

  // tolerate differences in the last decimal place at which monoIsotopicMasses specified
  // i.e., we consider masses upto 0.00001 away from each other to be identical
  // note that the mass of an electron is 5.5e-4 Da, so this is 1/50th of that. Basically
  // imprecision in arithmetic, not physics.
  val tolerance = 1e-5

  def equalUptoTolerance(a: Double, b: Double) = Math.abs(a - b) < tolerance

  // as discussed in https://github.com/20n/act/issues/419#issuecomment-244655526
  // the size of the enumerated set has to be C(n+r-1, r) where n=20 and r=length of peptides

  def choose(a: Int, b: Int) = {
    // n!
    def fact(n: Int): Long = if (n == 1) 1 else n * fact(n-1)
    // n!/n-k! = n * n-1 * ... * n-k+1
    def factUpto(n: Int, upto: Int): Long = if (n == upto) 1 else n * factUpto(n-1, upto)

    // computing the direct formula `fact(a) / (fact(b) * fact(a-b))` will overflow
    // so we compute the numerator and denominator separately
    // and we also know that C(a,b) = C(a,a-b), so we pick the version that minimizes
    // the numerator and denominator values
    if (b < a-b)
      factUpto(a, a-b) / fact(b)
    else
      factUpto(a, b) / fact(a-b)
  }
  def combinationsWithRepeats(n: Int, r: Int) = choose(n+r-1, r)

  def checkEnumerationSizeCorrect() {

    // we will create an enumeration class, get all its elements (will take time), and then
    // compare the size against the expected combinations formula
    def checkNumPeptidesEnumCorrect(lenPeptidesAndSz: (Int, Long)) {
      val (l, sz) = lenPeptidesAndSz
      val enumSz = getPeptideEnumerator(l).size.toLong
      assert( enumSz == sz )
    }

    // now iterate with peptide lengths 1..6
    (1 to 6).toList.map(len => (len, combinationsWithRepeats(20, len))).foreach(checkNumPeptidesEnumCorrect)
  }

  class Peptide(val len: Int, val composition: Map[AminoAcid, Int], val formula: Map[Atom, Int], val mass: Double)

  def checkSpecificPeptides() {

    // for the peptides here that do not have HMDB/Metlin links, you can validate the mass using the
    // spreadsheet linked in the PR message for #420. An example of that is the DPPSAT peptide below.

    // list of specific peptides to check. tuples of their length, and accurate monoisotopic mass
    val dppsat = {
      val aa = Map('D'->1, 'P'->2, 'S'->1, 'A'->1, 'T'->1).map{ case (s, n) => fromSymbol(s) -> n }
      new Peptide(
        len = 6,
        composition = aa,
        formula = Map(C->33, H->47, O->8, N->7, S->0),
        mass = 586.259859
      )
    }

    // This is one of the highest signals we see across all samples in the urine analysis #370, #371
    // 228.147393  http://www.hmdb.ca/metabolites/HMDB11174  L-isoleucyl-L-proline
    // Most authoritative ground truth we have observed, so need to ensure that our alg here work for it
    val diPeptideVeryHighSignalInUrine = {
      val aa = Map('I'->1, 'P'->1).map{ case (s, n) => fromSymbol(s) -> n }
      new Peptide(
        len = 2,
        composition = aa,
        formula = Map(C->11, H->20, O->3, N->2, S->0),
        mass = 228.147393
      )
    }

    // Below are four peptides between masses [463.1840, 463.1890]. We encountered signals around this
    // mass when we were looking for a L4n1 mass at 463.184234 (which is a wierd non-human chemical)
    // So we created plots for potential polypeptides in the range by looking the following 3 peptides
    // from metlin hits:
    // https://metlin.scripps.edu/metabo_list_adv.php?molid=&mass_min=463.1840&mass_max=463.1890
    //
    // See email thread on 08/30/16, subject "min of replicates across all samples using new algorithm"
    // Mark created plots for these masses under /shared-data/Mark/jaffna_lcms/issue_371/set3
    // where we can clearly see (in fine grained analysis) that our search for the mz 463.184234
    // was pulling up the fourPeptideInUrineB mass.
    //
    // Incidentally, it also pointed to the fact that we should be doing fine_grained instead of
    // coarse_grained, because we don't loose any signals, and in coarse grained all of
    // triPeptideInUrine and fourPeptideInUrine{A, B}) are hit as candidates for the peak that is
    // most likely centered on the `463.184917007` mass of fourPeptideInUrineB.

    // https://metlin.scripps.edu/metabo_info.php?molid=15671
    val triPeptideInUrine = {
      new Peptide(
        len = 3,
        composition = Map(Gln -> 1, Trp -> 1, Met -> 1),
        formula = Map(C->21, H->29, N->5, O->5, S->1),
        mass = 463.188942
      )
    }

    // https://metlin.scripps.edu/metabo_info.php?molid=105850
    val fourPeptideInUrineA = {
      new Peptide(
        len = 4,
        composition = Map(Ala->1, Gly->1, Trp->1, Met->1),
        formula = Map(C->21, H->29, N->5, O->5, S->1),
        mass = 463.188942
      )
    }
     
    // https://metlin.scripps.edu/metabo_info.php?molid=109102
    val fourPeptideInUrineB = {
      new Peptide(
        len = 4,
        composition = Map(Ala->1, Arg->1, Cys->1, Asp->1),
        formula = Map(C->16, H->29, N->7, O->7, S->1),
        mass = 463.184917007
      )
    }

    val specificPeptidesToCheck =
      List(dppsat,
          diPeptideVeryHighSignalInUrine,
          triPeptideInUrine,
          fourPeptideInUrineA,
          fourPeptideInUrineB)

    // First check: We validate the construction of each individual polypeptide itself.
    //   a) check that its specific chemical formula equals its specified monoisotopic mass
    //   b) check that its specific AA formula equals its monoisotopic mass
    specificPeptidesToCheck.foreach( pp => {
      // check "a)"
      val fromAminoAcids = computeMonoIsotopicMass(pp.composition)
      println(s"from amino acid formula: $fromAminoAcids == ${pp.mass}")
      assert( equalUptoTolerance(fromAminoAcids, pp.mass) )
      // check "b)"
      val fromAtoms = computeMassFromAtomicFormula(pp.formula)
      println(s"from atomic formula: $fromAtoms == ${pp.mass}")
      assert( equalUptoTolerance(fromAtoms, pp.mass) )
    } )

    // Second check: For each specific polypeptide, we validate that its mass shows up in the
    // enumeration corresponding to its length.
    // That is as simple as calling the enumerator and looking for the mass in the output
    // But because we do not want to enumerate multiple times for the same length, we aggregate
    // the peptides by length, and then make one enumerator, and check all of them in the output.

    // pick each length, create an enumerator for that length, and check that all peptides of
    // that length are contained within that enumerator's output of masses
    val lenSet = specificPeptidesToCheck.map(_.len).toSet

    lenSet.foreach( sz => {
      val generator = getPeptideEnumerator(sz)
      // get the set of peptides to check
      val peptides = specificPeptidesToCheck.filter(_.len == sz)
      // get the set of masses that the enumerator creates
      val masses = generator.toList
      // check if any peptide's mass is not generated
      def massListHasPeptideMass(pmass: Double) = masses.find(m => equalUptoTolerance(pmass, m.mass)) match {
        case Some(_) => true
        case None => false
      }
      val notFound = peptides.filterNot(p => massListHasPeptideMass(p.mass))

      // assert that there are no peptides whose mass was not found,
      // i.e., all peptides had their masses in the output
      assert( notFound.size == 0 )
    })

  }

  val atomOrderInFormula = List(C, H, N, O, S)
  def computeFormulaFromElements(elems: Map[Atom, Int]) = {
    // for each pair such as (C, 2) and (N, 5) specified in the elemental composition of an AA, first
    // convert it `C2` and `N5` (the `.map` below), and then concatenate them together (the `.reduce` below)
    val elemnum: Map[Atom, String] = elems.map{
      case (atom, 0) => (atom, "")
      case (atom, 1) => (atom, atom.symbol.toString)
      case (atom, num) => (atom, atom.symbol + num.toString)
    }

    atomOrderInFormula.map{ case atom =>
      elemnum.get(atom) match {
        case Some(elemN) => elemN
        case None => throw new Exception("formula does not have one of CHNOS specified")
      }
    }.reduce(_ + _)
  }

  def computeMassFromAtomicFormula(elems: Map[Atom, Int]): Double = {
    // for each pair such as (C, 2) specified in the elemental composition of an AA, first convert
    // it `massOf(C) * 2` (the `.map` below), and then add them together (the `.reduce` below)
    elems.map{ case (atom, num) => atom.monoIsotopicMass * num }.reduce(_ + _)
  }

  def checkAllAminoAcidMasses() {
    // check that each amino acid is specified precisely
    allAminoAcids.foreach(aa => {
      val massFromElements: Double = computeMassFromAtomicFormula(aa.elems)
      val formulaFromElements: String = computeFormulaFromElements(aa.elems)

      // check that the pre-specified mass matches what we might compute from its atomic composition
      assert(equalUptoTolerance(aa.monoIsotopicMass, massFromElements))

      // check that the chemical formula specified matches what we might compute from its atomic composition
      assert(aa.formula.equals(formulaFromElements))
    })
  }

  def checkNChooseK() {
    val nChooseKPairs = (20 to 25).zip(1 to 5)
    val altVersionPairs = nChooseKPairs.map{ case (n, k) => (n, n-k) }
    nChooseKPairs.zip(altVersionPairs).foreach{ case ((n1, k1), (n2, k2)) =>
      val c1 = choose(n1, k1)
      val c2 = choose(n2, k2)
      assert(c1 == c2)
    }
  }

}
