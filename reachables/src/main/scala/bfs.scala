package com.c20n.act

object bfs {
  def main(args: Array[String]) = 
    if (args.length != 2)
      println("Usage: bfs mongohost mongoport reachables.json")
    else { 
      val host = args(0)
      val port = args(1)
      val f = args(2)
      println("Output going to " + f)
    }
}
