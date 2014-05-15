package com.act.reachables
import java.lang.Runtime

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
    if (args.length != 0) {
      println("Usage: run (without any arguments)")
      System.exit(-1);
    } 

    println("Recommended that you run with at least -Xmx8g.") 
    println("Or else process will likely run OOM hours later.")
    println("If you did press enter to continue: ")
    readLine

    install_all()
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
    installer_infer_ops()
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

  def installer_infer_ops() {
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

    var args = Seq[String]("-config", "data/config.xml", "-exec", "INFER_OPS", "-port", port, "-start", "0")
    if (installOnlyWhitelistRxns)
      args ++= Seq[String]("-rxns_whitelist", "data/rxns-w-good-ros.txt")
    initiate_operator_inference(args)
  }

  def execCmd(cmd: List[String]) {
    val p = Runtime.getRuntime().exec(cmd.toArray)
    p.waitFor()
    println("Exec done: " + cmd.mkString(" "))
    println("OUT: " + scala.io.Source.fromInputStream(p.getInputStream).getLines.mkString("\n"))
    println("ERR: " + scala.io.Source.fromInputStream(p.getErrorStream).getLines.mkString("\n"))
    println("Press enter to continue")
    readLine
  }
}
