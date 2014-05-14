import sbt._
import Keys._
import java.lang.Runtime

import complete.DefaultParsers._

object ActSpecificCommand extends Build
{
    // Declare a single project, adding several new commands, which are discussed below.
    lazy override val projects = Seq(root)
    lazy val root = Project("root", file(".")) settings(
        commands ++= Seq(cleanAll, canYouBuild, canYouArgs)
    )

    def f1(p: String) = "chemicals.tsv"
    def f2(p: String) = p + ".graph.json"
    def f3(p: String) = p + ".trees.json"
    def f4(p: String) = p + "-data"
    val generated_files = List( f1 _, f2 _, f3 _, f4 _ )
    def cleanAll = Command.args("cleanAll", "prefix") { (state, args) =>
      if (args.length != 1) {
        println("Need single argument of prefix used with run")
      } else {
        val prefix = args(0)
        println("Really clear all run data?")
        println("The following files will be obliterated: ")
        val fs = generated_files map ( fn => fn(prefix) )
        for (f <- fs) { println(get_sz(f)) }
        val y = Console.readLine
        if (y == "y" or y == "Y") {
          for (f <- fs) { remove(f) }
        }
      }
      state
    }

    def remove(f: String) {
      val cmd = "rm -rf " + f
      val p = Runtime.getRuntime().exec(cmd)
      p.waitFor()
    }

    def get_sz(f: String) = {
      val cmd = "du -sh " + f + " | cut -f 1 -d' '"
      val p = Runtime.getRuntime().exec(cmd)
      p.waitFor()
      scala.io.Source.fromInputStream(p.getInputStream).getLines.mkString("; ")
    }

    // A simple, no-argument command that prints "Hi",
    //  leaving the current state unchanged.
    def canYouBuild = Command.command("canYouBuild") { state =>
        println("Yes, I can build!")
        state
    }

    // A simple, multiple-argument command that prints "Hi" followed by the arguments.
    //   Again, it leaves the current state unchanged.
    def canYouArgs = Command.args("canYouArgs", "<arglist>") { (state, args) =>
        println("I can take multiple args: " + args.toString())
        state
    }
}
