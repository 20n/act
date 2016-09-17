package act.shared

import act.shared.{CmdLineParser, OptDesc}
import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, AminoAcid, AllAminoAcids}
import act.shared.ChemicalSymbols.{Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser} 
import act.shared.ChemicalSymbols.{Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg}
import act.shared.ChemicalSymbols.Helpers.{fromSymbol, computeMassFromAtomicFormula, computeFormulaFromElements}

// SMT solver
import com.microsoft.z3._
import collection.JavaConversions._

//
// We might want to move these ADTs to a file of their own.
//

sealed trait Expr
case class Const(c: Int) extends Expr
case class Var(val id: String) extends Expr
case class Term(val c: Const, val v: Var) extends Expr
case class LinExpr(val terms: Set[Term]) extends Expr

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
  val bvSz = 32
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
        val kvs = for (v <- model.getDecls) yield {
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
        // println(blockingClause)
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
    // println(s"num = $num")
    num.getInt
  }
}
  
class MzToFormula(numDigitsOfPrecision: Int = 5, elements: Set[Atom] = Set(C,H,O,N,S,P)) {
  type ChemicalFormula = Map[Atom, Int]
  val intMassesForAtoms = elements.map(a => { 
      val integralMass = (math round a.monoIsotopicMass).toInt
      (a, integralMass) 
    }).toMap
  val varsForAtoms = elements.map(a => {
      val variable = Var(a.symbol.toString)
      (a, variable)
    }).toMap

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

  def formulaeForMz(mz: Double): Set[ChemicalFormula] = {
    Set()
  }

  def buildConstraintOverInts(closeMz: Int) = {
    val linExprTerms: Set[Term] = elements.map(a => {
        val coefficient = Const(intMassesForAtoms(a))
        val variable = varsForAtoms(a)
        Term(coefficient, variable)
      }
    )
    val lhse = LinExpr(linExprTerms)
    val rhse = Const(closeMz)
    val ineq = LinIneq(lhse, Eq, rhse)

    // instantiate upper and lower bounds on each variable
    // sat solvers, when asked to enumerate will absolutely find all solutions
    // which means if we search over the domain of 32 length bitvectors (bv32)
    // then the two complement representation might allow for values that are 
    // negative. So bounds help in ensuring we are looking in the right places.
    val boundLists = elements.map(a => {
        val min = 0
        // TODO: check performance of the sat solving with these intricate bounds
        // This precise a bound may be making life difficult for the solver.
        // Alternatives are: 
        //  #1) `closeMz`
        //  #2) lowest x, such that 2^x > closeMz
        // `#2` is a bit vector with only a single highest significant bit set
        // that translates to a very easy comparison boolean circuit.
        val max = (math ceil (closeMz.toDouble / a.monoIsotopicMass)).toInt
        val lowerBound = LinIneq(varsForAtoms(a), Ge, Const(0))
        val upperBound = LinIneq(varsForAtoms(a), Le, Const(max))
        (lowerBound, upperBound)
      }
    ).toList.unzip

    // combine all bounds
    val bounds = boundLists match {
      case (la, lb) => la ++ lb 
    }

    ineq :: bounds
  }
}

object MzToFormula {

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List()
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    val formulator = new MzToFormula // Hah! The "formulator"
    testOneSolnOverCNO(103, Some((5, 2, 1)))
    testAllSolnOverCNO(103, Set((5, 2, 1), (0, 2, 5)))
  }

  def getConstraintsOverCNO(c: Var, n: Var, o: Var, approxMass: Int) = {
    /* construct 12c + 15o + 14n == $approxMass */
    val lhse = LinExpr(Set(Term(Const(12), c), Term(Const(15), o), Term(Const(14), n)))
    val rhse = Const(approxMass)
    val ineq = LinIneq(lhse, Eq, rhse)

    val bounds = List(
      LinIneq(c, Ge, Const(0)),
      LinIneq(c, Lt, Const(approxMass)),
      LinIneq(n, Ge, Const(0)),
      LinIneq(n, Lt, Const(approxMass)),
      LinIneq(o, Ge, Const(0)),
      LinIneq(o, Lt, Const(approxMass))
    )

    ineq :: bounds
  }

  def testOneSolnOverCNO(approxMass: Int, expected: Option[(Int, Int, Int)]) {

    val (c, n, o) = (Var("c"), Var("n"), Var("o"))
    val constraints = getConstraintsOverCNO(c, n, o, approxMass)
    val sat = Solver.solveOne(constraints)

    (sat, expected) match {
      case (Some(soln), Some(exp)) => {
        println(s"Test find one: C${soln(c)}O${soln(o)}N${soln(n)} found with mass ~${approxMass}")
        assert( (soln(c), soln(n), soln(o)) == exp )
      }
      case (None, None) => {
        println(s"No formula over CNO has approx mass ${approxMass}. This is expected.")
      }
      case _ => {
        // unexpected outcome from the sat solver
        assert (false)
      }
    }

  }

  def testAllSolnOverCNO(approxMass: Int, expected: Set[(Int, Int, Int)]) {

    val (c, n, o) = (Var("c"), Var("n"), Var("o"))
    val constraints = getConstraintsOverCNO(c, n, o, approxMass)
    val sat = Solver.solveMany(constraints)

    val solns = for (soln <- sat) yield (soln(c), soln(n), soln(o))
    val descs = sat.map{ soln => s"C${soln(c)}O${soln(o)}N${soln(n)}" }
    println(s"Test enumerate all: Found ${descs.size} formula with mass ~${approxMass}: $descs")

    assert(solns equals expected)

  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "output-file",
                    name = "output TSV file",
                    desc = "The output file for the computed values! Magic!",
                    isReqd = false, hasArg = false)

}
