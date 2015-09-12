package com.act.reachables

import java.io.{File, PrintWriter}

import act.server.SQLInterface.MongoDB

import scala.collection.JavaConversions._
import scala.io.Source

object postprocess_reachables {

  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage: run --prefix=PRE --regressionSuiteDir=path --extractReachables --writeGraphToo")
      println("Example: run --prefix=r")
      println("         will create reachables tree with prefix r and by default with only enzymes that have seq")
      println("Example: run --prefix=r --regressionSuiteDir=path ")
      println("         will just run the regressions over a dataset with prefix 'r'")
      println("Example: run --prefix=r --extractReachables ")
      println("         will convert the actdata structure with the specified prefix to trees/tables")
      System.exit(-1);
    }

    val params = new CmdLine(args)
    val prefix = params.get("prefix") match {
      case Some(x) => x
      case None => println("Need --prefix. Abort"); System.exit(-1); ""
    }
    val write_other_formats = params.get("extractReachables") != None

    val regression_suite_files: Set[String] =
      params.get("regressionSuiteDir") match {
        case Some(dir) => {
          val files = new File(dir).listFiles
          val testfiles = files
            .map(n => n.getAbsolutePath)
            .filter(_.endsWith(".test.txt"))
          testfiles.toSet
        }
        case _ => Set()
      }
    val write_graph_too = params.get("writeGraphToo") != None

    printf("Will write other formats of reachables graph? %s\n", write_other_formats)
    printf("Will run regression suite? %s\n", regression_suite_files)

    println("Deserializing reachables graph")
    ActData.instance.deserialize(prefix + ".actdata")

    val tree = ActData.instance().getActTree


    // get inchis for all the reachables
    // Network.nodesAndIds(): Map[Node, Long] i.e., maps nodes -> ids
    // chemId2Inchis: Map[Long, String] i.e., maps ids -> inchis
    // so we take the Map[Node, Long] and map each of the key_value pairs
    // by looking up their ids (using n_ids._2) in chemId2Inchis
    // then get a nice little set from that Iterable

    val reachables: Map[Long, (String, String)] = tree.nodesAndIds.map(x => id2InChIName(x._2)).toMap
    def fst(x: (String, String)) = x._1
    val r_inchis: Set[String] = reachables.values.toSet.map( fst ) // reachables.values are (inchi, name)


    if (write_other_formats) {
      write_reachable_tree(prefix, write_graph_too, reachables)
    }

    if (regression_suite_files.nonEmpty) {
      run_regression_suite(prefix, regression_suite_files, r_inchis)
    }
  }

  def id2InChIName(id: Long) = id ->
    (ActData.instance().mapChemId2Inchis(id), ActData.instance().mapChemId2ReadableName(id))

  def run_regression_suite(prefix: String, regression_suite_files : Set[String], r_inchis: Set[String]): Unit = {
    val rdir = prefix + ".regressions/" // regression output directory

    // create output directory for regression test reports, if not already exists
    mk_regression_test_reporting_dir(rdir)
    // run regression suites if provided
    regression_suite_files.foreach(test => reachables.run_regression(r_inchis, test, rdir))
  }

  def write_reachable_tree(prefix: String, write_graph_too: Boolean, reachables: Map[Long, (String, String)]) {
    val g = prefix + ".graph.json" // output file for json of graph
    val t = prefix + ".trees.json" // output file for json of tree
    val r = prefix + ".reachables.txt" // output file for list of all reachables
    val e = prefix + ".expansion.txt" // output file for tree structure of reachables expansion

    // Connect to the DB so that extended attributes for chemicals can be fetched as we serialize.
    val db = new MongoDB("localhost", 27017, "actv01")

    println("Writing disjoint graphs to " + g + " and forest to " + t)

    val tree = ActData.instance().getActTree

    def tab(id_inchi_name: (Long, (String, String))) = id_inchi_name._1 + "\t" + id_inchi_name._2._2 + "\t" + id_inchi_name._2._1

    write_to(r, reachables.map(tab).reduce(_ + "\n" + _))
    println("Done: Written reachables list to: "  + r)

    write_to(e, tree2table(tree, reachables))
    println("Done: Written reachables tree as spreadsheet to: "  + e)

    val disjointtrees = tree.disjointTrees(db) // a JSONObject
    val treejson = disjointtrees.toString(2) // serialized using indent = 2
    write_to(t, treejson)
    println("Done: Writing disjoint trees");

    if (write_graph_too) {
      println("scala/reachables.scala: You asked to write graph, in addition to default tree.")
      println("scala/reachables.scala: This will most likely run out of memory")
      val disjointgraphs = tree.disjointGraphs(db) // a JSONArray
      val graphjson = disjointgraphs.toString(2) // serialized using indent = 2
      write_to(g, graphjson)
      println("scala/reachables.scala: Done writing disjoint graphs");
    }

    println("Done: Written reachables to trees (and graphs, if requested).")
  }

  def tree2table(tree: Network, reachables: Map[Long, (String, String)]) = {

    // helper function to help go from
    // java.util.HashMap[java.lang.Long, java.lang.Integer] to Map[Long, Int]
    def ident(id: Long) = id -> Int.unbox(tree.nodeDepths.get(id))

    // run the helper over the nodeDepths to get node_id -> depth map
    val nodes_depth: Map[Long, Int] = tree.nodeDepths.map(x => ident(x._1)).toMap

    // get all depths as a set (this should be a continuous range from 0->n)
    val all_depths = nodes_depth.values.toSet

    // construct the inverse map of depth -> set of nodes at that depth
    val depth_to_nodes: Map[Int, Set[Long]] = {

      // function that checks if a particular (node, depth) pair is at depth `d`
      def is_at_depth(d: Int, nid_d: (Long, Int)): Boolean = nid_d._2 == d

      // function that takes a depth `d` and returns for it the nodes at that depth
      def depth2nodes(d: Int): Set[Long] = {

        // filter the map of node_id -> depth those that are at depth `d`
        val atdepth: Map[Long, Int] = nodes_depth.filter( x => is_at_depth(d, x) )

        // take the (node_id, depth) pairs that are at depth `d` and return
        // the set of their node_ids by unzipping and then converting to set
        atdepth.toList.unzip._1.toSet
      }

      // now map each depth `x` to the set of nodes at depth `x`
      all_depths.map(x => x -> depth2nodes(x)).toMap
    }

    // using the above-computed map of `depth -> set(nodes at that depth)`
    // create a map of `depth -> set(node_data strings at that depth)`
    def node2str(id: Long) = {
      val inchi_name: Option[(String, String)] = reachables.get(id)
      val description = inchi_name match {
        case None => "no name/inchi"
        case Some((inchi, name)) => name + " " + inchi
      }
      id + ": " + description
    }

    // create a map with the metadata strings in the value fields
    val depth_to_nodestrs = depth_to_nodes.map{ case (k, v) => (k, v.map(node2str)) }

    // sort the list of depths, so that we can print them out in order
    val sorted_depths = all_depths.toList.sorted

    // go from list of depths, to sets of node_strs at that depth
    val projected_nodes = sorted_depths.map(depth => depth_to_nodestrs(depth))

    // collapse all node_strs at each depth to tab-separated
    val node_lines = projected_nodes.map(set => set.reduce(_ + "\t" + _))

    // return the lines concatenated together with newlines
    node_lines.reduce(_ + "\n" + _)
  }

  def run_regression(reachable_inchis: Set[String], test_file: String, output_report_dir: String) {
    val testlines: List[String] = Source.fromFile(test_file).getLines.toList
    val testcols: List[List[String]] = testlines.map(line => line.split("\t").toList)

    val hdrs = Set("inchi", "name", "plausibility", "comment", "reference")
    if (testcols.length == 0 || !testcols(0).toSet.equals(hdrs)) {
      println("Invalid test file: " + test_file)
      println("\tExpected: " + hdrs.toString)
      println("\tFound: " + testcols(0).toString)
    } else {

      // delete the header from the data set, leaving behind only the test rows
      val hdr = testcols(0)
      val rows = testcols.drop(1)

      def add_hdrs(row: List[String]) = hdr.zip(row)

      // partition test rows based on whether this reachables set passes or fails them
      val (passed, failed) = rows.partition(testrow => run_regression(add_hdrs(testrow), reachable_inchis))

      val report = generate_report(test_file, passed, failed)
      write_to(output_report_dir + "/" + new File(test_file).getName, report)
      println("Regression file: " + test_file)
      println("Total test: " + rows.length + " (passed, failed): (" + passed.length + ", " + failed.length + ")")
    }
  }

  def mk_regression_test_reporting_dir(dir: String) {
    val dirl = new File(dir)
    if (dirl exists) {
      if (dirl.isFile) {
        println(dir + " already exists as a file. Need it as dir for regression output. Abort.")
        System.exit(-1)
      }
    } else {
      dirl.mkdir()
    }
  }

  def generate_report(f: String, passed: List[List[String]], failed:List[List[String]]) = {
    val total = passed.length + failed.length
    val write_successes = false
    val write_failures = true

    val lines =
    // add summary to head of report file
      List(
        "** Regression test result for " + f,
        "\tTOTAL: " + total + " PASSED: " + passed.length,
        "\tTOTAL: " + total + " FAILED: " + failed.length
      ) ++ (
        // add details of cases that succeeded
        if (write_successes)
          passed.map("\t\tPASSED: " + _).toList
        else
          List()
        ) ++ (
        // add details of cases that failed
        if (write_failures)
          failed.map("\t\tFAILED: " + _).toList
        else
          List()
        )

    // make the report as a string of lines
    val report = lines reduce (_ + "\n" + _)

    // return the report
    report
  }

  def run_regression(row: List[(String, String)], reachable_inchis: Set[String]): Boolean = {
    val data = row.toMap
    val inchi = data.getOrElse("inchi", "") // inchi column
    val should_exist = data.getOrElse("plausibility", "TRUE").toBoolean // plausibility column

    val exists = reachable_inchis.contains(inchi)

    if (should_exist)
      exists // if should-exist then output whether it exists
    else
      !exists // if should-not-exist then output negation of it-exists in reachable
  }

  def write_to(fname: String, json: String) {
    val file = new PrintWriter(new File(fname))
    file write json
    file.close()
  }


}
