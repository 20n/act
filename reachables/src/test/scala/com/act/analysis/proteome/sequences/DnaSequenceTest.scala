package com.act.analysis.proteome.sequences

import org.scalatest._

class DnaSequenceTest extends FlatSpec with Matchers {

  "DNA" should "correctly translate basic sequences without complement" in {
    DNA.translate("aaa", complement = false) should be("uuu")
    DNA.translate("ttt", complement = false) should be("aaa")
    DNA.translate("ccc", complement = false) should be("ggg")
    DNA.translate("ggg", complement = false) should be("ccc")
  }

  "DNA" should "by default, return the RNA complement of itself (Only t->u changes)" in {
    DNA.translate("aaa") should be("aaa")
    DNA.translate("ttt") should be("uuu")
    DNA.translate("ccc") should be("ccc")
    DNA.translate("ggg") should be("ggg")
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
