package com.act.biointerpretation.mechanisminspection

import chemaxon.formats.MolExporter
import chemaxon.formats.MolImporter
import chemaxon.struc.RxnMolecule
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.apache.commons.lang3.StringUtils
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.List

object ChemicalRenderer {
  val OPTION_INCHI_LIST: String = "l"
  val OPTION_OUTPUT_DIRECTORY: String = "d"
  val OPTION_BUILDERS: util.List[Option.Builder] = new util.ArrayList[Option.Builder]() {
  }
  val HELP_FORMATTER: HelpFormatter = new HelpFormatter
  val HELP_MESSAGE: String = StringUtils.join(Array[String]("This class renders representations of a reaction."), "")

  @throws(classOf[IOException])
  def main(args: Array[String]) {
    val opts: Options = new Options
    import scala.collection.JavaConversions._
    for (b <- OPTION_BUILDERS) {
      opts.addOption(b.build)
    }
    var cl: CommandLine = null
    try {
      val parser: CommandLineParser = new DefaultParser
      cl = parser.parse(opts, args)
    }
    catch {
      case e: ParseException => {
        System.err.format("Argument parsing failed: %s\n", e.getMessage)
        HELP_FORMATTER.printHelp(classOf[ChemicalRenderer].getCanonicalName, HELP_MESSAGE, opts, null, true)
        System.exit(1)
      }
    }
    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(classOf[ChemicalRenderer].getCanonicalName, HELP_MESSAGE, opts, null, true)
      return
    }
    val in: BufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cl.getOptionValue(OPTION_INCHI_LIST))))
    var line: String = null
    while ((({
      line = in.readLine; line
    })) != null) {
      line = line.replace("\n", "")
      MolImporter.importMol(db.getChemicalFromChemicalUUID(sub).getInChI)
      RxnMolecule.REACTANTS
      val id_inc: Array[String] = line.split("\t")
      val id: String = "CHEBI:" + id_inc(0)
      val inchi: String = id_inc(1)
      inchis.put(id, inchi)
    }
  }

  try {
    HELP_FORMATTER.setWidth(100)
  }
}