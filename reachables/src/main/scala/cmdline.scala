package com.act.reachables

class CmdLine(args: Array[String]) {
  val pairs = args.toList.map(split)
  val map = pairs.toMap

  def split(arg: String) = {
    val sploc = arg indexOf '='
    if (arg.startsWith("--")) {
      if (sploc != -1) {
        val spl = arg splitAt sploc
        (spl._1 drop 2, spl._2 drop 1) // remove the "--" from _1 and "=" from _2
      } else {
        (arg drop 2, true)
      }
    } else {
      ("nokey", arg)
    }
  }

  def get(key: String) = {
    map get key
  }
}
