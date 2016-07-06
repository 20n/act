package com.act.analysis.proteome.sequences

import org.scalatest._

class DnaSequenceTest extends FlatSpec with Matchers {

  "DNA" should "correctly translate basic sequences" in {
    DNA.translate("aaa") should be("uuu")
    DNA.translate("ttt") should be("aaa")
    DNA.translate("ccc") should be("ggg")
    DNA.translate("ggg") should be("ccc")
  }

  "DNA" should "not allow empty sequences and throw an error" in {
    a[Exception] should be thrownBy {
      DNA.translate("")
    }
  }

  "DNA" should "throw NoSuchElementException when invalid character exists in sequence" in {
    a[NoSuchElementException] should be thrownBy {
      DNA.translate("q")
    }

    a[NoSuchElementException] should be thrownBy {
      DNA.translate("aaao")
    }
  }
}
