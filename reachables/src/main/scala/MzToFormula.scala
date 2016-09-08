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

sealed trait CompareOp
case object Lt extends CompareOp
case object Gt extends CompareOp
case object Ge extends CompareOp
case object Le extends CompareOp
case object Eq extends CompareOp

sealed trait Inequality
case class LinIneq(val lhs: Expr, val ineq: CompareOp, val rhs: Expr) extends Inequality

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

  type BvVars = (BitVecExpr, Map[FuncDecl, Var])
  type BoolVars = (BoolExpr, Map[FuncDecl, Var]) 

  def mkExpr(e: Expr): BvVars = e match {
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
      val fn: (BvVars, BvVars) => BvVars = { 
        case ((bv1, vars1), (bv2, vars2)) => (ctx.mkBVAdd(bv1, bv2), vars1 ++ vars2) 
      }
      val terms = ts.map(mkExpr)
      terms.reduce(fn)
    }
  }

  def mkIneq(eq: LinIneq): BoolVars = {
    val compareFn = eq.ineq match {
      case Lt => ctx.mkBVSLT _
      case Gt => ctx.mkBVSGT _
      case Ge => ctx.mkBVSGE _
      case Le => ctx.mkBVSLE _
      case Eq => ctx.mkEq _
    }
    val (lhs, varsLhs) = mkExpr(eq.lhs)
    val (rhs, varsRhs) = mkExpr(eq.rhs)
    (compareFn(lhs, rhs), varsLhs ++ varsRhs)
  }

  def solveOne(eq: LinIneq, bounds: List[LinIneq]): Option[Map[Var, Int]] = {
    
    val (ctr, varsInEq) = mkIneq(eq)
    val (bnds, varsBnds) = bounds.map(mkIneq).unzip

    val varsInBounds = varsBnds.reduce(_++_)
    val vars: Map[FuncDecl, Var] = varsInEq ++ varsInBounds

    // val boundConstraints = Set(b1, b2, b3)
    val constraints = Set(ctr) ++ bnds.toSet

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

  def solved(e: com.microsoft.z3.Expr, m: Model): Int = {
    // BitVecNum extends BitVecExpr, which extends Expr
    // is there a better way to get to BitVecNum, than doing a cast to subclass?
    // https://github.com/Z3Prover/z3/blob/master/src/api/java/BitVecNum.java
    val bv: BitVecExpr = e.asInstanceOf[BitVecExpr]
    val num: BitVecNum = bv.asInstanceOf[BitVecNum]
    println(s"bv: $bv and num: $num")
    num.getInt
  }
}
  
class MzToFormula(numDigitsOfPrecision: Int = 5, formulaOver: Set[Atom] = Set(C,H,O,N,S,P)) {
  type ChemicalFormula = Map[Atom, Int]

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
}

object MzToFormula {

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List()
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    testOneSolnOverCNO(103, Some((5, 2, 1)))
  }

  def testOneSolnOverCNO(approxMass: Int, expected: Option[Tuple3[Int, Int, Int]]) {

    /* construct 12c + 15o + 14n == $approxMass */
    val (c, n, o) = (Var("c"), Var("n"), Var("o"))
    val lhse = LinExpr(Set(Term(Const(12), c), Term(Const(15), o), Term(Const(14), n)))
    val rhse = Const(approxMass)
    val ineq = LinIneq(lhse, Eq, rhse)

    val bounds = List(
      LinIneq(c, Lt, Const(approxMass)),
      LinIneq(n, Lt, Const(approxMass)),
      LinIneq(o, Lt, Const(approxMass))
    )

    val sat = Solver.solveOne(ineq, bounds)

    (sat, expected) match {
      case (Some(soln), Some(exp)) => {
        println(s"solution = $soln")
        println(s"C${soln(c)}O${soln(o)}N${soln(n)} has mass approx ${approxMass}")
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

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "output-file",
                    name = "output TSV file",
                    desc = "The output file for the computed values! Magic!",
                    isReqd = false, hasArg = false)

}
