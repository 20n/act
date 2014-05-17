package com.act.reachables
import java.lang.Runtime
import act.server.SQLInterface.MongoDB
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
  var port="27017"
  var host="localhost"
  var dbs="actv01"

  // location where KEGG data files can be found
  var kegg_loc="data/kegg"

  // in the brenda data what is the max rxnid we expect to see
  var maxBrendaRxnsExpected="60000"

  // install with or without whitelist: only set to true while debugging
  var installOnlyWhitelistRxns=false

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
      install_all()
    } else {
      val cmd = args(0)
      val cargs = args.drop(1)
      println("Going to run " + cmd + " with args: " + cargs.mkString("(", ", ", ")") + ". Enter to continue:")
      readLine
      if (cmd == "checkmongod")
        checkmongod(cargs)
      else if (cmd == "kegg")
        installer_kegg()
      else if (cmd == "balance")
        installer_balance()
      else if (cmd == "energy")
        installer_energy()
      else if (cmd == "rarity")
        installer_rarity()
      else if (cmd == "infer_ops")
        installer_infer_ops(cargs)
      else 
        println("Unrecognized init module: " + cmd) ;
    }
  }

  def printhelp() {
    def hr() = println("*" * 80)
    hr
    println("Recommended that you run with at least -Xmx8g.")
    println("Or else process will likely run OOM hours later.")
    hr
    println("Usage:")
    println("without argument: install_all")
    println("checkmongod <collection> <ref:port> [<idx_field e.g., _id> [<bool: lists are sets>]]")
    println("infer_ops [<rxnid | rxnid_l-rxnid_h>] : if range omitted then all inferred")
    hr
  }

  def checkmongod(cargs: Array[String]) {
    def hr() = println("*" * 80)
    def hre() = Console.err.println("*" * 80)
    val db = new MongoDB(host, port.toInt, dbs)
    val rids = db.getAllReactionUUIDs(); println("rids: " + rids.take(10).mkString("/"))
    val oids = db.graphByOrganism(4932); println("rids: " + oids.take(10).mkString("/")) // Saccaromyces cerevisiae
    val coll = cargs(0)
    val refport = cargs(1)
    val idx_field = if (cargs.length >= 3) cargs(2) else "_id"
    val unorderedLists = if (cargs.length >= 4) cargs(3).toBoolean else true

    // diff: P[P[List, List], Map[O, O]] of (id_added, id_del), id->updated
    val diff = MongoDB.compare(coll, idx_field, port.toInt, refport.toInt, unorderedLists)
    val add = (diff fst) fst
    val del = (diff fst) snd
    val upd = (diff snd)
    hr
    println(add.size() + " entries added")
    println(del.size() + " entries deleted")
    println(upd.keySet.size() + " entries updated")
    hr

    println("Do you want to output the full dump to stderr?")
    readLine

    hre
    Console.err.println("Added IDs: " + add.mkString(", "))
    hre
    Console.err.println("Deleted IDs: " + del.mkString(", "))
    hre
    Console.err.println("Updated: " + upd.mkString("{\n\n", "\n", "\n\n}"))
    
  }

  def initiate_install(args: Seq[String]) {
    act.installer.Main.main(args.toArray)
  }

  def initiate_operator_inference(args: Seq[String]) {
    act.client.CommandLineRun.main(args.toArray)
  }

  def install_all() {
    /* Original script source (unused-scripts/install-all.sh)
        if [ $# -ne 2 ]; then
          echo "----> Aborting(install-all.sh). Need <port> <-w-whitelist | -wo-whitelist> as argument!"
          exit -1
        fi
        port=$1
        w_or_wo_whitelist=$2
        
        ./installer.sh $port
        ./installer-kegg.sh $port data/kegg
        ./installer-balance.sh $port
        ./installer-energy.sh $port
        ./installer-rarity.sh $port 0 60000
        ./installer-infer-ops.sh $port 0 $w_or_wo_whitelist
    */

    installer() // installs brenda
    installer_kegg()
    installer_balance()
    installer_energy()
    installer_rarity()
    installer_infer_ops(new Array[String](0)) // pass empty array: we want to infer ops for all rxns
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
        java -Xmx2g -jar installer.jar BRENDA $port $host $dbs data brenda.txt nodes.dmp names.dmp inchi_PCdata.txt all-InChIs.txt cofactors.txt cofac-pairs-AAMs.txt ecoliMetabolites cleanup-chemnames-litmining.json imp_chems_autogen.txt 
        #     remove accumulated chemicals lists...
        rm data/imp_chems_autogen.txt
        
        # B) install: sequences
        mongoimport --host $host --port $port --db $dbs --collection sequences --file data/sequences.json

    */
    // because we attempt to use wildcards, which are bash-interpreted, we have to call bash to expand them
    execCmd(List("bash","-c","cat data/imp_chemicals_*.txt > data/imp_chems_autogen.txt"))
    val params = Seq[String]("BRENDA", port, host, dbs, "data", "brenda.txt", "nodes.dmp", "names.dmp", "inchi_PCdata.txt", "all-InChIs.txt", "cofactors.txt", "cofac-pairs-AAMs.txt", "ecoliMetabolites", "cleanup-chemnames-litmining.json", "imp_chems_autogen.txt")
    initiate_install(params)
    execCmd(List("rm", "data/imp_chems_autogen.txt"))
    execCmd(List("mongoimport", "--host", host, "--port", port, "--db", dbs, "--collection", "sequences", "--file", "data/sequences.json"))
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

  def installer_balance() {
    /* Original script source (unused-scripts/installer-balance.sh)
        if [ $# -ne 1 ]; then
        	echo "----> Aborting(installer-balance.sh). Need <port> as arguments!"
        	exit -1
        fi
        port=$1
        
        java -jar installer.jar BALANCE $port localhost actv01
    */
    val params = Seq[String]("BALANCE", port, host, dbs)
    initiate_install(params)
  }

  def installer_energy() {
    /* Original script source (unused-scripts/installer-energy.sh)
        if [ $# -ne 1 ]; then
        	echo "----> Aborting(installer-energy.sh). Need <port> as arguments!"
        	exit -1
        fi
        port=$1
        
        java -jar installer.jar ENERGY $port localhost actv01
    */
    val params = Seq[String]("ENERGY", port, host, dbs)
    initiate_install(params)
  }

  def installer_rarity() {
    /* Original script source (unused-scripts/installer-rarity.sh)
        if [ $# -ne 3 ]; then
        	echo "----> Aborting(installer-rarity.sh). Need <port> <start_id> <end_id> as arguments!"
        	exit -1
        fi
        port=$1
        
        java -jar installer.jar RARITY $port localhost actv01 $2 $3
    */
    val params = Seq[String]("RARITY", port, host, dbs, "0", maxBrendaRxnsExpected)
    initiate_install(params)
  }

  def installer_infer_ops(cargs: Array[String]) {
    /* Original script source (unused-scripts/installer-infer-ops.sh)
        # 1. compute rarity statistics over chemicals in the reactions DB
        #        -- install those metrics back in the DB
        # 2. compute operators
        #        -- robustly: restarting if DB cursor goes missing...
        # only line 3 of the four lines above is currently implemented!!!!!
        
        if [ $# -ne 3 ]; then
        	echo "----> Aborting(installer-infer-ops.sh). Need <port> <start_id> <-w-whitelist | -wo-whitelist> as argument!"
        	exit -1
        fi
        port=$1
        
        if [ $3 == "-w-whitelist" ]; then
        	java -Xmx8182M -jar mongoactsynth.jar -config ../MongoActSynth/war/config.xml -exec INFER_OPS -port $port -start $2 -rxns_whitelist data/rxns-w-good-ros.txt
        elif [ $3 == "-wo-whitelist" ]; then
        	java -Xmx8182M -jar mongoactsynth.jar -config ../MongoActSynth/war/config.xml -exec INFER_OPS -port $port -start $2
        else
        	echo "Third argument needs to be either -w-whitelist or -wo-whitelist"
        fi
    */
    var args = Seq[String]("-config", "data/config.xml", "-exec", "INFER_OPS", "-port", port)

    if (cargs.length == 0) {
      args ++= Seq[String]("-start", "0")
    } else {
      var range = cargs(0).split("-")
      args ++= Seq[String]("-start", if (range(0) == "") "0" else range(0).toString)
      if (range.length == 2)
        args ++= Seq[String]("-end", range(1).toString)
    }
    
    if (installOnlyWhitelistRxns)
      args ++= Seq[String]("-rxns_whitelist", "data/rxns-w-good-ros.txt")
    initiate_operator_inference(args)
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
