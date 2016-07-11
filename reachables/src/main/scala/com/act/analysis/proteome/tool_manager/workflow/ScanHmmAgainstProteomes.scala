package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

import com.act.analysis.proteome.tool_manager.jobs.{HeaderJob, Job, JobManager, ScalaJob}
import com.act.analysis.proteome.tool_manager.tool_wrappers.{HmmerWrapper, ScalaJobWrapper, ShellWrapper}
import shapeless.list

import scala.collection.mutable.ListBuffer

class ScanHmmAgainstProteomes(hmmDirectory: File, proteomeFile: File, outputDirectory: File) extends Workflow {
  def defineWorkflow(): Job = {
    HmmerWrapper.setBinariesLocation("/Users/michaellampe/ThirdPartySoftware/hmmer-3.1b2-macosx-intel/binaries")


    // Dummy command to head all the rest
    val headerCommand = new HeaderJob()
    val hmmFiles = hmmDirectory.list.filter(x => x.endsWith(".hmm"))

    val runBatch = ListBuffer[Job]()
    hmmFiles.foreach(hmmFile =>
    {
      val job = HmmerWrapper.hmmsearch(new File(hmmDirectory, hmmFile).getAbsolutePath,
                                       proteomeFile.getAbsolutePath,
                                       new File(outputDirectory.getAbsoluteFile, s"Proteome:Hmm_${hmmFile}.results").getAbsolutePath
                                      )
      runBatch.append(job)
    })

    headerCommand.thenRunBatch(runBatch.toList)
  }
}
