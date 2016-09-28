package com.act.reachables

import java.lang.Runtime
import act.server.MongoDB
import collection.JavaConversions._ // for automatically converting to scala collections

/* This is the scala version of what we originally had as a bunch of scripts for
 * setting up the NoSQL DB with public data (brenda, kegg, and associated chem
 * information. These scripts used to be housed under src/Act/Installer/_.sh
 * For each of the relevant ones we now have functions in scala below.
 *
 * install-all.sh: 
 */
object initdb {

  /* default configuration parameters */

  // hardcode the port and host, as only under exceptional circumstance is the
  // data supposed to be installed on non-local machines.
  var port = "27017"
  var host = "localhost"
  var dbs = "actv01"

  // the reference mongodb is running on this port?
  var default_refport = "27018" 
  // also chemicals or some other valid collection
  var default_collection = "actfamilies" 
  // also InChIKey for chemicals for instance
  var default_indexfield = "_id" 

  // location where KEGG data files can be found
  var kegg_loc = "data/kegg"

  // location where METACYC data files can be found
  // var metacyc_loc="data/biocyc-flatfiles" // the full set exists here
  var metacyc_loc = "metacyc"

  // location of SwissProt (the "reviewed" part of UniProt) data files
  var swissprot_loc = "data/swissprot"

  // location of priority chemicals, e.g., reachables, 
  // that we pull vendors for first, before the rest of the db
  var reachables_file = "data/chemspider_vendors_reachables.txt"

  // location of vendors file (cached data retrieved from ChemSpider) 
  var chem_vendors_file = "data/chemspider_vendors.txt"

  // location of patents file (cached data retrieved from Google Patents) 
  var chem_patents_file = "data/chemspider_patents.txt"

  // location of inchi list for which to install Bing Search Results
  var inchis_for_bingsearch_file = "data/bing/chemicals_list_for_bing_xref"

  // in the brenda data what is the max rxnid we expect to see
  var maxBrendaRxnsExpected = "60000"

  // install with or without whitelist: only set to true while debugging
  var installOnlyWhitelistRxns = false

  /* end: default configuration parameters */

  def main(args: Array[String]) {
    printhelp()

    if (args.length == 0) {
      println("You asked for an install_all!")
      println("This will overwrite your db at " + host + ":" + port + "/" + dbs)
      println("*" * 70)
      println("If you go create a new DB, you can run checkmongod ")
      println("against a reference DB after this installation finishes")
      println("You would mongod --dbpath refdb --port 27018")
      println("And then sbt \"run checkmongod actfamilies 27018\" 2>dbcmp.log")
      println("*" * 70)
      println("Press enter if you really want to create a new db on 27017?")
      readLine
      install_all(new Array[String](0))
    } else {
      val cmd = args(0)
      val cargs = args.drop(1)
      println("Will run " + cmd + " w/ args: "+cargs.mkString("(", ", ", ")")) 
      // println("Enter to continue:"); readLine

      if (cmd == "install")
        install_all(cargs)
      else if (cmd == "checkmongod")
        checkmongod(cargs)
      else if (cmd == "metacyc")
        installer_metacyc(cargs)
      else if (cmd == "kegg")
        installer_kegg()
      else if (cmd == "swissprot")
        installer_swissprot()
      else if (cmd == "map_seq")
        installer_map_seq()
      else if (cmd == "vendors")
        installer_vendors()
      else if (cmd == "patents")
        installer_patents()
      else if (cmd == "infer_sar")
        installer_infer_sar(cargs)
      else if (cmd == "keywords")
        installer_keywords()
      else if (cmd == "chebi")
        installer_chebi_applications()
      else if (cmd == "bingsearch")
        installer_search_results()
      else 
        println("Unrecognized init module: " + cmd) ;
    }
  }

  def printhelp() {
    def hr() = println("*" * 80)
    val maxmem = Runtime.getRuntime().maxMemory() / (1000*1000)
    val totmem = Runtime.getRuntime().totalMemory() / (1000*1000)
    hr
    if (maxmem < 7500) {
      println("The JVM is allowed max: " + maxmem + "MB")
      println("Recommended that you run with at least 8GB")
      println("Or else process will likely run OOM hours later.")
      println("Put export SBT_OPTS=\"-Xmx8G\" in your ~/.bash_profile")
      hr
    }
    println("Usage:")
    println("without argument: install_all")
    println("install_all     : installs the entire system, brenda, kegg, metacyc, swissprot included")
    println("install omit_X omit_Y: installs all, but omits some datasets; omit_kegg, omit_metacyc, omit_swissprot, omit_infer_ops valid options")
    println("checkmongod <collection> <ref:port> [<idx_field e.g., _id> [<bool: lists are sets>]]")
    println("infer_ops [<rxnid | rxnid_l-rxnid_h>] : if range omitted then all inferred")
    println("metacyc [range] : installs all data/biocyc-flatfiles/*/biopax-level3.owl files,")
    println("                : range='start-end', the indices are ls order #, you may omit any.")
    hr
  }

  def checkmongod(cargs: Array[String]) {
    def hr() = println("*" * 80)
    def hre() = Console.err.println("*" * 80)
    val db = new MongoDB(host, port.toInt, dbs)
    // val rids = db.getAllReactionUUIDs(); println("rids: " + rids.take(10).mkString("/"))
    // val oids = db.graphByOrganism(4932); println("rids: " + oids.take(10).mkString("/")) // Saccaromyces cerevisiae
    val coll = if (cargs.length >= 1) cargs(0) else default_collection
    val refport = if (cargs.length >= 2) cargs(1) else default_refport
    val idx_field = if (cargs.length >= 3) cargs(2) else default_indexfield
    val unorderedLists = if (cargs.length >= 4) cargs(3).toBoolean else true

    // diff: P[P[List, List], Map[O, O]] of (id_added, id_del), id->updated
    println("Started compare. Please wait.")
    val diff = MongoDB.compare(coll, idx_field, port.toInt, refport.toInt, unorderedLists)
    val add = (diff fst) fst
    val del = (diff fst) snd
    val upd = (diff snd)
    hr
    println("Compare results:")
    println(add.size() + " entries added in " + port + " compared to " + refport)
    println(del.size() + " entries deleted in " + port + " compared to " + refport)
    println(upd.keySet.size() + " entries updated in " + port + " compared to " + refport)
    hr

    println("Do you want to output the full dump to stderr?")
    var yn = readLine

    if (yn == "y" || yn == "Y") {
      hre
      Console.err.println("Added IDs: " + add.mkString(", "))
      hre
      Console.err.println("Deleted IDs: " + del.mkString(", "))
      hre
      Console.err.println("Updated: " + upd.mkString("{\n\n", "\n", "\n\n}"))
    }
    
  }

  def initiate_install(args: Seq[String]) {
    act.installer.Main.main(args.toArray)
  }

  def install_all(cargs: Array[String]) {
    /* Original script source (unused-scripts/install-all.sh)
        if [ $# -ne 2 ]; then
          echo "----> Aborting(install-all.sh). Need <port> <-w-whitelist | -wo-whitelist> as argument!"
          exit -1
        fi
        port=$1
        w_or_wo_whitelist=$2
        
        ./installer.sh $port
        ./installer-kegg.sh $port data/kegg
        ./installer-balance.sh $port // DEPRECATED
        ./installer-energy.sh $port  // DEPRECATED
        ./installer-rarity.sh $port 0 60000 // DEPRECATED
        ./installer-infer-ops.sh $port 0 $w_or_wo_whitelist
    */

    // installs brenda; and the rest of the core system
    installer() 

    // installs kegg, metacyc, swissprot; unless told to omit
    if (!cargs.contains("omit_kegg")) {
      installer_kegg() 
    }

    if (!cargs.contains("omit_metacyc")) {
      // empty array input => all files installed
      installer_metacyc(new Array[String](0)) 
    }

    if (!cargs.contains("omit_swissprot")) {
      installer_swissprot()
      installer_map_seq()
    }

    if (!cargs.contains("omit_vendors")) {
      installer_vendors()
    }

    if (!cargs.contains("omit_patents")) {
      installer_patents()
    }

    if (!cargs.contains("omit_infer_sar")) {
      // pass empty array to infer_sar; to infer sar for all accessions
      installer_infer_sar(new Array[String](0))
    }

    if (!cargs.contains("omit_keywords")) {
      // pick query terms from each doc in collection: put under keywords
      installer_keywords()
    }

    if (!cargs.contains("omit_chebi")) {
      installer_chebi_applications()
    }

    if (!cargs.contains("omit_bing")) {
      installer_search_results()
    }
  }

  def installer() {
    /* Original script source (unused-scripts/installer.sh)
        if [ $# -ne 1 ]; then
        	echo "----> Aborting(installer.sh). Need port as argument!"
        	exit -1
        fi
        port=$1
        host="localhost"
        dbs="actv01"
        
        
        # A) install: BRENDA chemicals cofactors natives synonyms
        #     accumulate important chemicals lists...
        cat data/imp_chemicals_*.txt > data/imp_chems_autogen.txt
        #     do the actual install...
        java -Xmx2g -jar installer.jar BRENDA $port $host $dbs data brenda.txt nodes.dmp names.dmp inchi_PCdata.txt all-InChIs.txt cofactor-inchis.txt cofac-pairs-AAMs.txt ecoliMetabolites cleanup-chemnames-litmining.json imp_chems_autogen.txt 
        #     remove accumulated chemicals lists...
        rm data/imp_chems_autogen.txt
        
        # B) install: sequences
        mongoimport --host $host --port $port --db $dbs --collection sequences --file data/sequences.json

    */
    // because we attempt to use wildcards, which are bash-interpreted, we have to call bash to expand them
    execCmd(List("bash","-c","cat data/imp_chemicals_*.txt > data/imp_chems_autogen.txt"))
    val params = Seq[String]("BRENDA", port, host, dbs, "data", "brenda.txt", "nodes.dmp", "names.dmp", "inchi_PCdata.txt", "all-InChIs.txt", "cofactor-inchis.txt", "cofac-pairs-AAMs.txt", "ecoliMetabolites", "cleanup-chemnames-litmining.json", "imp_chems_autogen.txt")
    initiate_install(params)
    execCmd(List("rm", "data/imp_chems_autogen.txt"))
    execCmd(List("mongoimport", "--host", host, "--port", port, "--db", dbs, "--collection", "sequences", "--file", "data/sequences.json"))
  }

  def installer_metacyc(cargs: Array[String]) {
    var params = Seq[String]("METACYC", port, host, dbs, metacyc_loc)

    // there are 3528 files in the current download, so 
    // 4000 should suffice for sometime in the future
    val default_range = Seq[String]("0", "4000") 

    if (cargs.length == 0) {
      params ++= default_range
    } else {
      var range = cargs(0).split("-")
      params ++= Seq[String](if (range(0) == "") default_range(0) else range(0).toString)
      params ++= Seq[String](if (range.length == 1) default_range(1) else range(1).toString)
    }
    
    initiate_install(params)
  }

  def installer_kegg() {
    /* Original script source (unused-scripts/install-kegg.sh)
        if [ $# -ne 2 ]; then
        	echo "----> Aborting(installer-kegg.sh). Need <port> <directory with kegg files> as arguments!"
        	exit -1
        fi
        port=$1
        
        java -jar installer.jar KEGG $port localhost actv01 $2
    */
    val params = Seq[String]("KEGG", port, host, dbs, kegg_loc)
    initiate_install(params)
  }

  def installer_swissprot() {
    val params = Seq[String]("SWISSPROT", port, host, dbs, swissprot_loc)
    initiate_install(params)
  }

  def installer_map_seq() {
    val params = Seq[String]("MAP_SEQ", port, host, dbs)
    initiate_install(params)
  }

  def installer_infer_sar(cargs: Array[String]) {
    val params = Seq[String]("INFER_SAR", port, host, dbs) ++ cargs
    initiate_install(params)
  }

  def installer_keywords() {
    val params = Seq[String]("KEYWORDS", port, host, dbs)
    initiate_install(params)
  }

  def installer_vendors() {
    val params = Seq[String]("VENDORS", port, host, dbs, chem_vendors_file)
    val priority_chems = Seq[String](reachables_file)
    initiate_install(params ++ priority_chems)
  }

  def installer_patents() {
    val params = Seq[String]("PATENTS", port, host, dbs, chem_patents_file)
    val priority_chems = Seq[String](reachables_file)
    initiate_install(params ++ priority_chems)
  }

  def installer_chebi_applications() {
    val params = Seq[String]("CHEBI", port, host, dbs)
    initiate_install(params)
  }

  def installer_search_results() {
    val params = Seq[String]("BING", port, host, dbs)
    val priority_chems = Seq[String](inchis_for_bingsearch_file)
    initiate_install(params ++ priority_chems)
  }

  def execCmd(cmd: List[String]) {
    val p = Runtime.getRuntime().exec(cmd.toArray)
    p.waitFor()
    println("Exec done: " + cmd.mkString(" "))
    // println("OUT: " + scala.io.Source.fromInputStream(p.getInputStream).getLines.mkString("\n"))
    // println("ERR: " + scala.io.Source.fromInputStream(p.getErrorStream).getLines.mkString("\n"))
    // println("Press enter to continue")
    // readLine
  }
}
