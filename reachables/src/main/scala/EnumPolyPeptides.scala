package com.act.lcms

import java.io.PrintWriter
import com.act.lcms.MS1.{MetlinIonMass}
import act.shared.{CmdLineParser, OptDesc}
import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, AminoAcid, AllAminoAcids}
import act.shared.ChemicalSymbols.{Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser} 
import act.shared.ChemicalSymbols.{Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg}
import act.shared.ChemicalSymbols.MonoIsotopicMass
import act.shared.ChemicalSymbols.Helpers.{fromSymbol, computeMassFromAtomicFormula, computeFormulaFromElements}

object EnumPolyPeptides {
  // This class enumerates all polypeptides upto a certain mass.
  // Consider a polypeptide of length 6 composed of the aminoacids, shortname "DPPSAT", 
  // made of 'D' 'P' 'P' 'S' 'A' 'T'. ChemicalSymbols.AminoAcid has to longname mapping:
  // D = Aspartate P = Proline, S = Serine A = Alanine T = Threonine
  // Go to http://web.expasy.org/peptide_mass/ and input "DPPSAT", select "[M]": Mass 586.2598
  // See also the test `checkSpecificPeptides() { val dppsat: Peptide ..}` with mass 586.259859

  // The primary interface is through the function `getPeptideEnumerator` and the heavy 
  // lifting done by `getAminoAcidCombinations`. We only need to get combinations and not
  // permutations because all peptides with the same multi-set of aminoacids will have the
  // same mass. Once we enumerate the multi-sets, we compute for each `M` its ion masses
  // such as `M+H` `M+Na` by calling into the MS1.MetlinIonMass and this `row` of data
  // is held in PeptideMass. That is what we write to the output file.

  class Peptide(val len: Int, 
    val composition: Map[AminoAcid, Int], 
    val formula: Map[Atom, Int], 
    val mass: MonoIsotopicMass)

  class PeptideMass(val representative: List[AminoAcid],
                    val mass: MonoIsotopicMass,
                    val ionMasses: List[(MetlinIonMass, MonoIsotopicMass)]) {
    override def toString() = {
      // convert the representative to a string, DPPSAT
      val reprSymbol = representative.map(_.symbol.toString).reduce(_ + _)

      // note that here we assume that the list stays ordered the same way the header was computed for `tsvHdrs`
      // both are computed from ionDeltas and so there should be no reordering of the lists
      val ionMzs = ionMasses.map{ case (ion, mz) => mz.toString }
      val together = List(reprSymbol, mass.toString) ++ ionMzs
      together.mkString("\t")
    }
  }

  def formulaToListAAs(formula: Map[AminoAcid, Int]): List[AminoAcid] = {
    val aminoAcidsInaRow = formula.toList.flatMap{ case (a, n) => List.fill(n)(a) }
    // return sorted, so that it looks like ADPPST as opposed to SAPDTP
    aminoAcidsInaRow.sortWith(_.symbol < _.symbol)
  }

  def computeMonoIsotopicMass(formula: Map[AminoAcid, Int]): MonoIsotopicMass = {
    val numPeptides = formula.values.sum

    // This is where the actual algorithm from #419 is being implemented
    // What this does is compute the mass of the "chain of amino acids"
    // One water is removed to connect two amino acids, therefore to form
    // a chain of len N, we need to remove N-1 waters. 
    val numWatersToRemove = numPeptides - 1
    val massOfWater = List(H, O, H).map(_.mass).reduce(_ + _)
    val massToRemove = massOfWater * numWatersToRemove
    val combinedMass = formulaToListAAs(formula).map(_.mass).reduce(_ + _)
    val finalMass = combinedMass - massToRemove

    finalMass
  }

  // PeptideMass is the data that gets written to the output. It contains: 
  // a) the mass of the peptide
  // b) a representative amino acid with that peptide mass
  // c) masses of metlin ions corresponding to that peptide mass 
  def toPeptideMassRow(formula: Map[AminoAcid, Int], ions: Option[List[String]]): PeptideMass = {
    val mass = computeMonoIsotopicMass(formula)

    new PeptideMass(formulaToListAAs(formula), mass, computeMetlinIonMasses(mass, ions))
  }

  def getAminoAcidCombinations(maxLen: Int): Iterator[List[AminoAcid]] = {
    // scala stdlib has a combinations(k) where it returns lists of size `k`
    // to allow this combinations to create with repetitions we just give it
    // the max allowed repetitions

    // first, create a list by replicating the elements maxLen number of times
    // this is a list of lists
    val replicatedLists = List.fill(maxLen)(AllAminoAcids)

    // flatten the list created above, so that we have one long list of as many
    // repeated amino acids as can be in the final set. by repeating them here,
    // we can then pick *without* repetition
    val pickSetNonDistinctElems = replicatedLists.flatten

    // then we just ask the standard library to give us a standard combinations
    // i.e., pick `maxLen` elements from this mega-list
    pickSetNonDistinctElems.combinations(maxLen)
  }

  implicit def toFormula(aas: List[AminoAcid]): Map[AminoAcid, Int] = {
    // convert List[AminoAcids] to formula Map[AminoAcids -> count]
    aas.groupBy(identity).mapValues(_.size)
  }

  // This function enumerators all combinations of multi-sets that might compose a polypeptide chain
  // Then we calculate its monoisotopic mass and hold that representative polypeptide for reference
  // The second option allows selection of a subset of ions to output e.g., "M+H,M+Na". If None
  // specified, it will write all metlin ion masses to the output.
  def getPeptideEnumerator(maxLen: Int, ions: Option[List[String]]): Iterator[PeptideMass] = {
    // we first get an iterator over all combinations with repetition of aminoacid sets
    val aminoAcidGroups = getAminoAcidCombinations(maxLen)

    // convert each List[AminoAcids] to Map[AminoAcids -> count]
    // (using toFormula implicitly), and then to PeptideMass row
    val peptideMasses = aminoAcidGroups.map(l => toPeptideMassRow(l, ions))

    peptideMasses
  }

  def getMetlinIons(ionsRestriction: Option[List[String]]): List[MetlinIonMass] = ionsRestriction match {
    case None => MS1.ionDeltas.toList
    case Some(ions) => MS1.ionDeltas.filter(i => ions.contains(i.getName)).toList
  }

  def computeMetlinIonMasses(m: MonoIsotopicMass, 
    ions: Option[List[String]]): List[(MetlinIonMass, MonoIsotopicMass)] = {
    // we could have called `MS1.java:queryMetlin`, but that creates a completely new List(MetlinIonMass)
    // My guess is that by using the `public static final ionDeltas` as the key the scala compiler
    // should be clever enough to not create copies of the key. The only extra memory we will use here
    // should be the computed mass values for the ions (the values of the map).
    def ionMassTuple(ion: MetlinIonMass) = (ion -> new MonoIsotopicMass(MS1.computeIonMz(m.initMass, ion)))
    val ionMasses: List[(MetlinIonMass, MonoIsotopicMass)] = getMetlinIons(ions).map(ionMassTuple).toList
    ionMasses
  }

  def getTSVHdr(ions: Option[List[String]]) = {
    val ionHeaders: List[(String, MS1.IonMode)] = getMetlinIons(ions).map(ion => (ion.getName, ion.getMode)).toList
    val tsvHdrs = List("Representative", "M") ++ ionHeaders.map{ case (ionName, mode) => ionName + "/" + mode }
    tsvHdrs
  }

  class Stats {
    // we are going to log data in histogram buckets. the window size is as below
    val window = 1.0 // Da
    // the histogram will be stored as Map(bucketid -> numelements)
    var histogram = Map[Int, Int]()

    def log(mz: MonoIsotopicMass) {
      // TODO: we should not be inspecting the actual initialization mass in general, but
      // for the sake of non-granular histograming, and window bucketing, we let it pass
      val n: Int = (math floor (mz.initMass / window)).toInt
      val curr: Int = histogram.get(n) match { case None => 0; case Some(c) => c }
      histogram = histogram + (n -> (curr + 1))
    }

    def log(row: PeptideMass) {
      val masses: List[MonoIsotopicMass] = List(row.mass) ++ row.ionMasses.map{ case (_, mz) => mz }
      masses foreach log
    }

    def mkString(kvDelim: String = "\t", entryDelim: String = "\n") = {
      // we want to output the histogram such that it can be easily slurped into a plotter, e.g., gnuplot
      // for that ideally, we would like to output two columns, the first column being bucket's start_mz
      // and the second column being num element in the window [start_mz, start_mz + window)

      // We first construct a column graph that converts the (bucketid, num) map to (start_mz, num)
      val columnGraph = histogram.map{ case (bucket, c) => (bucket * window, c) }.toList.sorted

      // Then we collapse the (start_mz, num) to a delimited row, e.g., "start_mz TAB num"
      val columns = columnGraph.map{ case (w, c) => w + kvDelim + c }

      // Output each row of "start_mz TAB num" separated by delimiter, default = newline
      columns.mkString(entryDelim)
    }
  }

  def writeFlush(outFile: PrintWriter, line: String) = {
    outFile.write(line + "\n")
    outFile.flush
  }

  def main(args: Array[String]) {

    val className = this.getClass.getCanonicalName
    val opts = List(optOutFile, optMaxLen, optIonSet, optRunStats)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    // read the command line options
    val maxPeptideLength: Int = (cmdLine get optMaxLen).toInt
    val outTsvFile: PrintWriter = new PrintWriter(cmdLine get optOutFile)
    val ionSetGiven: String = cmdLine get optIonSet
    val ionSet: Option[List[String]] = ionSetGiven match { 
      case null => None
      case _ => Some(ionSetGiven.split(',').toList) 
    }

    // we'll be logging statistics, if the cmd line says so
    val stats = new Stats

    // do the actual work
    writeFlush(outTsvFile, getTSVHdr(ionSet) mkString "\t")
    (1 to maxPeptideLength).foreach { peptideLen =>
      val allMasses = getPeptideEnumerator(peptideLen, ionSet)
      allMasses.foreach(x => {
          stats log x
          writeFlush(outTsvFile, x.toString)
        })
    }
    outTsvFile.close()
    if (cmdLine has optRunStats) { println(stats.mkString()) }

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
                    desc = """Maximum length, in num of amino acids, of polypeptides to consider. 
                              Note that this grows with C(19+n, n), i.e., close to exponential.
                              Also, note that above lengths 16 the polypeptide will be `>950Da` in size
                              and hence beyond the size range current LCMS instrument can detect.""",
                    isReqd = true, hasArg = true)

  val optIonSet = new OptDesc(
                    param = "i",
                    longParam = "ion-set",
                    name = "restricted ion set",
                    desc = """If the output set is to be limited to less than all ions from Metlin,
                              specify that set as a comma separated list here. E.g., M+H,M+Na""",
                    isReqd = false, hasArg = true)

  val optRunStats = new OptDesc(
                    param = "s",
                    longParam = "run-stats",
                    name = "accumulate stats after computing masses",
                    desc = """After computing masses for peptides, we examine and accumulate
                              some basic stats on the masses, e.g., their distribution.""",
                    isReqd = false, hasArg = false)

  // TODO: move this into the tests directory
  def runAllUnitTests() {
    checkNChooseK
    checkEnumerationSizeCorrect
    checkSpecificPeptides
    checkAllAminoAcidMasses
  }

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
      val enumSz = getPeptideEnumerator(l, None).size.toLong
      assert( enumSz == sz )
    }

    // now iterate with peptide lengths 1..6
    (1 to 6).toList.map(len => (len, combinationsWithRepeats(20, len))).foreach(checkNumPeptidesEnumCorrect)
  }

  def checkSpecificPeptides() {

    // for the peptides here that do not have HMDB/Metlin links, you can validate the mass using the
    // spreadsheet linked in the PR message for #420. An example of that is the DPPSAT peptide below.

    // list of specific peptides to check. tuples of their length, and accurate monoisotopic mass
    val dppsat = {
      val aa = Map('D'->1, 'P'->2, 'S'->1, 'A'->1, 'T'->1).map{ case (s, n) => fromSymbol(s) -> n }
      new Peptide(
        len = 6,
        composition = aa,
        formula = Map(C->24, H->38, O->11, N->6, S->0),
        mass = new MonoIsotopicMass(586.259859)
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
        mass = new MonoIsotopicMass(228.147393)
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
        mass = new MonoIsotopicMass(463.188942)
      )
    }

    // https://metlin.scripps.edu/metabo_info.php?molid=105850
    val fourPeptideInUrineA = {
      new Peptide(
        len = 4,
        composition = Map(Ala->1, Gly->1, Trp->1, Met->1),
        formula = Map(C->21, H->29, N->5, O->5, S->1),
        mass = new MonoIsotopicMass(463.188942)
      )
    }
     
    // https://metlin.scripps.edu/metabo_info.php?molid=109102
    val fourPeptideInUrineB = {
      new Peptide(
        len = 4,
        composition = Map(Ala->1, Arg->1, Cys->1, Asp->1),
        formula = Map(C->16, H->29, N->7, O->7, S->1),
        mass = new MonoIsotopicMass(463.184917007)
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
      if (!fromAminoAcids.equals(pp.mass)) 
        println(s"Assertion failure: ${pp.mass} != ${fromAminoAcids.initMass}")
      assert( fromAminoAcids.equals(pp.mass) )
      // check "b)"
      val fromAtoms = computeMassFromAtomicFormula(pp.formula)
      assert( fromAtoms.equals(pp.mass) )
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
      val generator = getPeptideEnumerator(sz, None)
      // get the set of peptides to check
      val peptides = specificPeptidesToCheck.filter(_.len == sz)
      // get the set of masses that the enumerator creates
      val masses = generator.toList
      // check if any peptide's mass is not generated
      def massListHasPeptideMass(pmass: MonoIsotopicMass) = masses.find(m => pmass.equals(m.mass)) match {
        case Some(_) => true
        case None => false
      }
      val notFound = peptides.filterNot(p => massListHasPeptideMass(p.mass))

      // assert that there are no peptides whose mass was not found,
      // i.e., all peptides had their masses in the output
      assert( notFound.isEmpty )
    })

  }

  def checkAllAminoAcidMasses() {
    // check that each amino acid is specified precisely
    AllAminoAcids.foreach(aa => {
      val massFromElements = computeMassFromAtomicFormula(aa.elems)
      val formulaFromElements: String = computeFormulaFromElements(aa.elems)

      // check that the pre-specified mass matches what we might compute from its atomic composition
      assert( massFromElements.equals(aa.mass) )

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
