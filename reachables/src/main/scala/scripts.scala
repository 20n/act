package com.act.scripts

import scala.io.Source

object readwiki {
  // this needs to imperative code (so vars!!) coz we are really 
  // concerned about efficiency of processing a 45GB text file!
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage sbt \"run /absolute/path/to/enwiki-20YYMMDD-pages-articles.xml\"");
      System.exit(-1)
    }
    var last_title = ""
    val tstart = "<title>".length
    for (line <- Source.fromFile(args(0)).getLines()) {
      if (line contains "<title>") {
        last_title = line
      }
      val stdinchi = line indexOf "StdInChI"
      val inchi = line indexOf "InChI"
      val valid_start = line.indexOf("1/") > -1 || line.indexOf("1S/") > -1
      if (valid_start && (stdinchi > -1 || inchi > -1)) {
        val title_s = tstart + last_title.indexOf("<title>")
        val title_e = last_title indexOf "</title>"
        val article = last_title.substring(title_s, title_e)
        val url = "<a href=\"http://en.wikipedia.org/wiki/" + article + "\">" + article + "</a>"
        val title = url + "\t" + (if (stdinchi > -1) "StdInChI" else "")
        val idx = if (stdinchi > -1) (stdinchi + 3) else inchi
        val inc = line.substring(idx).replaceAll(" ", "")
        println(inc + "\t" + title)
      }
    }
    println("Completed. Now run ./truncate_wtabs.sh to canonicalize the inchis before copying them into the front end for search.");
  }
}
