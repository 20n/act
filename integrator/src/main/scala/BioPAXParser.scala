package com.c20n.act

import org.biopax.paxtools.io.BioPAXIOHandler
import org.biopax.paxtools.io.SimpleIOHandler
import org.biopax.paxtools.model.BioPAXFactory
import org.biopax.paxtools.model.Model
import org.biopax.paxtools.model.level3.Protein
import org.biopax.paxtools.model.level3.Pathway
import java.io.{FileInputStream, File}
// To implicitly convert java collections (returned by paxtools)
// into scala collections that we can iterate over...
import collection.JavaConversions._

object BioPAXParser {
  def main(args: Array[String]) = 
    if (args.length != 1)
      println("No BioPAX file to process")
    else { 
      val f = args(0)
      println("Data file: " + f)
      // import BioPAX from OWL file (auto-detects level)
      val biopaxIO = new SimpleIOHandler
      val model = biopaxIO convertFromOWL (new FileInputStream(f))
      val pathways = model.getObjects(classOf[Pathway])
      println("\n" * 20)
      for (p <- pathways)
        println(p.getName)
    }
}
