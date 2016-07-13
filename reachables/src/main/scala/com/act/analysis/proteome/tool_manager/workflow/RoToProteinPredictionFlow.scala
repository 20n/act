package com.act.analysis.proteome.tool_manager.workflow

import java.io.File

import act.server.MongoDB
import com.act.analysis.proteome.tool_manager.jobs.{Job, JobManager}
import com.act.analysis.proteome.tool_manager.tool_wrappers.{ClustalOmegaWrapper, HmmerWrapper, ScalaJobWrapper, ShellWrapper}
import com.mongodb.{BasicDBList, BasicDBObject, DBObject}
import org.biojava.bio.seq.ProteinTools
import org.biojava.nbio.core.sequence.ProteinSequence
import org.biojava.nbio.core.sequence.compound.AminoAcidCompoundSet
import org.biojava.nbio.core.sequence.io.ProteinSequenceCreator
import org.biojava.nbio.core.sequence.io.FastaWriterHelper
import spire.syntax.field
import spray.http.CacheDirectives.public

import scala.annotation.meta.beanGetter
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.runtime.{universe => ru}


class RoToProteinPredictionFlow extends Workflow {
  var roValue: Option[Int] = None
  protected var outputFastaFromRos: Option[File] = None
  protected var alignedFastaFileOutput: Option[File] = None
  protected var outputHmmProfile: Option[File] = None
  protected var resultsFile: Option[File] = None

  override def parseArgs(args: List[String]){
    // Parse args w/ arg builder here
    println(args)

    ShellWrapper.shellCommand(List("ls"))






    if (checkIfAllFieldsAreSomething()) {
      startWorkflowBlocking()
    } else {
      throw new Exception()
    }
  }


  def defineWorkflow(): Job = {
    val panProteomeLocation = "/Volumes/shared-data/Michael/PanProteome/pan_proteome.fasta"

    val roToFasta = ScalaJobWrapper.wrapScalaFunction(getRosToFastaFromDb)

    // Align sequence so we can build an HMM
    ClustalOmegaWrapper.setBinariesLocation("/Users/michaellampe/ThirdPartySoftware/clustal-omega-1.2.0-macosx")
    val alignFastaSequences = ClustalOmegaWrapper.alignProteinFastaFile(outputFastaFromRos.get.getAbsolutePath,
                                                                        alignedFastaFileOutput.get.getAbsolutePath)
    alignFastaSequences.writeOutputStreamToLogger()
    alignFastaSequences.writeErrorStreamToLogger()

    // Build a new HMM
    val buildHmmFromFasta = HmmerWrapper.hmmbuild(outputHmmProfile.get.getAbsolutePath,
      alignedFastaFileOutput.get.getAbsolutePath)
    buildHmmFromFasta.writeErrorStreamToLogger()
    buildHmmFromFasta.writeOutputStreamToLogger()

    // Use the built HMM to find novel proteins
    val searchNewHmmAgainstPanProteome = HmmerWrapper.hmmsearch(outputHmmProfile.get.getAbsolutePath,
                                                              panProteomeLocation,
                                                              resultsFile.get.getAbsolutePath)
    searchNewHmmAgainstPanProteome.writeErrorStreamToLogger()
    searchNewHmmAgainstPanProteome.writeOutputStreamToLogger()

    // Setup ordering
    roToFasta.thenRun(alignFastaSequences).thenRun(buildHmmFromFasta).thenRun(searchNewHmmAgainstPanProteome)

    roToFasta
  }

  def getRosToFastaFromDb(): Unit =  {
    // Instantiate Mongo host.
    val host = "localhost"
    val port = 27017
    val db = "marvin"
    val mongo = new MongoDB(host, port, db)


    /*
    Query Database for enzyme IDs based on a given RO
     */
    val key = new BasicDBObject
    val exists = new BasicDBObject
    val returnFilter = new BasicDBObject
    exists.put("$exists", "true")
    key.put(s"mechanistic_validator_result.${roValue.get}", exists)
    returnFilter.put("ecnum", 1)

    JobManager.logInfo(s"Querying reactionIds from Mongo")
    val reactionIds = mongoQueryReactions(mongo, key, returnFilter).map(x => x.get("ecnum"))
    JobManager.logInfo(s"Found ${reactionIds.size} enzyme ID numbers from RO.")


    /*
    Query sequence database for enzyme sequences
     */
    val seqKey = new BasicDBObject
    val in = new BasicDBObject
    val reactionIdsList = new BasicDBList
    for (rId <- reactionIds) {
      reactionIdsList.add(rId)
    }

    in.put("$in", reactionIdsList)
    seqKey.put("ecnum", in)
    val seqFilter = new BasicDBObject
    seqFilter.put("seq", 1)
    seqFilter.put("ecnum", 1)
    seqFilter.put("metadata.name", 1)

    JobManager.logInfo("Querying Enzyme IDs for sequences from Mongo")
    val sequenceReturn = mongoQuerySequences(mongo, seqKey, seqFilter).toList
    JobManager.logInfo("Finished sequence query.")

    // Map sequences and name to proteinSequences

    val sequences = sequenceReturn.map(x => {
      val seq = x.get("seq")
      if (seq != null) {
        val newSeq = new ProteinSequence(seq.toString)

      // TODO CLEANUP

        val num = x.get("ecnum")
        val metadataObject: DBObject = x.get("metadata").asInstanceOf[DBObject]
        val name = metadataObject.get("name")

        if (num != null) {
          newSeq.setOriginalHeader(s"${name.toString} + | + ${num.toString}")
          Some(newSeq)
        } else {
          None
        }
      } else {
        None
      }
    })

    val proteinSequences = sequences.flatten

    // Write to output
    JobManager.logInfo(s"Writing ${sequenceReturn.length} " +
      s"sequences to Fasta file at ${outputFastaFromRos.get.getAbsolutePath}.")
    outputFastaFromRos.get.createNewFile()
    FastaWriterHelper.writeProteinSequence(outputFastaFromRos.get, proteinSequences.asJavaCollection)
  }

  def mongoQueryReactions(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Set[DBObject] ={
    val ret = mongo.getIteratorOverReactions(key, false, filter)
    val buffer = mutable.Set[DBObject]()
    while (ret.hasNext) {
      val current = ret.next
      buffer add current
    }
    buffer.toSet
  }

  def mongoQuerySequences(mongo: MongoDB, key: BasicDBObject, filter: BasicDBObject): Set[DBObject] ={
    val ret = mongo.getIteratorOverSeq(key, false, filter)
    val buffer = mutable.Set[DBObject]()
    while (ret.hasNext) {
      val current = ret.next
      buffer add current
    }
    buffer.toSet
  }

}
