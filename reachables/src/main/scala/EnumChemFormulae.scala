package com.act

import act.shared.ChemicalSymbols.{Atom, C, H, N, O, P, S, Cl, Br, F, I, MonoIsotopicMass}
import act.shared.MassToFormula
import act.shared.ChemicalSymbols.Helpers.computeMassFromAtomicFormula
import act.shared.{CmdLineParser, OptDesc}
import java.io.PrintWriter
import act.shared.StableChemicalFormulae

class EnumChemFormulae(maxElemCounts: String = "C30H100N30O30P30S30Cl5F5I5Br5", maxMass: MonoIsotopicMass = new MonoIsotopicMass(1000.00)) {
  type ChemicalFormula = Map[Atom, Int]
  def toFormula(s: String): ChemicalFormula = MassToFormula.getFormulaMap(s)
  // need an instance to be able to build the chemical formula string
  val m2f = new MassToFormula

  val max = toFormula(maxElemCounts)

  def enumerate(out: PrintWriter) = {
    // only enumerate stable chemical formulae
    val stableFormulae = new StableChemicalFormulae
    // print header
    out.println(outformat(None))
    // print enumeration
    for (c <- 1 to max(C);
         h <- 1 to max(H);
         n <- 0 to max(N);
         o <- 0 to max(O);
         p <- 0 to max(P);
         s <- 0 to max(S);
         cl <- 0 to max(Cl);
         f <- 0 to max(F);
         b <- 0 to max(Br);
         i <- 0 to max(I)
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

object EnumChemFormulae {

  def main(args: Array[String]) {
    val opts = List(optOutFile, optUptoFormula)
    val className = this.getClass.getCanonicalName
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }

    val doer = if (cmdLine has optUptoFormula) {
      new EnumChemFormulae(cmdLine get optUptoFormula)
    } else {
      new EnumChemFormulae
    }

    doer.enumerate(out)
  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outjson",
                    name = "filename",
                    desc = "Output json of peaks, mz, rt, masses, formulae etc.",
                    isReqd = false, hasArg = true)

  val optUptoFormula = new OptDesc(
                    param = "m",
                    longParam = "max-elem-counts",
                    name = "formula",
                    desc = "Max atoms to enumerate, defaults to C30H100N20O20P20S20",
                    isReqd = false, hasArg = true)

}
