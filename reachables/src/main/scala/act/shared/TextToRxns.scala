package act.shared

import java.io.PrintWriter
import java.io.File
import java.net.URLEncoder
import java.net.URI
import java.io.FileNotFoundException
import uk.ac.cam.ch.wwmm.chemicaltagger.ChemistryPOSTagger
import uk.ac.cam.ch.wwmm.chemicaltagger.ChemistrySentenceParser
import uk.ac.cam.ch.wwmm.chemicaltagger.Utils

import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.Document
import nu.xom.converters.DOMConverter

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFTextStripper

import com.act.biointerpretation.l2expansion.SparkInstance

sealed trait InputType
case class TextFile(val fname: String) extends InputType
case class WebURL(val url: String) extends InputType
case class RawText(val text: String) extends InputType
case class PdfFile(val fname: String) extends InputType

object TextToRxns {
  def main(args: Array[String]) {
    val opts = List(optOutFile, optRawText, optPdfFile, optTextFile, optUrl, optChecks)
    val className = this.getClass.getCanonicalName
    val cmdLine: CmdLineParser = new CmdLineParser(className, args, opts)

    val out: PrintWriter = {
      if (cmdLine has optOutFile) 
        new PrintWriter(cmdLine get optOutFile)
      else
        new PrintWriter(System.out)
    }

    if (cmdLine has optChecks) {
      runChecks(out)
    } else {
      val data = {
        if (cmdLine has optUrl)
          Some(new WebURL(cmdLine get optUrl))
        else if (cmdLine has optTextFile)
          Some(new TextFile(cmdLine get optTextFile))
        else if (cmdLine has optPdfFile)
          Some(new PdfFile(cmdLine get optPdfFile))
        else if (cmdLine has optRawText)
          Some(new RawText(cmdLine get optRawText))
        else None
      }

      val extractor = new TextToRxns
      extractor.extract(data, out)
    }
  }

  def runChecks(out: PrintWriter) {
    val extractor = new TextToRxns
    
    val testURL = "https://www.ncbi.nlm.nih.gov/pubmed/20564561?dopt=Abstract&report=abstract&format=text"
    val testSentences = List(
      // p-aminophenylphosphocholine + H2O  p-aminophenol + choline phosphate
      """Convert p-aminophenylphosphocholine and H2O to p-aminophenol and choline phosphate in 3.1.4.38""",
      // the following triple set should be validated:
      // 4-chloro-phenylglycine + H2O + O2 -> (4-chlorophenyl)acetic acid + NH3 + H2O2
      """The cell converted 4-chloro-phenylglycine to (4-chlorophenyl)acetic acid in 
      the presence of water and O2 and released ammonia and H2O2.
      This happened in Rhodosporidium toruloides and BRENDA has it under 1.4.3.3"""
    )
    for (testSent <- testSentences)
      extractor.extract(Some(new RawText(testSent)), out)

    // extractor.extract(Some(new WebURL(testURL)), out)
  }

  val optOutFile = new OptDesc(
                    param = "o",
                    longParam = "outjson",
                    name = "file",
                    desc = "Output json of peaks, mz, rt, masses, formulae etc.",
                    isReqd = false, hasArg = true)

  val optRawText = new OptDesc(
                    param = "r",
                    longParam = "raw-text",
                    name = "string",
                    desc = s"plain text to process",
                    isReqd = false, hasArg = true)

  val optPdfFile = new OptDesc(
                    param = "p",
                    longParam = "pdf",
                    name = "file",
                    desc = s"pdf file to extract text from",
                    isReqd = false, hasArg = true)

  val optTextFile = new OptDesc(
                    param = "t",
                    longParam = "text",
                    name = "file",
                    desc = s"text file to process",
                    isReqd = false, hasArg = true)

  val optUrl = new OptDesc(
                    param = "u",
                    longParam = "url",
                    name = "URI",
                    desc = s"web location to retrieve for text",
                    isReqd = false, hasArg = true)

  val optChecks = new OptDesc(
                    param = "c",
                    longParam = "check-tests",
                    name = "tests",
                    desc = s"runs a few tests",
                    isReqd = false, hasArg = false)
}

class TextToRxns {

  def extract(dataSrc: Option[InputType], out: PrintWriter) = {
    dataSrc match {
      case None => 
      case Some(incoming) => {
        val textData = incoming match {
          case WebURL(url) => scala.io.Source.fromURL(url).mkString
          case RawText(text) => text
          case PdfFile(file) => {
            val pdf = PDDocument.load(new File(file))
            val stripper = new PDFTextStripper
            stripper.getText(pdf)
          }
          case TextFile(file) => scala.io.Source.fromFile(file).mkString
        }
        getChemicals(textData)
      }
    }
    out close
  }

  val MOLECULE_PATH = "//MOLECULE"

  def getChemicals(text: String) = {
    // Calling ChemistryPOSTagger
    val posContainer = ChemistryPOSTagger.getDefaultInstance().runTaggers(text);

    // Returns a string of TAG TOKEN format (e.g.: DT The NN cat VB sat IN on DT the NN matt)
    // Call ChemistrySentenceParser either by passing the POSContainer or by InputStream
    val chemistrySentenceParser = new ChemistrySentenceParser(posContainer);

    // Create a parseTree of the tagged input
    chemistrySentenceParser.parseTags();

    // Return an XMLDoc
    val doc = chemistrySentenceParser.makeXMLDocument();
    Utils.writeXMLToFile(doc,"file1.xml");

    val docFactory = DocumentBuilderFactory.newInstance
    docFactory.setValidating(false)
    docFactory.setNamespaceAware(true)
    docFactory.setFeature("http://xml.org/sax/features/namespaces", false)
    docFactory.setFeature("http://xml.org/sax/features/validation", false)
    docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

    val docBuilder = docFactory.newDocumentBuilder
    val domImpl = docBuilder.getDOMImplementation
    val w3cDoc = DOMConverter.convert(doc, domImpl)

    val chems = extractChemicals(w3cDoc)
    val chemSubsets = chems.toSet.subsets.toList
    val subsProdCandidates = for (s <- chemSubsets; p <- chemSubsets; if (!s.equals(p))) yield (s, p)
    println(s"Pairs to check for: ${subsProdCandidates.size}")
    val passValidation = subsProdCandidates.map(fromPairSetToMap).filter(validate)
    println(s"Found valid: ${passValidation.size}")
  }

  def fromPairSetToMap(subPrd: (Set[(String, String)], Set[(String, String)])): 
    (Map[String, String], Map[String, String]) = {
    (subPrd._1.toMap, subPrd._2.toMap)
  }

  def validate(subPrd: (Map[String, String], Map[String, String])): Boolean = {
    val substrateNames = subPrd._1.keys.toList
    val productNames = subPrd._2.keys.toList

    val substrates = subPrd._1.values.toList
    val products = subPrd._2.values.toList
    val passingEros = SparkInstance.validateReactionNoLicense(true)(substrates, products)
    if (passingEros.size > 0) {
      println(s"Validated: $substrateNames -> $productNames")
      val roIds = passingEros.toList.map(_.getName)
      println(s"           Using mechanism: $roIds")
    }
    passingEros.size > 0
  }

  def chemNameToInChI(name: String) = {
    val nameEncoded = URLEncoder.encode(name, "UTF-8")
    val uri = new URI("https", "cactus.nci.nih.gov", "/chemical/structure/" + name + "/stdinchi", null)
    try {
      val ret = scala.io.Source.fromURL(uri.toString).mkString
      if (ret startsWith "InChI=")
        Some((name, ret))
      else {
        println(s"Gotten bad response for: $name")
        None
      }
    } catch {
      case e: FileNotFoundException => {
        println(s"Gotten null response for: $name")
        None
      }
    }
  }

  def extractChemicals(doc: Document) = {
    val xpath = XPathFactory.newInstance.newXPath
    val nodes = xpath.evaluate(MOLECULE_PATH, doc, XPathConstants.NODESET).asInstanceOf[NodeList]
    def getText(n: Node): List[String] = {
      if (n == null)
        List() 
      else if (n.getNodeType() == Node.TEXT_NODE)
        List(n.getTextContent)
      else {
        val children = n.getChildNodes
        val childrenTrav = for (idx <- 0 to children.getLength) yield children.item(idx)
        childrenTrav.toList.map(getText).reduce(_ ++ _)
      }
    }
    val nodesTrav = for (idx <- 0 to nodes.getLength) yield nodes.item(idx)
    val mols = nodesTrav.map(getText).map(tokens => if (tokens.isEmpty) "" else tokens.reduce(_ + " " + _))
    // return sort|uniq
    val chemNames = mols.sortWith(_ > _).distinct
    val inchis = chemNames.map(chemNameToInChI).filter(_ != None).map{ case Some((n,i)) => n -> i }.toMap
    inchis
  }

}
