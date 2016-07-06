package com.act.analysis.proteome.sequences

/**
  * Defines a sequence of RNA and all the traits that come with it
  */
object RNA {
  // Codon table
  private val characterConversions = Map(
    ("uuu", "F"), ("uuc", "F"), ("uua", "L"), ("uug", "L"),
    ("cuu", "L"), ("cuc", "L"), ("cua", "L"), ("cug", "L"),
    ("auu", "I"), ("auc", "I"), ("aua", "I"), ("aug", "M"),
    ("guu", "V"), ("guc", "V"), ("gua", "V"), ("gug", "V"),
    ("ucu", "S"), ("ucc", "S"), ("uca", "S"), ("ucg", "S"),
    ("ccu", "P"), ("ccc", "P"), ("cca", "P"), ("ccg", "P"),
    ("acu", "T"), ("acc", "T"), ("aca", "T"), ("acg", "T"),
    ("gcu", "A"), ("gcc", "A"), ("gca", "A"), ("gcg", "A"),
    ("uau", "Y"), ("uac", "Y"), ("uaa", "*"), ("uag", "*"),
    ("cau", "H"), ("cac", "H"), ("caa", "Q"), ("cag", "Q"),
    ("aau", "N"), ("aac", "N"), ("aaa", "K"), ("aag", "K"),
    ("gau", "D"), ("gac", "D"), ("gaa", "E"), ("gag", "E"),
    ("ugu", "C"), ("ugc", "C"), ("uga", "*"), ("ugg", "W"),
    ("cgu", "R"), ("cgc", "R"), ("cga", "R"), ("cgg", "R"),
    ("agu", "S"), ("agc", "S"), ("aga", "R"), ("agg", "R"),
    ("ggu", "G"), ("ggc", "G"), ("gga", "G"), ("ggg", "G"))

  private val stopCodons = List("uaa", "uag", "uga")
  private val startCodon = "aug"

  /**
    * Translates a sequence into a list of proteins, if any
    *
    * @param sequence                 - RNA sequence as a string
    * @param minProteinSequenceLength - The smallest allowed protein, defaults to 100
    * @return List of strings that are possible proteins from the RNA
    */
  def translate(sequence: String, minProteinSequenceLength: Int = 100): List[String] = {
    require(sequence.length > 0, message = "Sequence must be of a length of at least 1.")

    def findProteinsInFrame(currentFrameSequence: String, offset: Int = 0): List[String] = {
      // Offset allows us to change frames by dropping the first n characters.
      // From there we divide into 3s and filter out any that isn't a 3 (The trailing sequences, if any)
      val offsetSequence = currentFrameSequence.drop(offset).toLowerCase
      val currentFrame = offsetSequence.grouped(3).toList
      val filteredFrame = currentFrame.filter(x => x.length == 3)

      /**
        * Looks through frame to find possible protein sequences
        */
      def findProteins(currentFrameList: List[String]): List[String] = {
        if (currentFrameList.length < minProteinSequenceLength | currentFrameList.isEmpty) return List[String]()

        // Search for start codons, then stopcodons after that
        val (_, afterStartCodon) = currentFrameList.span(x => x != startCodon)
        val (untranslatedProtein, _) = afterStartCodon.span(x => !stopCodons.contains(x))

        // No stop codons are left in frame, so return
        if (afterStartCodon.length == untranslatedProtein.length) return List[String]()

        // Translate into protein
        val protein = untranslatedProtein.map(x => characterConversions(x)).mkString

        // Remove start codon so that search can continue from that point
        val currentFrameWithoutStartCodon = afterStartCodon.drop(1)

        // Validate correct sequence length

        var append = List[String]()
        if (protein.length >= minProteinSequenceLength) append = List(protein.mkString)

        append ::: findProteins(currentFrameWithoutStartCodon)
      }

      findProteins(filteredFrame)
    }

    // Construct all possible reading frames
    val readingFrame1 = findProteinsInFrame(sequence, offset = 0)
    val readingFrame2 = findProteinsInFrame(sequence, offset = 1)
    val readingFrame3 = findProteinsInFrame(sequence, offset = 2)

    readingFrame1 ::: readingFrame2 ::: readingFrame3
  }
}
