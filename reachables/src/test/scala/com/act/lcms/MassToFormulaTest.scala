package com.act.lcms

import act.shared.ChemicalSymbols.{Atom, C, N, O}
import org.scalatest.{FlatSpec, Matchers}


class MassToFormulaTest extends FlatSpec with Matchers {
  // WARNING: these tests might fail on a Mac, with the following error:
  // "no libz3java in java.library.path"

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

  testcases.foreach{
    test => {
      val (intMz, elems, validAtomicFormulae) = test
      val formulator = new MassToFormula(atomSpace = elems) // Hah! The "formulator"
      val constraints = formulator.buildConstraintOverInts(intMz)
      val validSolns = validAtomicFormulae.map(_.map(kv => (MassToFormula.atomToVar(kv._1), kv._2)))
      testOneSolnOverCNO(constraints, intMz, validSolns, formulator)
      testAllSolnOverCNO(constraints, intMz, validSolns, formulator)
    }
  }

  def testOneSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MassToFormula) {

    val vars = constraints.flatMap(Solver.getVars)
    val sat = Solver.solveOne(constraints)


    if (expected.isEmpty) {
      "MassToFormula" should s"find no formulae over CNO with approx mass ${intMz}" in {
        sat shouldBe empty
      }
    } else {
      "MassToFormula" should s"find solutions as expected over CNO with approx mass ${intMz}" in {
        val soln = sat.get
        expected should contain (soln)
      }
    }
  }

  def testAllSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MassToFormula) {

    val vars = constraints.flatMap(Solver.getVars)
    val sat = Solver.solveMany(constraints)

    val descs = sat.map{ soln => s"${f.buildChemFormulaV(soln)}" }

    "MassToFormula" should s"enumerate the correct set of formulae for ~${intMz}: $descs" in {
      sat should be equals expected
    }
  }
}
