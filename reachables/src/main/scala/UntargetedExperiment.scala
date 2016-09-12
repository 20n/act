package com.act.lcms

import java.io.PrintWriter
import act.shared.{CmdLineParser, OptDesc}

class LCMSExperiment(val name: String, val peaks: UntargetedPeakSpectra)

class UntargetedExperiment(val controls: Set[LCMSExperiment], val hypotheses: Set[LCMSExperiment]) {
}

object UntargetedExperiment {
  def main(args: Array[String]) {
    val className = this.getClass.getCanonicalName
    val opts = List(optOutFile, optControls, optHypotheses, optRunTests)
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    // read the command line options
    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }
  }

  val optControls = new OptDesc(
                    param = "c",
                    longParam = "controls",
                    name = "filenames",
                    desc = """Experimental controls""".stripMargin,
                    isReqd = false, hasArgs = true)

  val optHypotheses = new OptDesc(
                    param = "e",
                    longParam = "experiments",
                    name = "filenames",
                    desc = """Experiments""".stripMargin,
                    isReqd = false, hasArgs = true)

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outfile",
                    name = "filename",
                    desc = "The file to which ...",
                    isReqd = true, hasArg = true)

  val optRunTests = new OptDesc(
                    param = "t",
                    longParam = "run-tests",
                    name = "run regression tests",
                    desc = """Run regression tests.""",
                    isReqd = false, hasArg = false)

}
