package com.act.scripts

import scala.io.Source
import act.server.SQLInterface.MongoDB
import org.json.JSONArray
import act.installer.patents.FTO_GoogleNonAPISearch
import scala.collection.JavaConverters._
import java.io.IOException

object readwiki {
  // this needs to imperative code (so vars!!) coz we are really 
  // concerned about efficiency of processing a 45GB text file!
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage sbt \"runMain com.act.scripts.readwiki /absolute/path/to/enwiki-20YYMMDD-pages-articles.xml\"");
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

        // only output those that have not explicitly been excluded
        if (!(do_not_install contains inc))
          println(inc + "\t" + title)
      }
    }
    println("Completed. Now run ./truncate_wtabs.sh to canonicalize the inchis before copying them into the front end for search.");
  }

  /* there are some inchis that are just outright crazy. they cause the indigo to crash the JVM
     and not just throw an exception. So we just syntactically eliminate them from consideration
     */
  val do_not_install = Set("InChI=1/C12H10AsCl/c14/h1-10H")
}

object customer_patents {
  def main(args: Array[String]) {
    if (args.length != 2) {
      println("Usage sbt \"runMain com.act.scripts.customer_patents \"L'Oreal\" absolute/path/to/inchi/list.txt\"")
      System.exit(-1)
    }
    val company = args(0)
    val inchifile = args(1)
    val google = new FTO_GoogleNonAPISearch

    System.err.println("Querying patents by company: " + company)
    var map = Map[String, Set[String]]()
    val r = scala.util.Random
    for (inchi <- Source.fromFile(inchifile).getLines) {
      if (!inchi.equals("")) {
        try {
          Thread.sleep(1000 + r.nextInt(5000))
          val patents = google.GetPatentIDsForCompanyPatents(inchi, company).asScala.toSet
          map = map + (inchi -> patents)
          println(inchi + "\t" + map(inchi).mkString(","))
          if (patents nonEmpty) {
            System.err.println(company + "\t has patents on \t" + inchi + "\t" + patents.mkString(","))
          }
        } catch {
          case e: IOException => {
            if (e.getMessage.startsWith("StatusCode = 503") || e.getMessage.startsWith("StatusCode = 403")) {
              System.err.println("Google is blocking us now. Stacktrace below. ABORTING.\n\n")
              System.err.println("Failed on: " + inchi)
              // e.printStackTrace
              System.exit(-1)
            } else {
              System.err.println("Failed lookup: " + inchi)
              map = map + (inchi -> Set())
              println(inchi + "\t" + "")
            }
          }
        }
      } else {
        // print an empty output line for an empty input line
        println()
      }
    }
    // already printed them while retrieving...
    // for (inchi <- Source.fromFile(inchifile).getLines) {
    //   if (inchi.equals(""))
    //     println
    //   else
    //     println(inchi + "\t" + map(inchi).mkString(","))
    // }
  }
}

object create_vendors_table {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage sbt \"runMain com.act.scripts.create_vendors_table absolute/path/to/inchi/list.txt\"")
      System.exit(-1)
    }
    val db = new MongoDB()
    var all_vendors = List[String]()
    var vendor_urls = Map[String, String]()
    var chem_vendors = Map[String, Map[String, String]]()
    for (inchi <- Source.fromFile(args(0)).getLines) {
      if (!inchi.equals("")) {
        val chem = db.getChemicalFromInChI(inchi)
        val vendors:JSONArray = chem.getChemSpiderVendorXrefs
        var chem_vend = Map[String, String]()
        for (i <- 0 to vendors.length - 1) {
          val vendor = vendors getJSONObject i
          val vend_name = vendor getString "ds_name"
          val vend_url = if (vendor has "ds_url") vendor getString "ds_url" else "BLANK"
          val xref = if (vendor has "ext_id") (vendor get "ext_id").toString else "BLANK"
          if (!all_vendors.contains(vend_name)) {
            all_vendors = vend_name :: all_vendors
            vendor_urls = vendor_urls + (vend_name -> vend_url)
          }
          chem_vend = chem_vend + (vend_name -> xref)
          chem_vendors = chem_vendors + (inchi -> chem_vend)
        }
      }
    }
    val sorted_vendors = all_vendors.sorted
    // print the urls of the vendors in order
    print("\t\t")
    for (vendor <- sorted_vendors)
      print(vendor_urls(vendor) + "\t")
    println
    // print the names of the vendors in order
    print("\t\t")
    for (vendor <- sorted_vendors)
      print(vendor + "\t")
    // leave empty line and print col1 header
    println; println("InChI") 
    // now print the actual table
    for (inchi <- Source.fromFile(args(0)).getLines) {
      print(inchi)
      if (!inchi.equals("")) {
        val this_vendors = chem_vendors(inchi)
        print("\t" + this_vendors.size)
        for (vendor <- sorted_vendors) {
          print("\t")
          if (this_vendors contains vendor) {
            print(this_vendors(vendor))
          }
        }
      }
      println
    }
  }
}
