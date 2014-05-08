package com.act.reachables

import java.io.PrintWriter
import java.io.File

object reachables {
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Usage: run out_prefix")
      System.exit(-1);
    } 

    val prefix = args(0)
    val g = prefix + ".graph.json"
    val t = prefix + ".trees.json"
    println("Writing disjoint graphs to " + g + " and forest to " + t)

    val act = new LoadAct(true).run() // true = Load with chemicals
    val tree = ActData.ActTree

    val disjointgraphs = tree.disjointGraphs()
    val file1 = new PrintWriter(new File(g))
    file1 write (disjointgraphs)
    file1.close()

    val disjointtrees = tree.disjointTrees()
    val file2 = new PrintWriter(new File(t))
    file2 write (disjointtrees)
    file2.close()

    println("Done")
  }
}
