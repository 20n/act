package act.shared

import java.io.{PrintWriter, File, ObjectOutputStream, ObjectInputStream, FileOutputStream, FileInputStream}
import java.io.FileNotFoundException
import java.net.{URI, URLEncoder}
import java.util.InputMismatchException
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

import com.act.analysis.chemicals.molecules.{MoleculeExporter, MoleculeFormat, MoleculeImporter}
import com.act.biointerpretation.l2expansion.SparkInstance
import com.act.biointerpretation.cofactorremoval.CofactorsCorpus
import com.act.biointerpretation.mechanisminspection.Ero

import scala.collection.JavaConverters._

sealed trait InputType
case class TextFile(val fname: String) extends InputType
case class WebURL(val url: String) extends InputType
case class RawText(val text: String) extends InputType
case class PdfFile(val fname: String) extends InputType

object TextToRxns {

  val MIN_CHEM_NAME_LEN = 3
  val MAX_CHEM_COMBINATION_SZ = 2

  val cofactors: List[String] = {
    val defaultMoleculeFormat = MoleculeFormat.strictNoStereoInchi
    def sanitize(inchi: String) = {
      val mol = MoleculeImporter.importMolecule(inchi)
      MoleculeExporter.exportMolecule(mol, defaultMoleculeFormat)
    }
    val c = new CofactorsCorpus()
    c.loadCorpus()
    c.getInchiToName.asScala.keys.map(sanitize).toList
  }

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

      getRxnsFrom(data)
    }
  }

  def runChecks(out: PrintWriter) {
    // test extractions from sample sentences
    val testSentences = List(
      // p-aminophenylphosphocholine + H2O  p-aminophenol + choline phosphate
      """Convert p-aminophenylphosphocholine and H2O to p-aminophenol and choline phosphate in 3.1.4.38""",
      // the following triple set should be validated:
      // 4-chloro-phenylglycine + H2O + O2 -> (4-chlorophenyl)acetic acid + NH3 + H2O2
      """The cell converted 4-chloro-phenylglycine to (4-chlorophenyl)acetic acid in 
      the presence of water and O2 and released ammonia and H2O2.
      This happened in Rhodosporidium toruloides and BRENDA has it under 1.4.3.3"""
    )
    for (testSent <- testSentences) {
      println(s"Extracting from: '${testSent.substring(0,75)}...'")
      getRxnsFromString(testSent)
    }

    // test extractions from a web url
    val testURL = "https://www.ncbi.nlm.nih.gov/pubmed/20564561?dopt=Abstract&report=abstract&format=text"
    getRxnsFromURL(testURL)

    // test extractions from a PDF file
    getRxnsFromPDF("/Volumes/shared-data/Saurabh/text2rxns/coli-paper.pdf")
  }

  def getRxnsFrom(dataSrc: Option[InputType]): List[ValidatedRxn] = {
    val extractor = new TextToRxns
    val rxns = extractor.extract(dataSrc)
    extractor.flushWebCache
    rxns
  }
  def getRxnsFromWO(dataSrc: Option[InputType]) = unObjectify(getRxnsFrom(dataSrc))

  def unObjectify(rxns: List[ValidatedRxn]): List[List[List[List[String]]]] = {
    def deconstructNameInChI(x: NamedInChI) = List(x.name, x.inchi) // array 1-deep
    def deconstructRxn(r: ValidatedRxn): List[List[List[String]]] = {
      val substrates = r.substrates.map(deconstructNameInChI).toList // array 2-deep
      val products = r.products.map(deconstructNameInChI).toList // array 2-deep
      val ros = r.validatingROs match { 
        case None => List(List[String]())
        case Some(rrs) => List(rrs.map(_.getName).toList)
      }  // array 2-deep
      List(substrates, products, ros) // array 3-deep
    }
    rxns.map(deconstructRxn).toList // array 4-deep
  }

  def getRxnsFromURL(url: String) = getRxnsFromWO(Some(new WebURL(url)))
  def getRxnsFromPDF(fileLoc: String) = getRxnsFromWO(Some(new PdfFile(fileLoc)))
  def getRxnsFromTxt(fileLoc: String) = getRxnsFromWO(Some(new TextFile(fileLoc)))
  def getRxnsFromString(sentence: String) = getRxnsFromWO(Some(new RawText(sentence)))

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

class NamedInChI(val name: String, val inchi: String) {
  override def toString = name
}

class ValidatedRxn(
  val substrates: List[NamedInChI], 
  val products: List[NamedInChI],
  val validatingROs: Option[List[Ero]]
) {
  override def toString = {
    val s = substrates.map(_.toString).reduce(_ + " + " + _)
    val p = products.map(_.toString).reduce(_ + " + " + _)
    val ros = validatingROs match {
      case None => "no ROs"
      case Some(ros) => ros.map(_.getName).toString
    }
    s"$s -> $p [$ros]"
  }
}

class TextToRxns(val webCacheLoc: String = "text2rxns.webcache") {

  var webCache: Map[URI, String] = {
    val webc: Map[URI, String] = try {
      val ois = new ObjectInputStream(new FileInputStream(webCacheLoc))
      val obj = ois.readObject
      obj match {
        case cache: Map[URI, String] => cache
        case _ => throw new InputMismatchException 
      }
    } catch {
      case e: FileNotFoundException => {
        println(s"Web cache not found. Initializing new.")
        Map()
      }
      case e: InputMismatchException => {
        println(s"Invalid web cache. Resetting.")
        Map()
      }
    }
    println(s"Cache retrieved. Size = ${webc.size}")
    webc
  }

  def flushWebCache() = {
    val oos = new ObjectOutputStream(new FileOutputStream(webCacheLoc))
    oos.writeObject(webCache)
    oos.close
    println(s"Web cache flushed.")
  }

  def extract(dataSrc: Option[InputType]) = {
    dataSrc match {
      case None => List()
      case Some(incoming) => {
        val textData = incoming match {
          case WebURL(url) => {
            val uri = new URI(url)
            retrieve(uri)
          }
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
  }

  val MOLECULE_PATH = "//MOLECULE"

  def getChemicals(text: String) = {
    // Calling ChemistryPOSTagger
    val posContainer = ChemistryPOSTagger.getDefaultInstance().runTaggers(text)

    // Returns a string of TAG TOKEN format (e.g.: DT The NN cat VB sat IN on DT the NN matt)
    // Call ChemistrySentenceParser either by passing the POSContainer or by InputStream
    val chemistrySentenceParser = new ChemistrySentenceParser(posContainer)

    // Create a parseTree of the tagged input
    chemistrySentenceParser.parseTags()

    // Return an XMLDoc
    val doc = chemistrySentenceParser.makeXMLDocument()
    Utils.writeXMLToFile(doc,"text2rxns.NLPed.xml")

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

    val (chems, cofactors) = extractChemicals(w3cDoc)
    println(s"Removed cofactors found [${cofactors.size}]: $cofactors")
    println(s"Finding reactions using [${chems.size}]: $chems")
    val chemSubsets = limitedSzSubsets(chems)
    val subsProdCandidates = for (s <- chemSubsets; p <- chemSubsets; if (!s.equals(p))) yield (s, p)
    val passValidation = subsProdCandidates.map(passThroughEROs).filter(_.validatingROs != None)
    passValidation
  }

  def limitedSzSubsets(candidates: List[NamedInChI]): List[List[NamedInChI]] = {
    val diffSzCombs = for (sz <- 1 to TextToRxns.MAX_CHEM_COMBINATION_SZ) yield {
      candidates.combinations(sz).toList
    }
    diffSzCombs.toList.flatten
  }

  def passThroughEROs(subPrd: (List[NamedInChI], List[NamedInChI])): ValidatedRxn = {
    val substrates = subPrd._1
    val products = subPrd._2

    val subsInchis = substrates.map(_.inchi).toList
    val prodInchis = products.map(_.inchi).toList
    val passingEros = SparkInstance.validateReactionNoLicense(true)(subsInchis, prodInchis).toList

    val validatingROs = passingEros.size match {
      case 0 => None
      case _ => Some(passingEros)
    }

    new ValidatedRxn(substrates, products, validatingROs)
  }

  def chemNameToInChI(name: String) = {
    val nameEncoded = URLEncoder.encode(name, "UTF-8")
    val uri = new URI("https", "cactus.nci.nih.gov", "/chemical/structure/" + name + "/stdinchi", null)
    val ret = retrieve(uri)
    if (ret startsWith "InChI=")
      Some((name, ret))
    else {
      None
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
    val chemNames = mols.sortWith(_ > _).distinct

    // some names are just too short, e.g. "DAO", to be good chemical tokens. remove those
    val chemNamesNotTooShort = chemNames.filter(_.size > TextToRxns.MIN_CHEM_NAME_LEN)

    // from names "n" get inchis "i", and get pairs (n, i)
    val withInchis = chemNamesNotTooShort.map(chemNameToInChI) 
    val taggedInchis = withInchis.filter(_ != None).map{ case Some((n,i)) => new NamedInChI(n,i) }

    // report those inchis that we could not resolve
    val mappedNames = taggedInchis.map(_.name)
    val didNotResolveInchis = chemNames.filterNot(x => mappedNames.contains(x))
    println(s"Not resolved: $didNotResolveInchis")

    // identify the chemicals that resolved to cofactors
    def isCofactor(nameInchi: NamedInChI) = TextToRxns.cofactors.contains(nameInchi.inchi)
    val taggedChems = taggedInchis.filterNot(isCofactor).toList
    val taggedCofactors = taggedInchis.filter(isCofactor).toList

    (taggedChems, taggedCofactors)
  }

  def retrieve(uri: URI): String = {
    if (webCache contains uri) {
      webCache(uri)
    } else {
      println(s"Cache miss. Going to the web.")
      val retrieved = try {
        scala.io.Source.fromURL(uri.toString).mkString
      } catch {
        // incurred a 404. cache that as empty string
        case e: FileNotFoundException => ""
      }
      // update the webcache
      // mutable store!
      webCache = webCache + (uri -> retrieved)

      // return web retrieval
      retrieved
    }
  }

}
