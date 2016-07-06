package com.act.analysis.proteome.sequences

import org.scalatest._

class RnaSequenceTest extends FlatSpec with Matchers {
  "RNA" should "translate RNA to proteins starting at a start codon" in {
    RNA.translate("gaggaggagaugggaggaggaggauaggcggcg", minProteinSequenceLength = 1) should be(List("MGGGG"))
  }

  "RNA" should "translate RNA to proteins that are in any reading frame" in {
    RNA.translate("ggaggaggagaugggaggaggaggauaggcggcg", minProteinSequenceLength = 1) should be(List("MGGGG"))
    RNA.translate("gggaggaggagaugggaggaggaggauaggcggcg", minProteinSequenceLength = 1) should be(List("MGGGG"))
  }

  "RNA" should "not translate proteins that do not have a stop codon" in {
    RNA.translate("gaggagaugggaggaggaggagcggcg", minProteinSequenceLength = 1) should be(List[String]())
  }

  "RNA" should "return multiple proteins if multiple start codons exist within the same frame" in {
    RNA.translate("augaugauguauuauuauuauuag", minProteinSequenceLength = 1) should be(List("MMMYYYY", "MMYYYY", "MYYYY"))
  }

  "RNA" should "return multiple proteins if multiple start codons exist in a different frame" in {
    RNA.translate("augaaugccagauucuucuaguagcuagcca", minProteinSequenceLength = 1) should be(List("MNARFF", "MPDSSSS"))
  }

  "RNA" should "be able to limit the size of possible sequences" in {
    RNA.translate("augaagaagaagaagaagaagaagaaguagaugaaaaaauag", minProteinSequenceLength = 3).length should be(2)
    RNA.translate("augaagaagaagaagaagaagaagaaguagaugaaaaaauag", minProteinSequenceLength = 4).length should be(1)
  }

  "RNA" should "be able to be chained with DNA to create a protein" in {
    RNA.translate(DNA.translate("tacgaggaggaggaggagatc"), minProteinSequenceLength = 1) should be(List("MLLLLL"))
  }
}
