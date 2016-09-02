package com.act.analysis.chemicals

import com.act.workflow.tool_manager.jobs.management.JobManager
import org.scalatest.{FlatSpec, Matchers}

class ChemicalSimilarityTest extends FlatSpec with Matchers {
  "ChemicalSimilarity" should "score the same InChI as completely similar" in {

    ChemicalSimilarity.calculateSimilarity()
  }
}
