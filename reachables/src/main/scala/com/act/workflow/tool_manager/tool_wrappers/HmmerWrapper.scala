package com.act.workflow.tool_manager.tool_wrappers

import java.io.File

import com.act.workflow.tool_manager.jobs.ShellJob

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
  def hmmalign(hmmFile: File, seqFile: File, outputFile: File): ShellJob = {
    constructJob(HmmCommands.HmmAlign.getCommand, Option(HmmCommands.HmmAlign.getCommand),
      List("--amino", hmmFile.getAbsolutePath, seqFile.getAbsolutePath, "-o", outputFile.getAbsolutePath))
  }

  /**
    * Builds an HMM profile from a sequence alignment
    *
    * @param outputHmmFile Where to write the output
    * @param msaFile       The multiple sequence alignment file to construct the profile from
    */
  def hmmbuild(outputHmmFile: File, msaFile: File): ShellJob  = {
    constructJob(
      HmmCommands.HmmBuild.getCommand,
      Option(HmmCommands.HmmBuild.getCommand),
      List("--amino", outputHmmFile.getAbsolutePath, msaFile.getAbsolutePath))
  }

  /**
    * Scans
    *
    * @param hmmDatabase  A database of protein files (Big fasta, indexed via hmmpress)
    * @param sequenceFile A single sequence
    * @param outputFile   Where to place output file
    */
  def hmmscan(hmmDatabase: String, sequenceFile: File, outputFile: File): ShellJob = {
    val job = constructJob(HmmCommands.HmmScan.getCommand, Option(HmmCommands.HmmScan.getCommand),
      List("-o", outputFile.getAbsolutePath, hmmDatabase, sequenceFile.getAbsolutePath))

    // Set a retry job of press if something goes wrong
    // If you want a laugh, read the documentation for this function with option -f , it will overwrite bad files
    job.setJobToRunPriorToRetrying(constructJob(HmmCommands.HmmPress.getCommand, Option(HmmCommands.HmmPress.getCommand),
      List("-f", hmmDatabase), retryJob = true))
    job
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
  def hmmpress(hmmFile: File): ShellJob  = {
    constructJob(HmmCommands.HmmPress.getCommand, Option(HmmCommands.HmmPress.getCommand),
      List("-f", hmmFile.getAbsolutePath))
  }


  /**
    * Search profiles against a sequence database
    *
    * @param hmmFile          File containing 1 or more HMM profiles
    * @param sequenceDatabase Sequences to search against
    * @param outputFile       Where to place the results
    */
  def hmmsearch(hmmFile: File, sequenceDatabase: File, outputFile: File): ShellJob = {
    constructJob(HmmCommands.HmmSearch.getCommand, Option(HmmCommands.HmmSearch.getCommand),
      List("-o", outputFile.getAbsolutePath, hmmFile.getAbsolutePath, sequenceDatabase.getAbsolutePath))
  }

  /**
    * Iteratively search seqfile sequences against seqdb sequences
    *
    * @param sequenceFile     Query sequences
    * @param sequenceDatabase Sequences to search against
    * @param outputFile       Where to place the results
    */
  def jackhmmer(sequenceFile: File, sequenceDatabase: String, outputFile: File): ShellJob  = {
    constructJob(HmmCommands.JackHammr.getCommand, Option(HmmCommands.JackHammr.getCommand),
      List(sequenceFile.getAbsolutePath, sequenceDatabase, "-o", outputFile.getAbsolutePath))
  }

  /**
    * Search protein sequences against sequence database
    *
    * @param sequenceFile     Sequences to query sequenceDB
    * @param sequenceDatabase Sequences queried against
    * @param outputFile       Where to place the results
    */
  def phmmer(sequenceFile: File, sequenceDatabase: String, outputFile: File): ShellJob  = {
    constructJob(HmmCommands.Phmmer.getCommand, Option(HmmCommands.Phmmer.getCommand),
      List(sequenceFile.getAbsolutePath, sequenceDatabase, "-o", outputFile.getAbsolutePath))
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
  object HmmCommands {
    sealed class Command(command: String) {
      def getCommand: String = command
    }

    case object HmmBuild extends Command("hmmbuild")
    case object HmmAlign extends Command("hmmalign")
    case object HmmScan extends Command("hmmscan")
    case object HmmPress extends Command("hmmpress")
    case object HmmSearch extends Command("hmmsearch")
    case object JackHammr extends Command("jackhmmr")
    case object Phmmer extends Command("phmmer")
    case object HmmConvert extends Command("hmmconvert")
    case object HmmEmit extends Command("hmmemit")
    case object HmmFetch extends Command("hmmfetch")
    case object HmmLogo extends Command("hmmlogo")
    case object HmmPgmd extends Command("hmmpgmd")
  }

}
