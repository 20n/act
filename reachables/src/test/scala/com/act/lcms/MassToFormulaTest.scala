package com.act.lcms

import act.shared.ChemicalSymbols._
import org.scalatest.{FlatSpec, Matchers}


class MassToFormulaTest extends FlatSpec with Matchers {

  ignore should "be able to solve integral solutions - uses z3 solver" in {
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

    def testOneSolnOverCNO(constraints: List[BooleanExpr], intMz: Int, expected: Set[Map[Var, Int]], f: MassToFormula) {

      if (expected.isEmpty) {
        val sat = Solver.solveOne(constraints)
        "MassToFormula" should s"find no formulae over CNO with approx mass ${intMz}" in {
          sat shouldBe empty
        }
      } else {
        val sat = Solver.solveOne(constraints)
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
  }

  "MassToFormula" should s"correctly parse formulae" in {
    val testCases = Map(
      "C20BrCl2" -> Map(C->20, Br->1, Cl->2),
      "BrCl2" -> Map(Br->1, Cl->2),
      "BrC2" -> Map(Br->1, C->2),
      "CCl" -> Map(C->1, Cl->1),
      "ClC" -> Map(C->1, Cl->1),
      "IFClH20CBrN1OP2" -> Map(C->1, H->20, N->1, O->1, P->2, Cl->1, Br->1, I->1, F ->1)
    )
    testCases.foreach(formula => MassToFormula.getFormulaMap(formula._1) should be equals formula._2)
  }

  "MassToFormula" should "identify the given edge cases as valid molecules" in {
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
    val stableCnstr = new StableChemicalFormulae

    val parsed = edgeCases.map(mol => MassToFormula.formulaFromInChI(mol._2._2))
    parsed.foreach(p => p.isDefined should be(true))
    parsed.foreach(p => stableCnstr.isValid(p.get._2) should be(true))
  }

  ignore should "be able to solve acetaminophen - uses z3 Solver" in {
    val apapSolnLimitedAtoms: Map[Atom, Int] = MassToFormula.getFormulaMap("C8H9NO2", fillToAllAtom = false)
    val apapSoln: Map[Atom, Int] = MassToFormula.getFormulaMap("C8H9NO2", fillToAllAtom = true)
    val inchi = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)"
    val apapCases = Set[(Double, List[Atom], Map[Atom, Int], String)](
      // the formula has to be specified to at least a certain # digits of precision
      // the number of digits of precision required is specified under MonoIsotopicMass
      // One case that is well used is 3 digits of precision. If you specify less,
      // e.g., 151.06 instead of 151.063 for apap, a valid solution will not be found
      (151.063324, apapSolnLimitedAtoms.keys.toList, apapSolnLimitedAtoms, inchi),
      (151.063,    apapSolnLimitedAtoms.keys.toList, apapSolnLimitedAtoms, inchi),
      (151.063324, AllAtoms, apapSoln, inchi),
      (151.063,    AllAtoms, apapSoln, inchi)
    )

    apapCases foreach MassToFormula.solveNCheck
  }
  
  "MassToFormula" should "correctly check min formula constraints" in {

    // Test cases are constructed in the following way
    // min formula string -> Map(test formula string -> check result)
    // For instance, C5H10O2N2 should pass the check with min formula C4H10O2N2, but C4H10O1N2 should not
    val testCases = Map(
      "C4H10O2N2" -> Map(
        "C5H10O2N2" -> true,
        "C4H100O2N2" -> true,
        "C4H10O2N2BrCl" -> true,
        "C4H10O2N2" -> true,
        "C4H10O1N2" -> false,
        "C3H10O2N2" -> false),
      "CH3ClBr" -> Map(
        "CH3ClBr" -> true,
        "CH4ClBr" -> true,
        "CH3" -> false,
        "CH3Br" -> false
      )
    )

    testCases foreach {
      case (minFormulaString, testMap) =>
        val minFormula = MassToFormula.getFormulaMap(minFormulaString)
        val minFormulaConstraint = new AtLeastMinFormula(minFormula)
        testMap foreach {
          case testFormula =>
            withClue(s"Checking test case ${testFormula._1} with min formula ${minFormulaString}:") {
              minFormulaConstraint.check(MassToFormula.getFormulaMap(testFormula._1)) should be(testFormula._2)
            }
        }
    }
  }

}
