package act.shared

import act.shared.{CmdLineParser, OptDesc}
import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, Br, Cl, I, F, AllAtoms, AminoAcid, AllAminoAcids, MonoIsotopicMass}
import act.shared.ChemicalSymbols.{Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser}
import act.shared.ChemicalSymbols.{Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg}
import act.shared.ChemicalSymbols.Helpers.{fromSymbol, computeMassFromAtomicFormula, computeFormulaFromElements}

// testing chemicals from the DB
import act.server.MongoDB
import com.act.lcms.MassCalculator.calculateMass

// SMT solver
import com.microsoft.z3._

// scala/java stuff
import scala.annotation.tailrec
import collection.JavaConversions._
import java.io.PrintWriter
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{currentMirror => cm}

sealed trait Expr {
  def <=(other: Expr): LinIneq = LinIneq(this, Le, other)
  def <(other: Expr): LinIneq = LinIneq(this, Lt, other)
  def >=(other: Expr): LinIneq = LinIneq(this, Ge, other)
  def >(other: Expr): LinIneq = LinIneq(this, Gt, other)
  def ==(other: Expr): LinIneq = LinIneq(this, Eq, other)
  def +(other: Expr): Expr = ArithExpr(Add, List(this, other))
  def -(other: Expr): Expr = ArithExpr(Sub, List(this, other))
  def *(other: Expr): Expr = ArithExpr(Mul, List(this, other))
  def /(other: Expr): Expr = ArithExpr(Div, List(this, other))
  def mod(other: Expr): Expr = ArithExpr(Mod, List(this, other))
}
case class Const(c: Int) extends Expr
case class Var(val id: String) extends Expr
case class Term(val c: Const, val v: Var) extends Expr
case class LinExpr(val terms: List[Term]) extends Expr {
  def +(term: Term): LinExpr = LinExpr(term :: this.terms)
}
case class ArithExpr(val op: ArithmeticOp, val terms: List[Expr]) extends Expr

// TODO: FIX ADT 
// The structure below is not right. Done correctly, there should be case
// classes for And, Or, Not, Lt, Gt that extend BooleanExpr
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

sealed trait ArithmeticOp
case object Mod extends ArithmeticOp
case object Add extends ArithmeticOp
case object Sub extends ArithmeticOp
case object Mul extends ArithmeticOp
case object Div extends ArithmeticOp

sealed trait BooleanExpr {
  def and(other: BooleanExpr): BooleanExpr = Multi(And, List(this, other))
  def or(other: BooleanExpr): BooleanExpr = Multi(Or, List(this, other))
  def implies(other: BooleanExpr) = Multi(Or, List(this.not, other))
  def not = Unary(Not, this)
}
case class LinIneq(val lhs: Expr, val ineq: CompareOp, val rhs: Expr) extends BooleanExpr
case class Multi(val op: BoolOp, val b: List[BooleanExpr]) extends BooleanExpr
case class Unary(val op: BoolOp, val a: BooleanExpr) extends BooleanExpr

object Solver {

  // solver = z3 under MIT license.
  // git clone git@github.com:Z3Prover/z3.git
  // compile instructions: https://github.com/Z3Prover/z3
  // the jar needs to be in the lib directory: `com.microsoft.z3.jar`
  // and the dynamic runtime link libraries in lib/native/${os}/

  val config = Map("model" -> "true")
  val ctx: Context = new Context(config)

  def newSolver(): Solver = ctx mkSolver
  // we multiply two numbers and need the product to be within bounds
  // so we have to allow for twice what might be in each.
  // The solver over bit vectors solves the circuit (so agnostic to sign),
  // and (I think) returns two's complement representations when `getInt` is
  // called on the bit vector.
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
    case ArithExpr(op, ts) => {
      val operation = op match {
        case Add => ctx.mkBVAdd _
        case Sub => ctx.mkBVSub _
        case Mul => ctx.mkBVMul _
        case Div => ctx.mkBVSDiv _
        case Mod => ctx.mkBVSMod _
      }
      val fn: (SMTExprVars, SMTExprVars) => SMTExprVars = {
        case ((bv1, vars1), (bv2, vars2)) => (operation(bv1, bv2), vars1 ++ vars2)
      }
      val terms = ts.map(mkExpr)
      terms.reduce(fn)
    }
  }

  def mkClause(eq: BooleanExpr): SMTBoolExprVars = {
    eq match {
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
    case Unary(_, e) => {
      getVars(e)
    }
    case Multi(_, es) => {
      es.map(getVars).reduce(_++_)
    }
  }

  def solveOne(eqns: List[BooleanExpr]): Option[Map[Var, Int]] = time {

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
        val kvs = for (v <- model.getDecls if vars(v) != MassToFormula.oneVar) yield {
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

  // With solveMany and helper solveManyAux, we solve for all possible satisfying solutions.
  // We do it the standard way: Solve the constraint set, recursively add a "blocking" clause
  // clause "negation(this specific solution)" and continue until UNSAT. When UNSAT return solns
  def solveMany(eqns: List[BooleanExpr]): Set[Map[Var, Int]] = time { solveManyAux(eqns, Set()) }

  @tailrec
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

  // The stats below are updated after each solve call. 
  var _lastSolveTimeTaken = 0L
  def updateLastSolveTimeTaken(ns: Long): Unit = {
    _lastSolveTimeTaken = ns
  }

  def getLastSolveTimeTaken() = _lastSolveTimeTaken
   
  def time[T](blk: => T): T = {
    val start = System.nanoTime()
    val rslt = blk
    val end = System.nanoTime()
    updateLastSolveTimeTaken(end - start)
    rslt
  } 
}

class MassToFormula(val specials: Set[Specials] = Set(), private val atomSpace: List[Atom] = AllAtoms) {
  // this is critical correctness parameter. The solver searches the integral space
  // int(mass) + { -delta .. +delta} for candidate solutions. it then validates each
  // solution in the continuous domain (but up to the precision as dictated by mass
  // mass equality of MonoIsotopicMass. If the solver fails to find valid formulae
  // for some test cases we would need to expand this delta to ensure the right space
  // is being searched. APAP (and 2k other DB chems) get solved with delta = 0!
  val delta = 0

  // this sorted is so that we order in Hill System Order. See fn `hillSystemOrder`
  val elements = atomSpace.sortBy(_.symbol)

  type ChemicalFormula = Map[Atom, Int]

  val intMassesForAtoms: ChemicalFormula = elements.map(a => {
      val integralMass = a.mass.rounded(0).toInt
      (a, integralMass)
    }).toMap
  val varsForAtoms = elements.map(a => (a, MassToFormula.atomToVar(a))).toMap
  val atomsForVars = elements.map(a => (MassToFormula.atomToVar(a), a)).toMap

  def computedMass(formula: ChemicalFormula) = formula.map{ case (a, i) => a.mass * i }.reduce(_+_)
  def toChemicalFormula(x: (Var, Int)) = (atomsForVars(x._1), x._2)

  // we can formulate an (under-determined) equation using integer variables c, h, o, n, s..
  // that define the final formula of the composition of the molecule:
  // LHS_precise := `c * 12.000000 + h * 1.007825 + o * 15.994915 + n * 14.003074 + s * 31.972071`
  // RHS_precise := `mass`
  // We are looking for satisfying variable assignments that make `LHS_precise = RHS_precise`
  //
  // As a starting point, we solve a simpler equation by rounding the monoisotopic masses of
  // the component atoms. Then we can formulate an integer LHS_int and see if we can get a
  // mass "close to" the precise mass:
  // LHS_int := `12c + h + 16o + 14n + 32s`
  // RHS_int := `floor(mass) + {0, +-1, +-2, .. +-delta}`
  // Note that `delta` cannot be arbitrarily high if we restrict attention to `mass` values that
  // are bounded, e.g., by `950Da` (in the case where the molecules are to be LCMS detected)
  // This is because each atom's monoisotopic mass deviates from their integral values in the 2nd-3rd
  // decimal place, and which means a whole lot of atoms would have to be accumulated (consistently
  // in one direction, that too) to get a full 1Da deviation from integral values.
  // Funnily, in our testing we found that `delta = 0` suffices, at least over CHNOPS. Maybe if we
  // included many more low molecular weight atoms (so that *many* of them could be in the formula)
  // and their mass difference from integral weights was substantial, we'd need to go to `delta = 1`.
  // For now `delta = 0` it is!
  //
  // We use an SMT solver to enumerate all satisfying solutions to this integral formula.
  //
  // Then we find the satisfying solution, which also makes `LHS_precise = RHS_precise`. Output
  // all of those solutions!

  def solve(mass: MonoIsotopicMass): List[ChemicalFormula] = {
    val RHSints = (-delta to delta).map(d => (mass rounded 0).toInt + d).toList

    val candidateFormulae = RHSints.map( intMz => {
      val constraints = buildConstraintOverInts(intMz)
      val specializations = specials.map(_.constraints)
      val sat = Solver.solveMany(constraints ++ specializations)
      val formulae = sat.map(soln => soln.map(toChemicalFormula))
      formulae
    }).flatten

    val approxMassSat = candidateFormulae.map{ soln => s"${buildChemFormulaA(soln)}" }
    MassToFormula.reportPass(s"\t Int match soln with +-${delta}: ${approxMassSat.size}")

    // candidateFormulae has all formulae for deltas to the closest int mass
    // For each formula that we get, we need to calculate whether this is truly the right
    // mass when computed at the precise mass. If it is then we filter it out to the final
    // formulae as as true solution
    val matchingFormulae = candidateFormulae.filter( computedMass(_).equals(mass) )
    val preciseMassSatFormulae = matchingFormulae.map{ soln => s"${buildChemFormulaA(soln)}" }
    MassToFormula.reportPass(s"\t Matching formulae[${preciseMassSatFormulae.size}] = $preciseMassSatFormulae")

    matchingFormulae
  }

  val nonCHAtoms = elements.filter(_ != C).filter(_ != H).sortBy(_.symbol)
  def hillSystemOrder(atoms: List[Atom], hasC: Boolean): List[Atom] = {
    // This is from https://en.wikipedia.org/wiki/Chemical_formula#Hill_system
    if (hasC) {
      C :: H :: nonCHAtoms
    } else {
      elements
    }
  }

  def buildChemFormulaA(soln: ChemicalFormula) = {
    val hillOrder = hillSystemOrder(elements, soln.contains(C) && soln(C) != 0)
    hillOrder.map(a => {
        soln.get(a) match {
          case None | Some(0) => ""
          case Some(1) => a.symbol
          case _ => a.symbol + soln(a)
        }
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

    // Our linear equations need a constant term, but our unfortunate Term ADT only allows
    // Terms to be (Constant x Variable), which means to get a constant term, we need a 
    // variable `one` whose numerical value "solves" to `1`. Then we can use that variable
    // in Term(c, one) and that Term will equal the value of the constant `c`.
    // (TODO: FIX ADT will get rid of this hack!)
    val onec = LinIneq(MassToFormula.oneVar, Eq, Const(1))

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
            val lineBonds = Term(Const(-others.size), MassToFormula.oneVar)
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

    // |bounds| = |elements|, which defaults to |AllAtoms| and is 6 (for CHONPS),
    // so this final contraints set is 2 + 6 = 8.
    ineq :: onec :: bounds
  }

}

object MassToFormula {
  def atomToVar(a: Atom) = Var(a.symbol)
  def term(c: Int) = Term(Const(c), oneVar)
  def term(c: Int, a: Atom) = Term(Const(c), MassToFormula.atomToVar(a))
  // See comment on `onec` in `buildConstraintOverInts` for how we use `oneVar`
  // to anchor constants, by being able to put them in terms that need to be of
  // the form Constant x Variable.
  def oneVar  = Var("v=1")

  // These can be overwritten by command line arguments to specific files
  // We can pass them around as arguments, but it was getting very painful.
  // So we chicken-ed out and used `var`s
  var outStream = new PrintWriter(System.out)
  var errStream = new PrintWriter(System.err)
  var errStreamToConsole = true
  var outStreamToConsole = true

  val allSpecials = typeOf[Specials].typeSymbol.asClass.knownDirectSubclasses
  val allSpecialNm = allSpecials.map(_.toString.stripPrefix("class "))

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List(optMz, optMassFile, optOutputFile, optOutFailedTests, optRunTests, optSpecials)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    outStream = {
      if (cmdLine has optOutputFile) {
        outStreamToConsole = false
        new PrintWriter(cmdLine get optOutputFile)
      } else {
        new PrintWriter(System.out)
      }
    }

    errStream = {
      if (cmdLine has optOutFailedTests) {
        errStreamToConsole = false
        new PrintWriter(cmdLine get optOutFailedTests)
      } else {
        new PrintWriter(System.err)
      }
    }

    val specials: Set[Specials] = {
      if (cmdLine has optSpecials) {
        val spls = (cmdLine getMany optSpecials).toSet
        // check if the provided specialization is valid, before loading it as a class
        if (!spls.forall(s => allSpecialNm.contains(s))) {
          errStream.println("Invalid specialization provided.")
          errStream.println("Specializations of solver supported: " + allSpecialNm.mkString(", "))
          errStream.flush
          System.exit(1)
        }
        def nameToClass(s: String) = {
          val fullyQualifiedName = this.getClass.getPackage.getName + "." + s
          Class.forName(fullyQualifiedName)
          .newInstance
          .asInstanceOf[Specials]
        }
        val splConstraints = spls.map(nameToClass)
        splConstraints
      } else {
        Set()
      }
    }

    if (cmdLine has optRunTests) {
      // TODO: Eventually, move to scalatest.
      runAllUnitTests
    } else if ((cmdLine has optMz) && !(cmdLine has optMassFile)) {
      val mass = (cmdLine get optMz).toDouble
      solve(specials)(List(mass))
    } else if ((cmdLine has optMassFile) && !(cmdLine has optMz)) {
      val source = scala.io.Source.fromFile(cmdLine get optMassFile)
      val massStrs = try source.getLines.toList finally source.close()
      // expect input lines to be tab separated with the first column the mass
      // and the rest can have meta data with them (e.g., the err output file)
      // we split by tabs and then pick the first element to convert to double
      solve(specials)(massStrs.map(_.split("\t")(0).toDouble))
    } else {
      // we cannot handle both a single mass and a mass file on the command line
      // so write a snarky message to user, and abort! Ouch.
      errStream.println(s"You specified both a single mass and a mass file as input. Is one or the other? Aborting.")
      System.exit(-1)
    }

  }

  def solve(specials: Set[Specials])(masses: List[Double]): Unit = {
    val f = new MassToFormula(specials)
    masses.foreach( m => {
      val solns = f.solve(new MonoIsotopicMass(m))
      val allChemicalFormulae = solns.map(f.buildChemFormulaA)
      outStream.write(m + "\t" + allChemicalFormulae.mkString("\t") + "\n")
      outStream.flush
    })
  }

  def solveNCheck(test: (Double, List[Atom], Map[Atom, Int], String)) {
    val (mass, atoms, expected, inchi) = test
    val formulator = new MassToFormula(atomSpace = atoms)

    val atomsInt = atoms.map(a => a.mass.rounded(0) * expected(a)).sum
    val massRounded = math round mass
    val solnsFound = formulator.solve(new MonoIsotopicMass(mass))
    val timeTaken = Solver.getLastSolveTimeTaken / 1000000.0 // ns to milliseconds
    val isPass = solnsFound.contains(expected)
    val passFail = if (isPass) "PASS" else "FAIL"

    val log = List(s"$mass",
                      s"$atoms",
                      s"${formulator.buildChemFormulaA(expected)}",
                      s"rounded(mass)=$massRounded",
                      s"rounded(atoms)=$atomsInt",
                      s"$inchi",
                      s"$passFail",
                      s"$timeTaken").mkString("\t")

    if (isPass)
      reportPass(s"$log")
    else
      reportFail(s"$log")
  }

  // Do not write color codes if we are writing to files
  // But when writing to screen it is good to see visual indicators
  def reportFail(s: String) = reportHelper(s, errStream, if (errStreamToConsole) Console.RED else "")
  def reportPass(s: String) = reportHelper(s, outStream, if (outStreamToConsole) Console.GREEN else "")

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

  val optSpecials = new OptDesc(
                    param = "s",
                    longParam = "specials",
                    name = "restrictions",
                    desc = s"""Do solving in a specialized, i.e., restricted space of solutions
                             |For example, under a setting where the num carbons dominates.
                             |To specialize the solver to that, pass it MostlyCarbons.
                             |Any of the following, or a comma separated list of multiple are
                             |supported: ${allSpecialNm}.""".stripMargin,
                    isReqd = false, hasArgs = true)

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
    testMultiCharAtoms
    testStableFormulaEdgeCases
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

  def testMultiCharAtoms() {
    assert(getFormulaMap("C20BrCl2").equals(Map(C->20, Br->1, Cl->2)))
    assert(getFormulaMap("BrCl2").equals(Map(Br->1, Cl->2)))
    assert(getFormulaMap("BrC2").equals(Map(Br->1, C->2)))
    assert(getFormulaMap("CCl").equals(Map(C->1, Cl->1)))
    assert(getFormulaMap("ClC").equals(Map(C->1, Cl->1)))
    assert(getFormulaMap("IFClH20CBrN1OP2").equals(Map(C->1, H->20, N->1, O->1, P->2, Cl->1, Br->1, I->1, F ->1)))
  }

  def formulaFromInChI(i: String) = {
    try {
      // This extracts the formula string specified in the InChI.
      // In the cases where a `/p` layer is specified the `H` number is inaccurate.
      // It needs to be adjusted for the num protons specified, as in examples below
      //
      // Examples we need to handle:
      // InChI=1S/C7H5NO4/c9-6(10)4-1-2-8-3-5(4)7(11)12/h1-3H,(H,9,10)(H,11,12)/p-2   -> C7H3NO4, -2 adjusted
      // InChI=1S/C6H12O3/c1-4(2)3-5(7)6(8)9/h4-5,7H,3H2,1-2H3,(H,8,9)/p-1/t5-/m0/s1  -> C6H11O3, -1 adjusted
      // InChI=1S/C6H13NO2S/c1-10(2)4-3-5(7)6(8)9/h5H,3-4,7H2,1-2H3/p+1/t5-/m0/s1     -> C6H14NO2S, +1 adjusted

      val layers = i.split("/")

      // layer 1 is where the chemical formula string is. also, we remove all salt delimiters
      val formula = layers(1)
      val formulaToDeconstruct = formula.replaceAll("\\.","") 

      // find the proton layer
      val pLayer = layers.find(l => l(0) == 'p')
      val protonAdjust = pLayer match { 
        case None => 0
        case Some(l) => (l substring 1).toInt
      }

      val formulaMap = getFormulaMap(formulaToDeconstruct)
      val protonAdjFormula = formulaMap + (H -> (formulaMap(H) + protonAdjust))
      Some((formula, protonAdjFormula))
    } catch {
      case e: Exception => None
    }
  }

  ////
  // TODO: Replace below with Chemaxon.Molecule.getAtomCount(atomicNumber)
  // Function `getFormulaMap: String -> Map[Atom, Int]` and helpers `getFormulaMapStep`,
  // `get{Num, Atom}AtHead`, and `headExtractHelper` won't be needed. That will take care
  // of fixing the greedy approach here that only works for single char atoms 
  // (Line: `!isHeadAlreadyAtom && c.isLetter`)
  //
  // Considerations that will need to be tested before the move:
  // 1. Chemaxon license?
  // 2. Proton adjustment in `formulaFromInChI`: Does Chemaxon automatically do that?
  //    * Current code has been tested to be correct with the adjustment
  //    * When switch to Chemaxon, test if the proton adjustment still needed.
  ////

  def getAtomAtHead(f: String): (Atom, String) = {
    def doMove(hd: String, c: Char) = {
      val isHeadAlreadyAtom = AllAtoms.exists(_.symbol.equals(hd))
      val lookAheadIsAtom = AllAtoms.exists(_.symbol.equals(hd + c))
      lookAheadIsAtom || !isHeadAlreadyAtom
    }
    val (nStr, tail) = headExtractHelper("", f, doMove)
    val maybeAtom = AllAtoms.find(_.symbol.equals(nStr))
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
    val stop = tail.isEmpty || !doMove(head, tail(0))
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

      // in the case of salts, there might be that we see the same atom more than once
      // e.g., in C6H6N2O2.H3N we will be processing the string C6H6N2O2H3N here
      // and so we will see H twice, once with num=6 and once with num=3; similarly N=2 and N=1
      val atomCnt = num + (if (curr contains atom) curr(atom) else 0)
      val map = curr + (atom -> atomCnt)
      getFormulaMapStep(rest2, map)
    }
  }

  def testDBChemicals(n: Int, maxMz: Double) {
    val db = new MongoDB("localhost", 27017, "actv01")
    val dbCur = db.getIteratorOverChemicals
    val chems = for(i <- 0 until n) yield db.getNextChemical(dbCur)

    val testformulae = for {

      chem <- chems
      inchi = chem.getInChI
      mass = try { 
        Some(calculateMass(inchi).doubleValue)
      } catch { 
        case e: Exception => None
      }
      (formulaStr, formulaMap) = formulaFromInChI(inchi) match {
        case None => ("", Map[Atom, Int]())
        case Some(f) => f
      }

      if {
        // We only test over formulae that:
        //    1) have at least all of CHO in them
        //    2) no atoms outside of `AllAtoms`
        //    3) is not a salt
        //    4)  a) inchi can be loaded, and mass calculated by `MassCalculator`
        //        b) mass < 200Da (most of our standards were <200Da. Can go higher but solving takes long then!)
        // below we construct booleans for each of these four tests.

        // Condition "1)"
        val allMainAtomsPresent = List(C, H, O).forall(formulaMap.contains)

        // Condition "2)"
        // We need to check if all atom symbols present in the string are ones we know about.
        val noUnrecognizedAtoms = formulaMap.keys.forall(AllAtoms.contains)

        // Condition "3)"
        val isSaltComplex = formulaStr.indexOf('.') != -1

        // Condition "4)"
        val massOk = mass match {
          case None => false
          case Some(mass) => mass < maxMz
        }
        
        // Check if all conditions 1)..4) hold
        allMainAtomsPresent && noUnrecognizedAtoms && !isSaltComplex && massOk
      }
    } yield {
      mass match { 
        case Some(mass) => {
          // map each formula to its mass and inchi, e.g.,
          // C6H5N1O4 -> (155.021859, "InChI=1S/C6H5NO4/c8-4-2-1-3(6(10)11)5(9)7-4/h1-2H,(H,10,11)(H2,7,8,9)")
          // C7H3N1O4 -> (165.006209, "InChI=1S/C7H5NO4/c9-6(10)4-1-2-8-3-5(4)7(11)12/h1-3H,(H,9,10)(H,11,12)/p-2")
          // Note the last case above, where the mass has to be adjusted for the `-2` protons
          (mass, formulaMap.keys.toList, formulaMap, inchi)
        }
      }
    }

    val dbCases = testformulae.toSet
    dbCases foreach solveNCheck
  }

  def unitTestIntegralSolns() {
    // The type parameter is needed or else the compiler fails to infer
    // the right type for the tuple elements. we can specify it as a type on the variable,
    // or parameter to the constructor. The latter looks more readable.
    val testcases = Set[(Int, List[Atom], Set[Map[Atom, Int]])](
      (
        104,                        // atomic mass aiming for
        List(C, N, O),              // atomic space to search
        Set(Map(C->6, N->0, O->2),  // sets of valid formulae
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
        val formulator = new MassToFormula(atomSpace = elems) // Hah! The "formulator"
        val constraints = formulator.buildConstraintOverInts(intMz)
        val validSolns = validAtomicFormulae.map(_.map(kv => (MassToFormula.atomToVar(kv._1), kv._2)))
        testOneSolnOverCNO(constraints, intMz, validSolns, formulator)
        testAllSolnOverCNO(constraints, intMz, validSolns, formulator)
      }
    }
  }

  def testOneSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MassToFormula) {

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

  def testAllSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MassToFormula) {

    val vars = constraints.map(Solver.getVars).flatten
    val sat = Solver.solveMany(constraints)

    val descs = sat.map{ soln => s"${f.buildChemFormulaV(soln)}" }
    if (!(sat equals expected)) {
      reportFail(s"FAIL: Enumerate: Found ${descs.size} formulae for ~${intMz}: $descs but expected $expected")
      reportFail(s"FAIL DEBUG: satisfying solution - expected = ${sat -- expected}")
      reportFail(s"FAIL DEBUG: expected - satisfying solution = ${expected -- sat}")
    }
  }

  def testStableFormulaEdgeCases() = {
    // These test cases comes from https://github.com/20n/act/pull/473#issuecomment-252290577
    val edgeCases = Map(
      "atp"     -> (506.995758, "InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)(H2,11,12,13)(H2,18,19,20)/t4-,6-,7-,10-/m1/s1"),
      "choline" -> (104.106987, "InChI=1S/C5H14NO/c1-6(2,3)4-5-7/h7H,4-5H2,1-3H3/q+1"), // Choline will likely to cause problems due to quaternary ammonium
      "glucopyranose" -> (180.063385, "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5-,6-/m1/s1"),
      "acetone" -> (58.041866, "InChI=1S/C3H6O/c1-3(2)4/h1-2H3"),
      "lysine"  -> (146.10553, "InChI=1S/C6H14N2O2/c7-4-2-1-3-5(8)6(9)10/h5H,1-4,7-8H2,(H,9,10)/t5-/m0/s1"),
      "cumene"  -> (120.093903, "InChI=1S/C9H12/c1-8(2)9-6-4-3-5-7-9/h3-8H,1-2H3"),
      "trichloroacetic acid" -> (161.904205, "InChI=1S/C2HCl3O2/c3-2(4,5)1(6)7/h(H,6,7)"),
      "foa"     -> (413.973694, "InChI=1S/C8HF15O2/c9-2(10,1(24)25)3(11,12)4(13,14)5(15,16)6(17,18)7(19,20)8(21,22)23/h(H,24,25)"), //has fluorines
      "ppGpp"   -> (602.95697, "InChI=1S/C10H17N5O17P4/c11-10-13-7-4(8(17)14-10)12-2-15(7)9-5(16)6(30-36(26,27)32-34(21,22)23)3(29-9)1-28-35(24,25)31-33(18,19)20/h2-3,5-6,9,16H,1H2,(H,24,25)(H,26,27)(H2,18,19,20)(H2,21,22,23)(H3,11,13,14,17)"), 
      "erythronolide B" -> (402.261749, "InChI=1S/C21H38O7/c1-8-15-11(3)17(23)12(4)16(22)10(2)9-21(7,27)19(25)13(5)18(24)14(6)20(26)28-15/h10-15,17-19,23-25,27H,8-9H2,1-7H3/t10-,11+,12+,13+,14-,15-,17+,18+,19-,21-/m1/s1"),
      "calicheamicin" -> (1367.27417, "InChI=1S/C55H74IN3O21S4/c1-12-57-30-24-73-35(22-34(30)68-6)78-48-43(63)40(26(3)75-53(48)77-33-17-15-13-14-16-19-55(67)23-32(61)41(58-54(66)72-10)38(33)29(55)18-20-82-84-81-11)59-80-36-21-31(60)50(28(5)74-36)83-51(65)37-25(2)39(56)46(49(71-9)45(37)69-7)79-52-44(64)47(70-8)42(62)27(4)76-52/h13-14,18,26-28,30-31,33-36,40,42-44,47-48,50,52-53,57,59-60,62-64,67H,12,20-24H2,1-11H3,(H,58,66)/b14-13-,29-18+/t26-,27+,28-,30+,31+,33+,34+,35+,36+,40-,42+,43+,44-,47-,48-,50-,52+,53+,55-/m1/s1"),  //Will possibly cause problems due to unusual trifulfide group
      "chloramphenicol" -> (322.012329, "InChI=1S/C11H12Cl2N2O5/c12-10(13)11(18)14-8(5-16)9(17)6-1-3-7(4-2-6)15(19)20/h1-4,8-10,16-17H,5H2,(H,14,18)/t8-,9-/m1/s1"),
      "teixobactin" -> (1241.713257, "InChI=1S/C58H95N15O15/c1-12-28(5)42(70-49(79)37(61-11)23-34-19-17-16-18-20-34)53(83)67-39(26-74)51(81)65-36(21-22-41(59)76)48(78)69-44(30(7)14-3)55(85)71-43(29(6)13-2)54(84)68-40(27-75)52(82)73-46-33(10)88-57(87)45(31(8)15-4)72-50(80)38(24-35-25-62-58(60)64-35)66-47(77)32(9)63-56(46)86/h16-20,28-33,35-40,42-46,61,74-75H,12-15,21-27H2,1-11H3,(H2,59,76)(H,63,86)(H,65,81)(H,66,77)(H,67,83)(H,68,84)(H,69,78)(H,70,79)(H,71,85)(H,72,80)(H,73,82)(H3,60,62,64)/t28-,29-,30-,31-,32-,33-,35-,36+,37+,38-,39-,40-,42-,43-,44+,45-,46+/m0/s1")  
    )

    val abortIfFail = false

    val stableCnstr = new StableChemicalFormulae
    for ((name, massInChI) <- edgeCases) {
      val (mass, inchi) = massInChI
      val parsed: Option[(String, Map[Atom, Int])] = formulaFromInChI(inchi)
      assert(parsed.isDefined, s"Failed: $name, Invalid InChI: $inchi")
      val formula = parsed match { case Some((str, form)) => form }
      val passes = stableCnstr.isValid(formula)
      val msg = (if (passes) "Validated: " else "Failed: ") + s"$name, Mass: $mass, Formula: $formula"
      if (abortIfFail) assert(passes, msg) else println(msg)
    }
  }

}

sealed trait Specials {
  type ChemicalFormula = Map[Atom, Int]
  def constraints(): BooleanExpr
  def describe(): String
  def check(f: ChemicalFormula): Boolean
  def isValid(f: ChemicalFormula): Boolean = {
    val fillOut = AllAtoms.filterNot(f contains _).map(a => a -> 0).toMap
    check(f ++ fillOut)
  }

  def t(c: Int, a: Atom) = MassToFormula.term(c, a)
  def t(a: Atom) = MassToFormula.term(1, a)
  def t(c: Int) = MassToFormula.term(c)
  def toExpr(t: Term) = LinExpr(List(t))
  def e(c: Int, a: Atom) = toExpr(t(c, a))
  def e(a: Atom) = toExpr(t(a))
  def e(c: Int) = toExpr(t(c))
}

class MostlyCarbons extends Specials {
  def describe() = "C>=N + C>=O + C>=S + C>=P"
  def constraints() = {
    val cn = e(C) >= e(N)
    val co = e(C) >= e(O)
    val cs = e(C) >= e(S)
    val cp = e(C) >= e(P)
    cn and co and cs and cp
  }
  def check(f: ChemicalFormula) = {
    val cn = f(C) >= f(N)
    val co = f(C) >= f(O)
    val cs = f(C) >= f(S)
    val cp = f(C) >= f(P)
    cn && co && cs && cp
  }
}

class MoreHThanC extends Specials {
  def describe() = "H>=C"
  def constraints() = e(H) >= e(C)
  def check(f: ChemicalFormula) = f(H) >= f(C)
}

class LessThan3xH extends Specials {
  def describe() = "3C>=H"
  def constraints() = e(3, C) >= e(H)
  def check(f: ChemicalFormula) = 3 * f(C) >= f(H)
}

class StableChemicalFormulae extends Specials {
  def describe() = "Stable chemical formulae"

  //
    // These constraints describe the space of stable organic chemical formulae
    // As described in Issue #463 and PR #473 discussions

    // **************** Constraint c1 *******************
    // For the fully reduced molecule, the number of unoccupied bonds is:
    // cBonds = (C-2)*2 + 6 = 2C + 2
    // Assuming we only allow heteroatoms to be directly attached to a carbon, then:
    // numHetero = N+S+O;
    // numHetero <= cBonds;
    // **************** Constraint c2 *******************
    // The maximal value for H is given by:
    // hMax = 2N  + S + O + Cbonds - numHetero;
    // The permitted values of H are given by:
    // for(int H=Hmax; H>=0; H=H-2)
    // hMax is 2N+S+O+cBonds-negNumHetero = 2N+S+O+(2C+2)-(N+S+O) = N+2C+2
    // ensuring H is moves in exactly 2 increments requires creating another
    // free variable `Z` such that H = N+2C+2 - 2*Z, where Z >= 0. That is
    // more complicated. [ TODO later ]
    // For now we just ensure 0 <= H <= hMax
    // **************** Constraint c3 *******************
    // If `ph` represents the number of phosphate groups in the molecule:
    // There must be at least one O already present.
    // The total number of O's present must be greater than 3*P.
    // **************** Constraint c4 *******************
    // int halogensAndHydrogens = H + Cl + Br + I + F //For the final formula
    // halogensAndHydrogens <= Hmax
    // halogensAndHydrogens %2 == Hmax % 2
  //

  def constraints() = {
    // constraint c1 above
    val cBonds = e(2, C) + e(2)
    val numHetero = e(N) + e(S) + e(O)
    val c1 = numHetero <= cBonds

    // constraint c2 above
    val negNumHetero = e(-1, N) + e(-1, S) + e(-1, O)
    val hMax = e(2, N) + e(S) + e(O) + cBonds + negNumHetero
    val c2 = e(H) >= e(0) and e(H) <= hMax

    // constraint c3 above
    val c3 = e(P) < e(5) and (e(P) > e(0) implies e(O) > e(3, P))

    // constraint c4 above
    val halogensAndH = e(H) + e(Cl) + e(Br) + e(I) + e(F)
    val mod2Equals = (halogensAndH mod e(2)) == (hMax mod e(2))
    val c4 = halogensAndH <= hMax and mod2Equals

    val constraint = c1 and c2 and c3 and c4
    constraint
  }

  def check(f: ChemicalFormula) = {
    // constraint c1 above
    val cBonds = 2 * f(C) + 2
    val numHetero = f(N) + f(S) + f(O)
    val c1 = numHetero <= cBonds

    // constraint c2 above
    val negNumHetero = -f(N) + -f(S) + -f(O)
    val hMax = 2 * f(N) + f(S) + f(O) + cBonds + negNumHetero
    val c2 = f(H) >= 0 && f(H) <= hMax

    // constraint c3 above
    val c3 = f(P) < 5 && ( !(f(P) > 0) || (f(O) > 3 * f(P)))

    // constraint c4 above
    val halogensAndH = f(H) + f(Cl) + f(Br) + f(I) + f(F)
    val c4 = halogensAndH <= hMax && halogensAndH % 2 == hMax % 2

    val constraint = c1 && c2 && c3 && c4
    constraint
  }
}
