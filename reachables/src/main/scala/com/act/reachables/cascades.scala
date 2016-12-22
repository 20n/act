package com.act.reachables

import java.io.{File, FileOutputStream, FileWriter, PrintWriter}
import java.util.concurrent.atomic.AtomicInteger

import act.server.MongoDB
import act.shared.helpers.MongoDBToJSON
import act.shared.{Chemical, Reaction}
import org.json.{JSONArray, JSONObject}

import scala.collection.JavaConversions._

object cascades {
  val DEFAULT_DB = ("localhost", 27017, "jarvis_2016-12-09")

  def main(args: Array[String]) {
    /* -------- Command Line Options, TODO Use normal CLI parsing tools --------- */
    if (args.length == 0) {
      println("Usage: run --prefix=PRE [--max-depth=DEPTH]")
      System.exit(-1)
    }

    val params = new CmdLine(args)
    val prefix = params.get("prefix") match {
                    case Some(x) => x
                    case None => println("Need --prefix. Abort"); System.exit(-1); ""
                 }

    val outputDirectory = params.get("output-dir") match {
      case Some(x) => x
      case None => ""
    }

    params.get("cache-cascades") match {
      case Some(x) => Cascade.doCacheCascades(x.toBoolean)
      case None => // let the defaults hold
    }
    params.get("do-hmmer") match {
      case Some(x) => Cascade.doHmmerSeqFinding(x.toBoolean)
      case None => // let the default hold
    }

    // the reachables computation should have been run prior
    // to calling cascades, and it would have serialized the
    // the state of ActData. Now read it back in
    ActData.instance.deserialize(new File(outputDirectory, prefix + ".actdata").getAbsolutePath)
    println("Done deserializing data.")

    val cascade_depth = params.get("max-depth") match {
                           case Some(d) => d.toInt
                           case None => GlobalParams.MAX_CASCADE_DEPTH
                        }

    /* -------- Where we start the cascade stuff --------- */
    write_node_cascades(prefix, cascade_depth, outputDirectory)
  }

  def get_reaction_by_UUID(db: MongoDB, rid: Long): Reaction = {
    val reaction_is_reversed = rid < 0
    if (reaction_is_reversed) {
      val pos_rxnid: Long = Reaction.reverseID(rid)
      val raw_rxn = db.getReactionFromUUID(pos_rxnid)
      raw_rxn.makeReversedReaction()
    } else {
      db.getReactionFromUUID(rid)
    }
  }

  def write_node_cascades(p: String, depth: Integer, outputDirectory: String) {


    /* -------- Create File Structure --------- */
    val dir = new File(outputDirectory, p + "-data/").getAbsolutePath
    val chemlist = new File(outputDirectory, p + ".chemicals.tsv").getAbsolutePath
    val dirl = new File(dir)
    if (dirl exists) {
      if (dirl.isFile) {
        println(dir + " already exists as a file. Need to dump data to that dir")
        System.exit(-1)
      }
    } else {
      dirl.mkdir()
    }

    // We use this DB to get information about the chemicals and reactions.
    val db = new MongoDB(DEFAULT_DB._1, DEFAULT_DB._2, DEFAULT_DB._3)

    // Set(nodeIDs) = nids from the tree minus those artificially asserted as reachable
    val reachableSet = get_set(ActData.instance.ActTree.nids.values()) diff 
                        get_set(ActData.instance.chemicalsWithUserField_treeArtificial)

    // List(nodesIDs) = nids as a List
    val reachables = reachableSet.toList.filter(x => !ActData.instance().cofactors.contains(x))
    println(s"Reachables count is ${reachables.length}")

    // do we use Classes of rxns or all unbinned rxns? Based on flag.
    val producers = if (GlobalParams.USE_RXN_CLASSES) ActData.instance.rxnClassesThatProduceChem else ActData.instance.rxnsThatProduceChem 
    val consumers = if (GlobalParams.USE_RXN_CLASSES) ActData.instance.rxnClassesThatConsumeChem else ActData.instance.rxnsThatConsumeChem 

    // List(Set(rxnids)) : all outgoing connections to this node
    // Not just the ones that are in the tree, but all potential children
    // These potential children are reachable, modulo those whose rxn requires
    // unreachable other substrate
    val rxnsThatConsume = reachables.map( n => get_set(consumers.get(n)) )
    val downRxns = rxnsThatConsume.map( ridset => ridset.map( r => new ReachRxn(r, reachableSet)) )

    // List(Set(rxnids)) : all incoming connections to this node
    // Not just the ones that are in the tree, but all potential parents that
    // were rejected as parents (but as still reachable), and those that are
    // are plain not reachable.
    val rxnsThatProduce  = reachables.map( n => get_set(producers.get(n)) )
    val upRxns = rxnsThatProduce.map( ridset => ridset.map( r => new ReachRxn(r, reachableSet)) )

    // List(parents) : parents of corresponding reachables
    def getp(n: Long): Long = { val p = ActData.instance.ActTree.get_parent(n); if (p == null) -1 else p; }
    val parents = reachables.map( getp )

    val reach_neighbors = (reachables zip parents) zip (upRxns zip downRxns)
    for (tuple <- reach_neighbors) {
      val reachid = tuple._1._1
      val json = updowns_json(tuple)
      val jsonstr = json.toString(2)
      write_to(new File(dir, s"c$reachid.json").getAbsolutePath, jsonstr)
    }

    println("Done: Written node updowns.")

    // construct cascades for each reachable and then convert it to json
    Waterfall.init(reachables, upRxns)
    Cascade.init(reachables, upRxns)
    Cascade.set_max_cascade_depth(depth)

    val counter = new AtomicInteger()

    // TODO Allow CLI options here
    // These reachables are ordered such that common biosynthesizable molecules are done first.
    // val allReachables: List[Long] = List(878L, 1443L, 174960L, 1293L, 448L, 341L, 1496L, 1490L, 1536L, 750L, 4026L, 475L, 716L, 552L) ::: List(878L, 1209L, 552L, 716L, 475L, 4026L, 750L, 1536L, 1490L, 1496L, 341L, 448L, 1293L, 174960L, 1443L, 45655, 19637L, 684L) // , 358L, 2124L, 6790L)
    val allReachables: List[Long] = List(878L, 1209L, 552L, 716L, 475L, 4026L, 750L, 1536L, 1490L, 1496L, 341L, 448L, 1293L, 174960L, 1443L, 45655, 19637L, 684L, 358L, 2124L, 6790L) ::: reachables

    // // These two reachables are stellar examples of how caching causes issues
    // // val allReachables = List(1293L, 1209L)

    allReachables.foreach(reachid => {
      val msg = f"id=$reachid%6d\tcount=${counter.getAndIncrement()}%5d\tCACHE SIZES: {cascades=${Cascade.cache_nw.size}%4d, pre_rxns=${Cascade.cache_bestpre_rxn.size}%4d, nodeMerger=${Cascade.nodeMerger.size}%5d}"
      Cascade.time(msg) {
        print(f"Reachable ID: $reachid%6d: ")
        // constructInformationForReachable modifies global scope variables, so can't run in parallel.
        constructInformationForReachable(reachid, dir)
      }
    })

    println("Done: Written node cascades/waterfalls.")

    def merge_lset(a:Set[Long], b:Set[Long]) = a ++ b
    val rxnids = rxnsThatProduce.reduce(merge_lset) ++ rxnsThatConsume.reduce(merge_lset)
    for (rxnid <- rxnids) {
      val json = rxn_json(get_reaction_by_UUID(db, rxnid))
      val jsonstr = json.toString(2)
      write_to(new File(dir, s"r$rxnid.json").getAbsolutePath, jsonstr)
    }

    println("Done: Written reactions.")

    // upRxns is List(Set[ReachRxn]): need to pull out all chems in each set within each elem of list
    def foldset(s: Set[ReachRxn]) = {
      var acc = Set[Long]()
      for (cas <- s)
        for (c <- cas.getReferencedChems())
          acc += c
      acc
    }
    def foldlistset(acc: Set[Long], s: Set[ReachRxn]) = acc ++ foldset(s)
    val upmols = upRxns.foldLeft(Set[Long]())( foldlistset )
    val downmols = downRxns.foldLeft(Set[Long]())( foldlistset )
    val molecules = (reachables ++ parents).toSet ++ upmols ++ downmols
    for ( mid <- molecules ) {
      val mjson = mol_json(db.getChemicalFromChemicalUUID(mid))
      val jsonstr = mjson.toString(2)
      write_to(new File(dir, s"m$mid.json").getAbsolutePath, jsonstr)
    }

    println("Done: Written molecules.")

    // now write a big tab-sep file with the "id smiles inchi synonyms"
    // of all chemicals referenced so that later we can run a process
    // to render each one of those chemicals.
    val chemfile = to_append_file(chemlist)
    for (mid <- molecules) {
      val torender = torender_meta(db.getChemicalFromChemicalUUID(mid))
      append_to(chemfile, torender)
    }
    chemfile.close()

    println("Done: Written chemicals.tsv.")

    def hr() = println("*" * 80)
    hr
    println("Now run the following to get svg images (molecule and cascades):")
    println("./src/main/resources/mksvgs.sh " + chemlist + " " + dir + ". Takes a good amount of time! :)")
    hr
    println("After that you may set your webserver's document root to: <act.git loc>/api/www/html")
    println("And then go to http://yourserver/nw/clades-tree-collapse.html")
    println("If the page cannot locate the reachables tree, you may have to ")
    println("ln -s ./" + p + "{-data, .graph.json, .trees.json} <act.git loc>/api/www/html/nw/")
    hr
  }

  def constructInformationForReachable(reachid: Long, dir: String): Unit = {
    // write to disk; JS front end uses json
    val waterfall = new Waterfall(reachid)
    val json    = waterfall.json()
    val jsonstr = json.toString(2)
    write_to(new File(dir, s"p$reachid.json").getAbsolutePath, jsonstr)

    // write to disk; cascade as dot file
    val cascade = new Cascade(reachid)
    val dot     = cascade.dot()
    write_to(new File(dir, s"cscd$reachid.dot").getAbsolutePath, dot)
    val writer = new FileWriter(new File(dir, s"paths$reachid.txt"))
    writer.write(cascade.allStringPaths.mkString("\n"))
    writer.close()

    // color attributes are cascade specific. so we clear them after each
    // cascade run. otherwise because we cache nodes, colors bleed across cascades
    Edge.clearAttributeOnAllEdges("color")
  }

  def rxn_json(r: Reaction) = {
    val id = r.getUUID()
    val mongo_json = MongoDB.createReactionDoc(r, id)
    val json = MongoDBToJSON.conv(mongo_json)
    json
  }

  def updowns_json(c: ((Long, Long), (Set[ReachRxn], Set[ReachRxn]))) = {
    val chemid = c._1._1
    val parent = c._1._2
    val uprxns = c._2._1
    val downrxns = c._2._2
    val up = new JSONArray
    for (r <- uprxns) up.put(r.json)
    val down = new JSONArray
    for (r <- downrxns) down.put(r.json)

    val json = new JSONObject
    json.put("chemid", chemid)
    json.put("parent", parent)
    json.put("upstream", up)
    json.put("downstream", down)

    // return a tuple of json string of up and down from a node
    json
  }

  def mol_json(c: Chemical) = {
    if (c == null) {
      new JSONObject
    } else {
      val mongo_moljson = MongoDB.createChemicalDoc(c, c.getUuid())
      val json = MongoDBToJSON.conv(mongo_moljson)
      json 
    }
  }

  def torender_meta(c: Chemical) = {
    if (c == null) {
      "(null)"
    } else {
      val inchi = c getInChI
      val smiles = c getSmiles
      val id = c getUuid
      // various names: canon: String, synonyms: List[String], brendaNames: List[String], 
      // not queried: (pubchem) names: Map[String, String[]]
      // c.getCanon() returns a mutable Buffer whose + operation creates a malformed name in the output.  Use List() and
      // ++ to correct this issue.
      val names = c.getSynonyms() ++ c.getBrendaNames() ++ List(c.getCanon())

      id + "\t" + smiles + "\t" + inchi + "\t" + String.join("\t", names)
    }
  }

  final case class UnsupportedFlag(msg: String) extends RuntimeException(msg)


  def mergeMaps[X,Y](m1: Map[X, Set[Y]], m2: Map[X, Set[Y]]) = {
    // merge the maps of this and other; taking care to union
    // value sets rather than overwrite
    val keys = Set[X]() ++ m1.keys ++ m2.keys
    val kvs = for (s <- keys) yield {
      val a = if (m2 contains s) m2(s) else Set[Y]()
      val b = if (m1 contains s) m1(s) else Set[Y]()
      s -> (a ++ b)
    }

    Map() ++ kvs
  }

  def filter_by_edit_dist(substrates: List[Long], prod: Long): List[Long] = {
    def get_inchi(m: Long) = {
      val inchi = ActData.instance.chemId2Inchis.get(m)
      Option(inchi)
    }

    val basis = List('C', 'N', 'O')
    val emptybasis = List(0, 0, 0)

    def get_atom_counts(formula: String) = Map() ++ (for (atom <- basis) yield {
      val regex = (atom + """([0-9]+)""").r
      atom -> ((regex findFirstMatchIn formula) match {
        case Some(m) => m.group(1).toInt;
        case None => if (formula contains atom) 1 else 0;
      })
    })

    def vectorize(m: Option[String]) = m match {
                                          case None => emptybasis
                                          case Some(inchi) => {
                                            val spl = inchi.split('/')
                                            if (spl.size <= 2)
                                              emptybasis
                                            else {
                                              val formula = spl(1)
                                              val atomcounts = get_atom_counts(formula)
                                              basis.map(atom => atomcounts(atom))
                                            }
                                          }
                                       }
    def edit_dist(s: Option[String], p: Option[String]) = {
      val pvec = vectorize(p)
      val svec = vectorize(s)
      if (pvec == emptybasis || svec == emptybasis)
        Int.MaxValue
      else
        // compute the \sum_{b=basis_elem} \abs(diff on b)
        (pvec zip svec).map{ case (pv, sv) => math.abs(pv-sv) }.reduce(_ + _)
    }
    val distances = substrates.map(s => get_inchi(s)).map(edit_dist(_, get_inchi(prod)))
    val min_dist = distances.reduce(math.min)
    val keep = filterByAuxList(substrates, distances, (d:Int) => d == min_dist)
    keep
  }

  def filterByAuxList[A, B](a: List[A], b: List[B], bpredicate: B=>Boolean) = {
    (a zip b).filter{ case (ae, be) => bpredicate(be) }.unzip._1
  }

  def to_append_file(fname: String) = {
    new PrintWriter(new FileOutputStream(fname), true)
  }

  def append_to(file: PrintWriter, line: String) {
    file append (line + "\n")
  }

  def write_to(fname: String, json: String) {
    val file = new PrintWriter(new File(fname))
    file write json
    file.close()
  }

  def get_set(c: java.util.Collection[java.lang.Long]): Set[Long] = {
    var s = Set[Long]()
    if (c != null) {
      for (l <- c)
        s += l
    }
    s
  }
}
