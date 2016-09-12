package com.act.lcms

import java.io.PrintWriter

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

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "output",
                    name = "filename",
                    desc = """Output file with ....
                             |Each line is tab separated list of ....""".stripMargin,
                    isReqd = false, hasArg = true)

  val optRunTests = new OptDesc(
                    param = "t",
                    longParam = "run-tests",
                    name = "run regression tests",
                    desc = """Run regression tests. This will take some time.""",
                    isReqd = false, hasArg = false)

}
