package com.act.reachables

import java.io.PrintWriter
import java.io.File

object reachables {
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Usage: run reachables.json")
      System.exit(-1);
    } 

    val f = args(0)
    println("Output going to " + f)

    val act = new LoadAct(true).run() // true = Load with chemicals
    val tree = ActData.ActTree
    val json = tree.jsonstr()
    println(json)
    val file = new PrintWriter(new File(f))
    file write (json)
    file.close()
  }
}
