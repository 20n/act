package com.act.analysis.proteome.tools

import java.nio.file.{Files, Paths}

import scala.sys.process._

/**
  * Wraps the HMMER toolkit in a way that we can easily call from Scala/Java.
  * Hammer documentation comes from http://eddylab.org/software/hmmer3/3.1b2/Userguide.pdf
  */
object HmmerWrapper extends ToolWrapper {
  /*
  Build Models - These methods handle building up HMM models
  */
  /**
    * Invokes hmmalign on an amino acid sequence file.
    *
    * @param hmmFile    Individual HMM profile
    * @param seqFile    Sequence Profile
    * @param outputFile Where to write the output to
    */
  def hmmalign(hmmFile: String, seqFile: String, outputFile: String): Unit = {
    nonblockingJobWrapper {
      val output = constructCommand("hmmalign", List("--amino", hmmFile, seqFile)).!!
      saveToOutputFile(outputFile, output)
    }
  }

  /**
    * Builds an HMM profile from a sequence alignment
    *
    * @param outputHmmFile Where to write the output
    * @param msaFile       The multiple sequence alignment file to construct the profile from
    */
  def hmmbuild(outputHmmFile: String, msaFile: String): Unit = {
    nonblockingJobWrapper {
      constructCommand("hmmbuild", List("--amino", outputHmmFile, msaFile)).!!
    }
  }


  /*
  Protein queries - These methods handle searching between proteins and HMMs
   */

  /**
    * Scans
    *
    * @param hmmDatabase
    * @param sequenceFile
    * @param outputFile
    */
  def hmmscan(hmmDatabase: String, sequenceFile: String, outputFile: String): Unit = {
    nonblockingJobWrapper {
      /*
      Let's assume if one of the four files from hmmpress are here, they will all be

      If it isn't, we want to run hmmpress in a blocking way so that we create the files prior to hmmscan starting
       */
      if (!Files.exists(Paths.get(hmmDatabase + ".h3f"))) hmmpress(hmmDatabase, blocking = true)
      println("Started scan")

      // We use the -o option here because we want to get the integer code in the case of an error
      val output = constructCommand("hmmscan", List("-o", outputFile, hmmDatabase, sequenceFile)).!

      // Nonzero output code means error, which may occur if hmmpress files are corrupted.
      // We can attempt to fix this by checking for a bad output and pressing if we see that.
      if (output != 0) {
        println("Error in hmmscan.  Attempting to hmmpress again prior to trying another scan.")
        hmmpress(hmmDatabase, blocking = true)
        println("Press complete, starting scan.")
        constructCommand("hmmscan", List("-o", outputFile, hmmDatabase, sequenceFile)).!
      }
    }
  }

  /**
    * This writes to the directory where HMM file is currently and creates four files
    * hmmFile.{h3f, h3i, h3m, h3p}
    * *
    * We sometimes may want to do this in a blocking fashion
    * (For example, prior to hmmscan which requires the above 4 files first),
    * so that is also available, but turned off by default
    *
    * @param hmmFile  File containing multiple HMM profiles
    * @param blocking If to run as a future or not, blocking means program will not continue until press is complete.
    */
  def hmmpress(hmmFile: String, blocking: Boolean = false): Unit = {
    // If you want a laugh, read the documentation for this function with option -f
    val command = constructCommand("hmmpress", List("-f", hmmFile))
    if (blocking) {
      command.!!
    } else {
      nonblockingJobWrapper {
        command.!!
      }
    }
  }

  /**
    * Search profiles against a sequence database
    *
    * @param hmmFile          File containing 1 or more HMM profiles
    * @param sequenceDatabase Sequences to search against
    * @param outputFile       Where to place the results
    */
  def hmmsearch(hmmFile: String, sequenceDatabase: String, outputFile: String): Unit = {
    nonblockingJobWrapper {
      val output = constructCommand("hmmsearch", List(hmmFile, sequenceDatabase)).!!
      saveToOutputFile(outputFile, output)
    }
  }


  /**
    * Iteratively search seqfile sequences against seqdb sequences
    *
    * @param sequenceFile     Query sequences
    * @param sequenceDatabase Sequences to search against
    * @param outputFile       Where to place the results
    */
  def jackhmmer(sequenceFile: String, sequenceDatabase: String, outputFile: String): Unit = {
    nonblockingJobWrapper {
      val output = constructCommand("jackhmmer", List(sequenceFile, sequenceDatabase)).!!
      saveToOutputFile(outputFile, output)
    }
  }

  /**
    * Search protein sequences against sequence database
    *
    * @param sequenceFile     Sequences to query sequenceDB
    * @param sequenceDatabase Sequences queried against
    * @param outputFile       Where to place the results
    */
  def phmmer(sequenceFile: String, sequenceDatabase: String, outputFile: String): Unit = {
    nonblockingJobWrapper {
      val output = constructCommand("phmmer", List(sequenceFile, sequenceDatabase)).!!
      saveToOutputFile(outputFile, output)
    }
  }


  /*
  Other utilities - These do conversions or give added benefits to HMMs/Proteins
   */
  def hmmconvert(): Unit = {
    throw new UnsupportedOperationException
    // TODO: Implement
  }

  def hmmemit(): Unit = {
    throw new UnsupportedOperationException
    // TODO: Implement
  }

  def hmmfetch(): Unit = {
    throw new UnsupportedOperationException
    // TODO: Implement
  }

  def hmmlogo(): Unit = {
    throw new UnsupportedOperationException
    // TODO: Implement
  }

  def hmmpgmd(): Unit = {
    throw new UnsupportedOperationException
    // TODO: Implement
  }
}
