package com.act.reachables

import java.io.{File, PrintWriter}

import act.server.MongoDB

import scala.collection.JavaConversions._
import scala.io.Source

object reachables {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage: run --prefix=PRE --hasSeq=true|false --regressionSuiteDir=path --extra=[semicolon-sep db.chemical fields]")
      println("Example: run --prefix=r")
      println("         will create reachables tree with prefix r and by default with only enzymes that have seq")
      println("Example: run --prefix=r --extra=xref.CHEBI;xref.DEA;xref.DRUGBANK")
      println("         will make sure all CHEBI/DEA/DRUGBANK chemicals are included. Those that are already reachable will be in the normal part of the tree and those that are not will have parent_id < -1 ")
      System.exit(-1);
    } 

    val params = new CmdLine(args)
    val prefix = params.get("prefix") match { 
                    case Some(x) => x
                    case None => println("Need --prefix. Abort"); System.exit(-1); ""
                 }
    
    write_reachable_tree(prefix, params)
  }

  def write_reachable_tree(prefix: String, opts: CmdLine) { 
    val rdir = prefix + ".regressions/" // regression output directory

    // Connect to the DB so that extended attributes for chemicals can be fetched as we serialize.
    val db = new MongoDB("localhost", 27017, "jarvis")

    val needSeq = 
      opts.get("hasSeq") match { 
        case Some("false") => false
        case _ => true 
      }

    val universal_natives = 
      opts.get("useNativesFile") match { 
        case Some(file) => {
          val data = Source.fromFile(file).getLines
          val inchis = data.filter{ x => x.length > 0 && x.charAt(0) != '#' }
          collection.mutable.Set(inchis.toSeq:_*)
        }
        case _ => null
      }

    val universal_cofactors = 
      opts.get("useCofactorsFile") match { 
        case Some(file) => {
          val data = Source.fromFile(file).getLines
          val inchis = data.filter{ x => x.length > 0 && x.charAt(0) != '#' }
          collection.mutable.Set(inchis.toSeq:_*)
        }
        case _ => null
      }

    val regression_suite_files = 
      opts.get("regressionSuiteDir") match { 
        case Some(dir) => {
          val files = new File(dir).listFiles
          val testfiles = files
                            .map(n => n.getAbsolutePath)
                            .filter(_.endsWith(".test.txt"))
          testfiles.toSet
        }
        case _ => Set()
      }

    val chems_w_extra_fields = 
      opts.get("extra") match { 
        case Some(fields) => fields split ";"
        case None => null
      }

    // set parameter for whether we want to exclude rxns that dont have seq
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = needSeq

    // compute the reachables tree!
    val restrict_to_enzymes_that_have_seqs = false
    val tree = LoadAct.getReachablesTree(universal_natives, 
                                          universal_cofactors, 
                                          restrict_to_enzymes_that_have_seqs, 
                                          chems_w_extra_fields)
    println("Done: L2 reachables computed. Num reachables found: "  + tree.nodesAndIds.size)

    // get inchis for all the reachables
    // Network.nodesAndIds(): Map[Node, Long] i.e., maps nodes -> ids
    // chemId2Inchis: Map[Long, String] i.e., maps ids -> inchis
    // so we take the Map[Node, Long] and map each of the key_value pairs
    // by looking up their ids (using n_ids._2) in chemId2Inchis
    // then get a nice little set from that Iterable
    def id2InChIName(id: Long) = id -> (ActData.instance.chemId2Inchis.get(id), ActData.instance.chemId2ReadableName.get(id))
    def tab(id_inchi_name: (Long, (String, String))) = id_inchi_name._1 + "\t" + id_inchi_name._2._2 + "\t" + id_inchi_name._2._1

    val reachables: Map[Long, (String, String)] = tree.nodesAndIds.map(x => id2InChIName(x._2)).toMap
    def fst(x: (String, String)) = x._1
    val r_inchis: Set[String] = reachables.values.toSet.map( fst ) // reachables.values are (inchi, name)

    // create output directory for regression test reports, if not already exists
    mk_regression_test_reporting_dir(rdir)
    // run regression suites if provided
    regression_suite_files.foreach(test => run_regression(r_inchis, test, rdir))

    // serialize ActData, which contains a summary of the relevant act
    // data and the corresponding computed reachables state
    ActData.instance.serialize(prefix + ".actdata")
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
