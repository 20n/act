package act.shared

// testing chemicals from the DB
import act.server.MongoDB
import act.shared.ChemicalSymbols.{Atom, Br, C, Cl, F, H, I, MonoIsotopicMass, N, O, P, S, AllAtoms}
import com.act.lcms.MassCalculator.calculateMass

// SMT solver
import com.microsoft.z3._

// scala/java stuff
import scala.annotation.tailrec
import collection.JavaConversions._
import java.io.PrintWriter
import scala.reflect.runtime.universe._


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
case class Var(id: String) extends Expr
case class Term(c: Const, v: Var) extends Expr
case class LinExpr(terms: List[Term]) extends Expr {
  def +(term: Term): LinExpr = LinExpr(term :: this.terms)
}
case class ArithExpr(op: ArithmeticOp, terms: List[Expr]) extends Expr

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
case class LinIneq(lhs: Expr, ineq: CompareOp, rhs: Expr) extends BooleanExpr
case class Multi(op: BoolOp, b: List[BooleanExpr]) extends BooleanExpr
case class Unary(op: BoolOp, a: BooleanExpr) extends BooleanExpr

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
    case Const(c) =>
      val expr = ctx.mkNumeral(c.toString, bv_type).asInstanceOf[BitVecNum]
      (expr, Map())
    case Var(v) =>
      val expr = ctx.mkBVConst(v, bvSz)
      (expr, Map(expr.getFuncDecl -> Var(v)))
    case Term(c, v) =>
      val (coeff, varsCoeff) = mkExpr(c)
      val (variable, varsVar) = mkExpr(v)
      val expr = ctx.mkBVMul(coeff, variable)
      (expr, varsCoeff ++ varsVar)
    case LinExpr(ts) =>
      // we need to write this separately so that we can specify the type of the anonymous function
      // otherwise the reduce below is unable to infer the type and defaults to `Any` causing compile failure
      val fn: (SMTExprVars, SMTExprVars) => SMTExprVars = {
        case ((bv1, vars1), (bv2, vars2)) => (ctx.mkBVAdd(bv1, bv2), vars1 ++ vars2)
      }
      val terms = ts.map(mkExpr)
      terms.reduce(fn)
    case ArithExpr(op, ts) =>
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
          case _ => ???
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
          case _ => ???
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
    case _ => ???
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

    val candidateFormulae = RHSints.flatMap( intMz => {
      val constraints = buildConstraintOverInts(intMz)
      val specializations = specials.map(_.constraints)
      val sat = Solver.solveMany(constraints ++ specializations)
      val formulae = sat.map(soln => soln.map(toChemicalFormula))
      formulae
    })

    val approxMassSat = candidateFormulae.map{ soln => s"${buildChemFormulaA(soln)}" }

    // candidateFormulae has all formulae for deltas to the closest int mass
    // For each formula that we get, we need to calculate whether this is truly the right
    // mass when computed at the precise mass. If it is then we filter it out to the final
    // formulae as as true solution
    val matchingFormulae = candidateFormulae.filter( computedMass(_).equals(mass) )
    val preciseMassSatFormulae = matchingFormulae.map{ soln => s"${buildChemFormulaA(soln)}" }

    val debug = false
    if (debug) 
      MassToFormula.reportPass(s"Integral solution ${approxMassSat.size}. Precise matches ${preciseMassSatFormulae.size} = $preciseMassSatFormulae")

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

        val upperBound = LinIneq(varsForAtoms(a), Le, Const(max))

        (lowerBound, upperBound)
      }
    ).unzip

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
    val opts = List(optMz, optMassFile, optOutputFile, optOutFailedTests, optRunDBTests, optSpecials)
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
          errStream.flush()
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

    if (cmdLine has optMinFormula) {
      val minFormula = cmdLine get optMinFormula
      val minFormulaMap = getFormulaMap(minFormula)
      specials + new AtLeastMinFormula(minFormulaMap)
    }

    if (cmdLine has optRunDBTests) {
      // TODO: Eventually, move to scalatest.
      val spl = cmdLine getMany optRunDBTests
      val (n, maxMz, doSolve) = (spl(0).toInt, spl(1).toDouble, spl(2).toBoolean)
      runDBChemicalsTests(n, maxMz, doSolve)
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
      outStream.flush()
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
    stream.flush()
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

  val optMinFormula = new OptDesc(
                    param = "f",
                    longParam = "min-formula",
                    name = "min formula for solving",
                    desc = s"""Attempt resolving to only formulae above a minimum count for each element.
                               |The minimum counts are defined by a formula, holding the counts.""".stripMargin,
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

  val optRunDBTests = new OptDesc(
                    param = "d",
                    longParam = "run-db-tests",
                    name = "<n,maxMz,solveAsWell>",
                    desc = """Run tests by pulling chemicals from DB. 
                             |n = num of chemicals to pull, maxMz = max size in Da,
                             |solveAsWell = boolean `true` or `false` if SAT solver to be checked.""".stripMargin,
                    isReqd = false, hasArgs = true)

  def runDBChemicalsTests(n: Int = 1000, maxMz: Double = 200.0, tryToSolve: Boolean = false) {
    reportPass(s"Running tests on chemicals from DB.")
    reportPass(s"Retrieving $n chemicals from DB ..")
    val dbChems = getDBChemicals(n, maxMz)
    reportPass(s".. Finished retrieving chemicals from DB.")
    testDBChemsStableFormula(dbChems)
    reportPass(s"Done checking all stable formulae.")

    if (tryToSolve) {
      solveFormulaeUsingSolver(dbChems)
      reportPass(s"Done solving for formulae using SAT solver..")
    }
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
  // `get{Num, Atom}AtHead`, and `headExtractHelper` won't be needed.
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

  def getFormulaMap(formula: String, fillToAllAtom: Boolean = false): Map[Atom, Int] = {
    val f = getFormulaMap(formula)
    val filled: Map[Atom, Int] = AllAtoms.filterNot(f contains _).map(a => a -> 0).toMap
    if (fillToAllAtom) f ++ filled else f
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

  def getDBChemicals(n: Int, maxMz: Double, server: String = "localhost", port: Int = 27017, dbs: String = "actv01") = {
    val db = new MongoDB(server, port, dbs)
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
        //    1) have at least all of C in them
        //    2) no atoms outside of `AllAtoms`
        //    3) is not a salt
        //    4)  a) inchi can be loaded, and mass calculated by `MassCalculator`
        //        b) mass < 200Da (most of our standards were <200Da. Can go higher but solving takes long then!)
        // below we construct booleans for each of these four tests.

        // Condition "1)"
        val allMainAtomsPresent = List(C).forall(formulaMap.contains)

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

    testformulae.toSet
  }

  def testDBChemsStableFormula(chems: Set[(Double, List[Atom], Map[Atom, Int], String)]) {
    val stableCnstr = new StableChemicalFormulae
    for ((mass, elems, formula, inchi) <- chems) {
      val passes = stableCnstr.isValid(formula)
      if (!passes) reportFail(s"FAIL: $formula, $inchi")
    }
  }

  def solveFormulaeUsingSolver(chems: Set[(Double, List[Atom], Map[Atom, Int], String)]) {
    chems foreach solveNCheck
  }
}

// Specials allow us to add specific constraints (e.g. stable chemicals, valency constraints) to the search
// Each set comes with a constraints and a check method, for respective use in the solver / the enumeration.
sealed trait Specials {
  type ChemicalFormula = Map[Atom, Int]
  def constraints(): BooleanExpr
  def describe(): String
  def check(f: ChemicalFormula): Boolean
  final def isValid(f: ChemicalFormula): Boolean = {
    val fillOut = AllAtoms.filterNot(f contains _).map(a => a -> 0).toMap
    check(f ++ fillOut)
  }

  // Handy shortcuts to construct expressions (e) or terms (t) from Atoms and coefficients
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

class ValencyConstraints extends Specials {
  def describe() = "Valence constraints"

  // there cannot be more than (valence * others) - count(others)
  // because there are at max valence*other bonds to make, and max available for H
  // are in cases where all others are in a single line, e.g., alcohols

  def constraints() = {
    val ts: List[Term] = AllAtoms.map(a => t(a.maxValency, a))
    val lineBonds: Term = t(-AllAtoms.size)
    // The following linear expression allows us enforce a valency constraint:
    // "Number of H in formula should be <= sum of valency of other atoms - count of other atoms"
    LinExpr(ts.::(lineBonds)) >= t(H)
  }
  def check(f: ChemicalFormula) = {
    val others = AllAtoms.filter(a => f.contains(a))
    val ts = others.map(a => a.maxValency * f(a)).sum - others.size
    ts >= f(H)
  }
}

class AtLeastMinFormula(minFormulaMap: Map[Atom, Int]) extends Specials {
  def describe() = minFormulaMap.map(element => element._1.symbol + " >= " + element._2.toString).reduce(_ + " + " + _)

  def moreThanMin(a: Atom) = {
    e(a) >= e(minFormulaMap.getOrElse(a, 0))
  }

  def constraints() = Multi(And, AllAtoms.map(a => moreThanMin(a)))
  def check(f: ChemicalFormula) = {
    AllAtoms.forall(a => f.getOrElse(a, 0) >= minFormulaMap.getOrElse(a, 0))
  }
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
    // For now we just ensure 0 <= H <= hMax + N (coz the N can have an extra charge)
    // **************** Constraint c3 *******************
    // If `ph` represents the number of phosphate groups in the molecule:
    // There must be at least one O already present.
    // The total number of O's present must be greater than 3*P.
    // **************** Constraint c4 *******************
    // int halogensAndHydrogens = H + Cl + Br + I + F //For the final formula
    // halogensAndHydrogens <= Hmax + N (since the N can have a + on it)
    // if (N <= 0) halogensAndHydrogens %2 == Hmax % 2 (since any single N's can break mod)
  //

  def constraints() = {
    // constraint c1 above
    val cBonds = e(2, C) + e(2)
    val numHetero = e(N) + e(S) + e(O)
    val c1 = numHetero <= cBonds

    // constraint c2 above
    val negNumHetero = e(-1, N) + e(-1, S) + e(-1, O)
    val hMax = e(2, N) + e(S) + e(O) + cBonds + negNumHetero
    val c2 = e(H) >= e(0) and e(H) <= hMax + e(N)

    // constraint c3 above
    val c3 = e(P) < e(5) and (e(P) > e(0) implies e(O) > e(3, P))

    // constraint c4 above
    val halogensAndH = e(H) + e(Cl) + e(Br) + e(I) + e(F)
    val mod2Equals = (halogensAndH mod e(2)) == (hMax mod e(2))
    val c4part1 = halogensAndH <= hMax + e(N)
    val c4part2 = e(N) > e(0) or mod2Equals
    val c4 = c4part1 and c4part2

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
    val c2 = f(H) >= 0 && f(H) <= hMax + f(N)

    // constraint c3 above
    val c3 = f(P) < 5 && ( !(f(P) > 0) || (f(O) > 3 * f(P)))

    // constraint c4 above
    val halogensAndH = f(H) + f(Cl) + f(Br) + f(I) + f(F)
    val c4part1 = halogensAndH <= hMax + f(N)
    val c4part2 = f(N) > 0 || halogensAndH % 2 == hMax % 2
    val c4 = c4part1 && c4part2

    val constraint = c1 && c2 && c3 && c4
    constraint
  }
}
