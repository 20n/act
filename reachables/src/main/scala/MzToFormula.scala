package act.shared

import act.shared.{CmdLineParser, OptDesc}
import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, AminoAcid, AllAminoAcids}
import act.shared.ChemicalSymbols.{Gly, Ala, Pro, Val, Cys, Ile, Leu, Met, Phe, Ser} 
import act.shared.ChemicalSymbols.{Thr, Tyr, Asp, Glu, Lys, Trp, Asn, Gln, His, Arg}
import act.shared.ChemicalSymbols.Helpers.{fromSymbol, computeMassFromAtomicFormula, computeFormulaFromElements}

// SMT solver
import com.microsoft.z3._
import collection.JavaConversions._

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

  // solver = z3 MIT license.
  // git clone git@github.com:Z3Prover/z3.git
  // compile instructions: https://github.com/Z3Prover/z3
  // the jar needs to be in the lib: `com.microsoft.z3.jar`
  // and the dynamic runtime link libraries in lib/native/${os}/
  println(System.getProperty("java.library.path"))
  System.loadLibrary("z3")
  System.loadLibrary("z3java")

  val config = Map("model" -> "true")
  val ctx: Context = new Context(config)

  def newSolver(): Solver = ctx mkSolver

  def testZ3(): Option[Model] = {

    /* construct 12c + 15o + 14n == 103 */
    val bvSz = 32
    val bv_type: Sort = ctx mkBitVecSort bvSz
    def makeConst(number: String): BitVecExpr = ctx.mkNumeral(number, bv_type).asInstanceOf[BitVecNum]
    val c: BitVecExpr = ctx.mkBVConst("c", bvSz)
    val o: BitVecExpr = ctx.mkBVConst("o", bvSz)
    val n: BitVecExpr = ctx.mkBVConst("n", bvSz)
    val e12c: BitVecExpr = ctx.mkBVMul(makeConst("12"), c)
    val e15o: BitVecExpr = ctx.mkBVMul(makeConst("15"), o)
    val e14n: BitVecExpr = ctx.mkBVMul(makeConst("14"), n)
    val lhs: BitVecExpr = ctx.mkBVAdd(e12c, ctx.mkBVAdd(e15o, e14n))
    val rhs: BitVecExpr = makeConst("103")
    val ctr: BoolExpr = ctx.mkEq(lhs, rhs)

    val b1 = ctx.mkBVSLE(c, rhs)
    val b2 = ctx.mkBVSLE(o, rhs)
    val b3 = ctx.mkBVSLE(n, rhs)

    val constraints = Set(ctr, b1, b2, b3)

    val m: Option[Model] = {
      val solver = newSolver
      constraints.foreach(x => solver.add(x))
      if ((solver check) == Status.SATISFIABLE)
        Some(solver getModel)
      else
        None
    }

    m match {
      case Some(model) => {
        val cval = solved(c, model)
        val oval = solved(o, model) 
        val nval = solved(n, model)
        println(s"C${cval}O${oval}N${nval} has mass approx 103")
        assert ((cval, nval, oval) == (5, 2, 1))
      }
      case None => assert (false)
    }
    m
  }

  def solved(bv: BitVecExpr, m: Model): Int = {
    val interpretation: Expr = m.getConstInterp(bv)
    // BitVecNum extends BitVecExpr, which extends Expr
    // is there a better way to get to BitVecNum, than doing a cast to subclass?
    // https://github.com/Z3Prover/z3/blob/master/src/api/java/BitVecNum.java
    val num: BitVecNum = interpretation.asInstanceOf[BitVecNum]
    num.getInt
  }
}
  
object MzToFormula {

  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List()
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    new MzToFormula().testZ3
  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "output-file",
                    name = "output TSV file",
                    desc = "The output file for the computed values! Magic!",
                    isReqd = false, hasArg = false)

}
