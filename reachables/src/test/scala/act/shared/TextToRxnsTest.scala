package act.shared

import org.scalatest.{FlatSpec, Matchers}

class TextToRxnsTest extends FlatSpec with Matchers {

  val testSentences = List(
    // Should extract 1 reaction:
    // p-aminophenylphosphocholine + H2O -> p-aminophenol + choline phosphate
    """Convert p-aminophenylphosphocholine and H2O to p-aminophenol and choline phosphate in 3.1.4.38""",

    // should extract:
    // 4-chloro-phenylglycine + H2O + O2 -> (4-chlorophenyl)acetic acid + NH3 + H2O2
    """The cell converted 4-chloro-phenylglycine to (4-chlorophenyl)acetic acid in
      the presence of water and O2 and released ammonia and H2O2.
      This happened in Rhodosporidium toruloides and BRENDA has it under 1.4.3.3""",

    // Should extract 3 reactions:
    // p-aminophenylphosphocholine -> p-aminophenol + choline phosphate
    // pyruvate -> lactate
    // lactate -> pyruvate
    """Convert H2O and p-aminophenylphosphocholine to p-aminophenol and choline phosphate,
      a reaction that is from the EC class 3.1.4.38. The cell also converted pyruvate to lactate."""
  )



  val validReactions: List[ValidatedRxn] = TextToRxns.getRxnsFromString(testSentences.head)
  validReactions.size should be > 0


  ignore should "be able to extract sentences from string, URLs and pdfs" in {
    for (testSent <- testSentences) {
      List(1,2).size should be > 0
      val validReactions: List[ValidatedRxn] = TextToRxns.getRxnsFromString(testSent)
      println(validReactions)
      validReactions.size should be > 0
    }

    // test extractions from a web url
    val testURL1 = "https://www.ncbi.nlm.nih.gov/pubmed/20564561?dopt=Abstract&report=abstract&format=text"
    val testURL2 = "http://www.nature.com/articles/ncomms5037"

    // TextToRxns.getRxnsFromURL(testURL1).length should be > 0
    // TextToRxns.getRxnsFromPDF("/Volumes/shared-data/Saurabh/text2rxns/limitedchems.pdf").length should be > 0
  }
}
