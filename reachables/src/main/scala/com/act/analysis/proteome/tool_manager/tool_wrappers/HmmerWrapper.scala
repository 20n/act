package com.act.analysis.proteome.tool_manager.tool_wrappers

import com.act.analysis.proteome.tool_manager.jobs.Job

/**
  * Wraps the HMMER toolkit in a way that we can easily call from Scala/Java.
  * Hammer documentation comes from http://eddylab.org/software/hmmer3/3.1b2/Userguide.pdf
  */
object HmmerWrapper extends ToolWrapper {

  /**
    * Invokes hmmalign on an amino acid sequence file.
    *
    * @param hmmFile    Individual HMM profile
    * @param seqFile    Sequence Profile
    * @param outputFile Where to write the output to
    */
  def hmmalign(hmmFile: String, seqFile: String, outputFile: String): Job = {
    constructJob(HmmCommands.HmmAlign, List("--amino", hmmFile, seqFile))
  }

  /**
    * Builds an HMM profile from a sequence alignment
    *
    * @param outputHmmFile Where to write the output
    * @param msaFile       The multiple sequence alignment file to construct the profile from
    */
  def hmmbuild(outputHmmFile: String, msaFile: String): Job = {
    constructJob(HmmCommands.HmmBuild, List("--amino", outputHmmFile, msaFile))
  }

  /**
    * Scans
    *
    * @param hmmDatabase
    * @param sequenceFile
    * @param outputFile
    */
  def hmmscan(hmmDatabase: String, sequenceFile: String, outputFile: String): Job = {
    val job = constructJob(HmmCommands.HmmScan, List("-o", outputFile, hmmDatabase, sequenceFile))

    // Set a retry job of press if something goes wrong
    // If you want a laugh, read the documentation for this function with option -f , it will overwrite bad files
    job.setJobToRunPriorToRetry(constructJob(HmmCommands.HmmPress, List("-f", hmmDatabase), retryJob=true))
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
    */
  def hmmpress(hmmFile: String): Job = {
    constructJob(HmmCommands.HmmPress, List("-f",hmmFile))
  }


  /**
    * Search profiles against a sequence database
    *
    * @param hmmFile          File containing 1 or more HMM profiles
    * @param sequenceDatabase Sequences to search against
    * @param outputFile       Where to place the results
    */
  def hmmsearch(hmmFile: String, sequenceDatabase: String, outputFile: String): Unit = {
    constructJob(HmmCommands.HmmSearch, List(hmmFile, sequenceDatabase))
  }

  /**
    * Iteratively search seqfile sequences against seqdb sequences
    *
    * @param sequenceFile     Query sequences
    * @param sequenceDatabase Sequences to search against
    * @param outputFile       Where to place the results
    */
  def jackhmmer(sequenceFile: String, sequenceDatabase: String, outputFile: String): Unit = {
    constructJob(HmmCommands.JackHammr, List(sequenceFile, sequenceDatabase))
  }

  /**
    * Search protein sequences against sequence database
    *
    * @param sequenceFile     Sequences to query sequenceDB
    * @param sequenceDatabase Sequences queried against
    * @param outputFile       Where to place the results
    */
  def phmmer(sequenceFile: String, sequenceDatabase: String, outputFile: String): Unit = {
    constructJob(HmmCommands.Phmmer, List(sequenceFile, sequenceDatabase))
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

  //TODO All commands that I plan to implement
  object HmmCommands extends Enumeration {
    type HmmCommands = Value
    val HmmBuild = "hmmbuild"
    val HmmAlign = "hmmalign"
    val HmmScan = "hmmscan"
    val HmmPress = "hmmpress"
    val HmmSearch = "hmmsearch"
    val JackHammr = "jackhmmr"
    val Phmmer = "phmmer"
    val HmmConvert = "hmmconvert"
    val HmmEmit = "hmmemit"
    val HmmFetch = "hmmfetch"
    val HmmLogo = "hmmlogo"
    val HmmPgmd = "hmmpgmd"
  }

}
