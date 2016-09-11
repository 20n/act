package act.shared

import act.shared.{CmdLineParser, OptDesc}
import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, AllAtoms, AminoAcid, AllAminoAcids, MonoIsotopicMass}
import act.shared.ChemicalSymbols.{Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser} 
import act.shared.ChemicalSymbols.{Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg}
import act.shared.ChemicalSymbols.Helpers.{fromSymbol, computeMassFromAtomicFormula, computeFormulaFromElements}

// testing chemicals from the DB
import act.server.MongoDB
import com.act.lcms.MassCalculator.calculateMass

// SMT solver
import com.microsoft.z3._

// scala/java stuff
import collection.JavaConversions._
import java.io.PrintWriter

//
// We might want to move these ADTs to a file of their own.
//

sealed trait Expr
case class Const(c: Int) extends Expr
case class Var(val id: String) extends Expr
case class Term(val c: Const, val v: Var) extends Expr
case class LinExpr(val terms: List[Term]) extends Expr

// The below is not the right way to structure this.
// There should be case classes for And, Or, Not, Lt, Gt that extend BooleanExpr
// TODO: fix this before mainlining this code
sealed trait CompareOp
case object Lt extends CompareOp
case object Gt extends CompareOp
case object Ge extends CompareOp
case object Le extends CompareOp
case object Eq extends CompareOp

sealed trait BoolOp
case object And extends BoolOp
case object Or extends BoolOp
case object Not extends BoolOp

sealed trait BooleanExpr
case class LinIneq(val lhs: Expr, val ineq: CompareOp, val rhs: Expr) extends BooleanExpr
case class Binary(val a: BooleanExpr, val op: BoolOp, val b: BooleanExpr) extends BooleanExpr
case class Multi(val op: BoolOp, val b: List[BooleanExpr]) extends BooleanExpr
case class Unary(val op: BoolOp, val a: BooleanExpr) extends BooleanExpr

object Solver {

  // solver = z3 under MIT license.
  // git clone git@github.com:Z3Prover/z3.git
  // compile instructions: https://github.com/Z3Prover/z3
  // the jar needs to be in the lib: `com.microsoft.z3.jar`
  // and the dynamic runtime link libraries in lib/native/${os}/

  val config = Map("model" -> "true")
  val ctx: Context = new Context(config)

  def newSolver(): Solver = ctx mkSolver
  // we multiply two numbers and need the product to be within bounds
  // so we have to allow fot twice what might be in each. 
  val bvSz = 24
  val bv_type: Sort = ctx mkBitVecSort bvSz

  type SMTExprVars = (BitVecExpr, Map[FuncDecl, Var])
  type SMTBoolExprVars = (BoolExpr, Map[FuncDecl, Var]) 

  def mkExpr(e: Expr): SMTExprVars = e match {
    case Const(c)         => {
      val expr = ctx.mkNumeral(c.toString, bv_type).asInstanceOf[BitVecNum]
      (expr, Map())
    }
    case Var(v)           => {
      val expr = ctx.mkBVConst(v, bvSz)
      (expr, Map(expr.getFuncDecl -> Var(v)))
    }
    case Term(c, v)       => {
      val (coeff, varsCoeff) = mkExpr(c)
      val (variable, varsVar) = mkExpr(v)
      val expr = ctx.mkBVMul(coeff, variable)
      (expr, varsCoeff ++ varsVar)
    }
    case LinExpr(ts)      => {
      // we need to write this separately so that we can specify the type of the anonymous function
      // otherwise the reduce below is unable to infer the type and defaults to `Any` causing compile failure
      val fn: (SMTExprVars, SMTExprVars) => SMTExprVars = { 
        case ((bv1, vars1), (bv2, vars2)) => (ctx.mkBVAdd(bv1, bv2), vars1 ++ vars2) 
      }
      val terms = ts.map(mkExpr)
      terms.reduce(fn)
    }
  }

  def mkClause(eq: BooleanExpr): SMTBoolExprVars = eq match {
    case LinIneq(lhs, op, rhs) => {
      val compareFn = op match {
        case Lt => ctx.mkBVSLT _
        case Gt => ctx.mkBVSGT _
        case Ge => ctx.mkBVSGE _
        case Le => ctx.mkBVSLE _
        case Eq => ctx.mkEq _
      }
      val (lExpr, varsLhs) = mkExpr(lhs)
      val (rExpr, varsRhs) = mkExpr(rhs)
      (compareFn(lExpr, rExpr), varsLhs ++ varsRhs)
    }
    case Binary(e1, op, e2) => {
      val boolFn = op match {
        case And => ctx.mkAnd _
        case Or  => ctx.mkOr _
      }
      val (lhs, varsLhs) = mkClause(e1)
      val (rhs, varsRhs) = mkClause(e2)
      (boolFn(lhs, rhs), varsLhs ++ varsRhs)
    }
    case Unary(op, e) => {
      val boolFn = op match {
        case Not => ctx.mkNot _
      }
      val (expr, vars) = mkClause(e)
      (boolFn(expr), vars)
    }
    case Multi(op, es) => {
      val (exprs, varsLists) = es.map(mkClause).unzip
      // exprs is a list, but we need to pass it to a vararg function, hence the `:_*`
      // if we just write boolFn(exprs) it expects a `boolFn(List[T])`, while the available is `boolFn(T*)`
      val boolExpr = op match {
        case And => ctx.mkAnd(exprs:_*)
        case Or  => ctx.mkOr(exprs:_*)
      }
      (boolExpr, varsLists.reduce(_++_))
    }
  }

  def getVars(e: Expr): Set[Var] = e match {
    case Const(_)    => Set()
    case Var(n)      => Set(Var(n))
    case Term(c, v)  => Set(v)
    case LinExpr(ts) => ts.map(getVars).reduce(_++_)
  }

  def getVars(eq: BooleanExpr): Set[Var] = eq match {
    case LinIneq(lhs, _, rhs) => {
      getVars(lhs) ++ getVars(rhs)
    }
    case Binary(e1, _, e2) => {
      getVars(e1) ++ getVars(e2)
    }
    case Unary(_, e) => {
      getVars(e)
    }
    case Multi(_, es) => {
      es.map(getVars).reduce(_++_)
    }
  }

  def solveOne(eqns: List[BooleanExpr]): Option[Map[Var, Int]] = {
    
    val (constraints, varsInEq) = eqns.map(mkClause).unzip

    val vars = varsInEq.reduce(_++_)

    // get the model (if one exists) from the sat solver, else prove unsatisfiable
    val m: Option[Model] = {
      val solver = newSolver
      constraints.foreach(x => solver.add(x))
      if ((solver check) == Status.SATISFIABLE)
        Some(solver getModel)
      else
        None
    }

    // extract the solved variables from the model and return the map
    m match {
      case Some(model) => {
        val kvs = for (v <- model.getDecls if vars(v) != Var("v=1")) yield {
          val cnst = model getConstInterp v
          val num = solved(cnst, model)
          vars(v) -> num
        }
        val assignments = kvs.toMap
        Some(assignments)
      }
      case None => {
        // unsatisfiable formula
        None
      }
    }
  }

  def solveMany(eqns: List[BooleanExpr]): Set[Map[Var, Int]] = solveManyAux(eqns, Set())

  def solveManyAux(eqns: List[BooleanExpr], solns: Set[Map[Var, Int]]): Set[Map[Var, Int]] = {
    solveOne(eqns) match {
      case None => solns
      case Some(s) => {
        val blockingClause = exclusionClause(s)
        solveManyAux(blockingClause :: eqns, solns + s)
      }
    }
  }

  def exclusionClause(soln: Map[Var, Int]): BooleanExpr = {
    val varEqVal: ((Var, Int)) => BooleanExpr = { case (v, c) => LinIneq(v, Eq, Const(c)) }
    val blockingClause = Unary(Not, Multi(And, soln.map(varEqVal).toList))
    blockingClause
  }

  def solved(e: com.microsoft.z3.Expr, m: Model): Int = {
    // BitVecNum extends BitVecExpr, which extends Expr
    // is there a better way to get to BitVecNum, than doing a cast to subclass?
    // https://github.com/Z3Prover/z3/blob/master/src/api/java/BitVecNum.java
    val bv: BitVecExpr = e.asInstanceOf[BitVecExpr]
    val num: BitVecNum = bv.asInstanceOf[BitVecNum]
    num.getInt
  }
}
  
class MzToFormula(elements: List[Atom] = AllAtoms) {
  // this is critical correctness parameter. The solver searches the integral space 
  // int(mz) + { -delta .. +delta} for candidate solutions. it then validates each
  // solution in the continuous domain (but up to the precision as dictated by mass
  // mass equality of MonoIsotopicMass. If the solver fails to find valid formulae
  // for some test cases we would need to expand this delta to ensure the right space
  // is being searched. APAP for instance gets solved even with delta = 0.
  val delta = 0

  type ChemicalFormula = Map[Atom, Int]

  val intMassesForAtoms: ChemicalFormula = elements.map(a => { 
      val integralMass = a.mass.rounded(0).toInt // (math round a.initMass).toInt
      (a, integralMass) 
    }).toMap
  val varsForAtoms = elements.map(a => (a, atomToVar(a))).toMap
  val atomsForVars = elements.map(a => (atomToVar(a), a)).toMap

  def atomToVar(a: Atom) = Var(a.symbol.toString)
  def computedMass(formula: ChemicalFormula) = formula.map{ case (a, i) => a.mass * i }.reduce(_+_)
  def toChemicalFormula(x: (Var, Int)) = (atomsForVars(x._1), x._2)

  // we can formulate an (under-determined) equation using integer variables c, h, o, n, s..
  // that define the final formula of the composition of the molecule:
  // LHS_precise := `c * 12.000000 + h * 1.007825 + o * 15.994915 + n * 14.003074 + s * 31.972071`
  // RHS_precise := `mz`
  // We are looking for satisfying variable assignments that make `LHS_precise = RHS_precise`
  //
  // As a starting point, we solve a simpler equation by rounding the monoisotopic masses of
  // the component atoms. Then we can formulate an integer LHS_int and see if we can get a 
  // mass "close to" the precise mz:
  // LHS_int := `12c + h + 16o + 14n + 32s`
  // RHS_int := `floor(mz) + {0, +-1, +-2, .. +-K}`
  // Note that K cannot be arbitrarily high if we restrict attention to `mz` values that
  // are bounded, e.g., by `950Da` (in the case where the molecules are to LCMS detected)
  // This is because each atom's monoisotopic mass deviates from their integral values in the 2nd-3rd
  // decimal place, and which means a whole lot of atoms would have to be accumulated (consistently
  // in one direction, that too) to get a full 1Da deviation from integral values. `K=2` probably suffices.
  //
  // We use an SMT solver to enumerate all satisfying solutions to this integral formula.
  //
  // Then we find the satisfying solution, which also makes `LHS_precise = RHS_precise`. Output
  // all of those solutions!

  def solve(mz: MonoIsotopicMass): List[ChemicalFormula] = {
    val RHSints = (-delta to delta).map(d => (mz rounded 0).toInt + d).toList

    val candidateFormulae = RHSints.map( intMz => {
      val constraints = buildConstraintOverInts(intMz)
      val sat = Solver.solveMany(constraints)
      val formulae = sat.map(soln => soln.map(toChemicalFormula))
      formulae
    }).flatten

    val approxMassSat = candidateFormulae.map{ soln => s"${buildChemFormulaA(soln)}" }
    MzToFormula.reportPass(s"\t Int match soln with +-${delta}: ${approxMassSat.size}")

    // candidateFormulae has all formulae for deltas to the closest int mz
    // For each formula that we get, we need to calculate whether this is truly the right
    // mz when computed at the precise mass. If it is then we filter it out to the final
    // formulae as as true solution
    val matchingFormulae = candidateFormulae.filter( computedMass(_).equals(mz) )
    val preciseMassSatFormulae = matchingFormulae.map{ soln => s"${buildChemFormulaA(soln)}" }
    MzToFormula.reportPass(s"\t Precise match soln: $preciseMassSatFormulae")

    matchingFormulae
  }

  def buildChemFormulaA(soln: ChemicalFormula) = {
    elements.map(a => {
        if (soln(a) != 0) 
          a.symbol.toString + soln(a) 
        else
          ""
      }
    ).reduce(_+_)
  }

  def buildChemFormulaV(soln: Map[Var, Int]) = {
    buildChemFormulaA(soln map toChemicalFormula)
  }

  def buildConstraintOverInts(closeMz: Int) = {
    val linExprTerms: List[Term] = elements.map(a => {
        val coefficient = Const(intMassesForAtoms(a))
        val variable = varsForAtoms(a)
        Term(coefficient, variable)
      }
    )
    val lhse = LinExpr(linExprTerms)
    val rhse = Const(closeMz)
    val ineq = LinIneq(lhse, Eq, rhse)
    val one  = Var("v=1")
    val onec = LinIneq(one, Eq, Const(1))

    // instantiate upper and lower bounds on each variable
    // sat solvers, when asked to enumerate will absolutely find all solutions
    // which means if we search over the domain of 32 length bitvectors (bv32)
    // then the two complement representation might allow for values that are 
    // negative. So bounds help in ensuring we are looking in the right places.
    val boundLists = elements.map(a => 
      {
        val lowerBound = LinIneq(varsForAtoms(a), Ge, Const(0))

        // default rounding gets to 3 decimal places, so basically accurate mass
        val atomMass = a.mass.rounded() 

        // TODO: check performance of the sat solving with these intricate bounds
        // This precise a bound may be making life difficult for the solver.
        // Alternatives are: 
        //  #1) `closeMz`
        //  #2) lowest x, such that 2^x > closeMz
        // `#2` is a bit vector with only a single highest significant bit set
        // that translates to a very easy comparison boolean circuit.
        val max = (math ceil (closeMz.toDouble / atomMass)).toInt

        val upper = a match {
          case H => {
            // there cannot be more than (valence * others) - count(others)
            // coz there are at max valenc*other bonds to make, and max available for H
            // are in cases where all others are in a single line, e.g., alcohols
            val others = elements.filter(!_.equals(H))
            val ts = others.map( a => Term(Const(a.maxValency), varsForAtoms(a)) )
            val lineBonds = Term(Const(-others.size), one)
            LinExpr(lineBonds :: ts)
          }
          case _ => Const(max)
        }
        val upperBound = LinIneq(varsForAtoms(a), Le, upper)

        (lowerBound, upperBound)
      }
    ).toList.unzip

    // combine all bounds
    val bounds = boundLists match {
      case (la, lb) => la ++ lb 
    }

    ineq :: onec :: bounds
  }
}

object MzToFormula {

  // These can be overwritten by command line arguments to specific files
  // We can pass them around as arguments, but it was getting very painful.
  // So we chicken-ed out and used `var`s
  var outStream = new PrintWriter(System.out)
  var errStream = new PrintWriter(System.err)

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List(optMz, optMassFile, optOutputFile, optOutFailedTests, optRunTests)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    val mzs: List[Double] = {
      if ((cmdLine has optMz) && (cmdLine has optMassFile)) {
        println(s"You specified both a single mass and a mass file as input. Is one or the other? Aborting.")
        System.exit(-1)
      }
      if (cmdLine has optMz) {
        List((cmdLine get optMz).toDouble)
      } else {
        if (cmdLine has optMassFile) {
          val source = scala.io.Source.fromFile(cmdLine get optMassFile)
          val mzStrs = try source.getLines.toList finally source.close()
          mzStrs.map(s => s.toDouble)
        } else List()
      }
    }

    outStream = {
      if (cmdLine has optOutFile) {
        new PrintWriter(cmdLine get optOutFile)
      } else {
        new PrintWriter(System.out)
      }
    }

    errStream = {
      if (cmdLine has optOutFailedTests) {
        new PrintWriter(cmdLine get optOutFailedTests)
      } else {
        new PrintWriter(System.err)
      }
    }

    solve(mzs, new MzToFormula)

    // TODO: move to scalatest. 
    // run unit test to make sure code is still sane
    if (cmdLine has optRunTests) {
      runAllUnitTests
    }
  }

  def solve(mzs: List[Double], f: MzToFormula): Unit = {
    mzs.foreach( m => {
      val solns = f.solve(new MonoIsotopicMass(m))
      val allChemicalFormulae = solns.map(f.buildChemFormulaA)
      outStream.write(allChemicalFormulae.mkString("\t") + "\n")
      outStream.flush
    })
  }

  def solveNCheck(test: (Double, List[Atom], Map[Atom, Int], String)) {
    val (mz, atoms, expected, inchi) = test
    val formulator = new MzToFormula(elements = atoms)

    val atomsInt = atoms.map(a => a.mass.rounded(0) * expected(a)).sum
    val mzRounded = math round mz
    val log = List(s"$mz", 
                      s"$atoms",
                      s"${formulator.buildChemFormulaA(expected)}",
                      s"rounded(mz)=$mzRounded",
                      s"rounded(atoms)=$atomsInt",
                      s"$inchi").mkString("\t")

    val solnsFound = formulator.solve(new MonoIsotopicMass(mz))

    if (solnsFound.contains(expected)) {
      reportPass(s"PASS: $log")
    } else {
      reportFail(s"FAIL: $log")
    }
  }

  def reportFail(s: String) = reportHelper(s, errStream, Console.RED) 
  def reportPass(s: String) = reportHelper(s, outStream, Console.GREEN)

  def reportHelper(s: String, stream: PrintWriter, color: String): Unit = {
    stream.write(color)
    stream.write(s)
    stream.println()
    stream.flush
  }

  val optMz = new OptDesc(
                    param = "m",
                    longParam = "mass",
                    name = "monoIsotopic value",
                    desc = """Given a single monoisotopic mass, compute possible chemical formulae.""",
                    isReqd = false, hasArg = true)

  val optMassFile = new OptDesc(
                    param = "i",
                    longParam = "file-of-masses",
                    name = "filename",
                    desc = """Input file with one line per mass. It can have other metadata
                             |associated with that mass in TSV form after the mass. Just ensure
                             |that the monoIsotopic mass is the first column. This goes hand in
                             |hand with the output of err-logfile. We can feed the output failed
                             |solvings back as input and iteratively improve the algorithm until
                             |it can solve all masses.""".stripMargin,
                    isReqd = false, hasArg = true)

  val optOutputFile = new OptDesc(
                    param = "o",
                    longParam = "formulae-outfiles",
                    name = "filename",
                    desc = """Output file with one line for each input monoIsotopic mass,
                             |Each line is tab separated list of formulas for that mass.""".stripMargin,
                    isReqd = false, hasArg = true)

  val optOutFailedTests = new OptDesc(
                    param = "l",
                    longParam = "err-logfile",
                    name = "filename",
                    desc = """There are a few knobs in solving (delta, atoms, decimal),
                             |We also run tests by pulling from the DB. So we log cases
                             |where the solving fails to compute the candidate formula.
                             |We log `mass inchi delta atomset otherknobs..` in TSV form.""".stripMargin,
                    isReqd = false, hasArg = true)

  val optRunTests = new OptDesc(
                    param = "t",
                    longParam = "run-tests",
                    name = "run regression tests",
                    desc = """Run regression tests. This will take some time.""",
                    isReqd = false, hasArg = false)

  def runAllUnitTests() {
    println(s"${Console.BLUE}Running all tests!")
    unitTestIntegralSolns()
    testDBChemicals(n = 10000, maxMz = 200.00)
    testAcetaminophen
  }

  def testAcetaminophen() {
    val apapSoln: Map[Atom, Int] = Map(C->8, H->9, N->1, O->2, S->0, P->0)
    val inchi = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"
    val apapCases = Set[(Double, List[Atom], Map[Atom, Int], String)](
      // the formula has to be specified to at least a certain # digits of precision
      // the number of digits of precision required is specified under MonoIsotopicMass
      // One case that is well used is 3 digits of precision. If you specify less,
      // e.g., 151.06 instead of 151.063 for apap, a valid solution will not be found
      (151.063324, List(C, H, N, O), apapSoln - (S, P), inchi),
      (151.063,    List(C, H, N, O), apapSoln - (S, P), inchi),
      (151.063324, AllAtoms, apapSoln, inchi),
      (151.063,    AllAtoms, apapSoln, inchi)
    )

    apapCases foreach solveNCheck
  }

  def formulaFromInChI(i: String) = {
    // TODO: this is not the accurate formula for inchis that have p+1 and p-1 
    // It ends up +1, or -1 away because of the added/missing H+
    i.split("/")(1)
  }

  def getAtomAtHead(f: String): (Atom, String) = {
    def doMove(hd: String, c: Char) = {
      val isHeadAlreadyAtom = AllAtoms.exists(_.symbol.toString.equals(hd))
      !isHeadAlreadyAtom && c.isLetter
    }
    val (nStr, tail) = headExtractHelper("", f, doMove)
    val maybeAtom = AllAtoms.find(_.symbol.toString.equals(nStr))
    maybeAtom match {
      case Some(atom) => (atom, tail)
      case None => throw new Exception(s"${nStr} not recognized as atom")
    }
  }

  def getNumAtHead(f: String): (Int, String) = {
    val (nStr, tail) = headExtractHelper("", f, (s, c) => c.isDigit)
    (nStr.toInt, tail)
  }

  def headExtractHelper(head: String, tail: String, doMove: (String, Char) => Boolean): (String, String) = {
    val stop = tail.isEmpty || doMove(head, tail(0)) == false
    if (stop) {
      (head, tail)
    } else {
      headExtractHelper(head + tail(0), tail.substring(1), doMove)
    }
  }

  def getFormulaMap(f: String): Map[Atom, Int] = getFormulaMapStep(f, Map())
  def getFormulaMapStep(f: String, curr: Map[Atom, Int]): Map[Atom, Int] = {
    if (f.isEmpty) {
      // base case of recursion, i.e., at end of formula
      curr
    } else {
      // recursive case, we pull chars in front to get atom string
      // and pull digits next if 
      val (atom, rest1) = getAtomAtHead(f)
      val (num, rest2) = if (rest1.isEmpty || rest1(0).isLetter) {
        // in the case when there is a single atom, then there wont 
        // be a number for us to extract, so we just return (1, rest)
        (1, rest1)
      } else {
        // there is a number to extract, return that
        getNumAtHead(rest1)
      }
      val map = curr + (atom -> num)
      getFormulaMapStep(rest2, map)
    }
  }

  def testDBChemicals(n: Int, maxMz: Double) {
    val db = new MongoDB("localhost", 27017, "actv01")
    val dbCur = db.getIteratorOverChemicals
    val formulaSet = for(i <- 0 until n) yield {
      val chem = db.getNextChemical(dbCur)
      val formula = formulaFromInChI(chem.getInChI)
      (formula, chem)
    }

    val testformulae = for {
      (formula, c) <- formulaSet;
      mz = try { Some(calculateMass(c.getInChI).doubleValue)  } catch { case e: Exception => None };
      if {
        val allMainAtomsPresent = List(C, H, O).forall(a => formula.indexOf(a.symbol) != -1)
        val allRecognizedAtomsRegex = s"[${AllAtoms.map(_.symbol.toString).reduce(_+_)}]"
        val noUnrecognizedAtoms = formula.replaceAll("[0-9]","").replaceAll(allRecognizedAtomsRegex, "").isEmpty
        val isComplex = formula.indexOf('.') != -1
        val mzOk = mz match {
          case None => false
          case Some(mass) => mass < maxMz
        }
        // only test over formulae that: 1) have CHO, and no 
        allMainAtomsPresent && noUnrecognizedAtoms && !isComplex && mzOk
      }
    } yield {
      mz match { case Some(mz) => formula -> (mz, c.getInChI) } 
    }

    def makeTest(kv: (String, (Double, String))): (Double, List[Atom], Map[Atom, Int], String) = kv match {
      case (formula, (mz, inchi)) => {
        val formulaMap = getFormulaMap(formula)
        (mz, formulaMap.keys.toList, formulaMap, inchi)
      }
    }

    val dbCases = testformulae.map(makeTest).toSet
    dbCases foreach solveNCheck
  }

  def unitTestIntegralSolns() {
    // The type parameter is needed or else the compiler fails to infer 
    // the right type for the tuple elements. we can specify it as a type on the variable,
    // or parameter to the constructor. The latter looks more readable.
    val testcases = Set[(Int, List[Atom], Set[Map[Atom, Int]])](
      (
        104,           // atomic mass aiming for
        List(C, N, O),  // atoms to derive formulae over
        Set(Map(C->6, N->0, O->2), // sets of valid formulae
          Map(C->5, N->2, O->1), 
          Map(C->1, N->2, O->4), 
          Map(C->2, N->0, O->5), 
          Map(C->4, N->4, O->0), 
          Map(C->0, N->4, O->3))
      )
    )

    testcases.foreach{ test => 
      {
        val (intMz, elems, validAtomicFormulae) = test
        val formulator = new MzToFormula(elements = elems) // Hah! The "formulator"
        val constraints = formulator.buildConstraintOverInts(intMz)
        val validSolns = validAtomicFormulae.map(_.map(kv => (formulator.atomToVar(kv._1), kv._2)))
        testOneSolnOverCNO(constraints, intMz, validSolns, formulator)
        testAllSolnOverCNO(constraints, intMz, validSolns, formulator)
      }
    }
  }

  def testOneSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MzToFormula) {

    val vars = constraints.map(Solver.getVars).flatten
    val sat = Solver.solveOne(constraints)

    sat match {
      case None => {
        if (expected.size == 0) {
          reportPass(s"PASS: No formula over CNO has approx mass ${intMz}, as required.")
        } else {
          // found a solution when none should have existed
          reportFail(s"FAIL: Did not find a solution for ${intMz}, when expected ${expected}")
        }
      }
      case Some(soln) => {
        if (expected contains soln) {
          reportPass(s"PASS: ${f.buildChemFormulaV(soln)} found with mass ~${intMz}")
        } else {
          reportFail(s"FAIL: Solver found ${sat.size} solutions but ${expected} was not in it")
        }
      }
    }

  }

  def testAllSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MzToFormula) {

    val vars = constraints.map(Solver.getVars).flatten
    val sat = Solver.solveMany(constraints)

    val descs = sat.map{ soln => s"${f.buildChemFormulaV(soln)}" }
    if (!(sat equals expected)) {
      reportFail(s"FAIL: Enumerate: Found ${descs.size} formulae for ~${intMz}: $descs but expected $expected")
      reportFail(s"FAIL DEBUG: satisfying solution - expected = ${sat -- expected}")
      reportFail(s"FAIL DEBUG: expected - satisfying solution = ${expected -- sat}")
    }
  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "output-file",
                    name = "output TSV file",
                    desc = "The output file for the computed values! Magic!",
                    isReqd = false, hasArg = false)

}
