package com.act

import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, Cl, Br, F, I, MonoIsotopicMass}
import act.shared.MassToFormula
import act.shared.ChemicalSymbols.Helpers.computeMassFromAtomicFormula
import act.shared.{CmdLineParser, OptDesc}
import java.io.PrintWriter
import act.shared.StableChemicalFormulae

object EnumChemFormulae {
  val defaultMaxMass = 1000.0 // Da
  val defaultMaxFormula = "C30H100N30O30P30S30Cl5F5I5Br5"
  def toFormula(s: String) = MassToFormula.getFormulaMap(s)
  val defaultMaxElementCounts = toFormula(defaultMaxFormula)

  def main(args: Array[String]) {
    val opts = List(optOutFile, optUptoFormula, optExcludeHalogens, optMaxMass)
    val className = this.getClass.getCanonicalName
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }

    def removeHalogens(f: Map[Atom, Int]) = f + (Cl->0, Br->0, I->0, F->0)
    val maxCountsAll = toFormula(if (cmdLine has optUptoFormula) cmdLine.get(optUptoFormula) else defaultMaxFormula)
    val maxCounts = if (cmdLine has optExcludeHalogens) removeHalogens(maxCountsAll) else maxCountsAll
    val maxMass = new MonoIsotopicMass(if (cmdLine has optMaxMass) cmdLine.get(optMaxMass).toDouble else defaultMaxMass)

    val doer = new EnumChemFormulae(maxCounts, maxMass)
    doer.enumerate(out)
  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outjson",
                    name = "filename",
                    desc = "Output json of peaks, mz, rt, masses, formulae etc.",
                    isReqd = false, hasArg = true)

  val optUptoFormula = new OptDesc(
                    param = "f",
                    longParam = "max-elem-counts",
                    name = "formula",
                    desc = s"Max atoms to enumerate, defaults to $defaultMaxFormula",
                    isReqd = false, hasArg = true)

  val optMaxMass = new OptDesc(
                    param = "m",
                    longParam = "max-mass",
                    name = "Da",
                    desc = s"Upper bound on molecule's mass, defaults to $defaultMaxMass",
                    isReqd = false, hasArg = true)

  val optExcludeHalogens = new OptDesc(
                    param = "x",
                    longParam = "exclude-halogens",
                    name = "",
                    desc = "By default CHNOPS+ClBrFI are enumerated over. If flag set, second set is excluded.",
                    isReqd = false, hasArg = false)

}

class EnumChemFormulae(maxElems: Map[Atom, Int] = EnumChemFormulae.defaultMaxElementCounts,
  maxMass: MonoIsotopicMass = new MonoIsotopicMass(EnumChemFormulae.defaultMaxMass)) {
  type ChemicalFormula = Map[Atom, Int]

  // need an instance to be able to build the chemical formula string
  val m2f = new MassToFormula
  // only enumerate stable chemical formulae
  val stableFormulae = new StableChemicalFormulae

  def enumerate(out: PrintWriter) = {
    // print header
    out.println(outformat(None))
    // print enumeration
    for (c <- 1 to maxElems(C);
         h <- 1 to maxElems(H);
         n <- 0 to maxElems(N);
         o <- 0 to maxElems(O);
         p <- 0 to maxElems(P);
         s <- 0 to maxElems(S);
         cl <- 0 to maxElems(Cl);
         f <- 0 to maxElems(F);
         b <- 0 to maxElems(Br);
         i <- 0 to maxElems(I)
       ) {
      val formula: ChemicalFormula = Map(C->c, H->h, N->n, O->o, P->p, S->s, Cl->cl, F->f, Br->b, I->i)
      val isStableChemical = stableFormulae.isValid(formula)
      if (isStableChemical) {
        val mass = computeMassFromAtomicFormula(formula)
        if (MonoIsotopicMass.isLt(mass, maxMass)) {
          val formulaStr = m2f.buildChemFormulaA(formula)
          out.println(outformat(Some((mass, formulaStr))))
        }
      }
    }
    // shall we close the file after running for a 100 years? Maybe, maybe not. :p
    out close
  }

  def outformat(massFormula: Option[(MonoIsotopicMass, String)]): String = {
    val row: List[String] = massFormula match {
      case None => {
        // output header
        List("mass", "formula")
      }
      case Some(mF) => {
        val (mass, formula) = mF
        List(mass.toString(6), formula)
      }
    }
    row.mkString("\t")
  }
}
